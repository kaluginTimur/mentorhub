// PreInvocation hook: once the conversation's context or step count passes budget,
// injects a one-time system message instructing the agent to finish its current step,
// write a handover, and stop with PARTIAL so a fresh agent can continue.
import { empty, envNumber, readInput, readTranscriptStats } from './lib.mjs';

const BUDGET_TOKENS = envNumber('CONTEXT_BUDGET', 250_000);
const BUDGET_STEPS = envNumber('CONTEXT_BUDGET_STEPS', 120);
const MARK = '[context-budget hook]';

try {
  const input = readInput();
  const transcriptPath = input.transcriptPath;
  const rawInitial = Number(input.initialNumSteps ?? 0);
  const initialNumSteps = Number.isFinite(rawInitial) ? rawInitial : 0;
  if (!transcriptPath) empty();

  const stats = readTranscriptStats(transcriptPath, MARK);
  if (!stats || stats.hasMarker) empty();

  const steps = Math.max(initialNumSteps, stats.stepCount);
  if (stats.tokens < BUDGET_TOKENS && steps < BUDGET_STEPS) empty();

  const tokK = Math.round(stats.tokens / 1000);
  const message =
    `${MARK} Your context is past the budget (~${tokK}k tokens / ${steps} steps). ` +
    'Finish the step you are on so nothing is half-edited, write a complete handover into ' +
    'docs/plans/<change>.md (or your brief) checking it against the working tree, and stop ' +
    'with status PARTIAL so a fresh agent can continue.';

  process.stdout.write(
    JSON.stringify({
      injectSteps: [
        {
          systemMessage: {
            systemMessage: message,
          },
        },
      ],
    }),
  );
  process.exit(0);
} catch {
  empty();
}
