import path from 'node:path';
import {
  DEFAULT_BRANCH_REASON,
  MIGRATION_IN_MAIN_REASON,
  MIGRATION_DIR,
  deny,
  isMigrationInMain,
  isThisProject,
  onDefaultBranch,
  readInput,
  repoRoot,
  toPosix,
} from './lib.mjs';

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

// The git subcommand of a segment that runs git: after variable assignments and git's own options, of which only -C and
// -c take a separate value. A segment that only mentions one (`echo git commit`, a commit message) does not match.
const gitRuns = (subcommand) => new RegExp(`^(\\w+=\\S+\\s+)*git\\s+((-[Cc]\\s+\\S+|--?[\\w-]+(=\\S+)?)\\s+)*(${subcommand})(\\s|$)`);
const GIT_COMMIT = gitRuns('commit');
// A command that changes the branch first commits somewhere the hook cannot see.
const GIT_CHANGES_BRANCH = gitRuns('checkout|switch');

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

// Hand-written waits hang when what they wait for has died or never started: a `pgrep -f` loop that matched its own
// shell, a `curl` loop on a dev server that failed to compile. Waiting goes through scripts/stack.mjs or the Bash timeout.
// A `for` loop is a wait when it leaves early (`break`, `exit`, `return`). Only what the shell runs counts: quoted text
// and heredocs (a commit message, a file being written) do not, except a script handed to a shell or `eval`.
const SHELL_SCRIPT_BEFORE = /(^|[\s;&|(/])((sh|bash|zsh)\s+(-\w+\s+)*-\w*c\s*|eval\s+|(sh|bash|zsh)\s*)$/;
function executed(text) {
  let out = '';
  for (let i = 0; i < text.length; i++) {
    const heredoc = /^<<-?\s*(['"]?)(\w+)\1[^\n]*\n([\s\S]*?)\n[ \t]*\2[ \t]*(?=\n|$)/.exec(text.slice(i));
    if (heredoc) {
      out += SHELL_SCRIPT_BEFORE.test(out) ? `\n${heredoc[3]}\n` : '\n';
      i += heredoc[0].length - 1;
      continue;
    }
    const c = text[i];
    if (c !== "'" && c !== '"') {
      out += c;
      continue;
    }
    let j = i + 1;
    while (j < text.length && text[j] !== c) j += c === '"' && text[j] === '\\' ? 2 : 1;
    out += SHELL_SCRIPT_BEFORE.test(out) ? ` ${text.slice(i + 1, j)} ` : ' ';
    i = j;
  }
  return out;
}
const LOOP = /\b(while|until|for)\s[\s\S]*?\bdo\b([\s\S]*?)\bdone\b/g;
const polls = (kind, body) => /\bsleep\b/.test(body) && (kind !== 'for' || /\b(break|exit|return)\b/.test(body));
if ([...executed(command).matchAll(LOOP)].some((m) => polls(m[1], m[2]))) {
  deny(
    'Blocked: a polling loop around `sleep`. It hangs when what it waits for has died or never started. ' +
      "To bring up the stack, run `node scripts/stack.mjs start` in the foreground with the Bash tool's timeout at 600000: it waits with a time limit and fails fast with the log. " +
      "Run a build or a test in the foreground with the Bash tool's timeout at 600000. In the main session, wait for a condition with the Monitor tool. " +
      "Never wait for another agent's build: stop with your report and say so, since the coordinator runs builds one at a time.",
  );
}

// A build, a test run or a server in the background leaves the agent polling its output file, or waiting on it after
// it died; scripts/stack.mjs detaches the servers itself and returns once they are healthy.
const BUILD_OR_SERVER = /\bmvnw\b|\bmvn\b|\bnpm\s+(start|test|t|run\s+\S+)\b|\bng\s+(serve|build|test|lint)\b|\bnpx\s+ng\b/;
if (input.tool_input?.run_in_background === true && BUILD_OR_SERVER.test(command)) {
  deny(
    "Blocked: a build, a test run or a server in `run_in_background`. Run builds and tests in the foreground with the Bash tool's timeout at 600000. " +
      "Start the stack with `node scripts/stack.mjs start` in the foreground with the same timeout: it detaches the servers and returns once they are healthy.",
  );
}

if (SQL_CLIENT.test(command) && DESTRUCTIVE_SQL.test(command)) {
  deny(`Blocked: destructive SQL against a database. ${MANUAL}`);
}

if (segments.some((s) => GIT_COMMIT.test(s)) && !segments.some((s) => GIT_CHANGES_BRANCH.test(s))) {
  const root = repoRoot(cwd);
  if (root && isThisProject(root) && onDefaultBranch(root)) deny(`Blocked: a commit on the default branch. ${DEFAULT_BRANCH_REASON}`);
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

// playwright-cli refuses `file:` URLs unless a session starts with unrestricted file access. Only manual-tester's mockup
// session starts so, by the one command manual-tester.md gives, and then takes only commands that look at the page, so
// it cannot read another local file (a `.env`, `~/.ssh`) or send one out. `playwright-cli *` in settings.json allows it
// all, so the hook accepts exact forms only; an unknown form is refused, never parsed.
const FILE_ACCESS = /PLAYWRIGHT_MCP_ALLOW_UNRESTRICTED_FILE_ACCESS|allowUnrestrictedFileAccess|cli\.config\.json/;
const UNCLEAR_SESSION = /PLAYWRIGHT_CLI_SESSION|[$`]|\s--s|\s-s(?!=)/;
const MOCKUP_SESSION = /\s-s=mockup(?=\s|$)/;
const MOCKUP_COMMAND = /\s-s=mockup (close|click|hover|press|mousewheel|resize|snapshot|find|screenshot|reload|set-color-scheme)(\s|$)/;
const MOCKUP_ONLY =
  'Local files open only by the mockup command of manual-tester.md, exactly as written there; name a session only as ' +
  '`-s=<name>`, with no `$`, backtick, ` -s` or ` --s` elsewhere in the command, typed text included; the mockup session ' +
  'takes only commands that look at the page. A refusal means the command is not allowed: do not rephrase it to get past this.';
const isMockupOpen = (s) =>
  new RegExp(
    '^PLAYWRIGHT_MCP_ALLOW_UNRESTRICTED_FILE_ACCESS=true ~/\\.nvm/nvm-exec npx playwright-cli -s=mockup open file://' +
      toPosix(repoRoot(cwd) ?? '/nonexistent').replace(/[.*+?^${}()|[\]\\]/g, '\\$&') +
      '/\\.screens/[\\w-]+/mockups/[\\w-]+\\.html --browser=chromium$',
  ).test(s);

// What the shell runs, so a commit message that names the variable passes; a playwright-cli command with its quoted text.
const runs = executed(command).split(/&&|\|\||[;|\n]/).map((s) => s.replace(/\s+/g, ' ').trim());
const checked = runs.some((s) => /\bplaywright-cli\b/.test(s)) ? segments.map((s) => s.replace(/["']/g, '')) : runs;
for (const s of checked) {
  const playwright = /\bplaywright-cli\b/.test(s);
  const fileAccess = FILE_ACCESS.test(s) || (playwright && /--config\b|\bfile:/i.test(s));
  const badSession = playwright && (UNCLEAR_SESSION.test(s) || (MOCKUP_SESSION.test(s) && !MOCKUP_COMMAND.test(s)));
  if ((fileAccess || badSession) && !isMockupOpen(s)) deny(`Blocked: \`${s}\`. ${MOCKUP_ONLY}`);
}
