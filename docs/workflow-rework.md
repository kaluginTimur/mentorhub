# Workflow rework: handover

Status: temporary. This document hands over the context for reworking the documentation and the agent workflow into a spec-driven process. It is updated at the end of every session and deleted when the rework is done. Branch: `workflow/rework`; the trial run is on `feature/mock-mode`.

## Where we are

Sessions so far: 2026-10-20 (motivation, first changes), 2026-10-31 (alternatives, target model, life cycle, format, tooling, migration plan), 2026-11-02 and 2026-11-03 (migration stages 1 to 4), 2026-11-04 and 2026-11-05 (the spec-anchored process, all requirements rewritten, the process, skills and agents). **Read "Next session: start here" first.**

**Done:**

- Stage 1: `docs/constitution.md` (10 rules), `docs/roadmap.md`, `docs/architecture/decisions/`, `CLAUDE.md` and `AGENTS.md`.
- Stage 2: `scripts/requirements.mjs` with `validate` and `trace`, reviewed by a subagent and fixed. `validate` is wired into the pre-commit hook, CI and the verification in `docs/development/docs.md`, where the format is now described. JUnit tests carry the id in `@DisplayName`.

**Done: migration stages 3 and 4.** The index and seven capability files in `docs/requirements/`: 204 scenarios, 1963 lines (against 1119 lines for the Requirements and UI sections of the specs, and about 1070 lines in the pilot, which had no UI). Every scenario without `(manual)` has a test (199, plus 5 manual), `AC-n` is gone from code and tests, `trace` runs in the hook, CI and the verification, and missing requirements fail. The Flyway migrations are squashed into one, and the decisions renumbered: audit is 0005 and asynchronous events 0006. The specs, the progress logs, the invariants, decision 0005 about the bootstrap and the stage notes are deleted; they remain in git.

- User decisions of 2026-11-03: the first pass of requirements (3780 lines, 479 scenarios) was too large and was compressed; every disputed point keeps the code as it is; a new link answers 200, as the contract and the code do (the requirement was wrong).
- The first pass was large because of Claude's brief, which carried the observable UI into scenarios and asked for one scenario per checked behavior. The compression rules are Claude's proposal: one shared requirement for list pages (PLT-9); UI scenarios only for behavior a user relies on; no exact strings, timings or field limits that the contract holds. Dropped UI detail stays in the code, its tests and `docs/ui/guidelines.md`.
- References in the skills and agents were only repointed so the check passes; they are rewritten in stage 5.
- This document keeps the old decision numbers in its history.

**The approach to scenarios is decided.** User decision, 2026-11-03: requirements should not describe every small detail, and tests should not cover what is not needed. User decision, 2026-11-04: a spec-anchored process; see **A spec-anchored process** under **Open questions**. The USR-14 examples below describe the file as it was.

## Next session: start here

State at the end of 2026-11-05. Stage 5 is written and committed on `workflow/rework`, and nothing is pushed. The verification last passed in full on the rewritten requirements (backend 919 test cases in the XML reports, whose text reports leave out nested classes; frontend 264 tests); after that only documents, skills, agents and scripts changed, checked by `check-docs`, `trace` (201 of 201, 4 manual) and the 62 tests of the tool.

**The trial run, which is why this session ended.** No skill or agent of the new process had ever run, so the user chose a trial on Mock mode, the first item of **Next** in the roadmap. Claude chose it because the roadmap has nothing smaller; it is real work, so its result can stay. In a new session the user runs `/spec mock-mode`, then `/clear` and `/feature mock-mode`, and stops at the plan gate.

**The trial so far (2026-11-07).** `/spec` ran on 2026-11-06. The change was renamed Sample center, on the branch `feature/sample-center`: a dev configuration of the real stack with personas and restored data, instead of a frontend without a backend. It stopped at the requirements gate; `/feature` has not run, and the work is uncommitted on that branch. Findings:

- User's finding: the agent took the roadmap line ("run the frontend without a backend or sign-in") as a rule and did not offer the real stack as an alternative until the user raised it. Fixed in this branch: the roadmap header says an item is an intention and its way is a first guess; `/spec` and stage 2 start with the goal and alternatives, and offer `/explore` when the goal itself reopens; `/explore` and stage 1 write the goal into the item line and put a way the user leans toward into product decisions. The split between the two is Claude's proposal, accepted by the user on 2026-11-07 ("давай граница между /spec и /explore как предложил"): `/explore` is where alternatives are compared, but `/spec` still questions the way first, since an item can look settled and turn out poor; another way to reach the same goal stays in `/spec`, and a reopened goal or a split goes to `/explore`.
- Claude's finding, fixed on the user's approval of 2026-11-07 ("да, применяй"): `/spec` suggested `/clear` and `/feature` once approved, but `/feature` stopped when the plan held no approval, and the approval had no fixed place. The plan template now has a `Requirements approved:` line; `/spec` fills it only on an explicit approval, and `/feature` asks for the approval and records it when the line says `none`. The plan of `feature/sample-center` predates the line and gets it on its next session.
- User's question, fixed on Claude's proposal accepted on 2026-11-07 ("давай"): nothing recorded which design documents a change touched. `/spec` listed only capability files, and `/feature` shortened the agents' lists of changes. The handover now has "Documents changed", built from git at every stop and not kept by hand, with the files new since the last stop marked; `/spec` and `/feature` show it in full.
- Claude's findings, not fixed: the `size` record measures against `main`, so it counts all of `workflow/rework` (173 files) until the PR; the main session of `/spec` ended at 121k context (79k average, 58 calls); and once the sample center exists, the prototype step of `frontend-dev` and the rule that a feature adds its sample data need a place in the agents and the process.

**When to come back to this handover** (agreed with the user on 2026-11-07; the user continues `/feature sample-center` in its own session, which by then had passed the requirements gate and stopped at the plan gate):

1. Right after the red tests are committed (stage 4, the plan's Red tests holds the commit). Check what runs for the first time: `test-engineer`'s red tests fail on an assertion against stubs and empty components, not on compilation; `trace` and the pre-commit hook pass on the commit of requirements with tests; the tests cite DEV-1.1 and DEV-1.2 instead of IDN-7.2 and IDN-7.3; the plan's `Requirements approved:` line and Documents changed work. Fix before the developers build on the red tests.
   **Checked on 2026-11-07, red commit `764ea7e`: passed, nothing to fix.** Claude reran both areas independently. Frontend: exactly the three DEV-3.3 tests red, each on an `AssertionError` (the switch does not exist yet), 266 green. Backend: exactly the five red tests the plan's handover names (DEV-1.2, DEV-1.3, DEV-2.1 and the two planned tests without an id in `SampleCenterTests` and `DevelopmentRealmConsistencyTests`), all assertion failures, no compilation errors or test errors. `trace` 207 of 207; no IDN-7 id is left in code, and the DEV ids are cited. `Requirements approved:` works. Not checked: Documents changed, since the `/feature` session started at 01:50, before that rule was committed at 02:32; check it at the next stop of a new session. Worth remembering for the cost review: `test-engineer` reached 136k context for nine scenarios, near the 150k at which a fresh agent is started. The red commit deletes the runner's tests while the runner stays until T3, a short gap in coverage that the plan's removal of the runner closes.
2. After `/feature` is accepted, before `/pr`. Review the whole trial: the list of details, whether the second test pass found a real bug (the first of the two changes that decide whether it stays), the cost per phase (`/usage-review`, `node .claude/scripts/agent-usage.mjs`), and Claude's findings not fixed above. Then steps 3 to 6 below. The PR of `workflow/rework` goes to main before the one of `feature/sample-center`, which sits on top of it.
3. Out of turn, whenever a skill or an agent departs from the process: the user marks it "for the rework" in the sample-center session. The fix goes to `workflow/rework` and is merged into `feature/sample-center` (fast-forward so far), not made on the feature branch.

- The branch `feature/mock-mode` already exists, on top of `workflow/rework`, because `main` has neither the new skills nor `docs/process.md`. `/spec` says to branch from the default branch: if it offers that, the answer is no. That wording is right after the PR.
- What to check: `/spec` starts `docs/plans/mock-mode.md` (planned) and reads only what is touched; whether Mock mode, a tool for developers, needs requirements at all, and the decision the roadmap asks for through `/decision`; `reviewer` runs in requirements mode; the handover is written and the usage recorded at the gate; a new session continues from the handover alone, with a small context (`/context`).
- What it cannot check: `test-engineer`, the developers and everything after the plan gate; and the contract step of `backend-dev`, since Mock mode probably has no contract change. These first run on real work, including red tests through stubs and empty components.

**The trial reviewed (2026-11-07, before `/pr`).** `/feature sample-center` finished: T1 to T7 done, one review round, re-review clean, verification green, plan and roadmap item deleted (`17f3ea7`); its usage record is not committed yet. No item of the lists of details was rejected. The second test pass found a real bug outside the change (AUD-6.5, roadmap Next). Weighted cost: Opus 5385k, Sonnet and Haiku 613k; the recorded size (207 files, 8790 lines) counts `workflow/rework`, the real size against it is 48 files and 2037 lines. One `backend-dev` (T4 and T6) reached 194k context, above the 150k threshold. `/usage-review sample-center` is for the user to run.

- User's finding: the guides grow with every feature; the sample center wrote its whole design into `docs/development/backend.md` (+592 words) and retold DEV-3 in `frontend.md`. Causes (Claude's analysis): stage 8 sent "durable how" to guides and left the line to the agent; the plan's T6 ordered a guide section; `check-docs` reminds about every code file a guide names. Fixed in `workflow/rework` on the user's word ("1 сделай сейчас"): a **Guides** section with the test "would someone on an unrelated change in this area need it?", stage 8 and `/feature` follow it, and `reviewer` reports a guide passage that fails it. The sample center's sections are trimmed in its own session; the other guides are audited as part of this rework, before the PR (step 5 of **When the user returns**; user: "аудит гайдов нужен в плане реворка, а не в роадмапе").
- User's finding: no one place said which document holds what; `docs/process.md` named the authorities, the Map of `AGENTS.md` and the Layout of `README.md` listed paths, none of them complete. Fixed on Claude's proposal, accepted with the import ("да, делай с импортом"): `docs/README.md` is the documentation map (holds, does not hold, authority) with the **Guides** section moved there from `docs/development/docs.md`; the root `CLAUDE.md` imports it, `AGENTS.md` and `README.md` point to it instead of listing documents, and `scripts/check-docs.mjs` now checks a section any document names with its file.
- Claude's findings, fixed in `workflow/rework` after the sample center was finished (user: "давай исправлять их тут"): the plan stage gives a change that alters screens a task that adds its sample data, the owner looks at the screens on the sample center as the personas `frontend-dev` names per state, and `reviewer` checks the sample data of changed screens. The 150k threshold did not hold because one brief named T4 and T6 (the agent passed 150k at call 55 of 85): `/feature` now briefs one task at a time. The prototype step of `frontend-dev` does not come back: the red tests come before the code, and the owner sees the screens at the frontend stop. Agents cannot look at screens themselves yet: no browser is set up for `frontend-dev`; the roadmap item **Agents look at screens** is the user's ("нужно обязательно добавить пункт в роадмапу"). The size against `main` fixes itself with the PR.
- The sample center is not measured (user decision, 2026-11-07): its record counted `workflow/rework` too, and it was dropped (`d5c6296`).

**When the user returns,** in order:

1. Ask what the trial showed. Look at `git status` on `feature/mock-mode`, the plan and `.claude/usage/mock-mode.jsonl`, once it exists. `/usage-review` and `node .claude/scripts/agent-usage.mjs` show the cost. The trial's work is probably uncommitted: requirements cannot be committed before the red tests, because the hook refuses a scenario without a test. Do not lose it.
2. Fix what it found in `workflow/rework`, not in `feature/mock-mode`, so the PR carries the fixes. Ask the user how to handle the uncommitted trial first: `git switch` carries uncommitted files along, so commit only the files of the fix by name. Bring the fixes to the trial branch with `git merge workflow/rework`: `git rebase` is denied in `.claude/settings.json`.
3. The user reads the rewritten requirements. They reviewed `user-administration` and `authorization`, and have not said they read the other five. To confirm then: the removal of INV-16.2 and PLT-1.1; IDN-9.6 and IDN-10.4 restored by the main session; INV-18.3, IEM-6.10 and IEM-11.2 added; the ban on the token in a log (INV-9) stays without a scenario of its own, and USR-17.2 gets no tests for its other two combinations (both decided on 2026-11-05 on Claude's proposals).
4. The test audit. Not decided: Claude proposes a read-only agent on `user-administration` that lists duplicates between `UsersControllerTest`, `UserAdministrationServiceTests` and `UsersEndpointTests`, and tests of implementation detail; the user decides what goes and whether to go on. It may come after the PR.
5. The guides audit, before the PR. One pass over `docs/development/` with the test in the **Guides** section of `docs/README.md`: feature internals that earlier changes wrote into the guides (the event registry backlog in `backend.md`, for example) move to code comments or go, and each guide keeps its rules, pitfalls and commands. The sample center's sections are trimmed on its own branch.
6. Before the PR: delete the **Workflow rework** item from the roadmap and this document, which stays in git. Then run the full verification for backend, frontend and docs, and `/pr` for one PR from `workflow/rework` to main. Afterwards merge main into `feature/mock-mode`.

**Decided and worth keeping in mind** (user decisions of 2026-11-05 on Claude's proposals, unless marked):

- About 17 thousand words of requirements is accepted: 17386 words and 205 scenarios, from 23870 and 204. Further cuts would remove rules about access, data, state and security and leave them to agents' guesses; the cost of size is handled by what an agent reads. Claude's expectation of 11 to 12 thousand was wrong: splitting combined scenarios added about as much as removing scenarios took away.
- To watch on Tracking core and the next feature: the user often rejecting items of the list of details means requirements are too thin; a kind of scenario that catches nothing and gets in the way of changes can go. The second test pass is removed if it finds no real bug on those two.
- `/usage-review` stays to measure the new workflow, although its old exit condition is met; the user rejected Claude's proposal to delete it.
- `test-engineer` runs on Opus for Tracking core, then the usage is checked. The prototype step of `frontend-dev` is gone until mock mode exists.
- Handovers in the plan are the user's request: a review can take days, and the work continues in a new session from a small context.

**Claude's choices, not discussed:** running backend controllers on Sonnet (the old step 4 experiment) is dropped, because the new process has no such step. No hook tells subagents apart; the coordinator and the reviewer check the diff of tests since the red commit. The threshold of the cheap path (more than one or two requirements, the contract or a migration) stays a draft until small changes happen.

## Earlier state and stage 5

Measured on 2026-11-03, after the compression:

- Requirements: 23870 words, against 15168 in the Requirements and UI sections of the old specs, so the compression that was measured in lines and scenarios still left 1.57 times the words. Statements hold 9699 words and scenarios 11873, and they repeat each other (USR-14). Volume moved into statements (INV-20: 360 words, IEM-6: 259, IDN-6: 205). The catalog AUD-7 (412 words) is long for a reason.
- The count of 204 is partly cosmetic: some scenarios merge several cases (USR-14.1), and 42 scenarios are cited by more than one test.
- Tests: 778 (519 backend, 259 frontend), about 257 of them with an id, so about 520 are invisible to `trace`. 16.3 thousand lines of tests for 8.9 thousand of production code (frontend counted without the generated client). Stage 4 added 1541 net lines; most of the volume was there before. Rules appear to be checked on several levels (`UsersControllerTest`, `UserAdministrationServiceTests`, `UsersEndpointTests`): a guess from the file names, the test bodies were not compared.

How other AI clients load the guides and the constitution is not considered for now, since each client works differently (user decision, 2026-11-03).

Attribution: "user decision" marks what the user decided; everything else is Claude's proposal or finding.

## Motivation

The user's assessment (2026-10-20):

- The project follows spec-driven development in name, but its constitution, the rules every change must keep, is weakly felt.
- Specs are bloated: about 600 lines each.
- There is no roadmap.

The user's goal (2026-10-31): ideas turn into specs, requirements into tests, and code is written to pass the tests. Specs are living: requirements may change, and the code follows the documents. A roadmap records plans and ideas.

### Concerns raised while specifying the invitation features

Each concern comes with the episode that showed it.

1. **Context grows with every feature.** `/spec` reads every spec in full, whether related or not.
2. **A spec mixes living requirements with a design snapshot.** After later changes, the old parts become historical noise that the next agent may trust. Example: the invitations spec still describes hashed tokens and a link "shown only once", both reversed on 2026-10-20.
3. **There is no defined way for one feature to update another feature's spec.** The reviewer found contradictions between the invitation-emails and invitation-links drafts.
4. **ADR immutability and ADR count.** Superseding instead of editing doesn't fit a product with no release and no users. Every small decision recorded as an ADR costs context in every future `/spec`. Example: an ADR proposed for synchronous email sending, which the user rejected as too small.
5. **Exceptions buried in specs and guides add entropy.** Example: an "email sent without an audit entry" exception to ADR 0006 in the invitation-emails draft. It was later removed by switching to asynchronous sending.
6. **Over-cautious decisions accumulate, and there is no light way to revisit them.** Example: invitation tokens stored only as hashes, which protected little because acceptance requires a verified matching email. Reversing that touched several specs and guides.
7. **Specs grow through review rounds.** Example: invitation-emails reached 17 acceptance criteria and was split into two features.
8. **Acceptance criteria numbers collide.** They are per spec (every spec has an AC-1), while code references them.
9. **Attribution.** User decisions and Claude's inferences must stay apart. An earlier spec labeled an inference as a user decision.
10. **No single constitution.** Non-negotiable rules are spread across `AGENTS.md`, the former invariants document, the ADRs and the guides.
11. **"What" and "how" share one document.** The spec's Design section goes down to class names, SQL and lock order. That makes specs large and makes them age quickly.
12. **`/feature` has no task breakdown.** Implementation follows a fixed pipeline (contract → migration → domain → API → frontend), not tasks derived from a plan for the feature.

## Current state

- The flow is described in `.claude/README.md`: `/spec` → reviewer → approval → `/feature` (subagents `backend-dev` and `frontend-dev`, checkpoints, a progress log) → `/review` → `/verify` → `/pr`, plus `/adr` and the temporary `/usage-review`.
- Knowledge is spread across `AGENTS.md`, the former invariants document, `docs/adr/` (eight ADRs), `docs/features/` (five specs with progress logs, about 2170 lines) and `docs/development/` (guides per area).
- Requirement references in code are comments such as `// AC-5` or `// AC-1, AC-4, AC-8, AC-10, AC-14, AC-16 …`; one test often covers many criteria. About 521 `AC-n` tokens, about 121 of them ambiguous because one file covers two features with the same numbers. "ADR 0…" appears in 33 files.
- The roadmap and many product decisions exist only outside the repository, in Claude's local memory (`roadmap-decisions`) and plan files. The seed list is under **Roadmap**.
- CI (`.github/workflows/ci.yml`) runs on pull requests and on pushes to main. `.githooks/pre-commit` runs `scripts/check-docs.mjs`.
- Changed on this branch on 2026-10-20 and to be replaced by the migration: the "Keeping decisions and specs current" section of `docs/development/docs.md` (living ADRs and spec Requirements, Design as a snapshot), the matching rule in `AGENTS.md`, `/adr` editing in place, the spec template marking Design as a snapshot.

## Alternatives considered

**User decision (2026-10-31):** build our own spec-driven workflow instead of adopting a framework. The user works alone, wants to know the whole process and define it to taste, and does not want to depend on someone else's prompts of a thousand lines. Ideas from the frameworks are borrowed freely.

- **GitHub Spec Kit:** constitution → specify → clarify → plan → tasks → implement. It keeps one spec per feature (`specs/NNN-feature/`), and these specs pile up: the problem we are leaving behind (concerns 2 and 3). Worth borrowing: the clarify step (ask before planning), the `[NEEDS CLARIFICATION]` marker instead of guessing, the "no how in the spec" rule, spec quality checklists, and an analyze step that checks spec, plan and tasks agree.
- **OpenSpec** (`@fission-ai/openspec` 1.15.0): living specs per capability plus changes with spec deltas, merged into the specs in code by `openspec archive`. A pilot migrated the access domain into it and tested the merge; the pilot was then discarded.

### Lessons from the OpenSpec pilot

To adopt:

- Requirements in a fixed, parseable format with SHALL/MUST and WHEN/THEN scenarios. The access domain fit without serious loss.
- A change carries deltas, and a **script** merges them into the living documents, not an agent.
- The merge is atomic across all capabilities of a change: one bad delta and no file is written.
- Merge guards: the target of MODIFIED or REMOVED must exist; ADDED and REMOVED of the same requirement in one change is an error; MODIFIED may not silently drop a scenario; a near-miss header gets a "did you mean" error.
- Free sections after the requirements (such as `## Known limitations`) survive the merge.

To avoid (OpenSpec's gaps):

- It matches requirements by the full header text, case- and space-sensitive. **Match by stable id.**
- Its `validate` exits 0 on deltas that `archive` then refuses. **Validation must be a dry run of the merge and fail with a non-zero exit.**
- A REMOVED whose target is missing is a silent no-op. Never allow silent no-ops.
- Duplicate ids pass. Check id uniqueness and that cross-references resolve.
- Lost update: two changes that both edit one requirement's text archive in turn, and the first edit is lost. Store a hash of the base block in MODIFIED and refuse if it changed.
- A delta cannot rename or remove a scenario. With ids as keys, this works.
- Project rules in its config are only prompt text. Rules that matter must be checked by the script.
- It checks structure, not meaning: after a change, one capability still said an action was not audited while another said it was. Cross-capability consistency stays a review task.

### Data from the pilot migration

- **Capability split** tried: identity (IDN), authorization (PERM: role → permission, 403 vs 404), platform (PLT: Problem Details, paging, client config), audit (AUD: rules plus one catalog of actions), user-administration (USR), invitations (INV, with invitation-links merged in), invitation-emails (IEM). 111 requirements.
- **Size:** about 1900 lines of specs became about 1070, mostly from dropping Design (779 lines).
- **Contradictions:** the five specs contradict each other in about 13 places. Examples: whether the bootstrap administrator is audited (the code audits it), whether `/me` returns the phone (it does), who may change users (`USERS_MANAGE`, not `USERS_READ`). The migration has to check each requirement against the code.
- **Prompt size, for reference:** OpenSpec feeds about 515 lines of instructions for a pass from proposal to tasks; its generated skills and commands add up to 2626 lines.

## Target model

Order of discussion: document model → life cycle of a change → format → tooling → migration → skills and agents. All decisions below are user decisions of 2026-10-31 unless marked otherwise.

### Agent neutrality

- Specifics of an AI client stay in its configuration folder (`.claude/`, `.cursor/`, `.cline/` and so on). Documents outside them use no client syntax, such as `@` imports, and no client-specific names.
- The root `CLAUDE.md` stays. It imports both `AGENTS.md` and `docs/constitution.md` (planned); `AGENTS.md` only names the constitution in plain text.
- The process description must become agent-neutral. Today `AGENTS.md` and `docs/development/docs.md` name Claude Code skills, subagents and loading behavior. Claude's proposal: a neutral `docs/development/process.md` (planned) describes stages, artifacts, gates and test rules; `.claude/README.md` maps the stages to skills and subagents, and skills point to `process.md` instead of retelling it.

### Constitution

- `docs/constitution.md` (planned), for humans and agents alike. The split test: "would this rule still hold if a team of people ran the project without Claude Code?" If yes, it belongs in the constitution; if it is about how an agent works in the repository, it belongs in `AGENTS.md`.
  - **Two more tests (user decision, 2026-11-02):** the constitution holds no domain-specific principles and no common knowledge about tools or frameworks.
  - **A rule of one area goes to that area's guide, and the mechanics of a decision stay in the decision.** The user asked why contract first and module boundaries were not in `docs/development/backend.md`; the wording of this test is Claude's, and the user applied it to the rules below.
  - Constitution (user decision, 2026-11-02, applying these tests): what the product is and is not; contract first (the order of work only, without the generator mechanics); vertical slices; done means verified; spec-driven principles; the language of artifacts.
  - Rules that left it: module boundaries stay in `docs/development/backend.md` and decision 0001; resource-level access checks and negative authorization tests moved to `docs/development/backend.md`; no passwords and `users.id` over `sub` stay in decision 0003; permissions over role names stay in decision 0004; the audit rules stay in decision 0006 and go to the `audit` requirements; 404 versus 403 goes to the `authorization` requirements; "generated code is never edited" is common knowledge. Known cost (Claude's assessment): these rules stop loading into every session, so tests with scenario ids and the review checklist have to catch violations, which holds only after migration stages 4 and 5.
  - `AGENTS.md`: the map, the agent workflow, what to read before planning, asking before destructive actions, explaining what a suggested destructive command destroys. User decision of 2026-11-03: the default about applied migrations belongs in `docs/development/backend.md`, and `AGENTS.md` does not point to `.claude/`, which Claude Code reads on its own.
  - Domain facts in the former invariants document (roles, `user_identities`, invitation token storage, gate rules) move to living requirements. Plans (feature order, the planned domain model, undefined observer rights) move to the roadmap. Claude's proposal: notes and their visibility levels also go to the roadmap, because notes are not implemented.
- Style conventions (for example "comment sparingly") go to the development guides. The language of artifacts stays in the constitution.
- **Claude's proposals, not objected to on 2026-11-02:** numbered rules (`C-n`) that a review can cite; each rule is one imperative line plus a "Why" line and a decision link where one exists; about 80 lines at most; an agent that finds a conflict with it stops and asks, and specs carry no exceptions to it; `AGENTS.md` holds no copies of its rules.
- **User decisions of 2026-11-02:**
  - An agent only proposes a rule, including one a decision introduces; the project owner adds it.
  - Applied migrations are not in the constitution: a developer is expected to know how Flyway works, and the rule reads as an instruction for an agent. The default is in `docs/development/backend.md`: applied migrations are never edited; editing or squashing needs the user's word and explicit confirmation. The agent cannot know whether a migration was applied or what editing it costs (possibly nothing, for a migration that reached no database or only a disposable local one).
  - Note visibility is not in the constitution, as a domain-specific rule.
  - Invitations stay out of the constitution: "today they exist, tomorrow they don't".
  - Personal data rules are dropped entirely, from the constitution and the roadmap: keeping data out of foreign SaaS, Kazakhstan specifics, and anything else not covered by the implementation of users.
  - An amendment leaves no dated line in the constitution.
  - `C-n` numbers are only a label for review reports and conversation, with no numbering rule: living documents and skills do not cite them, because the constitution is always loaded, so the numbers can change freely. "Living documents do not cite them" goes to the process description.
  - The constitution holds no agent instructions and no obligations a human team could not meet: "stop and report" reads as an instruction for an agent, since a developer stops only at really disputed points, and few human teams can separate the authors of tests and code. The constitution must not become a second `AGENTS.md`; these things are still described, on another level. So the test-engineer and developer split, the proof of the red phase and the decision-conflict report stay in force and move to the process description and the agent configuration.

### Architecture decisions

- ADRs become living architecture decisions in `docs/architecture/decisions/`, no longer called ADRs. Classic ADRs are immutable and superseded by new records; agents know them that way, so the name would contradict the rule of editing in place.
- A decision is edited in place. Sections: Context, Decision, Why, Rejected alternatives. A reversed choice becomes one line in Rejected alternatives, with its date and reason.
- Threshold: the decision is cross-cutting, hard to reverse, and had real alternatives.
- An index holds one line per decision; agents read the index and open a decision only when the task touches it.
- Numbers are short references. User decisions of 2026-11-03: 0007 is removed, because a persistence choice per module is cheap to change; its rules moved to `docs/development/backend.md`. 0005 is mostly behavior and moves to the requirements in stage 3. The remaining decisions are renumbered at the end of stage 4.

### Living requirements

- One file per capability in a flat `docs/requirements/`, with an index `README.md` that orders the capabilities logically. No numeric prefixes in file names: capabilities are a map, not a log, and the id prefix is the stable key.
- Cross-cutting behavior: the constitution states the principle; concrete behavior with ids and tests lives in capabilities such as `authorization`, `platform` and `audit`.
- Durable "how" after a change is deleted: an architectural choice with alternatives goes to the architecture decisions; conventions and common techniques go to the development guides; specific implementation details stay in the code, with a comment where they are not obvious. Requirements hold no "how".
- Every requirement passes the user's gate, so requirements carry no provenance marks. Attribution matters where decisions have not passed a gate yet: exploration notes, the roadmap, decisions.

### Roadmap

- One file, `docs/roadmap.md` (planned), about the future only. Sections:
  - Stages: milestones, for example the pilot release;
  - Now / Next / Later: features with one line of scope and the capabilities they touch;
  - Ideas: no commitment and no order;
  - Product decisions: decisions not yet implemented, marked with "user". User decision of 2026-11-02: no dates in the roadmap, and all these decisions are preliminary, discussed again when the feature is planned.
- Life cycle: idea → Next/Now → change → implemented. An implemented item is deleted. A product decision leaves with its implementation, into the requirements (behavior) or the architecture decisions (architectural choice).
- Only the user promotes ideas, reorders items and makes product decisions. An agent may add ideas, marked as its own suggestions.
- No external tracker while the user works alone.

### Life cycle of a change

Stages: Explore → Propose → Plan → Implement → Verify and review → Archive.

- **Explore is a formal stage.** It turns a vague idea into roadmap items: one line of scope, out of scope, affected capabilities, product decisions marked with "user", a split of a big idea into several items. It writes no requirements. When an agent notices that a brainstorm has started in an ordinary session and is growing, it offers the user to continue in Explore.
- **Spikes** (throwaway experiments in a separate branch, which leave findings, not code) are part of Explore, only when the agent suggests one and the user wants it.
- **Exploration notes** live in a temporary document during Explore (Claude proposed `docs/explorations/<topic>.md`), not in a change.
- **Propose** creates `docs/changes/<name>/` with `proposal.md` (why, scope, out of scope) and the requirement deltas; clarifying questions come before the plan. **Plan** adds `design.md` and `tasks.md`, each task citing requirement ids. (Claude's draft, accepted as the frame.)
- **Two user gates:** the requirements (end of Propose) and the plan (end of Plan). The spec is also reviewed by an agent; how that review fits in is open.
- **Tests follow TDD** and serve as one of the agents' evals. A separate test-engineer agent writes the tests; the developer agent writes the code to pass them and may not edit them.
  - Ownership: tests carrying requirement ids belong to the test engineer. The developer may add unit tests without ids for non-obvious algorithms. Shared test infrastructure (Testcontainers, test data factories) is edited by the test engineer.
  - Timing: after the plan gate, the test engineer covers the whole delta with red tests, then the developer works through the tasks. The test engineer reads only the requirements, the contract and the plan, never the implementation; the plan fixes what tests rely on before code exists (the API contract, component inputs, `data-testid` selectors).
  - The red phase is proven: before implementation each test runs and fails for the right reason, not a compile error, and the output goes into the checkpoint report.
  - Enforcement: at the developer's checkpoint a script checks the git diff, and a change to the test engineer's tests rejects the task. A Claude Code hook that blocks such edits is an extra layer, if it can tell subagents apart (to check).
  - A wrong test: the developer stops and reports it. The requirement decides; if it is unambiguous the test engineer fixes the test, if it is ambiguous the user decides and the requirement is clarified.
  - Against test bloat (Claude's proposal, not objected to): a test with an id checks a scenario at the level the scenario describes; each scenario has one owning level (no duplicate backend and frontend tests of one rule); unit tests without ids only for non-obvious algorithms.
- **A change is archived in the same PR** as the code: its deltas are merged into the living requirements, the roadmap item is deleted, durable "how" from `design.md` moves to decisions or guides, and the change folder is deleted.
- **A flawed earlier decision found during a change:**
  - The agent that finds it, at any stage, stops with a decision-conflict report: which decision or requirement, why it fails now, and the impact. It never widens the change silently or works around the decision.
  - The impact is computed, not guessed: decision → requirements that cite it → tests with their ids → the files those tests cover. A requirement that relies on an architecture decision cites its number.
  - The user picks one of three options: fold the rework into the current change, which goes back to Propose or Plan, with gates rerun only for the changed part; run a separate rework change first, with the current change paused and later rebased (the base hash check catches its stale MODIFIED deltas); or defer it to the roadmap, with a known limitation if needed.
  - The rework is ordinary TDD; the tests of all other requirements act as the regression net.
  - The decision is edited in place, and the old choice becomes a line in Rejected alternatives. A constitution rule is amended only by the user.
- **The cheap path,** as drafted by Claude for now, to revisit once small changes happen: a bug where the code departs from a requirement gets a test with the requirement's id and a fix, with no document change; a small behavior change edits the living requirement directly, with its test and code in one commit; anything touching more than one or two requirements, the contract or migrations is a full change.

### Format

- A capability file holds Purpose, Requirements, Known limitations and Out of scope. A requirement is `### <PREFIX>-<n> <Title>` (no `Requirement:` prefix) with SHALL/MUST statements; its scenarios are `#### <PREFIX>-<n>.<m> <Title>` with WHEN/THEN, and a manual check ends with `(manual)`. References are written `(INV-3)` and `(decision 0008)`. (Claude's draft; the header form and scenario ids are user decisions.)
- Scenarios have their own ids, and tests cite scenario ids.
- Deltas are ADDED, MODIFIED and REMOVED only. Matching is by id, so a rename is a MODIFIED. The base hash of a MODIFIED block is written by the script that copies the block into the delta, never by the agent.
- No compatibility with OpenSpec: we take its ideas, not the framework.
- Access tables: each capability holds a small normative "action → permission" table with scenarios for the key refusals; `authorization.md` holds one product-wide "role → permissions" table. Visibility tables follow the same idea, in their own capability.
- **Id allocation:** the next number is the highest in the file plus one, for requirements and scenarios alike. Reusing the number of a removed id is safe, because traceability rejects any test or reference that points at a removed id.
- **Parallel PRs taking the same id are a deliberate, accepted risk.** Git has no central place to hand out numbers atomically, and a check on each PR's own branch cannot see the other PR. Collisions are detected after the merge, not prevented:
  - the uniqueness check runs in the pre-commit hook and in the verification, so the first commit on any branch made from the new main fails;
  - the CI run on pushes to main runs it too;
  - a script renumbers the later PR's ids (delta and tests).
  - Rejected alternatives: deriving used ids from git history (the user objected that it makes intact history an unwritten requirement; squashes, rewrites and shallow CI clones break it); a "next number" counter per capability in the index (two PRs that each add one requirement make the same edit, and git merges identical edits without a conflict); a check on a branch rebased onto main before merge (both PRs can rebase before either merges); random id suffixes such as `INV-k7q2` (no collisions, but much harder to read).
- **The index** `docs/requirements/README.md` is the scripts' source of known prefixes and of prefix → file. The scripts check it and do not generate it.
- **Tests and scenarios:** by default one scenario per test, and the test name starts with its scenario id (`INV-9.1 …`); the script matches only prefixes listed in the index. End-to-end journey tests are allowed as a separate kind: they complement scenario tests, do not replace them, and traceability does not count them. They carry no mark (user decision, 2026-11-03): traceability reads only names that start with a scenario id, so a test without one is not counted.

### Tooling

- **One separate tool** for requirements, written in Node like `scripts/check-docs.mjs`, which keeps checking file references only. Subcommands proposed by Claude: `validate`, `merge`, `new-id`, `modify` (copy a block with its base hash into a delta), `renumber`, `trace`, `impact`.
  - Duties: validate requirement files and the index; validate deltas as a dry run of the merge; trace scenarios to tests; merge atomically on archive; allocate ids; renumber; check that the developer left the test engineer's tests unchanged; compute the impact of a decision.
- **Where it runs:** validation and traceability run in the pre-commit hook, in the verification and in CI. The other subcommands are called by the process stages.
- **Strictness** (strict: a violation fails with a non-zero exit; lenient: it is reported and the run passes):
  - living requirements are always strict: every scenario without `(manual)` has a test;
  - a test may cite an id from the living requirements or from the delta of an open change. A test citing an id that an open delta removes is reported, not failed, until Verify;
  - coverage of an open change's delta is reported until Verify, as progress ("7 of 12 new scenarios have tests");
  - the Verify stage and the merge on Archive are fully strict: every delta scenario is covered and no test cites a removed id, or the merge refuses.
  - So the tool does not need to know a change's stage: strictness follows from where a scenario lives and which command runs.

## Migration

Stages, each a separate commit on `workflow/rework` that leaves `node scripts/check-docs.mjs` green. Everything reaches main in one PR once the whole process is consistent, so main never holds new documents with old skills.

1. **Constitution, roadmap, architecture decisions.** In the main session, with the user's review.
   - `docs/constitution.md` (planned) from `AGENTS.md` and the former invariants document; facts and plans from `invariants.md` go to the roadmap now and to the requirements in stage 3; `invariants.md` is deleted once empty.
   - `docs/adr/` → `docs/architecture/decisions/` with an index; add Why and Rejected alternatives (including reversed choices such as hashed tokens and synchronous sending); update the "ADR 0…" references in 33 files.
   - `CLAUDE.md` imports `docs/constitution.md` (planned); `AGENTS.md` loses the rules that moved.
2. **The tool,** at least `validate` and `trace`, so the migrated requirements can be checked.
3. **Requirements.** The five specs become capability files with new ids, each requirement checked against the code; contradictions are collected in a table for the user to decide. Design sections are sorted (durable parts to decisions or guides, the rest dropped); specs and progress logs are deleted. Decision 0005 moves to the `identity` requirements: its behavior becomes scenarios, the cost of the bootstrap mailbox goes to Known limitations, the "trust email" setting goes to a guide, and the decision is deleted.
4. **Tests.** Tests are reshaped so that names start with scenario ids, replacing the `AC-n` comments. Scenarios without tests get new tests written by the test engineer from the requirements, without reading the implementation; a new test that fails reveals a real gap between code and requirement, for the user to decide.
   - **Last step, one commit (user decision, 2026-11-03):** squash the Flyway migrations into one and renumber the decisions (0006 → 0005, then 0008 → 0006). It comes last because a gap found above may need one more migration. Claude compares schema dumps from the old migrations and the new one on a temporary database, then runs the backend verification. The project hook forbids changing committed migrations, so Claude prepares the new file under a temporary name and the user runs one command that deletes the old files and renames it. The user then recreates the dev database with `docker compose -f infra/compose.yaml down -v`, which deletes the `postgres-data` volume with all dev data; this needs the user's confirmation at that moment. Afterwards the user watches migrations until the first release, so the squash is not on the roadmap.
5. **Process.** The neutral process description, skills and agents (the test engineer is a new agent), `docs/development/docs.md`, `.claude/README.md`, `AGENTS.md`.

### Who does what

Stages 3 and 4 run in subagents per capability, on Sonnet (user decision, 2026-11-03).

Capabilities of stage 3, proposed by Claude from the pilot and approved by the user on 2026-11-03:

| Capability | Prefix | Mainly from |
|---|---|---|
| identity | IDN | the identity foundation spec, decision 0005, the Identity section of the invariants |
| authorization | PERM | roles and permissions, 403 and 404, from the identity foundation spec and the invariants |
| platform | PLT | Problem Details, paging, client configuration |
| audit | AUD | the audit rules and one catalog of actions |
| user-administration | USR | the user administration spec |
| invitations | INV | the invitations and invitation links specs |
| invitation-emails | IEM | the invitation emails spec |

Steps of stage 3 (Claude's plan, not objected to):

1. The main session creates the index.
2. One subagent per capability moves the requirements and checks each against the code. It does not settle disputed points; it records them. The subagents run one after another, starting with `authorization`, `platform` and `audit`, which collect requirements from several specs, so that later ones do not duplicate them.
3. The main session collects the contradictions into one table, and the user decides them. The pilot found about 13.
4. After those decisions, the main session sorts the Design sections, and deletes the specs, the progress logs, the former invariants document and decision 0005. Quotes of user decisions in the progress logs then remain only in git.

Until the end of stage 4, `trace` shows almost no coverage; that is expected.

## Open questions

- **The test audit.** Claude's proposal, not discussed: a read-only agent lists duplicates, needed tests and tests of implementation detail for `user-administration`, and the user decides what goes. The test rules for new changes follow the same answer.
- **A spec-anchored process** (decided; kept here until the sections above are rewritten). Claude's proposal, accepted by the user on 2026-11-04, with changes to C-5 and C-7, dropping deltas and the tests in two passes. It reverses parts of the user decisions of 2026-10-31, and the sections above that describe deltas and full coverage are not rewritten yet. The term comes from Claude's memory of Birgitta Böckeler's article on Martin Fowler's site. The requirements today are not spec-as-source either: code cannot be regenerated from them, since UI detail stays in the code and about 520 of the 778 tests cite no scenario.
  - Authority: requirements for the behavior they describe, the contract for the API, decisions for architecture, the code and its tests for everything else. A detail that turns out to matter moves into the requirements.
  - Requirements: a statement of one or two sentences or a table; the WHEN/THEN format stays; a scenario only where a mistake is costly or hard to notice, and one behavior per scenario. A requirement may have no scenario only when a mistake is seen at once and cheap to fix; a rule about access, data, state, security or irreversible actions has one, however short (user decision, 2026-11-04; `validate` and `docs/development/docs.md` are changed).
  - Flow: the requirements are edited directly in the branch and the user reads the changed requirements in full, not as a diff, since details can get lost in one (user's view, 2026-11-04); a contract and one temporary plan file, deleted before the PR; `/review`, `/verify`, the PR.
  - Size: at about 8–10 scenarios in a change, the agent suggests splitting it, and the user decides (the number is the user's preference, 2026-11-04). It is advice, not a tool check. Requirements without scenarios escape this count, so the agent also mentions a change with many of them.
  - List of details: after implementation, the agent lists the details visible to a user that it chose on its own. A choice the user agrees with stays in the code and does not go into the requirements. A choice the user disagrees with is redone and most likely fixed in the requirements (user's rule, 2026-11-04). Claude's additions: the order is requirement, test with its id, then code; a one-off matter of taste, which no later change is likely to break, may be fixed in the code with an ordinary test. The list shows only what the agent recognizes as a choice.
  - Tests, in two passes: before the code, the test engineer writes red tests with ids for the scenarios, from the requirements, the contract and the plan. After the code, the test engineer in a fresh context writes tests of details from outside (through the API or a component), avoiding the implementation as far as possible, and reports doubtful behavior instead of fixing it in a test. The developer adds only unit tests for non-obvious algorithms, and reports any change to a test engineer's test. The user asked for this split on 2026-11-04, instead of Claude's first version, where the developer wrote the tests of details.
  - Limits of the second pass: risks, not an enumeration of cases; one level per rule; skipped for small changes; removed if it finds no real bugs on Tracking core and the next feature.
  - Tooling: `validate` and `trace` stay; deltas, `merge`, `modify`, `renumber`, `new-id` and `impact` are not built unless a team or parallel work appears.
  - Known costs: agents decide details on their own and may get them wrong; undescribed behavior can change without a document saying so; the line between a costly and a cheap mistake is a judgment; one more pass over the seven capability files.
  - Constitution: the user changed C-5 and C-7 on 2026-11-04. Requirements are the authority for the behavior they describe, not for every detail; a test of a scenario cites its id, and tests of details cite none.
  - Rewriting the requirements: before stage 5, in a subagent, starting with `user-administration`, which the user reviews before the other six files (user decision, 2026-11-04).
- **Agent review of the spec.** Before the requirements gate only (user decision, 2026-11-05, narrowing Claude's proposal of a review before each gate), with a fixed checklist (contradictions with living requirements and the constitution; testability of every scenario; completeness against the proposal's scope: error paths, empty states, refused access, concurrent edits, what each role sees, what is audited; ambiguity; "how" leaking into requirements), one round, and a size guard (8–10 scenarios) that suggests splitting the change. The reviewer reports gaps but does not write requirements; scope expansions go to roadmap Ideas or to the user as a question.
- **What an agent reads.** Done: **What to read** in `docs/process.md`, with `show` and `cited-by` in the tool. The first plan will show whether it holds.
