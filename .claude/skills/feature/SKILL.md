---
name: feature
description: Take a change from approved requirements to a verified branch: the plan and contract, red tests by test-engineer, code by backend-dev and frontend-dev, review and verification, with a handover in the plan before every stop for the user.
argument-hint: "<change-name>"
arguments: [change]
disable-model-invocation: true
---

Take the change `$change` from approved requirements to a verified branch: stages 3 to 8 of the **Stages** section of `docs/process.md`. You are the coordinator: you write the plan, brief the agents, relay their reports and the user's decisions, and keep the plan current. Every code change goes through an agent, and you do not read code or run builds to check their work: `reviewer` and `verifier` do that, so your context stays small.

## The plan is the memory

`docs/plans/$change.md` holds the state across sessions. The conversation is not a reliable memory: a new session must be able to continue from the plan, what its handover names and `git log` alone. Write it once per stop, in one edit, because every edit re-reads the whole session.

## Start

1. Read the plan. If it is not on the current branch, find the branch that has it (`git log --all -1 --format='%h %D' -- docs/plans/$change.md`) and offer to switch. If there is none, stop and suggest `/spec $change`.
2. Check the plan's `Requirements approved:` line. If it says `none` (or is missing), ask the user whether they approve the requirements as they stand now, naming the files from the handover. On an explicit yes, write the date and their words into that line and go on. On remarks, stop and suggest `/spec $change`. Requirements stay uncommitted until the red tests, so an edit made after the approval cannot be detected: an approval covers the files as they stand when it is given.
3. Continue from the handover. Read only what it names. Agents from an earlier session cannot be continued: start fresh ones, briefed from the plan.

## Before every stop for the user

Every stop below means these steps, in this order:

1. Write the plan: task statuses, the user's decisions in their words, the agents with their context sizes, and the handover, with its documents changed built from git as the plan template says.
2. Record the session under the current phase (`plan`, `implementation` or `review`), without a session id: `node .claude/scripts/agent-usage.mjs --record $change <phase>`. A later record of the same phase and session replaces it. Check that the sessions it prints are this change's; never pick a session id or pass `--force` without the user.
3. Relay the report as below, and say that the user can take their time: they can answer here, or run `/clear` first and then `/feature $change` to continue from the handover at a fraction of the context.

Never suggest `/compact`: a summary may drop what the plan keeps exactly.

## Agents and briefs

Brief every agent with the plan's path and one task. It reads the rest itself.

- **Within a task** (feedback, rework): continue the same agent with `SendMessage`.
- **Between tasks:** continue the same agent while its last context size is under about 150k tokens; above that, start a fresh one, since the context budget would soon stop it in the middle of the next task.
- **Context budget:** past about 250k, a hook in `backend-dev`, `frontend-dev` and `test-engineer` tells the agent to finish its step and stop with a PARTIAL report and a handover. The agent writes the handover into the plan's Partial work under its task itself, so that it is not retold. On PARTIAL, check that it is there, do not rewrite it, keep the task `in progress`, and brief a fresh agent of the same type with the same task, saying that it continues from the handover. Never continue the stopped agent. When the task is done, delete its Partial work.
- **An agent that ends without its report** (an error, a stop, a session that ended, a report without a status): if it can still be continued (this session; in a cloud session, its transcript as below), continue it once with `SendMessage`: end with the report, and if the task is not done, with status PARTIAL and the handover its Context budget section asks for. Otherwise brief a fresh agent of the same type with the same task, saying that there is no handover: it starts from `git status`, `git diff` and the plan, and runs the verification of its area before going on. Write into the plan's Partial work, under the task, that the agent stopped without a handover, and when.
- **Cloud sessions** (`CLAUDE_CODE_REMOTE=true`): before continuing an agent, check that its transcript is in the folder of the most recently written session: `ls -t ~/.claude/projects/$(pwd | sed 's/[^a-zA-Z0-9]/-/g')/*.jsonl | head -1` names the session, and `<session>/subagents/agent-<id>.jsonl` must exist. If not, start a fresh agent of the same type.

Before every brief or `SendMessage`, check that the current branch is the plan's. Never switch branches while an agent is running: background agents write to the same working tree.

## Relaying reports

Show a short summary: what was done, where to look first, the verification result. Show decisions, deviations, the list of details, the documents changed from the handover and questions in full; never shorten a question.

A failure an agent attributes to the environment (network, Docker, tool versions) goes to a `general-purpose` agent with the `model` parameter set to `sonnet`, to find the cause and a workaround. Until the cause is confirmed, it is an open question in the plan, not a fact.

## Plan

Write the plan's Requirements, Design, What the tests rely on and Tasks. Put in the design only what the agents would otherwise guess. Tasks follow the feature, not a fixed pipeline, and each names the scenarios it serves. When screens change, a `backend-dev` task adds their sample data, as the backend guide says, before the frontend stop. Propose a checkpoint on a hard part. Then brief `backend-dev` for the contract and any migration.

**Stop at the plan gate:** the user reads the plan and the diff of the contract and the migration. Relay remarks to the agent or fix the plan. Go on only when the user approves, and record that.

## Tests first

Brief `test-engineer` for its first pass. Check its report: every new or changed scenario has a test, and each failed on an assertion, not on compilation. `node scripts/requirements.mjs trace` must pass.

Offer to commit the requirements, the contract, the plan and the tests together, and write the commit into the plan's Red tests. Commit only if the user agrees.

A doubt the test engineer reports about a requirement goes to the user. If a requirement changes, it needs the user's reading again, for that part only.

## Implement

Brief `backend-dev` for the backend tasks and `frontend-dev` for the frontend ones. They may run in parallel, unless the plan orders them.

After each report, list what changed in the test files since the red commit. A changed test that carries an id is a stop: **Tests** in `docs/process.md` says who decides. Do not pass it on as done.

**Stop when the backend is green,** and at any task the plan marks as a checkpoint. The user reviews the diff in their IDE. Relay their answer in their words. On approval, offer to commit only that work plus the plan.

**When the frontend is green,** run `manual-tester` on the changed screens: the screens, the personas and the states from the plan and the requirements. Run the manual checks from the report on `manual-tester` too, with the `model` parameter set to `haiku`, the steps as a numbered list. Then **stop**: the user reads its report and screenshots (`.screens/<change>/<run>/`) and chooses what goes to `frontend-dev`. Relay their choice in their words. After the fixes, run `manual-tester` once more on the affected states only, then the user looks at the screens themselves, on the sample center, signed in as the personas the report names. A `NOT RUN` from `manual-tester` is a broken environment: show its error to the user and, if they agree, send it to the environment investigation of **Relaying reports**; do not retry it another way.

**List of details.** Show the user each developer's list. What the user agrees with stays as it is. For the rest, follow stage 5 of `docs/process.md`: a matter of taste goes to the developer with an ordinary test; a rule goes into the requirement first, then to `test-engineer`, then to the developer.

## Tests of details

Skip this for a small change, and say so. Otherwise brief a fresh `test-engineer` for its second pass. Doubtful behavior it reports goes to the user. A failing test it reports goes to the developer who owns the area, or to the user when it is not clearly a bug. Note in the plan whether it found a real bug: **Stages** in `docs/process.md` says the pass is removed if it finds none.

## Review loop

1. Run `reviewer` on the scope (the default model), with the base ref and the plan's path.
2. Show the user every finding at once, blockers to minors, with your recommendation. The user decides what to fix.
3. Send all accepted fixes to the agent that owns each area in one batch, so there is one fix round per review. For documentation fixes, tell the agent to check every statement against the code, not against the finding's wording.
4. If the fixes were substantial, run a re-review: `reviewer` with the `model` parameter set to `sonnet`, the previous findings and the ref the fixes start from. A new blocker or major from it goes back to step 2.

## Verify and finish

1. Run `verifier` with scope `all`.
2. Report what was built per requirement, the verification table, `trace`, open items and a proposed commit message. **Stop** for the user to accept.
3. On acceptance, finish as stage 8 of `docs/process.md` says: an architectural choice to the decisions, a rule for later changes to a guide (the **Guides** section of `docs/README.md`), the rest stays in code comments; delete the roadmap item, the plan and `.screens/<change>/`. Show the user these changes, and each passage added to a guide or widened with the four answers the **Guides** section of `docs/README.md` asks for; remove a passage the user does not agree with.
4. Commit only if the user asks. Then suggest `/pr`, and `/usage-review` once the pull request is open.
