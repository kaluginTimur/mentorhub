# Workflow cost after tracks

Branch: `chore/workflow-cost`
Roadmap item: none

## Scope

Confirmed by the owner at the path gate on 2026-11-15 ("да, подтверждаю": yes, confirmed), after `/usage-review` of tracks ("давай всё, что без риска, test-engineer можно попробовать на Sonnet и зафиксировать effort у агентов": do everything quality-neutral, try test-engineer on Sonnet, and pin the agents' effort).

**Goal:** a feature on the spec-anchored process costs less at the same quality: above all the coordinator (57% of Opus in tracks) and `test-engineer` (19%); `.claude/README.md` says truthfully where the cost goes now.

**Approach:**
1. A `Stop` hook in `.claude/settings.json` with a script in `.claude/hooks/`: when the main session's context at a stop is above about 200k, it shows the figure and suggests `/clear`, then the skill's command, to continue from the handover. A reminder only; it blocks nothing.
2. `/feature` (and `/chore` and the plan and brief templates, where the same rule is): the handover is replaced, not appended; it holds the current state only, and decisions stay in Decisions.
3. `frontend-dev` and the coordinator in `/feature` ask dependency API questions through `api-lookup` instead of reading `node_modules`, as `backend-dev` already does.
4. `test-engineer` on Sonnet as a trial; the Cost and context section names it a trial and the signals the next `/usage-review` judges it by: tests that check nothing, red tests that fail on compilation, wrong tests reported by developers.
5. Effort pinned at `medium` in every agent definition (`backend-dev`, `frontend-dev`, `test-engineer`, `reviewer`, `manual-tester`, `verifier`, `api-lookup`); the main session keeps the owner's `/effort`. Verify the frontmatter field's name in the Claude Code documentation first.
6. `.claude/README.md`, Cost and context: the conclusion that after the 1-hour cache and fresh agents the coordinator is the largest Opus share (51–91% in five changes: invitation-links, invitation-emails, agents-look-at-screens, observer-role-removed, tracks), and lines for the rules above.

**In:** the agent definitions, the skills `/feature` (and `/chore` where the handover rule is shared), the templates, `.claude/settings.json`, a new hook script, `.claude/README.md`.

**Out:** the process stages in `docs/process.md` (the second test pass stays until its second data point), the visual loop with `manual-tester`.

No requirement describes how the AI client's agents are configured, and none should: requirements describe MentorHub's behavior. Capabilities were not searched for that reason.

Evidence (the tracks usage record, in main since pull request #44): the coordinator's implementation session ran 150 calls at an average 235k context (last 359k), 3.95M weighted, 35% of the feature's Opus; the session after `/clear` averaged 68k. `frontend-dev` made 6 and the coordinator 4 inspections of dependency artifacts, with no `api-lookup` records. The plan reached 36 KB, its handover keeping a line per manual-test batch. Tracks ran all agents at `high`, inherited from the session; the old-process features ran at `medium`.

## Tasks

| Task | Who | Status |
|---|---|---|
| 1. Check the agent frontmatter field for effort and the `Stop` hook input (context size) in the Claude Code documentation | coordinator (Context7) | done |
| 2. All edits of the approach, 1–6 | coordinator | done |
| 3. Review and verification | reviewer, verifier (docs) | done |
| 4. Fixes of the review's 12 findings | coordinator | done |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

## Decisions

- 2026-11-15: Effort `medium` for every agent; the reminder threshold about 200k (proposed at the path gate, confirmed with the path).
- 2026-11-15: The review's findings: "исправь все замечания" (fix all the findings), on the recommendation that 10 is reworded to what the records show and 8 narrowed to the frontend.

## Handover

- Waiting for: `/pr`. Accepted by the owner ("принимаю, коммить": accepted, commit) and committed.
- To read: this brief; `git diff 67c714a` (the branch's base; origin/main has since gained only the tracks usage record).
- Documents changed: none under `docs/` apart from this brief; `AGENTS.md` unchanged. Changed: `.claude/agents/*.md` (effort medium in all, test-engineer on Sonnet, api-lookup answers behavior for frontend packages, the dev agents send dependency questions to api-lookup), `.claude/skills/feature/SKILL.md`, `.claude/skills/change/plan-template.md`, `.claude/skills/change/brief-template.md`, `.claude/skills/usage-review/SKILL.md`, `.claude/settings.json` (Stop hook), `.claude/README.md` (Cost and context intro and rows, hooks table and criterion); added `.claude/hooks/coordinator-context.mjs`.
- State: all 12 findings fixed. The hook resolves the repository root from `cwd`, skips while `session_crons` is non-empty, names `/change` for a new path and ends silently on any error; tested by piping JSON (a reminder from `backend/`, none with a cron, exit 0 on bad input). The Stop input fields were checked in the Claude Code hooks documentation. The README and usage-review figures were checked against the records: coordinator 47–91% of Opus, the session after `/clear` averaged 68k, observer-role-removed's test-engineer 0.22M (14%) at medium and its Sonnet agents at high.
- Open questions: none.
- On approval: `/pr`. Stage 8 added nothing: no guide rule, no decision, no roadmap item.
- On remarks: `/chore workflow-cost`.

## Agents

| Agent | Model | Id | Context |
|---|---|---|---|
| reviewer | opus | ae05e40683f18812f | 93k |
| reviewer (re-review) | sonnet | a462881bee36c068c | n/a |
| verifier (docs) | sonnet | ac2b9c7f77acab9bb | n/a |
