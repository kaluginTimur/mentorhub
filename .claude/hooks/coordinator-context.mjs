// Stop hook of the main session. When the session stops with a large context while a change's plan or brief exists,
// it hands the session one more turn (`additionalContext`) to tell the user the context size and whether `/clear` is
// safe now: every call re-reads the whole context, and the plan restores a new session exactly. A `systemMessage` to
// the user was tried first and went unnoticed among the session's own messages. Once said, it is said again only when
// the context grew by REPEAT_AFTER or the running tasks changed: a reminder at every stop turns into noise. Any failure
// ends the hook silently, so it never gets in the way of a stop.
import { existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { readInput, repoRoot } from './lib.mjs';

const THRESHOLD = Number(process.env.COORDINATOR_CONTEXT_REMINDER ?? 200000);
const REPEAT_AFTER = 30000;

try {
  remind(readInput());
} catch {
  // A reminder is not worth a failed stop.
}
process.exit(0);

function remind(input) {
  // This turn is already the hook's continuation: one per stop.
  if (input.stop_hook_active) return;

  // The session may have moved into a subfolder; the plans live at the repository root.
  const root = repoRoot(input.cwd ?? '.') ?? process.env.CLAUDE_PROJECT_DIR;
  if (!root) return;
  const dir = path.join(root, 'docs', 'plans');
  if (!existsSync(dir)) return;
  const plans = readdirSync(dir)
    .filter((f) => f.endsWith('.md') && f !== 'README.md')
    .sort((a, b) => statSync(path.join(dir, b)).mtimeMs - statSync(path.join(dir, a)).mtimeMs);
  if (plans.length === 0) return;

  const context = lastContext(input.transcript_path);
  if (context < THRESHOLD) return;

  const k = (n) => `${Math.round(n / 1000)}k`;
  const running = [...(input.background_tasks ?? []), ...(input.session_crons ?? [])].map(
    (t) => t.agent_type ?? t.description ?? t.prompt ?? t.type ?? 'a task',
  );

  const said = path.join(tmpdir(), `claude-coordinator-context-${String(input.session_id ?? 'session').replace(/\W/g, '')}.json`);
  const last = existsSync(said) ? JSON.parse(readFileSync(said, 'utf8')) : null;
  const runningKey = [...running].sort().join('\n');
  // A context smaller than the one last said was compacted: start over.
  if (last && context >= last.context && context < last.context + REPEAT_AFTER && last.running === runningKey) return;
  writeFileSync(said, JSON.stringify({ context, running: runningKey }));
  const lines = [
    `The session's context is at ${k(context)} tokens, above the reminder threshold of ${k(THRESHOLD)}: every further call re-reads it, and a new session continues from the handover in \`docs/plans/${plans[0]}\` at a fraction of the cost.`,
    'Before ending this turn, tell the user in one or two lines, in their language: the context size, and whether `/clear` is safe now.',
    '- Safe: the handover is current with the state now (decisions recorded, task statuses, what is uncommitted), and no agent or background task is running, since agents of this session cannot be continued after `/clear`. Name what to run after `/clear`: the command the handover names (`/feature`, `/chore` or `/spec` with the change\'s name, or `/change`).',
    '- Not safe yet: say what must happen first. If it is the handover, update it now and then say it is safe. If an agent is running, say that you will say so again at the next stop after its report.',
    running.length ? `Running now: ${running.join('; ')}.` : 'No background task is running now.',
    'Start no other work in this turn.',
  ];
  process.stdout.write(JSON.stringify({ hookSpecificOutput: { hookEventName: 'Stop', additionalContext: lines.join('\n') } }));
}

function lastContext(transcript) {
  if (!transcript || !existsSync(transcript)) return 0;
  let context = 0;
  for (const line of readFileSync(transcript, 'utf8').split('\n')) {
    if (!line.includes('"usage"')) continue;
    try {
      const entry = JSON.parse(line);
      if (entry.isSidechain) continue;
      const u = entry.message?.usage;
      if (u) context = (u.input_tokens ?? 0) + (u.cache_read_input_tokens ?? 0) + (u.cache_creation_input_tokens ?? 0);
    } catch {
      // A line still being written.
    }
  }
  return context;
}
