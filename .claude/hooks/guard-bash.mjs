import path from 'node:path';
import { APPLIED_MIGRATION_REASON, MIGRATION_DIR, deny, isAppliedMigration, readInput, repoRoot, toPosix } from './lib.mjs';

const MANUAL = 'If it is really needed, ask the user to run it manually with the `!` prefix.';

// Fixed-prefix commands (docker volume rm, dropdb, git rebase, ...) are denied in .claude/settings.json;
// these depend on flags in any position or on the command's content.
const DATA_LOSS = [
  /\bdocker\s+system\s+prune\b.*--volumes\b/,
  /\bdocker(-compose|\s+compose)\b.*\bdown\b.*\s(-[a-zA-Z]*v[a-zA-Z]*|--volumes)\b/,
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

const MIGRATION_PATH = /[\w./-]*db\/migration\/[\w.-]+\.sql/g;
const MUTATES_FILE =
  /\bsed\s+(-[a-zA-Z]*i|--in-place)|\bperl\s+-[a-zA-Z]*i|\brm\b|\bmv\b|\bgit\s+(rm|mv)\b|\btruncate\b|\btee\b|>\s*\S*db\/migration\/|\bcp\b[^;&|]*db\/migration\/\S+\.sql\s*($|[;&|])/;

const input = readInput();
const command = input.tool_input?.command ?? '';
const cwd = input.cwd ?? process.cwd();
const segments = command.split(/&&|\|\||[;|\n]/).map((s) => s.replace(/\s+/g, ' ').trim());

for (const segment of segments) {
  if (DATA_LOSS.some((re) => re.test(segment))) {
    deny(`Blocked: \`${segment}\` destroys local data (dev database or Docker volumes). ${MANUAL}`);
  }
  if (GIT_DESTRUCTIVE.some((re) => re.test(segment))) {
    deny(`Blocked: \`${segment}\` discards work or rewrites git history. ${MANUAL}`);
  }
}

if (SQL_CLIENT.test(command) && DESTRUCTIVE_SQL.test(command)) {
  deny(`Blocked: destructive SQL against a database. ${MANUAL}`);
}

const migrations = command.match(MIGRATION_PATH) ?? [];
if (migrations.length && MUTATES_FILE.test(command)) {
  const root = repoRoot(cwd);
  for (const token of migrations) {
    // Resolve by file name: the command may run after a `cd` the hook cannot see.
    if (root && isAppliedMigration(root, MIGRATION_DIR + path.posix.basename(toPosix(token)))) {
      deny(`${token}: ${APPLIED_MIGRATION_REASON}`);
    }
  }
}
