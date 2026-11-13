---
name: feature
description: Take a change from approved requirements to a verified branch: the plan and contract, red tests by test-engineer, code by backend-dev and frontend-dev, review and verification, with a handover in the plan before every stop for the user.
---

Take the change `<change>` from approved requirements to a verified branch: stages 3 to 8 of the **Stages** section of `docs/process.md`. You are the coordinator: you write the plan, brief the agents, relay their reports and the user's decisions, and keep the plan current. Every code change goes through an agent, and you do not read code or run builds to check their work: `reviewer` and `verifier` do that, so your context stays small.

## The plan is the memory

`docs/plans/<change>.md` holds the state across sessions. The conversation is not a reliable memory: a new session must be able to continue from the plan, what its handover names and `git log` alone. Write it once per stop, in one edit, because every edit re-reads the whole session.

## Start

1. Read the plan (`docs/plans/<change>.md`). If it is not on the current branch, find the branch that has it (`git log --all -1 --format='%h %D' -- docs/plans/<change>.md`) and offer to switch. If there is none, stop and suggest the `change` skill. If the branch is not `feature/<change>`, stop and ask: the branch's prefix names the path, and the `change` skill renames it.
2. Check the plan's `Requirements approved:` line. If it says `none` (or is missing), ask the user whether they approve the requirements as they stand now, naming the files from the handover. On an explicit yes, write the date and their words into that line and go on. On remarks, stop and suggest the `spec` skill for `<change>`. Requirements stay uncommitted until the red tests, so an edit made after the approval cannot be detected: an approval covers the files as they stand when it is given.
3. Continue from the handover. Read only what it names. If the plan and git disagree (a task marked done without its changes, changes for a task still pending), show the user the mismatch and ask; never guess. Agents from an earlier session cannot be continued: start fresh ones, briefed from the plan.

## Before every stop for the user

Every stop below means these steps, in this order:

1. Write the plan: task statuses, the user's decisions in their words, each report's "Notes for the next task" under Notes for later tasks, the agents (role, model, id, phase) in `Agents`, and the handover, with its documents changed built from git as `.claude/skills/change/plan-template.md` says.
2. Relay the report as below, and say that the user can take their time: they can answer here, or start a fresh Antigravity session later and invoke the `feature` skill for `<change>` to continue from the handover in `docs/plans/<change>.md` at a fraction of the context.

Never suggest compacting the conversation: a summary may drop what the plan keeps exactly.

## Agents and briefs

Before invoking a project subagent role (`api-lookup`, `backend-dev`, `frontend-dev`, `test-engineer`, `manual-tester`, `reviewer`, `verifier`) for the first time in the conversation, read `.claude/agents/<role>.md`, strip its YAML frontmatter, and register it via `define_subagent` with the capabilities and model tier specified in `.agents/plugins/mentorhub/rules/AGENTS.md` (appending the Antigravity tool mappings and noting that `backend/AGENTS.md` and `frontend/AGENTS.md` point to the area guides on demand).

Brief every agent with `docs/plans/<change>.md` and one task. Keep briefs scoped to one task or checkpoint at a time so subagent contexts stay small; the agent reads the rest itself.

- **5-minute resume cutoff:** Antigravity has no 1-hour subagent prompt cache, so a paused subagent's cache expires after 5 minutes.
  - **Within a task** (feedback, rework): you may continue the same subagent via `send_message` (targeting its `conversationId`) **only if less than 5 minutes have elapsed** since it stopped.
  - **After 5 minutes, between tasks, or after a stop for the user:** **never resume** a long-running subagent (`backend-dev`, `frontend-dev`, `test-engineer`) via `send_message`. Start a **fresh** `invoke_subagent` with `docs/plans/<change>.md` (its handover, decisions, and "Notes for later tasks") and the user's feedback. Put the last report's "Notes for the next task" into the brief when continuing before a stop has written them to the plan.
- **Parallel independent tasks (`swarm`):** When tasks split cleanly into parallel independent pieces (for example, independent `frontend-dev` screen tasks, or parallel `backend-dev` and `frontend-dev` tasks when the plan does not order them), use `swarm` with `workspace: "share"` so authors working concurrently get isolated working copies, and land their changes into your working copy before advancing. Keep `workspace: "inherit"` for read-only workers.
- **Context budget:** If a context-budget warning or limit causes `backend-dev`, `frontend-dev`, or `test-engineer` to finish its step and stop with a `PARTIAL` report and a handover, the agent writes the handover into the plan's `Partial work` section under its task itself, so that it is not retold. On `PARTIAL`, check that it is there, do not rewrite it, keep the task `in progress`, and brief a fresh `invoke_subagent` of the same role with the same task, saying that it continues from the handover. Never continue the stopped agent. When the task is done, delete its `Partial work` entry.
- **An agent that ends without its report** (an error, a stop, a session that ended, a report without a status): if it stopped less than 5 minutes ago in this session and can still receive messages, send one `send_message` asking it to end with the report (and if the task is not done, with status `PARTIAL` and a handover in `Partial work`). Otherwise brief a fresh `invoke_subagent` of the same role with the same task, saying that there is no handover: it starts from `git status`, `git diff`, and the plan, and runs the verification of its area before going on. Write into the plan's `Partial work` section, under the task, that the agent stopped without a handover, and when.

Before every `invoke_subagent`, `send_message`, or `swarm` call, check that the current branch is the plan's (`feature/<change>`). If it is not, stop and ask the user. Never switch branches while an agent is running.

## Relaying reports

Show a short summary: what was done, where to look first, the verification result. Show decisions, deviations, the list of details, the documents changed from the handover, and questions in full; never shorten a question.

A failure an agent attributes to the environment (network, Docker, tool versions) goes to a `self` subagent (`invoke_subagent` with `TypeName: "self"`, `Model: "inherit"`) to find the cause and a workaround. Until the cause is confirmed, it is an open question in the plan, not a fact.

A report that stops with questions under "API lookups" comes from an agent without subagent tools (such as `frontend-dev`, which has `EnableSubagentTools: false`). Register `api-lookup` if not yet registered (`Model: "flash_lite"`), invoke it via `invoke_subagent` with all the symbols in one question, and pass the answers back to the waiting agent (via `send_message` if under 5 minutes since it stopped, or a fresh `invoke_subagent` with the answers and handover if 5 minutes or more have passed).

## Plan

Write the plan's Requirements, Design, What the tests rely on, and Tasks. Put in the design only what the agents would otherwise guess. Tasks follow the feature, not a fixed pipeline, and each names the scenarios it serves. When screens change, a `backend-dev` task adds their sample data, as `docs/development/backend.md` says, before the frontend stop. Propose a checkpoint on a hard part. Then brief `backend-dev` (`Model: "pro"` or `"inherit"`) for the contract and any migration.

**Stop at the plan gate:** the user reads the plan and the diff of the contract and the migration. Relay remarks to `backend-dev` (respecting the 5-minute resume cutoff) or fix the plan. Go on only when the user approves, and record that.

## Tests first

Brief `test-engineer` (`Model: "pro"` or `"inherit"`) for its first pass. Check its report: every new or changed scenario has a test, and each failed on an assertion, not on compilation. `node scripts/requirements.mjs trace` must pass.

Offer to commit the requirements, the contract, the plan, and the tests together, and write the commit into the plan's Red tests. Commit only if the user agrees.

If at any stage the change turns out to be on the wrong path (it changes no requirement after all), stop, write into the handover `Waiting for: a new path` with what you found and what is already made, and switch to the `change` skill.

A doubt the test engineer reports about a requirement goes to the user. If a requirement changes, it needs the user's reading again, for that part only.

## Implement

Brief `backend-dev` for the backend tasks and `frontend-dev` (`EnableMCPTools: true`, `Model: "inherit"`) for the frontend ones. They may run in parallel unless the plan orders them.

After each report, list what changed in the test files since the red commit. A changed test that carries an id is a stop: **Tests** in `docs/process.md` says who decides. Do not pass it on as done. Also compare `git diff --stat` with the files the brief named: a file the brief did not name, or one changed far more than its task needs (a skill or a document that lost whole sections), goes back to the agent before you relay the report.

**Stop when the backend is green,** and at any task the plan marks as a checkpoint. The user reviews the diff in their IDE. Relay their answer in their words. On approval, offer to commit only that work plus the plan.

**When the frontend is green,** run `manual-tester` on the changed screens: the screens, the personas, and the states from the plan and the requirements. Run the manual checks from the report on `manual-tester` too, with `Model: "flash_lite"`, the steps as a numbered list. Then **stop**: the user reads its report and screenshots (`.screens/<change>/<run>/`) and chooses what goes to `frontend-dev`. Relay their choice in their words (to a fresh `frontend-dev` subagent after the stop). After the fixes, run `manual-tester` once more on the affected states only, then the user looks at the screens themselves, on the sample center, signed in as the personas the report names. A `NOT RUN` from `manual-tester` is a broken environment: show its error to the user and, if they agree, send it to the environment investigation of **Relaying reports**; do not retry it another way.

**List of details.** Show the user each developer's list. What the user agrees with stays as it is. For the rest, follow stage 5 of `docs/process.md`: a matter of taste goes to the developer with an ordinary test; a rule goes into the requirement first, then to `test-engineer`, then to the developer.

## Tests of details

Skip this for a small change, and say so. Otherwise brief a fresh `test-engineer` for its second pass. Doubtful behavior it reports goes to the user. A failing test it reports goes to the developer who owns the area, or to the user when it is not clearly a bug. Note in the plan whether it found a real bug: **Stages** in `docs/process.md` says the pass is removed if it finds none.

## Review loop

1. Register (if needed) and invoke `reviewer` (`Model: "pro"` or `"inherit"`) on the scope, with the base ref and the plan's path.
2. Show the user every finding at once, blockers to minors, with your recommendation. The user decides what to fix.
3. Send all accepted fixes to the agent that owns each area in one batch, so there is one fix round per review (use fresh subagents if more than 5 minutes have passed since the previous subagent stopped). For documentation fixes, tell the agent to check every statement against the code, not against the finding's wording.
4. If the fixes were substantial, run a re-review: a fresh `reviewer` with `Model: "inherit"`, the previous findings, and the ref the fixes start from. A new blocker or major from it goes back to step 2.

## Verify and finish

1. Register (if needed) and invoke `verifier` (`Model: "inherit"`) with scope `all`.
2. Report what was built per requirement, the verification table, `trace`, open items, and a proposed commit message. **Stop** for the user to accept.
3. On acceptance, finish as stage 8 of `docs/process.md` says: an architectural choice to the decisions, a rule for later changes to a guide (the **Guides** section of `docs/README.md`), the rest stays in code comments; delete the roadmap item, the plan, and `.screens/<change>/`. Show the user these changes, and each passage added to a guide or widened with the four answers the **Guides** section of `docs/README.md` asks for; remove a passage the user does not agree with.
4. Commit only if the user asks. Then suggest the `pr` skill.
