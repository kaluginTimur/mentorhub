# Run each manual check once

Branch: `chore/manual-checks-once`
Roadmap item: none

## Scope

Goal: after the frontend is green, `manual-tester` walks the numbered manual-check steps from `frontend-dev`'s report even when its screen run has just seen the same outcomes. In track-mentors all 5 steps repeated the screen run (0.12M Haiku; 0.37M in enrollment-mentors). Each step should be walked once, with nothing lost from what is checked.

Approach, confirmed at the path gate: in `.claude/skills/feature/SKILL.md`, section Implement, the paragraph "When the frontend is green": the coordinator compares the manual-check steps with the screen run's report and sends to the numbered `haiku` run only the steps whose outcome the screen run did not show. When it covered them all, the coordinator says so in the report to the user and skips the run. A step counts as covered only when the screen report names its outcome in the same state, as the same persona, with the same expected result; a visible button or screen alone does not cover it.

Rejected alternative: giving the steps to the screen run's brief, so that one `manual-tester` does both; the steps would then run on Sonnet instead of Haiku, and two report formats would mix.

In: that paragraph of the skill. Out: `docs/process.md` (it says only that the manual tester runs manual checks), the agents.

Requirements: none describes the workflow's skills, and none should; `docs/process.md` holds no order of the runs.

## Tasks

| Task | Who | Status |
|---|---|---|
| T1 Edit the paragraph of `.claude/skills/feature/SKILL.md` | coordinator | done |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

## Decisions

- 2026-11-22: "принимаю, всё по рекомендациям, коммить" — change accepted; both re-review minors fixed in the skill (a setup step's verdict says only whether its precondition held; `.screens/<change>/`, one folder per run).
- 2026-11-22: "всё по рекомендациям" — all four review findings fixed in one round.
- 2026-11-21: "да, подтверждаю" — goal, approach and path `chore/manual-checks-once` as proposed at the path gate.

## Handover

- Waiting for: `/pr`.
- To read: this brief.
- Documents changed: added this brief; changed `.claude/skills/feature/SKILL.md` (the paragraph "When the frontend is green").
- State: done and accepted; committed. Docs verification PASS.
- Open questions: none.
- On approval: `/pr`.
- On remarks: `/chore manual-checks-once`.

## Agents

| Agent | Model | Id | Context |
|---|---|---|---|
| reviewer | definition default | a7e3c75c72ef18b9a | — |
| reviewer | sonnet | aa91d6351622c437e | — |
| verifier | definition default | aa054c3e90750e59a | — |
