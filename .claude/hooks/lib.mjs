import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';

export const MIGRATION_DIR = 'backend/src/main/resources/db/migration/';

export function readInput() {
  return JSON.parse(readFileSync(0, 'utf8'));
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
  while (!existsSync(dir) || !statSync(dir).isDirectory()) dir = path.dirname(dir);
  const root = run('git', ['-C', dir, 'rev-parse', '--show-toplevel']);
  return root ? root.trim() : null;
}

export function toPosix(p) {
  return p.replaceAll('\\', '/');
}

// A migration that is not in main yet belongs to its change and may be edited: the dev database is rebuilt from the
// migrations on every dev start, and tests use an empty database. One in main may have been applied elsewhere.
export function isMigrationInMain(root, rel) {
  if (!rel.startsWith(MIGRATION_DIR) || !rel.endsWith('.sql')) return false;
  return ['main', 'origin/main'].some((ref) => run('git', ['-C', root, 'cat-file', '-e', `${ref}:${rel}`]) !== null);
}

export const MIGRATION_IN_MAIN_REASON =
  'This Flyway migration is in main. Never modify, move or delete it without the project owner\'s word: ' +
  'a database outside the repository may have applied it, and Flyway validates checksums. ' +
  'Add a new migration V<next>__<description>.sql instead, or edit the migration of this change if it has one.';

// A detached HEAD (a rebase or a bisect in progress) is no branch, so it is not the default one.
export function onDefaultBranch(root) {
  const branch = run('git', ['-C', root, 'symbolic-ref', '--short', '-q', 'HEAD']);
  return branch !== null && branch.trim() === 'main';
}

// Whether the checkout at root belongs to the project of this session: its own folder or one of its worktrees.
// Another repository on the machine has its own rules about its default branch.
export function isThisProject(root) {
  const project = process.env.CLAUDE_PROJECT_DIR;
  if (!project) return true;
  const commonDir = (dir) => run('git', ['-C', dir, 'rev-parse', '--path-format=absolute', '--git-common-dir'])?.trim();
  const mine = commonDir(project);
  return !mine || commonDir(root) === mine;
}

export function isCommitted(root, rel) {
  return run('git', ['-C', root, 'cat-file', '-e', `HEAD:${rel}`]) !== null;
}

export const DEFAULT_BRANCH_REASON =
  'Nothing is changed on the default branch: every change starts with Choosing a path in docs/process.md ' +
  '(the /change skill), where the owner confirms the goal, the approach and the path, and the change gets its branch. ' +
  'Run that step first; do not just create a branch to get past this.';

export function deny(reason) {
  process.stdout.write(
    JSON.stringify({
      hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: 'deny', permissionDecisionReason: reason },
    }),
  );
  process.exit(0);
}
