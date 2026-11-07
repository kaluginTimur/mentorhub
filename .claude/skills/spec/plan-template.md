# <Change title>

Branch: `feature/<change>`
Roadmap item: <its line, or "none">
Requirements approved: none
<!-- Only the user's explicit approval of the requirements as they stand: replace "none" with <YYYY-MM-DD> "<their words>". -->

## Scope

<Why, what is in, what is out.>

## Requirements

<The capabilities and ids this change adds, changes or removes.>

## Design

<From the plan stage: modules, data, API, how access and audit are enforced. Only what the agents would otherwise guess.>

## What the tests rely on

<From the plan stage: component inputs and `data-testid` selectors that exist before the code does.>

## Tasks

| Task | Scenarios | Who | Status |
|---|---|---|---|

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

<!-- Written by an agent stopped by the context budget, under a heading with its task; a fresh agent continues from it. Deleted when the task is done. -->

## Red tests

Commit: <the commit holding the requirements, the contract, this plan and the red tests>

## Decisions

<!-- One line per decision of the user, in their words. What is the agent's own stays out of this list. -->

- <YYYY-MM-DD>: <decision>

## Handover

<!-- Overwritten before every stop for the user. A new session reads the plan and what this names, and nothing else, so keep it short and exact. -->

- Waiting for: <what the user is doing>
- To read: <files, or the diff and its base>
- Documents changed: <built from git at every stop, never from memory: `git status --short` and `git diff --name-status $(git merge-base HEAD main)`. Every added or changed file in `docs/` (except this plan), `api/openapi.yaml`, `AGENTS.md` and the migrations, one line each: added or changed, and what changed in it. Mark "new since the last stop" each file the previous handover did not list, or that changed since. Under a guide, each passage added or widened, with its answers to the four questions of the **Guides** section of `docs/README.md`.>
- State: <what is done, and the verification result>
- Open questions: <none, or each with your recommendation>
- On approval: <the next command and step>
- On remarks: <the next command and step>

## Agents

<!-- Agents can be continued only within the session that started them; a new session starts fresh ones from this plan. Context is the size from the agent's last completion notification. Model is the `model` parameter of the call, or else the `model:` line of the agent's definition. -->

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
