# A path for every change

Branch: `chore/a-path-for-every-change`
Roadmap item: **A path for every change** (Next, item 1)

## Scope

**Goal.** Any change, wherever it starts, gets onto the path of `docs/process.md` that fits it and is led by the skills to the pull request. The owner does not have to know which command comes first, and no change skips the process unnoticed.

**Path.** A change outside the requirements: it edits `docs/process.md`, the skills, the hooks, `.claude/README.md` and `AGENTS.md`, and touches no capability, contract or application code. Done in the main session, then `/review`, `/verify`, `/pr`.

**Approach,** as the owner confirmed it:

1. A new skill of the first step, `/change`, which the agent may start itself: goal apart from the suggested way, approach and path with a recommendation, one gate, then the branch and the first document of the path.
2. `docs/process.md`: **Choosing a path** becomes the mandatory first step and takes the goal-and-approach step from Specify; nothing is changed on the default branch; whoever finds the change on the wrong path stops and returns to the first step.
3. The third path is redefined as a change outside the requirements (`chore`): the build, tooling, documents, and details of the code and the UI that the requirements do not hold. The question that separates the paths is whether the requirements describe what changes, or should, not whether a user sees it.
4. A new skill `/chore` leads that path from the brief to `/pr`. The brief is `docs/plans/<change>.md`, from a template whose headings are a subset of the plan's.
5. `/spec` no longer creates the branch or works out the goal and approach; `/explore` always keeps notes on its own branch. The agent may start `/explore`, `/spec` and `/chore` with the owner's agreement.
6. Branch prefixes name the path: `feature/`, `fix/`, `change/`, `chore/`, `explore/`.
7. A bug and a small change of behavior get no skills of their own: `/change` gives their steps and starts the agents. A small change of behavior always has a brief; a bug gets one at the owner's first decision or stop after the path gate.
8. Hooks: an edit of a committed file on the default branch is refused, and so is `git commit` there. `/pr` runs a check of the branch prefix against the diff.
9. The `/pr` template gains the owner's decisions and the guide passages with their four answers.
10. A rule the requirements should hold and do not: at the first step it makes the change a change of behavior; found on the way, it stops the change when the change alters that rule, and is a finding for the owner when it does not. The reviewer checks it on a `chore/` branch.
11. A bug whose case no scenario describes: a scenario when the case merits one (then the path is a small change of behavior), an ordinary test without an id otherwise.

**Out of scope.** Skills for the bug and small-change paths; running the path check in CI; recording token usage for a `chore` change.

## Tasks

| Task | Who | Status |
|---|---|---|
| Brief template, this brief | main session | done |
| `docs/process.md`, `docs/README.md`, `AGENTS.md` | main session | done |
| Skills `/change`, `/chore`; `/explore`, `/spec`, `/feature`, `/pr` and its template; `reviewer` | main session | done |
| Hooks, `scripts/check-path.mjs`, `settings.json`, `.claude/README.md` | main session | done |
| Review, verification, roadmap item deleted | main session | done |

## Partial work

## Decisions

Translated from Russian.

- 2026-11-10: on the stop trigger "it turned out that a requirement, the contract or a migration has to be changed or added": "this must be emphasized: it may turn out that the change is not covered by a requirement but in essence should be, and the process must account for that."
- 2026-11-10: "between `chore` and `minor`, `chore` is better: a change may be not minor at all in the amount of work and still be purely technical."
- 2026-11-10: "yes, by the recommendations on all open points": the goal, the approach and the path above. The points were: the brief lives in `docs/plans/<change>.md` with a template that is a subset of the plan's; the prefixes `feature/`, `fix/`, `change/`, `explore/`; no skills for a bug and a small change, `/change` gives their steps; `git commit` refused on the default branch and a check in `/pr` of the branch prefix against the diff; this change is made by the new rules, with a brief; a bug has no brief by default; a bug's case without a scenario gets a scenario (path `change/`) or an ordinary test; no skill leads the bug's steps after `/change`.
- 2026-11-10: "we agreed that chore does not record token usage, but I do not see where that is stated": a `chore` change records no usage, and the `/chore` skill and `.claude/README.md` say so. It was the agent's open question 4 until then.
- 2026-11-10: "I approve the other five choices too": an exploration started for one change's approach may return to choosing a path on the same branch; an `explore/` branch changes only files under `docs/`; a command that also changes the branch skips the commit check; a test of a requirement added for existing behavior is shown to fail when the rule is broken; the roadmap item is deleted at the finish.
- 2026-11-10, on the reviewer's 15 findings: "fix everything; on finding 6 I agree with the recommendation". The recommendation: a `change/` always has a brief, since it has a gate; a `fix/` gets one when a decision or a stop occurs, and it records the path-gate answer. This replaces "a bug has no brief by default" for the small change of behavior.
- 2026-11-10, on the five minors of the re-review: "fix by the recommendation, then run the verifier". Fixed: the order of deleting the roadmap item and the brief in `docs/process.md`; "commit the deletion if the brief was ever committed" in `/pr`; the return step of `/change` no longer creates the document again; `.claude/README.md` says the commit check sees only a direct `git` call. Left as it is: a commit message that holds `; git switch`.
- 2026-11-10: "I accept; delete the item from the roadmap and commit".

## Handover

- Waiting for: the owner runs `/pr`.
- To read: the diff against `main`; `docs/process.md` from **Choosing a path** to stage 2 and stage 8, and `.claude/skills/change/SKILL.md`, in full.
- Documents changed:
  - `docs/process.md`, changed: **Choosing a path** is the mandatory first step with the path gate, the five paths with their branch prefixes, and the new **When the path turns out wrong**; stage 1 keeps notes from the start and says how an exploration ends; stage 2 loses the goal-and-approach step; the handover goes into the plan or the brief; stage 8 says when a brief is deleted. New since the last stop: the alternatives with trade-offs and the two lost triggers for Explore restored; a small change of behavior always has a brief, a bug from its first stop; where the confirmed path goes when a change returns.
  - `docs/README.md`, changed: the `docs/plans/<change>.md` row names the brief.
  - `AGENTS.md`, changed: a rule that every change starts with **Choosing a path**; "read it before anything larger than a quick fix" removed.
  - No guide in `docs/development/` and no `docs/ui/guidelines.md` passage added or widened.
  - Outside `docs/`: new skills `change` and `chore` with the brief template (the plan template moved to `change`); `explore`, `spec`, `feature`, `pr` and its template, `review`, `reviewer`, `backend-dev`, `frontend-dev`; `guard-files.mjs`, `guard-bash.mjs`, `lib.mjs`; new `scripts/check-path.mjs` with its allow rule; `.claude/README.md`.
- State: all 15 findings fixed in one round. `node scripts/check-docs.mjs` and `node scripts/requirements.mjs trace` pass. The hooks were tried against a throwaway clone on `main`: a commit and an edit of a committed file are refused, also with `-C`, `-c` and a variable assignment; a commit message that mentions "checkout", `echo git commit`, a new file, another repository and a branch pass. `scripts/check-path.mjs` gives the same 24 files from the root and from `backend/`. `/change` and `/chore` were not run headless.
- Open questions: none.
- On approval: `/pr`, which reads this brief and deletes it.
- On remarks: apply them, rebuild this handover from git.

## Agents

| Agent | Model | Id | Context |
|---|---|---|---|
| reviewer | opus | a83d8f1c79fd64d47 | 99k |
| reviewer, re-review | sonnet | ab70e934ff28ee9d1 | 48k |
| verifier | default | a2532af7997c374e4 | - |
