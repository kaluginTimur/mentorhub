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

export function isCommitted(root, rel) {
  return run('git', ['-C', root, 'cat-file', '-e', `HEAD:${rel}`]) !== null;
}

let appliedScripts;

// Best effort: the dev database is only consulted when its compose container is running.
export function appliedInDevDb(scriptName) {
  if (appliedScripts === undefined) {
    appliedScripts = new Set();
    const id = run('docker', [
      'ps', '-q',
      '--filter', 'label=com.docker.compose.project=mentorhub',
      '--filter', 'label=com.docker.compose.service=postgres',
    ])?.trim().split('\n')[0];
    if (id) {
      const out = run('docker', [
        'exec', id, 'psql', '-U', 'mentorhub', '-d', 'mentorhub', '-tAc',
        'select script from flyway_schema_history where script is not null',
      ]);
      out?.split('\n').filter(Boolean).forEach((s) => appliedScripts.add(s.trim()));
    }
  }
  return appliedScripts.has(scriptName);
}

export function isAppliedMigration(root, rel) {
  if (!rel.startsWith(MIGRATION_DIR) || !rel.endsWith('.sql')) return false;
  return isCommitted(root, rel) || appliedInDevDb(path.posix.basename(rel));
}

export const APPLIED_MIGRATION_REASON =
  'Flyway migration is already committed or applied to the dev database. Never modify, move or delete it: ' +
  'Flyway validates checksums and the app will fail to start on any existing database. ' +
  'Add a new migration V<next>__<description>.sql instead.';

export function deny(reason) {
  process.stdout.write(
    JSON.stringify({
      hookSpecificOutput: { hookEventName: 'PreToolUse', permissionDecision: 'deny', permissionDecisionReason: reason },
    }),
  );
  process.exit(0);
}
