---
name: spec
description: Work out the requirements of a change with the user in the main session, edit docs/requirements/ on the branch and in the plan /change started, get them reviewed, and stop at the requirements gate with a handover. Start it yourself only after the user agreed, as at the path gate of /change.
argument-hint: "<change-name>"
arguments: [change]
---

Work out the requirements of the change `$change` with the user: stage 2 of the **Stages** section of `docs/process.md`. You are the designer. Tests are written from what you write here, so it must be complete enough for that, and no more detailed: **Requirements** in `docs/development/docs.md` says what a requirement holds and when it has a scenario.

## Start

1. Read the plan `docs/plans/$change.md`. If it is on another branch, say so and offer to switch. If there is none, or the branch is not `feature/$change`, stop and go to `/change`: the goal, the approach and the path are confirmed there, and it creates the branch and the plan.
2. If the plan has a handover, continue from it: read only the plan and what the handover names, and stop reading there.
3. Otherwise read as **What to read** in `docs/process.md` says: the roadmap item, the index and the capabilities it names, the decisions index, and the API contract for related endpoints. `docs/ui/guidelines.md` when screens change.

## Work it out with the user

Go one topic at a time. Propose a concrete draft from what you have read, give your recommendation, and ask only what the user has to decide. Domain rules come from the user: never invent who may see or do what. Record what is undecided in the plan instead of guessing, and the user's decisions in their words.

1. The goal and the approach are in the plan's Scope, as the user confirmed them at the path gate: do not reopen them. If the work shows that the path does not fit (no requirement changes, or the choice reopens what the change is for or splits it into several), stop, write into the handover `Waiting for: a new path` with what you found and what is already made, and go to `/change`.
2. Scope and what is out of scope, into the plan. Check it against the product scope in the constitution.
3. Who may do what, what each role sees, what is audited, error paths and empty states. Where access depends on relations between people, cover how they change: cohort and track conditions, past cohorts, a mentor reassigned, a graduate who becomes a mentor, status changes and archived cohorts. Each becomes a requirement or an out-of-scope line, not a list of its own.
4. Edit the capability files directly, as you go, so progress is not lost. A requirement that belongs to an existing capability goes there, and a change to another capability's behavior edits that capability.
5. Size: at about 8 to 10 scenarios or more, or many requirements without any, or when the change adds more than one new module or mixes application code with new local infrastructure (an identity provider, a mail server), suggest splitting the change, and let the user decide.

A decision that meets the threshold in the decisions index goes through `/decision`.

Do not commit. A scenario without a test fails the pre-commit hook; the requirements are committed with the red tests.

## Review

When the draft is complete, run the `reviewer` subagent in requirements mode with the plan's path. Show the user every finding with your recommendation, agree on changes and apply them. One round.

## Stop at the requirements gate

1. Add the agents to the plan's Agents table, and write the plan's handover: the capability files to read in full, the documents changed (built from git as the template says, so a decision or guide changed along the way is not missed), the open questions, and what to run after approval and after remarks.
2. Record the session as the change's `spec` phase, without a session id: `node .claude/scripts/agent-usage.mjs --record $change spec`. Check that the sessions it prints are this change's; never pick a session id or pass `--force` without the user.
3. Tell the user which files to read, in full and not as a diff, and show the documents changed list as it is in the plan. Then say that reading may take as long as they like: the plan holds everything needed, so they can continue here, or run `/clear` first and then `/spec $change` for remarks or `/feature $change` once approved.

The requirements are approved only when the user says so explicitly. Agreement with a finding or a single point is not approval. Record it in the plan's `Requirements approved:` line, with the date and the user's words.
