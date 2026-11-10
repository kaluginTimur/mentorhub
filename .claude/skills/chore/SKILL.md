---
name: chore
description: Lead a change outside the requirements (the build, tooling, documents, a detail of the code or the UI) from its brief to a verified branch ready for /pr, with a handover in the brief before every stop for the user. Start it yourself only after the user agreed, as at the path gate of /change.
argument-hint: "<change-name>"
arguments: [change]
---

Lead the change `$change` from its brief to a verified branch: the path **A change outside the requirements** of **Choosing a path** in `docs/process.md`. The brief `docs/plans/$change.md` holds the state across sessions: a new session continues from it, what its handover names and `git log`.

## Start

1. Read the brief. If it is not on the current branch, find the branch that has it (`git log --all -1 --format='%h %D' -- docs/plans/$change.md`) and offer to switch. If there is none, or the branch is not `chore/$change`, stop and go to `/change`: the path is chosen there, not here.
2. If the brief has a handover, continue from it and read only what it names. Otherwise write the tasks into the brief, and read what they touch.

## Work

- Documents, skills, hooks and scripts you edit yourself. Application code goes to `backend-dev` or `frontend-dev`, briefed with the brief's path and one task; add each agent to the brief's Agents table. On a PARTIAL report, a fresh agent of the same type continues from the brief's Partial work.
- A detail a user sees gets an ordinary test without an id when something can break it. When screens change, the user looks at them on the sample center before the review.
- A passage added to a guide or widened needs the four answers of the **Guides** section of `docs/README.md`: write them into the handover and show them to the user. Remove a passage the user does not agree with.
- Record the user's decisions in the brief, in their words. What you inferred stays out of that list.

## When the path is wrong

Stop as soon as the change needs a requirement changed or added, the contract or a migration. That includes a rule this change alters that no requirement holds and one should, by the criterion in **Requirements** of `docs/development/docs.md`: a rule about access, data, state transitions, security or irreversible actions, or one whose breach is hard to notice. Do not finish the task first: write into the handover `Waiting for: a new path` with what you found and what is already made, and go to `/change`.

A rule of that kind that the change does not alter is not a reason to stop: write it under the handover's open questions, and the user decides at the next stop what becomes of it.

## Before every stop for the user

Update the task statuses and the handover, with its documents changed built from git as the brief template says. Say that the user can answer here, or run `/clear` first and then `/chore $change`. Never suggest `/compact`.

Do not record token usage: `agent-usage.mjs --record` and `/usage-review` measure only changes that go through `/spec` and `/feature`.

## Review, verify, finish

1. Run `reviewer` with the base ref and the brief's path. Show the user every finding at once with your recommendation; the user decides what to fix. One fix round; after substantial fixes, a re-review with the `model` parameter set to `sonnet`, the previous findings and the ref the fixes start from.
2. Run `verifier` for the areas the change touches.
3. Report what was done, the verification table, open items and a proposed commit message. **Stop** for the user to accept.
4. On acceptance, finish as stage 8 of `docs/process.md` says: a rule for later changes to its guide, an architectural choice through `/decision`. Delete the roadmap item, if the change has one. Keep the brief: `/pr` takes the user's decisions and the answers for each guide passage from it, and deletes it before the pull request is opened.
5. Commit only if the user asks. Then suggest `/pr`.
