---
name: feature
description: Implement an approved feature spec by delegating to the backend-dev and frontend-dev subagents, with a user checkpoint after every backend step and a visual checkpoint for the frontend, then review and verify. Progress is kept in a per-feature log.
argument-hint: "<feature-name> [contract|migration|domain|api|frontend|review|verify]"
arguments: [feature, step]
disable-model-invocation: true
---

Implement the feature `$feature` from `docs/features/$feature.md`. You are the coordinator: you brief the agents, relay their reports and the user's decisions, and keep the progress log. In this flow you do not write code yourself; every code change goes through `backend-dev` or `frontend-dev`.

## Progress log

`docs/features/$feature.progress.md`, created from [progress-template.md](progress-template.md), is the memory of this feature across sessions: step statuses, the user's decisions in their words, open questions, step commits and agent ids. It lives on the feature branch, so it always describes the code next to it.

Update it at every checkpoint: when a step starts, when its report is shown, when the user decides, when a commit is made. The conversation is not a reliable memory; the log is.

## Start

1. Find the progress log. If it is not on the current branch, find the branch that has it before anything else: `git log --all -1 --format='%h %D' -- docs/features/$feature.progress.md` prints one line with the last commit and its branches, or nothing.
   - **Log on the current branch:** resume from it (see below).
   - **Log only on another branch:** do not start over and do not ask for a new spec. Tell the user which branch has them and the last step there, and offer to switch to that branch, to bring its commits here (merge or cherry-pick, only after the user agrees), or to start over deliberately.
   - **No log anywhere:** this is a fresh start.
2. Read the spec. Its status must be `approved` and no open question may block implementation; otherwise stop and suggest `/spec $feature`.
3. Run `git status`. If there are unrelated uncommitted changes, ask the user how to proceed before starting.
4. On a fresh start, create the branch `feature/$feature` from the default branch (or use the branch the user prefers), create the log from the template, and start with the contract.
5. Read the **Mock mode** section of `docs/development/frontend.md` to know whether the frontend prototype step is possible.

## Resuming

The log is the source of truth for what is approved and what was decided. `git log` on the branch confirms the committed steps; `git diff` shows only the work of the step in progress. If they disagree (a step marked approved without its changes, changes for a step marked pending), show the user the mismatch and ask; never guess.

Continue from the first step that is not approved; `$step`, when given, overrides that. Agents listed in the log can be continued with `SendMessage` only within the session that created them; otherwise start fresh agents and include the log in their brief.

## Agents and briefs

Brief every agent with: the spec path, the progress log path, the step to do, and the user's decisions since the spec was written. Record each agent's id in the log. Continue the same agent with `SendMessage` so it keeps its context; start a new one only when the old one is gone.

Before every brief or `SendMessage`, check that the current branch is the one in the log. If not, stop and ask the user. Never switch branches while an agent is running: background agents write to the same working tree.

## Backend: a checkpoint after every step

Steps of `backend-dev`: 1 contract, 2 migration, 3 domain (domain, service, authorization), 4 api (controllers and tests).

After each step:
1. Show the user the agent's report: the summary, "Where to look first", decisions, deviations and questions. Do not shorten the questions. Mark the step `in review`.
2. The user reviews the diff in their IDE and answers. Record the answer in the log's Decisions.
3. Relay the answer to the same agent with `SendMessage`, in the user's words: feedback means "apply this, verify, report again"; approval means the step is done.
4. On approval, mark the step `approved` and offer to commit it: only that step's files (backend steps: everything outside `frontend/`; frontend steps: `frontend/` only) plus the log, with a message following `AGENTS.md`. Commit only if the user agrees, then record the commit in the log. Then send "proceed to step N".

Never approve a step on the user's behalf and never send "proceed" without the user's explicit go-ahead.

## Frontend: starts after the contract is approved

Start `frontend-dev` in the background as soon as the user approves step 1. It runs in parallel with backend steps 2-4.

- **Mock mode implemented:** brief it for step A (prototype). When it reports, start the mock dev server in the background as the Mock mode section describes, and give the user the URL and how to see each state. Relay visual feedback with `SendMessage` until the user approves, then send "proceed to step B".
- **Mock mode not implemented:** tell the user the prototype step is skipped (mark it `skipped`) and the screens will be reviewed at the end; brief it for step B directly.

When step B is done, run the `reviewer` subagent with the focus "frontend changes of $feature". Send blocker and major findings to the same `frontend-dev` to fix, at most two rounds. Then show the user the screens, the manual check list per AC and any findings that remain, and offer to commit the frontend as described above.

## Review and verification

After backend step 4 and frontend step B:

1. Run `reviewer` on all changes of the feature. Show the user every finding; the user decides what to fix. Route each fix to the agent that owns the area, and re-run the review if the fixes were substantial.
2. Run `verifier` with scope `all`.
3. Report to the user:
   - what was built, per scenario;
   - AC coverage: every AC-n with its tests and its manual check;
   - the verification table;
   - open items and follow-ups;
   - a proposed commit message: plain English, no trailers, no mention of AI.
4. When the user accepts the feature, compare the spec's Design section with the final contract, migrations and modules, and update it wherever the implementation diverged (the log's decisions and the agents' deviations are the starting point). Show the user the changes to the spec, then set its status to `implemented` and mark the remaining log steps.
5. Commit only if the user asks. Then suggest `/pr` to open a pull request; this report and the log are its sources.
