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

- **A bug,** where the code departs from a requirement: a test with the requirement's id and the fix. No document changes. A bug found during other work becomes a GitHub issue, not a roadmap item.
- **A small change of behavior:** the requirement, its test and the code, in one commit, with no plan. The owner approves the changed requirement first.
- **A change with no behavior** (the build, tooling, documents): no requirements and no plan; the owner's decisions are in its roadmap item or the pull request. It still keeps the verification and stage 8: a rule for later changes goes to its guide, and the roadmap item is deleted once the owner's decisions and the answers for each guide passage are in the pull request description.
- **Everything else,** including anything touching more than one or two requirements, the contract or a migration: the stages below.

A change with about 8 to 10 scenarios or more, or with many requirements that have none, should be split. So should one that adds more than one new module, or mixes application code with new local infrastructure, such as an identity provider or a mail server. The agent suggests it and the owner decides.

## Stages

### 1. Explore (optional)

Turns a vague idea into items in `docs/roadmap.md`: one line of scope that names the goal, what is out of scope, the capabilities it touches, the owner's product decisions, and a split of a big idea into several items. It writes no requirements. Notes live in a temporary `docs/explorations/<topic>.md`, deleted once the items exist. A throwaway experiment in a separate branch is part of this stage, when an agent suggests one and the owner wants it; it leaves findings, not code.

An agent that notices a brainstorm growing in an ordinary session offers to continue in this stage.

### 2. Specify

Before any requirement, the designer states the item's goal apart from the way its line suggests, and proposes any other way that reaches the goal better, with the trade-offs; the owner chooses. If the choice reopens what the item is for, or splits it, the designer offers to go back to Explore.

On a new branch, the designer starts the temporary plan `docs/plans/<change>.md` with the scope, then edits `docs/requirements/` directly and asks before guessing. Requirements hold no "how". The reviewer then checks the changed requirements in one round: contradictions with other requirements and the constitution, testability of every scenario, completeness against the scope (error paths, empty states, refused access, concurrent edits, what each role sees, what is audited), ambiguity, and "how" leaking in. It reports gaps and does not write requirements; a widening of scope goes to the roadmap's ideas or to the owner as a question.

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

Delete the plan. An architectural choice with real alternatives goes to the decisions. A rule that later changes in an area must follow goes to that area's guide, in a few lines, as the **Guides** section of `docs/README.md` says; each passage added to a guide or widened comes to the owner with the answers that section asks for, and stays only if the owner agrees. Everything else about how the change is built stays in the code, with a comment where it is not obvious; when in doubt, it is a comment. The roadmap item is deleted, and its product decisions move to the requirements or the decisions. All of it goes into the same pull request as the code.

## Stopping for the owner

The owner may take days over a review, and a session should not have to be reloaded whole to continue. So before every stop for the owner (each gate, each checkpoint, review findings) the agent running the change writes a short handover into the plan: what the owner is reviewing and where, the state of the work and its verification, open questions, and the exact next step on approval and on remarks. The plan also keeps the owner's decisions in the owner's words.

A new session continues from the plan and what its handover names, not from the old session's history. The work itself stays on the branch or in the working tree. Requirements stay uncommitted until the red tests exist, as stage 4 explains, so a change cannot move to another machine before then.

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
