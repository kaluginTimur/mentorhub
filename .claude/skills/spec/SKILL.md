---
name: spec
description: Work out the requirements of a change with the user in the main session, edit docs/requirements/ on a new branch, get them reviewed, and stop at the requirements gate with a handover.
argument-hint: "<change-name>"
arguments: [change]
disable-model-invocation: true
---

Work out the requirements of the change `$change` with the user: stage 2 of the **Stages** section of `docs/process.md`. You are the designer. Tests are written from what you write here, so it must be complete enough for that, and no more detailed: **Requirements** in `docs/development/docs.md` says what a requirement holds and when it has a scenario.

## Start

1. If `docs/plans/$change.md` exists, continue from its handover. Read only the plan and what the handover names, and stop reading there. If the plan is on another branch, say so and offer to switch.
2. Otherwise run `git status`, ask how to proceed if there are unrelated changes, create the branch `feature/$change` from the default branch (or use the one the user prefers), and create the plan from [plan-template.md](plan-template.md).
3. Read as **What to read** in `docs/process.md` says: the roadmap item, the index and the capabilities it names, the decisions index, and the API contract for related endpoints. `docs/ui/guidelines.md` when screens change.

## Work it out with the user

Go one topic at a time. Propose a concrete draft from what you have read, give your recommendation, and ask only what the user has to decide. Domain rules come from the user: never invent who may see or do what. Record what is undecided in the plan instead of guessing, and the user's decisions in their words.

1. Goal and approach, before anything else. The roadmap item is an intention, not a decision, even when its line reads as settled: state its goal (who needs what, and why) apart from the way the line suggests, and propose any other way that reaches the goal better, with its trade-offs and your recommendation. The user chooses. If the choice reopens what the item is for, or splits it into several, offer to continue in `/explore` instead.
2. Scope and what is out of scope, into the plan. Check it against the product scope in the constitution.
3. Who may do what, what each role sees, what is audited, error paths and empty states. Each becomes a requirement or an out-of-scope line, not a list of its own.
4. Edit the capability files directly, as you go, so progress is not lost. A requirement that belongs to an existing capability goes there, and a change to another capability's behavior edits that capability.
5. Size: at about 8 to 10 scenarios or more, or many requirements without any, suggest splitting the change, and let the user decide.

A decision that meets the threshold in the decisions index goes through `/decision`.

Do not commit. A scenario without a test fails the pre-commit hook; the requirements are committed with the red tests.

## Review

When the draft is complete, run the `reviewer` subagent in requirements mode with the plan's path. Show the user every finding with your recommendation, agree on changes and apply them. One round.

## Stop at the requirements gate

1. Add the agents to the plan's Agents table, and write the plan's handover: the capability files to read in full, the open questions, and what to run after approval and after remarks.
2. Record the session as the change's `spec` phase, without a session id: `node .claude/scripts/agent-usage.mjs --record $change spec`. Check that the sessions it prints are this change's; never pick a session id or pass `--force` without the user.
3. Tell the user which files to read, in full and not as a diff. Then say that reading may take as long as they like: the plan holds everything needed, so they can continue here, or run `/clear` first and then `/spec $change` for remarks or `/feature $change` once approved.

The requirements are approved only when the user says so. Record that in the plan.
