# Process

How a change to MentorHub goes from an idea to main, when the project owner works with AI agents. It names no particular client: each one maps these stages to its own commands in its configuration folder. The rules every change must keep are in `docs/constitution.md`; its `C-n` numbers are labels for conversation and review reports, so living documents do not cite them.

## What is the authority

The Authority column of `docs/README.md` says which document decides when two disagree, and where a detail that turns out to matter moves.

## Roles

| Role | Does | Does not |
|---|---|---|
| Project owner | Decides the product and the requirements, and passes the gates | |
| Designer | Works out the requirements and the plan with the owner | Decide what the product does |
| Test engineer | Writes the tests that carry scenario ids and the tests of details, and owns shared test infrastructure | Read the implementation for the tests with ids; fix doubtful behavior in a test |
| Developer | Writes the code to pass the tests, and unit tests for non-obvious algorithms | Change a test engineer's test |
| Manual tester | Tests the running application through its UI: looks at the changed screens in each state as the personas who see them, runs manual checks, reports what is implausible | Edit code, tests or documents; fix what it finds |
| Reviewer | Checks requirements or a diff independently | Edit anything |

The test engineer and the developer are separate agents with separate contexts.

Only the owner promotes ideas, makes product decisions and amends the constitution. An agent proposes, and says which is which: in plans and decisions, what the owner decided stays apart from what an agent inferred. The roadmap holds only what the owner proposed or approved: an agent adds an item, an idea or a product decision only with the owner's word, so nothing in it is marked.

## Choosing a path

Every change starts here, wherever it began and however small it is. A change is anything that would be committed: an exploration and an edit of the roadmap too. Nothing is changed on the default branch.

Before anything is changed, the designer states the goal of the change (who needs what, and why) apart from any way already suggested for it, proposes an approach and any other way that reaches the goal better, with the trade-offs, and names the path that fits, with a recommendation. A roadmap item is an intention and its line a first guess, so the path follows from the goal and the approach, not from the item. The step is short. A choice that needs more (alternatives weighed at length, research, an experiment) goes to Explore, and so does one that reopens what a roadmap item is for, or splits it.

What separates the paths is whether the requirements describe what changes, or should. The designer names the capabilities searched and applies the criterion in **Requirements** of `docs/development/docs.md`: a rule that meets it and is not in the requirements makes the change a change of behavior, which starts with that requirement. Whether something is such a rule or a detail is the owner's decision.

- **Explore** (`explore/`), for an idea too vague to name its path, or a choice the step above cannot settle: stage 1 below.
- **A bug** (`fix/`), where the code departs from a requirement: a test with the scenario's id, and the fix. No document changes. When no scenario describes the broken case, the case either merits one, and the change is a small change of behavior, or gets an ordinary test without an id. A bug found during other work becomes a GitHub issue, not a roadmap item, and is not fixed in that change.
- **A small change of behavior** (`change/`): the requirement, changed or added, its test and the code, in one commit, with a brief instead of a plan. The owner approves the requirement first. When the requirement is added for what the code already does, its test passes at once, so the test engineer shows that it fails when the rule is broken.
- **A change outside the requirements** (`chore/`): the build, tooling, documents, and details of the code and the UI that the requirements do not hold, however much work it is. No requirements and no plan: a brief `docs/plans/<change>.md` holds the goal, the approach, the owner's decisions and the handover. A visible detail gets an ordinary test when something can break it, and the owner looks at the screen. It still keeps the verification and stage 8: a rule for later changes goes to its guide, and the roadmap item is deleted. The brief is deleted last, once the owner's decisions and the answers for each guide passage are in the pull request description.
- **Everything else** (`feature/`), including anything touching more than one or two requirements, the contract or a migration: stages 2 to 8 below.

**Path gate.** The owner confirms the goal, the approach and the path in one answer. Then the change gets its branch, whose prefix names the path, and what the owner confirmed goes into the first document of the path: the plan, the brief or the exploration notes. For a small change of behavior that is a brief too, since it has a gate. A bug starts without a document, and gets a brief at the first decision of the owner or stop for the owner after the gate; the brief then records what the owner confirmed at the gate as well. In every case what the owner confirmed goes into the pull request description.

A change with about 8 to 10 scenarios or more, or with many requirements that have none, should be split. So should one that adds more than one new module, or mixes application code with new local infrastructure, such as an identity provider or a mail server. The agent suggests it and the owner decides.

### When the path turns out wrong

Whoever finds the change on a path that does not fit stops and returns to this step, with what was found; the owner confirms the new path at the same gate. The usual signs: a change outside the requirements, or a bug fix, needs a requirement changed or added, the contract or a migration; a small change of behavior grows past one or two requirements; a change in the stages changes no requirement, or reopens what its roadmap item is for.

Nothing made so far is dropped silently. The branch is renamed, and what the owner confirms goes into the first document of the new path: the brief becomes a plan or the plan a brief, and on the way to Explore what it holds moves into the exploration notes. Code that changes behavior and was written before its requirement and its test cannot stay as it is, since its test never failed. The owner picks one:

- split: what is outside the requirements stays on this branch, and the behavior becomes a change of its own;
- move the whole change: that code leaves the working tree and is kept as a draft for the developer, and the change goes on from the requirement;
- defer: that code is removed, and the behavior becomes a roadmap item.

A rule found on the way that the requirements should hold and do not, and that the change does not alter, does not change the path. It goes to the owner as a finding at the next stop: a small change of behavior of its own (the requirement and a test of what the code already does), a roadmap item, or nothing.

## Stages

### 1. Explore

Turns a vague idea into items in `docs/roadmap.md`: one line of scope that names the goal, what is out of scope, the capabilities it touches, the owner's product decisions, and a split of a big idea into several items. It writes no requirements. Notes are kept from the start in a temporary `docs/explorations/<topic>.md`, deleted once the items exist. A throwaway experiment in a separate branch is part of this stage, when an agent suggests one and the owner wants it; it leaves findings, not code.

An exploration ends with its own pull request of the roadmap. One that was started to settle the approach of a change may instead return to **Choosing a path** with its result, on the same branch.

An agent that notices a brainstorm growing in an ordinary session offers to continue in this stage.

### 2. Specify

The designer continues the temporary plan `docs/plans/<change>.md`, which holds the goal and the approach from the path gate, with the scope, then edits `docs/requirements/` directly and asks before guessing. Requirements hold no "how". The reviewer then checks the changed requirements in one round: contradictions with other requirements and the constitution, testability of every scenario, completeness against the scope (error paths, empty states, refused access, concurrent edits, what each role sees, what is audited), ambiguity, and "how" leaking in. It reports gaps and does not write requirements; a widening of scope goes to the roadmap's ideas or to the owner as a question.

**Requirements gate.** The owner reads the changed requirements in full, not as a diff, since details get lost in one. Nothing further starts until the owner approves: tests are written from these requirements, so a wrong rule reaches both the tests and the code.

### 3. Plan

The designer completes the plan: the requirement ids it touches, the design, the tasks with their ids, and what the tests rely on before code exists (component inputs, `data-testid` selectors). A developer changes `api/openapi.yaml`, adds any migration, and keeps the build green with stubs. When the change adds or changes what a screen shows, a task adds its data to the sample center, the dev data set described in `docs/development/backend.md`, so that every state of those screens can be seen.

**Plan gate.** The owner reads the plan and the diff of the contract and the migration, and approves. The tests and the code rely on them, so a poor design would be redone along with both.

### 4. Tests first

The test engineer, from the requirements, the contract and the plan, writes a test for each new or changed scenario. The name starts with the scenario id. Backend tests go through the API against the stubs and frontend tests against empty components with the planned inputs, so each runs and fails on an assertion, not on compilation. The output of the failing run is the proof, and goes into the report.

The requirements, the contract, the plan and these tests are then committed together, because the traceability check refuses a scenario without a test. The red tests pass that check, and no check on a branch runs them before the pull request.

### 5. Implement

Developers work through the tasks of the plan, in the order it gives. The owner checks the backend once its tests are green, at any further checkpoint the plan names, and looks at the screens at the end, on the sample center. Before the owner does, the manual tester looks at the changed screens and runs the manual checks; the owner chooses what the developer fixes, and the manual tester re-checks the affected states once.

**List of details.** After implementation, a developer lists the details visible to a user that it chose on its own. The list shows only what the agent recognizes as a choice.

- A choice the owner agrees with stays in the code and does not go into the requirements.
- A choice the owner disagrees with is redone and, most likely, fixed in the requirements: first the requirement, then a test with its id, then the code.
- A one-off matter of taste that no later change is likely to break may be fixed in the code with an ordinary test.

### 6. Tests of details

The test engineer, in a fresh context, tests details from outside, through the API or a component, avoiding the implementation as far as possible. It reports doubtful behavior instead of fixing it in a test. It looks for risks, not an enumeration of cases, keeps one level per rule, and is skipped for small changes. If this stage finds no real bugs on Tracking core and the next feature, it is removed.

### 7. Review and verify

The reviewer checks the whole diff, the owner decides what to fix, and there is one round of fixes. The verification in `docs/development/` then runs for every area touched.

### 8. Finish

Delete the plan. A brief stays until the pull request description holds the owner's decisions and the answers for each guide passage, and is deleted then, before the pull request is opened. An architectural choice with real alternatives goes to the decisions. A rule that later changes in an area must follow goes to that area's guide, in a few lines, as the **Guides** section of `docs/README.md` says; each passage added to a guide or widened comes to the owner with the answers that section asks for, and stays only if the owner agrees. Everything else about how the change is built stays in the code, with a comment where it is not obvious; when in doubt, it is a comment. The roadmap item is deleted, and its product decisions move to the requirements or the decisions. All of it goes into the same pull request as the code.

## Stopping for the owner

The owner may take days over a review, and a session should not have to be reloaded whole to continue. So before every stop for the owner (each gate, each checkpoint, review findings) the agent running the change writes a short handover into the plan or the brief: what the owner is reviewing and where, the state of the work and its verification, open questions, and the exact next step on approval and on remarks. The plan or the brief also keeps the owner's decisions in the owner's words.

A new session continues from that document and what its handover names, not from the old session's history. The work itself stays on the branch or in the working tree. Requirements stay uncommitted until the red tests exist, as stage 4 explains, so a change cannot move to another machine before then.

## Tests

- A test of a scenario cites its id and checks the scenario at the level the scenario describes. Each scenario has one owning level: no duplicate backend and frontend tests of one rule.
- A rule is tested once, at the level that owns it. A test at another level that would fail for the same broken behavior and for no other is a duplicate. Rules of the platform, such as authentication and the problem format, are tested once, not again for each feature.
- The id goes on the test at the owning level. A twin of it at another level is deleted, not kept without the id.
- A developer who finds a wrong test stops and reports it. The requirement decides: if it is unambiguous, the test engineer fixes the test; if it is ambiguous, the owner decides and the requirement is clarified.
- End-to-end journey tests carry no id and complement scenario tests. They do not replace them.

## When an earlier decision turns out wrong

Whoever finds a conflict with the constitution, a decision or a requirement stops and reports which one, why it fails now, and the impact: the requirements that rely on it, their tests and the files those cover. Nobody widens the change silently or works around the conflict.

The owner picks one:

- fold the rework into the current change, which returns to the stage it affects, with the gates rerun only for the changed part;
- run a separate change first, with this one paused and then rebased;
- defer it to the roadmap, with a known limitation if needed.

A decision is changed as `docs/architecture/decisions/README.md` says.

## What to read

Read the index `docs/requirements/README.md` and the capabilities the roadmap item or the plan names. For a reference such as `(PERM-3)`, open only that block, not the whole file: `node scripts/requirements.mjs show PERM-3`. The reviewer of requirements reads more widely, the touched capabilities and those that cite them (`node scripts/requirements.mjs cited-by PERM`), because a contradiction can sit in a file nobody else opened. The same goes for decisions: read the index, and open the ones the task touches.
