# Workflow rework: handover

Status: temporary. This document hands over the context for reworking the documentation and the agent workflow into a spec-driven process. It is updated at the end of every session and deleted when the rework is done. Branch: `workflow/rework`.

## Where we are

Sessions so far: 2026-10-20 (motivation, first changes), 2026-10-31 (alternatives, target model, life cycle, format, tooling, migration plan), 2026-11-02 (composition of the constitution).

**Next:** migration stage 1 continues in the main session with the user's review (see **Migration**). `docs/constitution.md` (10 rules) and `docs/roadmap.md` are written and approved. `docs/architecture/decisions/` is written and approved. `CLAUDE.md` and `AGENTS.md` are done, which closes stage 1. Stage 2, the tool, is next. User decision of 2026-11-03: how other AI clients load the guides and the constitution is not considered for now, since each client works differently. Left for stage 5: the `/adr` skill and `docs/development/docs.md` still describe the old ADR format with a Status line; `.claude/README.md` and the reviewer still call `AGENTS.md` the project-wide rules; the `/pr` skill says commit messages follow `AGENTS.md`, while the language rule is now in the constitution. The comment `ADR 0006` in the applied migration `V3__user_administration.sql` stays, because editing it would break Flyway's checksum; the squash at the end of stage 4 removes it. The skills and guides still describe `docs/architecture/invariants.md` as holding the domain model, visibility and the roadmap stage; that stays until stage 5 rewrites them.

Not discussed yet: the skills and agents of the new process (the last discussion item), including the agent review of the spec and the agent-neutral process description (see **Open questions**). They are needed before migration stage 5, not before stage 1.

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
10. **No single constitution.** Non-negotiable rules are spread across `AGENTS.md`, `docs/architecture/invariants.md`, the ADRs and the guides.
11. **"What" and "how" share one document.** The spec's Design section goes down to class names, SQL and lock order. That makes specs large and makes them age quickly.
12. **`/feature` has no task breakdown.** Implementation follows a fixed pipeline (contract → migration → domain → API → frontend), not tasks derived from a plan for the feature.

## Current state

- The flow is described in `.claude/README.md`: `/spec` → reviewer → approval → `/feature` (subagents `backend-dev` and `frontend-dev`, checkpoints, a progress log) → `/review` → `/verify` → `/pr`, plus `/adr` and the temporary `/usage-review`.
- Knowledge is spread across `AGENTS.md`, `docs/architecture/invariants.md`, `docs/adr/` (eight ADRs), `docs/features/` (five specs with progress logs, about 2170 lines) and `docs/development/` (guides per area).
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
  - Domain facts in `docs/architecture/invariants.md` (roles, `user_identities`, invitation token storage, gate rules) move to living requirements. Plans (feature order, the planned domain model, undefined observer rights) move to the roadmap. Claude's proposal: notes and their visibility levels also go to the roadmap, because notes are not implemented.
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
- **The index** `docs/requirements/README.md` (planned) is the scripts' source of known prefixes and of prefix → file. The scripts check it and do not generate it.
- **Tests and scenarios:** by default one scenario per test, and the test name starts with its scenario id (`INV-9.1 …`); the script matches only prefixes listed in the index. End-to-end journey tests are allowed as a separate kind: they complement scenario tests, do not replace them, and traceability does not count them. How a journey test is marked is open.

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
   - `docs/constitution.md` (planned) from `AGENTS.md` and `docs/architecture/invariants.md`; facts and plans from `invariants.md` go to the roadmap now and to the requirements in stage 3; `invariants.md` is deleted once empty.
   - `docs/adr/` → `docs/architecture/decisions/` with an index; add Why and Rejected alternatives (including reversed choices such as hashed tokens and synchronous sending); update the "ADR 0…" references in 33 files.
   - `CLAUDE.md` imports `docs/constitution.md` (planned); `AGENTS.md` loses the rules that moved.
2. **The tool,** at least `validate` and `trace`, so the migrated requirements can be checked.
3. **Requirements.** The five specs become capability files with new ids, each requirement checked against the code; contradictions are collected in a table for the user to decide. Design sections are sorted (durable parts to decisions or guides, the rest dropped); specs and progress logs are deleted. Decision 0005 moves to the `identity` requirements: its behavior becomes scenarios, the cost of the bootstrap mailbox goes to Known limitations, the "trust email" setting goes to a guide, and the decision is deleted.
4. **Tests.** Tests are reshaped so that names start with scenario ids, replacing the `AC-n` comments. Scenarios without tests get new tests written by the test engineer from the requirements, without reading the implementation; a new test that fails reveals a real gap between code and requirement, for the user to decide.
   - **Last step, one commit (user decision, 2026-11-03):** squash the Flyway migrations into one and renumber the decisions (0006 → 0005, then 0008 → 0006). It comes last because a gap found above may need one more migration. Claude compares schema dumps from the old migrations and the new one on a temporary database, then runs the backend verification. The project hook forbids changing committed migrations, so Claude prepares the new file under a temporary name and the user runs one command that deletes the old files and renames it. The user then recreates the dev database with `docker compose -f infra/compose.yaml down -v`, which deletes the `postgres-data` volume with all dev data; this needs the user's confirmation at that moment. Afterwards the user watches migrations until the first release, so the squash is not on the roadmap.
5. **Process.** The neutral process description, skills and agents (the test engineer is a new agent), `docs/development/docs.md`, `.claude/README.md`, `AGENTS.md`.

Who does what: stages 3 and 4 run in subagents per capability; stage 1 runs in the main session.

## Open questions

- **Agent review of the spec.** Claude's proposal: a review inside Propose and Plan, before each user gate, with a fixed checklist (contradictions with living requirements and the constitution; testability of every scenario; completeness against the proposal's scope: error paths, empty states, refused access, concurrent edits, what each role sees, what is audited; ambiguity; "how" leaking into requirements), one round, and a size guard (8–10 requirements) that suggests splitting the change. The reviewer reports gaps but does not write requirements; scope expansions go to roadmap Ideas or to the user as a question.
- **The skills and agents of the new process,** and how to describe the process without naming client-specific skills and subagents.
- How a journey test is marked.
