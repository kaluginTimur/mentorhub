---
name: feature
description: Implement an approved feature spec by delegating to the backend-dev and frontend-dev subagents, with a user checkpoint after every backend step and a visual checkpoint for the frontend, then review and verify.
argument-hint: "<feature-name> [contract|migration|domain|api|frontend|review|verify]"
arguments: [feature, step]
disable-model-invocation: true
---

Implement the feature `$feature` from `docs/features/$feature.md`. You are the coordinator: you brief the agents, relay their reports and the user's decisions, and keep track of progress. In this flow you do not write code yourself; every code change goes through `backend-dev` or `frontend-dev`.

## Start

1. Read the spec. Its status must be `approved` and no open question may block implementation; otherwise stop and suggest `/spec $feature`.
2. Run `git status`. If there are unrelated uncommitted changes, ask the user how to proceed before starting.
3. Read the **Mock mode** section of `docs/development/frontend.md` to know whether the frontend prototype step is possible.
4. If a step was given (`$step`), resume from it: reconstruct what is already done from `git status`, `git diff` and the spec, and start fresh agents with briefs that say so. Otherwise start with the contract.

## Agents and briefs

Brief every agent with: the spec path, the step to do, anything the user has decided since the spec was written, and what earlier steps already did when the agent is new. Keep each agent's id. Continue the same agent with `SendMessage` so it keeps its context; start a new one only when the old one is gone.

## Backend: a checkpoint after every step

Steps of `backend-dev`: 1 contract, 2 migration, 3 domain (domain, service, authorization), 4 api (controllers and tests).

After each step:
1. Show the user the agent's report: the summary, "Where to look first", decisions, deviations and questions. Do not shorten the questions.
2. The user reviews the diff in their IDE and answers.
3. Relay the answer to the same agent with `SendMessage`, in the user's words: feedback means "apply this, verify, report again"; approval means "proceed to step N".

Never approve a step on the user's behalf and never send "proceed" without the user's explicit go-ahead.

## Frontend: starts after the contract is approved

Start `frontend-dev` in the background as soon as the user approves step 1. It runs in parallel with backend steps 2-4.

- **Mock mode implemented:** brief it for step A (prototype). When it reports, start the mock dev server in the background as the Mock mode section describes, and give the user the URL and how to see each state. Relay visual feedback with `SendMessage` until the user approves, then send "proceed to step B".
- **Mock mode not implemented:** tell the user the prototype step is skipped and the screens will be reviewed at the end; brief it for step B directly.

When step B is done, run the `reviewer` subagent with the focus "frontend changes of $feature". Send blocker and major findings to the same `frontend-dev` to fix, at most two rounds. Then show the user the screens, the manual check list per AC and any findings that remain.

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
4. When the user accepts the feature, compare the spec's Design section with the final contract, migrations and modules, and update it wherever the implementation diverged (the deviations from the agents' reports are the starting point). Show the user the changes to the spec, then set its status to `implemented`.
5. Commit only if the user asks.
