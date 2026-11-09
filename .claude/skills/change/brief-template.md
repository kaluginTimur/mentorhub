# <Change title>

Branch: `<chore, or fix or change for a stop on those paths>/<change>`
Roadmap item: <its line, or "none">

<!-- The brief of a change outside the requirements. Its headings are those of the plan template, so a change that moves to the stages gains sections and loses nothing. -->

## Scope

<The goal and the approach the user confirmed at the path gate, what is in, what is out. Which capabilities were searched, and why no requirement describes what changes or should.>

## Tasks

| Task | Who | Status |
|---|---|---|

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

<!-- Written by an agent stopped by the context budget, under a heading with its task; a fresh agent continues from it. Deleted when the task is done. -->

## Decisions

<!-- One line per decision of the user, in their words. What is the agent's own stays out of this list. They go into the pull request description before this brief is deleted. -->

- <YYYY-MM-DD>: <decision>

## Handover

<!-- Overwritten before every stop for the user. A new session reads the brief and what this names, and nothing else, so keep it short and exact. -->

- Waiting for: <what the user is doing; exactly `a new path` and what was found, when the path turned out wrong>
- To read: <files, or the diff and its base>
- Documents changed: <built from git at every stop, never from memory: `git status --short` and `git diff --name-status $(git merge-base HEAD main)`. Every added or changed file in `docs/` (except this brief) and `AGENTS.md`, one line each: added or changed, and what changed in it. Under a guide, each passage added or widened, with its answers to the four questions of the **Guides** section of `docs/README.md`.>
- State: <what is done, and the verification result>
- Open questions: <none, or each with your recommendation. A rule found on the way that the requirements should hold and do not is a question here.>
- On approval: <the next command and step>
- On remarks: <the next command and step>

## Agents

<!-- Agents can be continued only within the session that started them; a new session starts fresh ones from this brief. -->

| Agent | Model | Id | Context |
|---|---|---|---|
