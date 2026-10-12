import { readFileSync } from 'node:fs';
import path from 'node:path';
import { APPLIED_MIGRATION_REASON, MIGRATION_DIR, deny, isAppliedMigration, readInput, repoRoot, toPosix } from './lib.mjs';

const MANUAL = 'If it is really needed, ask the user to run it manually with the `!` prefix.';

const DATA_LOSS = [
  /\bdocker\s+volume\s+(rm|remove|prune)\b/,
  /\bdocker\s+system\s+prune\b.*--volumes\b/,
  /\bdocker(-compose|\s+compose)\b.*\bdown\b.*\s(-[a-zA-Z]*v[a-zA-Z]*|--volumes)\b/,
  /\bdropdb\b/,
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
  /\bgit\b.*\b(rebase|filter-branch|filter-repo)\b/,
  /\bgit\b.*\bcommit\b.*--amend\b/,
  /\bgit\b.*\bstash\s+(drop|clear)\b/,
  /\bgit\b.*\bbranch\b.*\s-D\b/,
  /\bgit\b.*\b(reflog\s+expire|update-ref\s+-d)\b/,
];

const COMMIT_ATTRIBUTION = /co-authored-by:|claude-session:|generated with|noreply@anthropic\.com|claude\.ai\/code/i;

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

if (/\bgit\b.*\bcommit\b/.test(command)) {
  let message = command;
  const file = command.match(/\bgit\b.*\bcommit\b.*\s(?:-F|--file)[=\s]+(\S+)/)?.[1];
  if (file) {
    try {
      message += readFileSync(path.resolve(cwd, file.replace(/^['"]|['"]$/g, '')), 'utf8');
    } catch {}
  }
  if (COMMIT_ATTRIBUTION.test(message)) {
    deny(
      'Commit messages in this project are plain English text with no trailers (Co-Authored-By, Claude-Session), ' +
        'no "Generated with" lines and no mention of AI agents or models. Remove them and commit again.',
    );
  }
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
