---
name: chore
description: >-
  Use this skill to lead a change outside the requirements (build, tooling,
  documents, or a detail of code or UI) on its chore/ branch from its brief in
  docs/plans/ to a verified branch ready for a pull request, with a handover in
  the brief before every stop for the user. Activate only after the user
  confirmed this path at the path gate of the change skill.
---

Lead the change `<change>` from its brief to a verified branch: the path **A change outside the requirements** of **Choosing a path** in `docs/process.md`. The brief `docs/plans/<change>.md` holds the state across sessions: a new session continues from it, what its handover names and `git log`.

## Start

1. Read the brief `docs/plans/<change>.md` (`read_file`). If it is not on the current branch, find the branch that has it (`git log --all -1 --format='%h %D' -- docs/plans/<change>.md` via `run_command`) and offer to switch. If there is none, or the branch is not `chore/<change>`, stop and go to the `change` skill: the path is chosen there, not here.
2. If the brief has a handover, continue from it and read only what it names. Otherwise write the tasks into the brief, and read what they touch.

## Work

- Documents, skills, hooks and scripts you edit yourself (`edit_file` or `write_file`). Application code goes to `backend-dev` or `frontend-dev` (registered via `define_subagent` from `.claude/agents/backend-dev.md` or `.claude/agents/frontend-dev.md` per `.agents/plugins/mentorhub/rules/AGENTS.md` and launched via `invoke_subagent`), briefed with the brief's path and one task; add each agent to the brief's Agents table. On a `PARTIAL` report, start a fresh `invoke_subagent` of the same role continuing from the brief's Partial work (and never resume a long-running subagent via `send_message` if more than 5 minutes have elapsed since it stopped).
- A detail a user sees gets an ordinary test without an id when something can break it. When screens change, the user looks at them on the sample center before the review.
- A passage added to a guide or widened needs the four answers of the **Guides** section of `docs/README.md`: write them into the handover and show them to the user. Remove a passage the user does not agree with.
- Record the user's decisions in the brief, in their words. What you inferred stays out of that list.

## When the path is wrong

Stop as soon as the change needs a requirement changed or added, the contract or a migration. That includes a rule this change alters that no requirement holds and one should, by the criterion in **Requirements** of `docs/development/docs.md`: a rule about access, data, state transitions, security or irreversible actions, or one whose breach is hard to notice. Do not finish the task first: write into the handover `Waiting for: a new path` with what you found and what is already made, and go to the `change` skill.

A rule of that kind that the change does not alter is not a reason to stop: write it under the handover's open questions, and the user decides at the next stop what becomes of it.

## Before every stop for the user

Update the task statuses and write the handover in `docs/plans/<change>.md`, with its documents changed built from git as `.claude/skills/change/brief-template.md` says, so the owner can answer here or start a fresh Antigravity session from the brief (continuing with the `chore` skill for `<change>`).

## Review, verify, finish

1. Register (from `.claude/agents/reviewer.md`) and invoke `reviewer` with the base ref and the brief's path. Show the user every finding at once with your recommendation; the user decides what to fix. One fix round; after substantial fixes, run a fresh re-review via `invoke_subagent` with `Model: "inherit"`, passing the previous findings and the ref the fixes start from.
2. Register (from `.claude/agents/verifier.md`) and invoke `verifier` for the areas the change touches.
3. Report what was done, the verification table, open items and a proposed commit message. **Stop** for the user to accept.
4. On acceptance, finish as stage 8 of `docs/process.md` says: a rule for later changes to its guide, an architectural choice through the `decision` skill. Delete the roadmap item, if the change has one. Keep the brief: the `pr` skill takes the user's decisions and the answers for each guide passage from it, and deletes it before the pull request is opened.
5. Commit only if the user asks. Then suggest running the `pr` skill.
