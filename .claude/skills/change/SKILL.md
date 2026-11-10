---
name: change
description: The first step of every change, before any committed file is edited - state the goal, propose the approach and the path of docs/process.md, get the user's one confirmation, then create the branch and the path's first document. Start it yourself whenever the user asks for anything that would be committed (a feature, a fix, a refactoring, a document or roadmap edit, an exploration) and the work is not already on a branch of its path, and when another skill finds its change on the wrong path.
argument-hint: "<what is needed: a roadmap item, an issue, an idea, a request>"
---

Choose the path of the change "$ARGUMENTS" with the user: **Choosing a path** in `docs/process.md`. You are the designer. Nothing is edited before the user confirms, and nothing on the default branch at all.

## Start

1. Run `git status` and `git branch --show-current`. With unrelated uncommitted changes, ask how to proceed.
2. On a branch other than the default one, the change may have passed this step already. Look for its brief or plan in `docs/plans/` or its notes in `docs/explorations/`, and take the first that fits:
   - The handover of the brief or the plan says `Waiting for: a new path`: a skill, or the steps of **Without a skill**, found the path wrong. Go to **A change that returns**.
   - A `fix/` or `change/` branch with a brief: continue the steps of **Without a skill** from its handover, and read only what it names.
   - A `fix/` branch without a brief: a bug has none until its first stop. Show the user what the branch holds (`git log`, `git status`) and ask where the work stands, then continue **Without a skill** from there.
   - An `explore/` branch without notes: the exploration is finished, since `/explore` deletes them at its end, and the user goes on with one change. Go on to **Propose**; the branch is renamed after the confirmation.
   - A `chore/`, `feature/` or `explore/` branch with its document: the path is chosen and its skill continues it, `/chore <change>`, `/spec <change>` or `/feature <change>`, `/explore <topic>`, from the handover or, for an exploration, from the notes. Say so, and reopen the path only if the user asks for that.
   - Anything else is a branch of another change, or one without a path's prefix: ask how to proceed.
3. Read what the choice needs and no more, as **What to read** in `docs/process.md` says: the roadmap item or the issue if there is one, the requirements index and the capabilities the change may touch, the decisions index. Read the code only as far as telling the paths apart needs, such as reproducing a bug. Run `git fetch` before relying on what the default branch holds.

## Propose

One message, short:

- **Goal:** who needs what, and why, apart from the way the request or the roadmap line suggests.
- **Approach:** the way you recommend, and any other that reaches the goal better or with a real trade-off, even when the roadmap line reads as settled. If the choice needs alternatives weighed at length, research or an experiment, or reopens what the roadmap item is for or splits it into several, propose the path Explore instead of dragging this step.
- **Requirements:** the capabilities you searched, and one of: a requirement describes what changes (name its id); none does and none should; none does and one should, by the criterion in **Requirements** of `docs/development/docs.md`. Whether something is a rule or a detail is the user's decision: give your recommendation and never settle it yourself.
- **Path,** with the reason:

  | Found | Path | Branch |
  |---|---|---|
  | An idea too vague to name its path, or a choice this step cannot settle | Explore | `explore/<topic>` |
  | The code departs from a requirement | A bug | `fix/<topic>` |
  | One or two requirements change or are added, no contract, no migration | A small change of behavior | `change/<change>` |
  | No requirement describes what changes, and none should: the build, tooling, documents, a detail of the code or the UI | A change outside the requirements | `chore/<change>` |
  | Anything else | The stages | `feature/<change>` |

  What users call a bug is wider than the path: the code may follow a requirement that is wrong (a change of behavior), or break a detail no requirement holds (a change outside the requirements).
- **Size:** suggest a split when the change meets the sizes **Choosing a path** names.
- Any question only the user can answer, each with your recommendation.

**Stop at the path gate.** The user confirms the goal, the approach and the path in one answer. Agreement with one point is not the confirmation. This step writes no handover: nothing exists yet.

## After the confirmation

1. Create the branch from the default branch, with the prefix of the path. On an `explore/` branch whose exploration is finished and whose user goes on with one change now, rename that branch instead (`git branch -m <prefix>/<change>`): its roadmap edits go into the change's pull request.
2. Write what the user confirmed, in their words, into the first document of the path, and go on:
   - **Explore:** `docs/explorations/<topic>.md`, then `/explore <topic>`.
   - **The stages:** `docs/plans/<change>.md` from [plan-template.md](plan-template.md), the goal and the approach in its Scope, then `/spec <change>`.
   - **A change outside the requirements:** `docs/plans/<change>.md` from [brief-template.md](brief-template.md), then `/chore <change>`.
   - **A small change of behavior:** `docs/plans/<change>.md` from [brief-template.md](brief-template.md), then **Without a skill** below.
   - **A bug:** no document yet. Follow **Without a skill** below; the brief is created at the first decision of the user or stop for the user, and records what they confirmed here as well.

   The user's confirmation of the path is their agreement to start that skill. Start it in this session, or name the command when the user wants to stop here.

## Without a skill

A bug and a small change of behavior have no skill of their own. Lead them here, and stop for the user where the steps say. **Tests** in `docs/process.md` says who writes and who may change a test, and **Stopping for the owner** what a handover holds: read both first.

**A bug.**

1. Brief `test-engineer`: a test that carries the scenario's id and fails on an assertion, written from the requirement. When no scenario describes the broken case, ask the user first: a new scenario, which makes this a small change of behavior, or an ordinary test without an id. Their answer is a decision: it goes into the brief.
2. Brief `backend-dev` or `frontend-dev` with the failing test and the requirement. A one-line fix you may make yourself. Nobody changes a test with an id to make the code pass.
3. Run `reviewer` with the base ref, and the brief's path if there is one; show the user every finding at once with your recommendation, and fix what they choose in one round. Run `verifier` for the areas touched. **Stop** for the user to accept, then suggest `/pr`, with the issue it fixes: only the user starts it.

**A small change of behavior.**

1. Edit the requirement, as **Requirements** in `docs/development/docs.md` says. **Stop:** the user reads and approves it.
2. `test-engineer` for the red test, then the developer, as for a bug. For a requirement added for what the code already does, the test passes at once: say so, and have `test-engineer` show that it fails when the rule is broken.
3. `reviewer`, `verifier` and the stop as for a bug. One commit with the requirement, the test and the code, if the user agrees. Then suggest `/pr`.

Before every stop for the user, write the handover into the brief, with `/change` as the command that continues: on a `fix/` or `change/` branch it comes back to these steps. A bug gets its brief then, from [brief-template.md](brief-template.md), if it has none. Record the user's decisions in the brief, in their words. `/pr` reads the brief and deletes it.

Stop when the path turns out wrong: the fix needs a requirement, the contract or a migration changed, or the small change grows past one or two requirements. Go to **A change that returns**; if there is a brief, write `Waiting for: a new path` into its handover first, with what you found. Another bug found on the way becomes a GitHub issue, if the user agrees, and stays out of this branch.

## A change that returns

A skill, or the steps of **Without a skill**, stopped because the path does not fit, and the handover of its document, if it has one, says `Waiting for: a new path` and what was found.

1. Read that document and `git status`, and list for the user what exists: the branch, the document, the commits and the uncommitted work.
2. Propose the new path as in **Propose**, and what happens to each thing made so far, as **When the path turns out wrong** in `docs/process.md` says. Code that changes behavior and was written before its requirement and test cannot stay as it is: offer the split, moving the whole change, or deferring, with your recommendation. **Stop at the path gate.**
3. Then, with the user's confirmation: rename the branch (`git branch -m <prefix>/<change>`; ask first if it was pushed), overwrite the handover so that it no longer waits for a new path, and turn the brief into a plan by adding the sections of [plan-template.md](plan-template.md) it lacks, or the plan into a brief by removing the ones [brief-template.md](brief-template.md) lacks. For code that leaves the working tree, save it as a patch outside the repository and tell the user where; never discard it.
4. Write what the user confirmed into that document, in their words, and go on as step 2 of **After the confirmation** names for the new path, its skill or **Without a skill**, without creating the document again. When the new path is Explore, move what the brief or the plan holds into `docs/explorations/<topic>.md` and delete it. When it is a bug or a small change of behavior, the plan becomes a brief. A bug that returns here without a brief gets one now.
