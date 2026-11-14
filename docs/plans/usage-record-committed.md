# Usage record committed

Branch: `chore/usage-record-committed`
Roadmap item: none

## Scope

Goal: the usage record (`.claude/usage/<change>.jsonl`), which measures token cost per unit of implementation, reaches the repository at every step without the owner being asked and without `--force`. Today no skill says it is committed, and recording the `review` phase again in `/pr` after stage 8 is refused.

Cause of the refusal: `agent-usage.mjs` looks for the window's agents in the plan and its history, but the reviewer and verifier rows are added to the plan's Agents table after its last commit, and stage 8 deletes the committed version, so neither holds them.

Approach:

- `/feature`: its commit steps (red tests, backend green, the finish of stage 8) name `.claude/usage/<change>.jsonl` as part of the commit, without asking; `/pr` already offers to commit the record, only its wording is aligned.
- `agent-usage.mjs`: when it checks that a chain belongs to the change, it also counts agents already in this change's earlier records; a session never recorded for the change is still refused. `.claude/README.md` (Recording usage) follows.
- Verification: recording `observer-role-removed` `review` again without `--force` (working copy, not committed) succeeds with the same agents and window as the forced record; a `--record` for another change is still refused.

Out: committing the plan's final version before stage 8 deletes it (rejected: an extra commit per feature that fixes the symptom, not the check).

Requirements: `docs/requirements/` describe the product, not the process tooling; no requirement describes this and none should.

## Tasks

| Task | Who | Status |
|---|---|---|
| 1. `agent-usage.mjs` counts agents of the change's earlier records; `.claude/README.md` follows | coordinator | done |
| 2. `/spec`, `/feature`, `/pr` name the usage record in their commit steps | coordinator | done |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

## Decisions

- 2026-11-15: path gate confirmed: goal, approach and path `chore/`, "да, подтверждаю" (after asking whether the script could lose sight of a phase).
- 2026-11-15: accepted: "да, принимаю, коммить".
- 2026-11-15: review finding 1 fixed (`/pr` step 3 commits the usage record without asking): "да, исправляй и запускай verifier".
- 2026-11-15: the record must be committed without asking: "этот файл нужен для того, чтобы понимать и анализировать расход токенов на единицу имплементации. если это не закреплено в CLAUDE.md или скиллах - это нужно исправить".

## Handover

- Waiting for: `/pr`; accepted and committed.
- To read: this brief.
- Documents changed (`git status --short`): none in `docs/` or `AGENTS.md`; changed outside them: `.claude/README.md` (Recording usage: the refusal condition and committing without asking), `.claude/scripts/agent-usage.mjs`, `.claude/skills/feature/SKILL.md`, `.claude/skills/spec/SKILL.md`, `.claude/skills/pr/SKILL.md`.
- State: tasks 1 and 2 done. Checked by hand: recording `observer-role-removed` `review` again without `--force` (its record copied from `feature/observer-role-removed`, then removed) recorded the same 3 agents (main, reviewer, verifier) with the same window start as the forced record; `--record nonexistent-change review` is still refused and writes no file.
- Open questions: none.
- On approval: `/pr`.
- On remarks: `/change`.

## Agents

| Agent | Model | Id | Context |
|---|---|---|---|
| reviewer | inherited | a6c4c5ee8d959a67d | one round |
| verifier | inherited | a6196e70bd85cde16 | docs |
