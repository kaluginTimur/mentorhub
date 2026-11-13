import path from 'node:path';
import {
  DEFAULT_BRANCH_REASON,
  MIGRATION_DIR,
  MIGRATION_IN_MAIN_REASON,
  allow,
  ask,
  deny,
  isMigrationInMain,
  isThisProject,
  onDefaultBranch,
  readInput,
  repoRoot,
  toPosix,
} from './lib.mjs';

const MANUAL = 'If it is really needed, ask the user to run it manually outside the agent.';

const DATA_LOSS = [
  /\bdocker\s+system\s+prune\b.*--volumes\b/,
  /\bdocker(-compose|\s+compose)\b.*(\bdown\b.*\s(-[a-zA-Z]*v[a-zA-Z]*|--volumes)\b|\s(-[a-zA-Z]*v[a-zA-Z]*|--volumes)\b.*\bdown\b)/,
  /\bflyway(:|\s+)clean\b/,
];

const SQL_CLIENT = /\b(psql|pg_restore)\b|\bdocker(\s+compose)?\s+exec\b/;
const DESTRUCTIVE_SQL = /\b(drop\s+(database|schema|table|owned)|truncate)\b/i;

const GIT_DESTRUCTIVE = [
  /\bgit\b.*\breset\b.*--hard\b/,
  /\bgit\b.*\bpush\b.*(\s-f\b|\s--force|\s\+\S)/,
  /\bgit\b.*\bclean\b.*\s-[a-zA-Z]*f/,
  /\bgit\b.*\bcheckout\b.*\s(--\s+)?\.(\s|$)/,
  /\bgit\b.*\brestore\b(?!.*--staged)(?=.*\s\.(\s|$))/,
  /\bgit\b.*\bpull\b.*--rebase\b/,
  /\bgit\b.*\bcommit\b.*--amend\b/,
  /\bgit\b.*\bbranch\b.*\s-D\b/,
];

// The git subcommand of a segment that runs git: after variable assignments and git's own options, of which only -C and
// -c take a separate value. A segment that only mentions one (`echo git commit`, a commit message) does not match.
const gitRuns = (subcommand) =>
  new RegExp(`^(\\w+=\\S+\\s+)*git\\s+((-[Cc]\\s+\\S+|--?[\\w-]+(=\\S+)?)\\s+)*(${subcommand})(\\s|$)`);

const GIT_COMMIT = gitRuns('commit');
// A command that changes the branch first commits somewhere the hook cannot see.
const GIT_CHANGES_BRANCH = gitRuns('checkout|switch');

const DENY_PREFIX_PATTERNS = [
  /^(\w+=\S+\s+)*docker\s+volume\s+(rm|remove|prune)(\s|$)/,
  /^(\w+=\S+\s+)*dropdb(\s|$)/,
  gitRuns('rebase|filter-branch|filter-repo|stash\\s+(drop|clear)|reflog\\s+expire|update-ref\\s+-d'),
];

const ASK_PATTERNS = [
  gitRuns('commit|push'),
  /^(\w+=\S+\s+)*docker(?:-compose|\s+compose)\s+((-[fp]\s+\S+|--(?:file|project-name|env-file|profile|project-directory)\s+\S+|--?[\w-]+(=\S+)?)\s+)*down(\s|$)/,
];

const MIGRATION_PATH = /[\w./-]*db\/migration\/[\w.-]+\.sql/g;
const MUTATES_FILE =
  /\bsed\s+(-[a-zA-Z]*i|--in-place)|\bperl\s+-[a-zA-Z]*i|\brm\b|\bmv\b|\bgit\s+(rm|mv)\b|\btruncate\b|\btee\b|>\s*\S*db\/migration\/|\bcp\b[^;&|]*db\/migration\/\S+\.sql\s*($|[;&|])/;

// Allow rules ported from .claude/settings.json.
const ALLOW_PATTERNS = [
  './mvnw verify *',
  './mvnw -B verify *',
  './mvnw -B -q verify *',
  './mvnw spotless:apply *',
  './mvnw generate-sources *',
  './mvnw -q generate-sources *',
  './mvnw test *',
  './mvnw -B test *',
  './mvnw -q test *',
  './mvnw -B -q test *',
  './mvnw -q dependency:build-classpath *',
  'unzip -l *',
  'javap *',
  'npm ci *',
  'npm test *',
  'npm run lint *',
  'npm run test *',
  'npm run build *',
  'npm run format *',
  'npm run format:check *',
  'npm run generate:api *',
  '~/.nvm/nvm-exec npm ci *',
  '~/.nvm/nvm-exec npm test *',
  '~/.nvm/nvm-exec npm run lint *',
  '~/.nvm/nvm-exec npm run build *',
  '~/.nvm/nvm-exec npm run format *',
  '~/.nvm/nvm-exec npm run format:check *',
  '~/.nvm/nvm-exec npm run generate:api *',
  'node scripts/check-docs.mjs *',
  'node scripts/check-path.mjs *',
  'node .claude/scripts/agent-usage.mjs *',
  'node scripts/requirements.mjs *',
  'node --test scripts/requirements.test.mjs *',
  '~/.nvm/nvm-exec npx playwright-cli *',
  'curl -sS -o /dev/null -w "%{http_code}" http://localhost:4200',
  'curl -sS http://localhost:8080/actuator/health',
  'mkdir -p *.screens/*',
  'gh auth status *',
  'gh pr view *',
  'gh pr list *',
  // Safe read-only inspection commands (so PreToolUse does not prompt on routine reads).
  'git status *',
  'git diff *',
  'git log *',
  'git show *',
  'git branch *',
  'git rev-parse *',
  'ls *',
  'head *',
  'tail *',
  'wc *',
  'grep *',
  'rg *',
  'find *',
  'echo *',
  'pwd',
  'which *',
].map(compileAllowPattern);

const ALLOW_REDIRECT_PATTERNS = [
  "setsid sh -c 'echo $$ > *.screens/*/backend.pgid; exec ./mvnw spring-boot:run' *",
  "setsid sh -c 'echo $$ > *.screens/*/frontend.pgid; exec ~/.nvm/nvm-exec npm start' *",
].map(compileAllowPattern);

function compileAllowPattern(pattern) {
  const hasTrailingStar = pattern.endsWith(' *');
  const core = hasTrailingStar ? pattern.slice(0, -2) : pattern;
  const escaped = core.replace(/[.+?^${}()|[\]\\]/g, '\\$&').replaceAll('*', '\\S*');
  return new RegExp(`^${escaped}${hasTrailingStar ? '(?: .*)?' : ''}$`);
}

// Split a shell command on &&, ||, ;, |, or newlines only outside single/double quotes.
function splitSegments(command) {
  const segments = [];
  let current = '';
  let inSingle = false;
  let inDouble = false;

  for (let i = 0; i < command.length; i += 1) {
    const ch = command[i];
    if (ch === '\\' && !inSingle && i + 1 < command.length) {
      current += ch + command[i + 1];
      i += 1;
      continue;
    }
    if (ch === "'" && !inDouble) {
      inSingle = !inSingle;
      current += ch;
      continue;
    }
    if (ch === '"' && !inSingle) {
      inDouble = !inDouble;
      current += ch;
      continue;
    }
    if (!inSingle && !inDouble) {
      const two = command.slice(i, i + 2);
      if (two === '&&' || two === '||') {
        segments.push(current);
        current = '';
        i += 1;
        continue;
      }
      if (ch === ';' || ch === '|' || ch === '\n') {
        segments.push(current);
        current = '';
        continue;
      }
    }
    current += ch;
  }
  segments.push(current);
  return segments.map((s) => s.replace(/\s+/g, ' ').trim()).filter(Boolean);
}

const SAFE_CD = /^cd\s+[^\s;|&<>()$`'"]+$/;

function isAllowedSegment(segment) {
  if (segment.includes('$(') || segment.includes('`')) return false;
  const cleaned = segment.replace(/\s+2>&1$/, '');
  if (ALLOW_REDIRECT_PATTERNS.some((re) => re.test(cleaned))) return true;
  if (/[<>]/.test(cleaned)) return false;
  return ALLOW_PATTERNS.some((re) => re.test(cleaned));
}

const input = readInput();
const command = input.toolCall?.args?.CommandLine ?? '';
const cwd = input.cwd ?? process.cwd();
if (!command.trim()) ask('Empty shell command.');

const segments = splitSegments(command);

// 1. Deny checks first (order matters: e.g., git commit on main / --amend, git push --force, docker compose down -v must deny, not ask).
for (const segment of segments) {
  if (DATA_LOSS.some((re) => re.test(segment))) {
    deny(`Blocked: \`${segment}\` destroys local data (dev database or Docker volumes). ${MANUAL}`);
  }
  if (GIT_DESTRUCTIVE.some((re) => re.test(segment))) {
    deny(`Blocked: \`${segment}\` discards work or rewrites git history. ${MANUAL}`);
  }
  if (DENY_PREFIX_PATTERNS.some((re) => re.test(segment))) {
    deny(`Blocked: \`${segment}\` is a destructive or history-rewriting command. ${MANUAL}`);
  }
}

if (SQL_CLIENT.test(command) && DESTRUCTIVE_SQL.test(command)) {
  deny(`Blocked: destructive SQL against a database. ${MANUAL}`);
}

if (segments.some((s) => GIT_COMMIT.test(s)) && !segments.some((s) => GIT_CHANGES_BRANCH.test(s))) {
  const root = repoRoot(cwd);
  if (root && isThisProject(root, input.workspacePaths) && onDefaultBranch(root)) {
    deny(`Blocked: a commit on the default branch. ${DEFAULT_BRANCH_REASON}`);
  }
}

const migrations = command.match(MIGRATION_PATH) ?? [];
if (migrations.length && MUTATES_FILE.test(command)) {
  const root = repoRoot(cwd);
  for (const token of migrations) {
    // Resolve by file name: the command may run after a `cd` the hook cannot see.
    if (root && isMigrationInMain(root, MIGRATION_DIR + path.posix.basename(toPosix(token)))) {
      deny(`${token}: ${MIGRATION_IN_MAIN_REASON}`);
    }
  }
}

// 2. Ask rules (git commit, git push, docker compose down).
for (const segment of segments) {
  if (ASK_PATTERNS.some((re) => re.test(segment))) {
    ask(`Confirmation required before running \`${segment}\`.`);
  }
}

// 3. Allow rules: allow only when every segment is explicitly allowed (allowing `cd <dir>` only when paired with an allowed command).
const hasExplicitAllow = segments.some((s) => isAllowedSegment(s));
if (hasExplicitAllow && segments.every((s) => isAllowedSegment(s) || SAFE_CD.test(s))) {
  allow();
}

ask(`Command requires confirmation: \`${command.trim()}\``);
