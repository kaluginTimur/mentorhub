# Frontend fix rounds and the workflow findings of cohorts

Branch: `chore/frontend-fix-rounds`
Roadmap item: none (findings of the cohorts `/usage-review`)

## Scope

**Goal.** Spend less on the frontend loop after an approved mockup: the manual tester does not re-check what the owner checks faster themselves, the frontend developer sees its own screens before the screen run, and a tester run is not lost to a stack from another checkout. The coordinator settles with the owner up front whether the schema of a change goes into a new migration or an existing one, so that no verifier runs are spent after a late change of the migration.

**Approach (confirmed at the path gate).**
1. Sample data before the frontend: the `backend-dev` task with a screen's sample data finishes before the frontend task starts (today `.claude/skills/feature/SKILL.md` says "before the frontend stop", and in cohorts T5 and T6 ran in parallel, so `frontend-dev` never saw its screens). The `frontend-dev` brief says to look at its own screens before its report.
2. Re-checks after fixes: after a round of fixes of the owner's own remarks, no `manual-tester` run; the owner checks. When the fixes are substantial (several screens, a shared component or shared styles), the coordinator offers a regression run at the stop and the owner decides. Differences from the mockup and bugs found by the screen run itself are re-checked as today.
3. The stack: `scripts/stack.mjs start` stops a stack whose checkout is a deleted worktree of this repository, since nobody can be using it. For a live or unknown other checkout, the coordinator runs `stack.mjs status` before starting `manual-tester` and asks the owner then, instead of after a `NOT RUN`.
4. Migrations: when a change touches the schema, the coordinator asks the owner at the plan stage, before briefing `backend-dev` for the contract and migration, whether the schema goes into a new migration or into an existing one in main (which needs the owner's word, as the backend guide says), and records the answer in the plan.
5. The Stop-hook line of **Cost and context** in `.claude/README.md` gains cohorts (coordinator average 122k).

**In:** `.claude/skills/feature/SKILL.md`, the `frontend-dev` brief or definition where needed, `scripts/stack.mjs`, `.claude/README.md`, the `/chore` skill where it has a migration step.

**Out:** removing the second test pass, the `test-engineer` model trial, the effort of built-in agents (open proposals of the cohorts `/usage-review`, not decided).

**Requirements.** None searched beyond the index: the change is the workflow's tooling and skills, which no requirement of the product describes or should.

## Tasks

| Task | Who | Status |
|---|---|---|
| T1 `stack.mjs`: a worktree under `.claude/worktrees/` is another checkout; `start` stops a service of a deleted worktree and refuses one of another live checkout; `manual-tester` told so | coordinator | done |
| T2 `/feature`: sample data before the frontend task; `stack.mjs status` before the screen run; no tester run after the owner's own remarks, a regression run offered after a substantial round; `frontend-dev` looks at its screens before its report | coordinator | done |
| T3 Migrations: `/feature` and `/chore` ask the owner new or existing migration before it is written | coordinator | done |
| T4 `.claude/README.md`: the Stop-hook line gains cohorts | coordinator | done |
| T5 Review and verification | reviewer, verifier | done |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

## Decisions

- 2026-11-24: Path, goal and approach confirmed ("да, подтверждаю, строку про хук тоже обнови"), with the automatic stop of a stack from a deleted worktree.
- 2026-11-24: Fix rounds: "прогоны после каждой моей правки делать не надо, но если правок было основательное количество, координатор должен предложить запуск тестировщика для регрессии".
- 2026-11-24: Review findings: "по всем пунктам как рекомендуешь, кроме 4 - ты предлагаешь frontend-dev заглядывать в системный промпт manual-tester?"
- 2026-11-24: Finding 4: "A, проверь поддержку подгрузки skills": the browser procedure moves from `manual-tester.md` into a skill `browser`, preloaded by `manual-tester` and `frontend-dev`. Preloading checked: it works for a subagent started by the Agent tool, not for a `--agent` run.
- 2026-11-24: Migrations: "надо закрепить, чтобы это обговаривалось сразу и не было ситуации, как в cohorts с лишними запусками верифаера".
- 2026-11-24: Accepted: "одобряю, коммить и пуш".

## Handover

- Waiting for: `/pr`.
- To read: this brief, `git diff origin/main` and the untracked `.claude/skills/browser/`.
- Documents changed: changed `.claude/README.md` (flow: frontend after the sample data and looking at its screens, the owner's own remarks without a tester run; a Cost and context row for that rule; the Stop-hook line gains cohorts; Changing the workflow: preloaded skills load only into a subagent started by the Agent tool); no file in `docs/` besides this brief; no guide passage.
- State: T1-T5 done. Review: 6 findings, all fixed; re-review on Sonnet: 2 minors (a reference to the moved section, creating the run folder), fixed. `stack.mjs` tested by hand (own group, shared group, live other checkout); preloading tested headless. Verifier docs: PASS (check-docs, 64/64, trace 293/293).
- Open questions: none.
- Accepted; stage 8 done (no guide passage, no roadmap item); committed and pushed. Next: `/pr`.
- On remarks: `/chore frontend-fix-rounds`.

## Agents

| Agent | Model | Id | Context |
|---|---|---|---|
| reviewer | inherited | a0182c91c66968bc1 | 57k |
| verifier | inherited | a5ef0e4711f39ed33 | 13k |
| reviewer | sonnet | aa587a28f1aa5e081 | re-review |
