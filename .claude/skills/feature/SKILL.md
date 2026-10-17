---
name: feature
description: Implement an approved feature spec by delegating to the backend-dev and frontend-dev subagents, with a user checkpoint after every backend step and a visual checkpoint for the frontend, then review and verify. Progress is kept in a per-feature log; each phase runs in its own session.
argument-hint: "<feature-name> [contract|migration|domain|api|frontend|review|verify]"
arguments: [feature, step]
disable-model-invocation: true
---

Implement the feature `$feature` from `docs/features/$feature.md`. You are the coordinator: you brief the agents, relay their reports and the user's decisions, and keep the progress log. In this flow you do not write code yourself; every code change goes through `backend-dev` or `frontend-dev`. You also do not read code or run builds to check the agents' work: `reviewer` and `verifier` do that, so your context stays small.

## Progress log

`docs/features/$feature.progress.md`, created from [progress-template.md](progress-template.md), is the memory of this feature across sessions: step statuses, the user's decisions in their words, handoff notes, pending relays, open questions and step commits. It lives on the feature branch, so it always describes the code next to it.

The conversation is not a reliable memory; the log is. A new session must be able to continue from the log, the spec and `git log` alone, so write everything there that a later step needs.

Write the log once per checkpoint, in one edit: when the user has decided, record the status, the decision, the handoff notes and, if made, the commit together. Also write it before a phase boundary, and record a relay as pending when you send it and clear it when the agent has acted.

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

The log is the source of truth for what is approved, what was decided and what is pending. `git log` on the branch confirms the committed steps; `git diff` shows only the work of the step in progress. If they disagree (a step marked approved without its changes, changes for a step marked pending), show the user the mismatch and ask; never guess.

Continue from the first step that is not approved; `$step`, when given, overrides that. Agents from an earlier session cannot be continued: start fresh ones briefed from the log. Relays marked pending in the log go into the new agent's brief.

## Agents and briefs

Brief every agent with: the spec path, the progress log path, the step to do, the user's decisions since the spec was written, and the handoff notes of earlier steps. Record each agent's id and, after each report, its context size in the log; the completion notification gives the size.

- **Within a step** (the user's feedback, rework): continue the same agent with `SendMessage`.
- **Between steps:** continue the same agent while its last context size is under about 150k tokens. Above that, start a fresh agent for the next step. Its old context is mostly earlier steps' reasoning, which it no longer needs, and every call re-reads it.

Before every brief or `SendMessage`, check that the current branch is the one in the log. If not, stop and ask the user. Never switch branches while an agent is running: background agents write to the same working tree.

## Relaying reports

Show the user a short summary of each report: what was done, the places to look first and the verification result. Show the decisions, deviations from the spec and questions in full; never shorten a question. The full report stays available to quote on request.

## Backend: a checkpoint after every step

Steps of `backend-dev`: 1 contract, 2 migration, 3 domain (domain, service, authorization), 4 api (controllers and tests). Steps 1 and 2 form one checkpoint: brief them together, and the user reviews both in one diff. Otherwise one step per checkpoint, unless the user asks to merge more for a small feature.

After each checkpoint:
1. Relay the report as above.
2. The user reviews the diff in their IDE and answers.
3. Relay the answer to the agent in the user's words: feedback means "apply this, verify, report again"; approval means the step is done.
4. On approval, mark the step `approved`, copy the report's "Notes for the next step" into the log's handoff notes, and offer to commit it: only that step's files (backend steps: everything outside `frontend/`; frontend steps: `frontend/` only) plus the log and the usage record, with a message following `AGENTS.md`. Commit only if the user agrees. Then send "proceed to step N", or start a fresh agent for it (see above).

Never approve a step on the user's behalf and never send "proceed" without the user's explicit go-ahead.

## Frontend: starts after the contract is approved

Start `frontend-dev` in the background at the start of phase 2, together with backend step 3. It runs in parallel with the remaining backend steps.

- **Mock mode implemented:** brief it for step A (prototype). When it reports, start the mock dev server in the background as the Mock mode section describes, and give the user the URL and how to see each state. Relay visual feedback with `SendMessage` until the user approves, then send "proceed to step B".
- **Mock mode not implemented:** tell the user the prototype step is skipped (mark it `skipped`) and the screens will be reviewed at the end; brief it for step B directly.

When step B is done, run the review loop below with the focus "frontend changes of $feature". Then show the user the screens and the manual check list per AC, and offer to commit the frontend as described above.

## Review loop

1. Run `reviewer` on the scope (the default model).
2. Show the user every finding at once, blockers to minors, with your recommendation per finding. The user decides what to fix.
3. Send all accepted fixes to the agent that owns each area in one batch, so there is one fix round per review.
4. If the fixes were substantial, run a re-review: `reviewer` with the `model` parameter set to `sonnet`, the previous findings and the ref the fixes start from. It checks those findings and the fix diff only. A new blocker or major from it goes back to step 2.

## Phase boundaries: one session per phase

The phases are: 1) `contract` (contract and migration); 2) `implementation` (domain, API and the frontend, which run in parallel); 3) `review` (the final review and verification). At the end of a phase, when no agent is running:

1. Record the session's token usage under the phase name, as the **Recording usage** section of `.claude/README.md` describes.
2. Write the log and include the usage record in the phase's last commit (or commit both on their own, if the user agrees).
3. Tell the user:

> Phase done. Run `/clear`, then `/feature $feature` to continue in a fresh session.

The new session starts from the log at a fraction of the old context. Never suggest `/compact`: a summary may drop details the log keeps exactly. The `review` phase ends the feature: record it before the final commit, and skip the message.

## Review and verification

After the backend and the frontend are approved:

1. Run the review loop on all changes of the feature.
2. Run `verifier` with scope `all`.
3. Report to the user:
   - what was built, per scenario;
   - AC coverage: every AC-n with its tests and its manual check;
   - the verification table;
   - open items and follow-ups;
   - a proposed commit message.
4. When the user accepts the feature, compare the spec's Design section with the final contract, migrations and modules, and update it wherever the implementation diverged (the log's decisions, handoff notes and the agents' deviations are the starting point). Show the user the changes to the spec, then set its status to `implemented` and mark the remaining log steps.
5. Commit only if the user asks. Then suggest `/pr` to open a pull request; this report and the log are its sources.
