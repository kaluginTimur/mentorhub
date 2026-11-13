---
name: spec
description: >-
  Use this skill to work out the requirements of a feature change (<change>)
  with the user, edit docs/requirements/ on its feature/ branch and update its
  plan in docs/plans/, get the requirements reviewed, and stop at the
  requirements gate with a handover. Activate only after the user confirmed the
  stages path at the path gate of the change skill.
---

Work out the requirements of the change `<change>` with the user: stage 2 of the **Stages** section of `docs/process.md`. You are the designer. Tests are written from what you write here, so it must be complete enough for that, and no more detailed: **Requirements** in `docs/development/docs.md` says what a requirement holds and when it has a scenario.

## Start

1. Read the plan `docs/plans/<change>.md` (`read_file`). If it is on another branch, say so and offer to switch. If there is none, or the branch is not `feature/<change>`, stop and go to the `change` skill: the goal, the approach and the path are confirmed there, and it creates the branch and the plan.
2. If the plan has a handover, continue from it: read only the plan and what the handover names, and stop reading there.
3. Otherwise read as **What to read** in `docs/process.md` says: the roadmap item, the index `docs/requirements/README.md` and the capabilities it names, the decisions index `docs/architecture/decisions/README.md`, and the API contract (`api/openapi.yaml`) for related endpoints. Read `docs/ui/guidelines.md` when screens change.

## Work it out with the user

Go one topic at a time. Propose a concrete draft from what you have read, give your recommendation, and ask only what the user has to decide. Domain rules come from the user: never invent who may see or do what. Record what is undecided in the plan instead of guessing, and the user's decisions in their words.

1. The goal and the approach are in the plan's Scope, as the user confirmed them at the path gate: do not reopen them. If the work shows that the path does not fit (no requirement changes, or the choice reopens what the change is for or splits it into several), stop, write into the handover `Waiting for: a new path` with what you found and what is already made, and go to the `change` skill.
2. Scope and what is out of scope, into the plan. Check it against the product scope in `docs/constitution.md`.
3. Who may do what, what each role sees, what is audited, error paths and empty states. Where access depends on relations between people, cover how they change: cohort and track conditions, past cohorts, a mentor reassigned, a graduate who becomes a mentor, status changes and archived cohorts. Each becomes a requirement or an out-of-scope line, not a list of its own.
4. Edit the capability files directly (`edit_file`), as you go, so progress is not lost. A requirement that belongs to an existing capability goes there, and a change to another capability's behavior edits that capability.
5. Size: at about 8 to 10 scenarios or more, or many requirements without any, or when the change adds more than one new module or mixes application code with new local infrastructure (an identity provider, a mail server), suggest splitting the change, and let the user decide.

A decision that meets the threshold in the decisions index (`docs/architecture/decisions/README.md`) goes through the `decision` skill.

Do not commit. A scenario without a test fails the pre-commit hook; the requirements are committed with the red tests.

## Review

When the draft is complete, register the `reviewer` subagent with `define_subagent` from `.claude/agents/reviewer.md` (if not yet registered, per `.agents/plugins/mentorhub/rules/AGENTS.md`) and launch it via `invoke_subagent` in requirements mode with the plan's path. Show the user every finding with your recommendation, agree on changes and apply them. One round.

## Stop at the requirements gate

1. Add the agents to the plan's Agents table, and write the handover in `docs/plans/<change>.md` (`edit_file` or `write_file`): the capability files to read in full, the documents changed (built from git as `.claude/skills/change/plan-template.md` says, so a decision or guide changed along the way is not missed), the open questions, and what to run after approval (the `feature` skill for `<change>`) and after remarks (the `spec` skill for `<change>`).
2. Tell the user which files to read, in full and not as a diff, and show the documents changed list as it is in the plan. Then say that reading may take as long as they like: the handover in `docs/plans/<change>.md` holds everything needed, so the owner can answer here or start a fresh Antigravity session from the plan (`spec` skill for remarks, or `feature` skill once approved).

The requirements are approved only when the user says so explicitly. Agreement with a finding or a single point is not approval. Record it in the plan's `Requirements approved:` line, with the date and the user's words.
