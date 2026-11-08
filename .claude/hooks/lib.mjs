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

export function deny(reason) {
  process.stdout.write(
    JSON.stringify({
      hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: 'deny', permissionDecisionReason: reason },
    }),
  );
  process.exit(0);
}
