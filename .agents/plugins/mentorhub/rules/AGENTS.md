# MentorHub Coordinator (Antigravity)

This plugin adapts MentorHub's spec-anchored workflow (`docs/process.md`) and `.claude/` role definitions for Antigravity. Repository-wide rules in `AGENTS.md` and `docs/constitution.md` still apply.

## Subagents: `.claude/agents/*.md` to Antigravity

Project subagent roles live in `.claude/agents/<role>.md`. Before invoking a role for the first time in a conversation, read its `.claude/agents/<role>.md` file, strip the YAML frontmatter, and register it with `define_subagent` (appending a short reminder of the Antigravity tool mappings below and that `backend/AGENTS.md` / `frontend/AGENTS.md` point to the area guides on demand).

### Role Mapping

| Role | `Model` | `EnableWriteTools` | `EnableSubagentTools` | `EnableMCPTools` | Notes |
|---|---|---|---|---|---|
| `api-lookup` | `flash_lite` | `true` | `false` | `false` | Shell inspection only (`run_command`); never edits files |
| `backend-dev` | `pro` (or `inherit`) | `true` | `true` | `false` | Writes backend/contract/migrations; may spawn `api-lookup` |
| `frontend-dev` | `inherit` | `true` | `false` | `true` | Uses `angular-cli` MCP; asks `api-lookup` via report if needed |
| `test-engineer` | `pro` (or `inherit`) | `true` | `true` | `false` | Writes tests only; may spawn `api-lookup` |
| `manual-tester` | `inherit` (`flash_lite` for numbered manual checks) | `true` | `false` | `false` | Browser/stack checks via `run_command`; never edits tracked files |
| `reviewer` | `pro` (or `inherit`) | `true` | `false` | `false` | Read-only `git` / inspection via `run_command`; never edits files |
| `verifier` | `inherit` | `true` | `false` | `false` | Runs Verification commands via `run_command`; never edits files |

- **Model tiers:** `opus` -> `pro` (or `inherit` if already on an equivalent tier), `sonnet` -> `inherit`, `haiku` -> `flash_lite`.
- **Capability flags:** In `define_subagent`, `EnableWriteTools: true` grants both file edits and `run_command`, so read-only roles that run shell commands (`api-lookup`, `manual-tester`, `reviewer`, `verifier`) also need `EnableWriteTools: true` and rely on their prompt instructions not to modify files.

### Tool Name Mapping

When reading `.claude/agents/*.md` or adapting instructions for subagents, map Claude Code tool names to Antigravity tools:

- `Read` -> `read_file`
- `Edit` -> `edit_file`
- `Write` -> `write_file`
- `Grep`, `Glob`, `Bash` -> `run_command`
- `Agent` -> `invoke_subagent` (or `swarm` for parallel independent tasks)
- `SendMessage` -> `send_message`

### Orchestration

- Use `invoke_subagent` for single delegated tasks and `send_message` (targeting the returned `conversationId`) for follow-ups.
- Use `swarm` when tasks split cleanly into parallel independent pieces (for example, independent frontend tasks or parallel review/verification). Use `workspace: "share"` when multiple workers edit or build concurrently; keep `inherit` for read-only workers.
- Subagents cannot ask the owner directly: they stop with a report, and the coordinator presents questions to the owner.

## 5-Minute Cache Mitigation

Antigravity has no `cacheTtl: 1h` subagent setting, so a paused subagent's prompt cache expires after 5 minutes.

- **Never resume** a long-running subagent (`backend-dev`, `frontend-dev`, `test-engineer`) via `send_message` if more than 5 minutes have elapsed since it stopped (enforced by `.agents/plugins/mentorhub/hooks/guard-subagent-resume.mjs`).
- Instead, start a **fresh** `invoke_subagent` with the handover in `docs/plans/<change>.md` and the owner's feedback.
- Keep briefs scoped to one task or checkpoint at a time so subagent contexts stay small.

## Context & Session Handover

- Antigravity does not have `/clear` or `.claude/scripts/agent-usage.mjs` (do not record `usage/<change>.jsonl` from Antigravity sessions).
- Before every stop for the owner (**Stopping for the owner** in `docs/process.md`), write a complete handover into `docs/plans/<change>.md` (or the change's brief) with the current state, verification status, open questions, and next steps on approval or remarks, so a fresh conversation can continue without relying on prior session history.
