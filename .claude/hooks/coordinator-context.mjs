// Stop hook of the main session. When the session stops for the user with a large context while a change's plan or
// brief exists, it reminds the user that `/clear` and the skill's command continue from the handover at a fraction of
// the cost: every call re-reads the whole context, and the plan restores a new session exactly. A reminder only: any
// failure ends it silently, so it never gets in the way of a stop.
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { readInput, repoRoot } from './lib.mjs';

const THRESHOLD = Number(process.env.COORDINATOR_CONTEXT_REMINDER ?? 200000);

try {
  remind(readInput());
} catch {
  // A reminder is not worth a failed stop.
}
process.exit(0);

function remind(input) {
  // Waiting for a background agent, a scheduled wakeup or a continuation by another hook is not a stop for the user.
  if (input.stop_hook_active) return;
  if ((input.background_tasks ?? []).length > 0 || (input.session_crons ?? []).length > 0) return;

  // The session may have moved into a subfolder; the plans live at the repository root.
  const root = repoRoot(input.cwd ?? '.') ?? process.env.CLAUDE_PROJECT_DIR;
  if (!root) return;
  const plans = path.join(root, 'docs', 'plans');
  if (!existsSync(plans) || !readdirSync(plans).some((f) => f.endsWith('.md') && f !== 'README.md')) return;
  if (!input.transcript_path || !existsSync(input.transcript_path)) return;

  let context = 0;
  for (const line of readFileSync(input.transcript_path, 'utf8').split('\n')) {
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
  if (context < THRESHOLD) return;

  const k = (n) => `${Math.round(n / 1000)}k`;
  process.stdout.write(JSON.stringify({
    systemMessage: `Context is at ${k(context)} tokens, and every further call re-reads it. If the handover is written, \`/clear\` and then the command it names (\`/feature\`, \`/chore\` or \`/spec\` with the change's name, or \`/change\` when it waits for a new path) continue from it at a fraction of the cost.`,
  }));
}
