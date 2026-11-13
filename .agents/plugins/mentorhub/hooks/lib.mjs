import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';

export const MIGRATION_DIR = 'backend/src/main/resources/db/migration/';

export const MIGRATION_IN_MAIN_REASON =
  'This Flyway migration is in main. Never modify, move or delete it without the project owner\'s word: ' +
  'a database outside the repository may have applied it, and Flyway validates checksums. ' +
  'Add a new migration V<next>__<description>.sql instead, or edit the migration of this change if it has one.';

export const DEFAULT_BRANCH_REASON =
  'Nothing is changed on the default branch: every change starts with Choosing a path in docs/process.md ' +
  '(the change skill), where the owner confirms the goal, the approach and the path, and the change gets its branch. ' +
  'Run that step first; do not just create a branch to get past this.';

export function readInput() {
  try {
    const raw = readFileSync(0, 'utf8').trim();
    return raw ? JSON.parse(raw) : {};
  } catch {
    return {};
  }
}

function respond(payload) {
  process.stdout.write(JSON.stringify(payload));
  process.exit(0);
}

export function allow() {
  respond({ decision: 'allow' });
}

export function ask(reason) {
  respond({ decision: 'ask', reason });
}

export function deny(reason) {
  respond({ decision: 'deny', reason });
}

export function empty() {
  respond({});
}

function run(cmd, args, options = {}) {
  try {
    return execFileSync(cmd, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'], timeout: 5000, ...options });
  } catch {
    return null;
  }
}

export function repoRoot(fromPath) {
  let dir = path.resolve(fromPath);
  while (!existsSync(dir) || !statSync(dir).isDirectory()) {
    const parent = path.dirname(dir);
    if (parent === dir) return null;
    dir = parent;
  }
  const root = run('git', ['-C', dir, 'rev-parse', '--show-toplevel']);
  return root ? root.trim() : null;
}

export function toPosix(p) {
  return p.replaceAll('\\', '/');
}

/**
 * Resolve a tool path argument against `inputCwd` (since hooks run with process.cwd()
 * set to the directory containing hooks.json, not the agent's workspace cwd).
 */
export function resolveRepoRelative(filePath, inputCwd) {
  const baseCwd = inputCwd || process.cwd();
  const resolvedPath = path.isAbsolute(filePath) ? path.resolve(filePath) : path.resolve(baseCwd, filePath);
  const root = repoRoot(resolvedPath);
  if (!root) return null;
  const rel = toPosix(path.relative(root, resolvedPath));
  if (rel.startsWith('..')) return null;
  return { root, rel, resolvedPath };
}

// A migration that is not in main yet belongs to its change and may be edited: the dev database is rebuilt from the
// migrations on every dev start, and tests use an empty database. One in main may have been applied elsewhere.
export function isMigrationInMain(root, rel) {
  if (!rel.startsWith(MIGRATION_DIR) || !rel.endsWith('.sql')) return false;
  return ['main', 'origin/main'].some((ref) => run('git', ['-C', root, 'cat-file', '-e', `${ref}:${rel}`]) !== null);
}

// A detached HEAD (a rebase or a bisect in progress) is no branch, so it is not the default one.
export function onDefaultBranch(root) {
  const branch = run('git', ['-C', root, 'symbolic-ref', '--short', '-q', 'HEAD']);
  return branch !== null && branch.trim() === 'main';
}

// Whether the checkout at root belongs to the project of this session: its own folder or one of its worktrees.
// Another repository on the machine has its own rules about its default branch.
export function isThisProject(root, workspacePaths) {
  const project = workspacePaths?.[0] || process.env.CLAUDE_PROJECT_DIR;
  if (!project) return true;
  const commonDir = (dir) => run('git', ['-C', dir, 'rev-parse', '--path-format=absolute', '--git-common-dir'])?.trim();
  const mine = commonDir(project);
  return !mine || commonDir(root) === mine;
}

export function isCommitted(root, rel) {
  return run('git', ['-C', root, 'cat-file', '-e', `HEAD:${rel}`]) !== null;
}

/**
 * Resolve a subagent's transcript.jsonl path from the coordinator's transcript path
 * (`/.../<brainDir>/<convId>/.system_generated/logs/transcript.jsonl`) and recipientId.
 * Returns null if the path structure is invalid or the target transcript does not exist.
 */
export function resolveSubagentTranscript(coordinatorTranscriptPath, recipientId) {
  if (!coordinatorTranscriptPath || !recipientId) return null;
  if (typeof coordinatorTranscriptPath !== 'string' || typeof recipientId !== 'string') return null;
  if (/[\/\\]|\.\./.test(recipientId)) return null;

  const resolved = path.resolve(coordinatorTranscriptPath);
  const logsDir = path.dirname(resolved);
  const sysGenDir = path.dirname(logsDir);
  const convDir = path.dirname(sysGenDir);
  const brainDir = path.dirname(convDir);

  if (path.basename(logsDir) !== 'logs' || path.basename(sysGenDir) !== '.system_generated') {
    return null;
  }

  const target = path.join(brainDir, recipientId, '.system_generated', 'logs', 'transcript.jsonl');
  return existsSync(target) ? target : null;
}

export function envNumber(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || raw === '') return fallback;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : fallback;
}

/**
 * Read size, exact/estimated token counts, step/planner counts, last activity timestamp,
 * and marker presence from a transcript.jsonl and its sibling transcript_full.jsonl.
 */
export function readTranscriptStats(transcriptPath, marker) {
  if (!transcriptPath || typeof transcriptPath !== 'string') return null;

  const dir = path.dirname(transcriptPath);
  const isFullInput = path.basename(transcriptPath) === 'transcript_full.jsonl';
  const compactPath = isFullInput ? path.join(dir, 'transcript.jsonl') : transcriptPath;
  const fullPath = isFullInput ? transcriptPath : path.join(dir, 'transcript_full.jsonl');

  const compactExists = existsSync(compactPath);
  const fullExists = existsSync(fullPath);
  if (!compactExists && !fullExists) return null;

  const compactStat = compactExists ? statSync(compactPath) : null;
  const fullStat = fullExists ? statSync(fullPath) : null;
  const bytes = Math.max(compactStat?.size ?? 0, fullStat?.size ?? 0);
  const estTokens = Math.round(bytes / 4);
  const fallbackMtimeMs = Math.max(compactStat?.mtimeMs ?? 0, fullStat?.mtimeMs ?? 0);

  const compactText = compactExists ? readFileSync(compactPath, 'utf8') : '';
  let fullText = null;
  const linesText = compactExists ? compactText : (fullText = readFileSync(fullPath, 'utf8'));

  let stepCount = 0;
  let plannerCount = 0;
  let lastTimestampMs = null;
  let exactTokens = null;

  for (const line of linesText.split('\n')) {
    const trimmed = line.trim();
    if (!trimmed) continue;
    try {
      const obj = JSON.parse(trimmed);
      stepCount += 1;
      if (obj.type === 'PLANNER_RESPONSE') {
        plannerCount += 1;
        if (typeof obj.input_tokens === 'number' || typeof obj.cache_read_tokens === 'number') {
          exactTokens = (obj.input_tokens ?? 0) + (obj.cache_read_tokens ?? 0);
        }
      }
      if (obj.created_at) {
        const ts = Date.parse(obj.created_at);
        if (!Number.isNaN(ts)) lastTimestampMs = ts;
      }
    } catch {
      // Ignore partial or malformed lines (e.g., concurrent flush).
    }
  }

  if (lastTimestampMs === null) {
    lastTimestampMs = fallbackMtimeMs;
  }

  let hasMarker = false;
  if (marker) {
    if (linesText.includes(marker)) {
      hasMarker = true;
    } else if (fullExists) {
      if (fullText === null) fullText = readFileSync(fullPath, 'utf8');
      hasMarker = fullText.includes(marker);
    }
  }

  const tokens = exactTokens ?? estTokens;
  return { bytes, estTokens, exactTokens, tokens, stepCount, plannerCount, lastTimestampMs, hasMarker };
}
