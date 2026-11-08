// PostToolUse hook declared in the frontmatter of the implementation agents (backend-dev, frontend-dev, test-engineer).
// Once an agent's context passes the budget, it tells the agent, once, to finish its step and stop with a PARTIAL
// report and a handover, so that a fresh agent continues the task. The message reaches the agent after the tool result,
// within its turn; it cannot interrupt a tool that is still running.
import { existsSync, readFileSync } from 'node:fs';
import path from 'node:path';
import { readInput } from './lib.mjs';

const BUDGET = Number(process.env.CONTEXT_BUDGET ?? 250000);
const MARK = '[context-budget hook]';

const input = readInput();
if (!input.agent_id) process.exit(0);

// The hook receives the session's transcript; each subagent writes its own next to it.
const file = path.join(path.dirname(input.transcript_path), input.session_id, 'subagents', `agent-${input.agent_id}.jsonl`);
if (!existsSync(file)) process.exit(0);

let context = 0;
for (const line of readFileSync(file, 'utf8').split('\n')) {
  // The message is kept in the transcript, so the agent is told only once.
  if (line.includes('"hook_additional_context"') && line.includes(MARK)) process.exit(0);
  if (!line.includes('"usage"')) continue;
  try {
    const u = JSON.parse(line).message?.usage;
    if (u) context = (u.input_tokens ?? 0) + (u.cache_read_input_tokens ?? 0) + (u.cache_creation_input_tokens ?? 0);
  } catch {
    // A line still being written.
  }
}
if (context < BUDGET) process.exit(0);

const k = (n) => `${Math.round(n / 1000)}k`;
process.stdout.write(JSON.stringify({
  hookSpecificOutput: {
    hookEventName: 'PostToolUse',
    additionalContext: `${MARK} Your context is at ${k(context)} tokens, past the budget of ${k(BUDGET)}. The budget is the cost of going on, not a lack of room: there is plenty of room left. Finish the step you are on, so that nothing is half-edited, then stop the work itself. Write the handover into the plan as your Context budget section says, checking it against the working tree, as fully as a fresh agent needs. Then end with your report, status PARTIAL.`,
  },
}));
