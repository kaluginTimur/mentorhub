// PreToolUse hook on send_message: blocks resuming long-running subagents after Antigravity's
// 5-minute prompt cache TTL has expired so the coordinator starts a fresh subagent from the plan handover.
import { allow, deny, envNumber, readInput, readTranscriptStats, resolveSubagentTranscript } from './lib.mjs';

const IDLE_MS = envNumber('SUBAGENT_IDLE_MS', 300_000);
const MIN_TOKENS = envNumber('SUBAGENT_RESUME_MIN_TOKENS', 25_000);
const MIN_BYTES = envNumber('SUBAGENT_RESUME_MIN_BYTES', 100_000);
const MIN_STEPS = envNumber('SUBAGENT_RESUME_MIN_STEPS', 8);

try {
  const input = readInput();
  const recipient = input.toolCall?.args?.Recipient;
  const transcriptPath = input.transcriptPath;
  if (!recipient || !transcriptPath) allow();

  const subagentTranscriptPath = resolveSubagentTranscript(transcriptPath, recipient);
  if (!subagentTranscriptPath) allow();

  const stats = readTranscriptStats(subagentTranscriptPath);
  if (!stats) allow();

  const idleMs = Date.now() - stats.lastTimestampMs;
  const hasContext = stats.tokens >= MIN_TOKENS || stats.bytes >= MIN_BYTES || stats.stepCount >= MIN_STEPS;
  if (idleMs > IDLE_MS && hasContext) {
    const idleMinutes = Math.round(idleMs / 60_000);
    const tokK = Math.round(stats.tokens / 1000);
    deny(
      `Resuming subagent ${recipient} is blocked: idle for ${idleMinutes} minutes ` +
        `(past the 5-minute prompt cache TTL) with ~${tokK}k tokens / ${stats.stepCount} steps. ` +
        `Spawn a fresh subagent from the handover in docs/plans/<change>.md instead of resuming.`,
    );
  }
} catch {
  // Fail open so malformed transcripts or unexpected read errors never block tool execution.
}

allow();
