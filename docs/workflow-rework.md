# Workflow rework: handover

Status: temporary. This document hands over the context for reworking the documentation and the Claude Code workflow in a fresh session. Delete it when the rework is done.

## Motivation

The user's assessment (2026-10-20):

- The project follows spec-driven development in name, but its constitution, the rules every change must keep, is weakly felt.
- Specs are bloated: about 600 lines each.
- There is no roadmap.

## Current state

- The flow is described in `.claude/README.md`: `/spec` → reviewer → approval → `/feature` (subagents `backend-dev` and `frontend-dev`, checkpoints, a progress log) → `/review` → `/verify` → `/pr`, plus `/adr` and the temporary `/usage-review`.
- Knowledge is spread across `AGENTS.md` (rules loaded into every session), `docs/architecture/invariants.md` (domain and security invariants), `docs/adr/` (seven ADRs), `docs/features/` (specs and progress logs) and `docs/development/` (guides per area).
- Size: `docs/features/` holds about 2170 lines of specs and progress logs. Backend and frontend code holds about 373 references of the form `AC-n` to acceptance criteria. Step 1 of `/spec` reads every ADR and every existing spec.
- The roadmap and many product decisions exist only outside the repository, in Claude's local memory and plan files. Examples: the feature order, the access lifecycle, the domain model, the pilot deployment. Nothing in `docs/` records them.

## Concerns raised while specifying the invitation features

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
11. **"What" and "how" share one document.** Spec-driven development separates the specification (behavior and its reasons) from the technical plan (how it is built). Here the spec's Design section goes down to class names, SQL and lock order. That is what makes specs large and makes them age quickly, because implementation details change more often than behavior.
12. **`/feature` has no task breakdown.** Implementation follows a fixed pipeline (contract → migration → domain → API → frontend), not tasks derived from a plan for the feature. The progress log records how the work went, not what is planned.

### Pure SDD or not

In an agent-driven project, stale documents cost more than keeping them current. Agents take documents literally and have no unwritten team knowledge to correct them. Updating requirements is cheap work an agent can do as part of every change. The rework therefore aims at living documents as the only description of behavior: every behavior change goes through them, and history stays in git, not in the documents. Costs to plan for:

- small changes and bug fixes need a cheap path that updates the affected requirements without a full `/spec`;
- review must check that documents and code agree, because a document claiming behavior the code lacks is worse than no document;
- tests stay the executable check of the requirements, so requirements and tests keep stable ids that refer to each other.

## Already changed on this branch

Accepted by the user on 2026-10-20; the rework may reshape it:

- Until the first release, ADRs and the Requirements of specs are living documents, changed in place, and a spec's Design is a snapshot. This is the "Keeping decisions and specs current" section in `docs/development/docs.md`, plus a rule in `AGENTS.md`.
- `/adr` edits an existing ADR with a dated "revised" note instead of writing a superseding one (`.claude/skills/adr/SKILL.md`).
- The spec template marks Design as a snapshot (`.claude/skills/spec/template.md`).

## Proposed directions

Discussed, not decided:

- **A short, explicit constitution** with only the non-negotiables, merged from the rules in `AGENTS.md` and `docs/architecture/invariants.md`.
- **A roadmap in the repository:** stages, planned features with a one-line scope, and the product decisions that today live only in memory. Seed content:
  - the agreed order: invitation links and emails, mock mode (needs an ADR), then the tracking core;
  - the access lifecycle: an ended enrollment removes the student role and disables a user left without roles; a configurable read-only grace period after graduation; expulsion revokes access at once; data is kept;
  - the domain model: Enrollment is the center and Cohort an optional grouping; individual SWE students and QA groups;
  - corporate SSO auto-provisioning later, with its own ADR;
  - personal data rules postponed; personal data stays out of foreign SaaS;
  - the first production is a pilot on rented hosting without corporate infrastructure (no SSO, no corporate email).
- **Living requirement documents per domain,** for example "access": identity, user administration and invitations. They hold scenarios, acceptance criteria, access, visibility and audit, with no design and no history.
  - A feature spec becomes a transient task statement. After implementation its requirements are merged into the domain document, and the spec and its progress log are deleted (git keeps them).
  - Acceptance criteria get stable unique ids (for example `INV-1`), renamed in code by a script.
- **`/spec` reads less:** the constitution, the roadmap, the affected domain documents and an ADR index with one line per ADR, instead of every ADR and spec.
- **An ADR threshold:** only cross-cutting, hard-to-reverse decisions become ADRs.
- **A spec size guard,** so features are split earlier.

## Open questions

- What "constitution" means for this project, and where it lives.
- The roadmap's format and place.
- How to convert the existing specs into domain documents: all at once, or one domain at a time.
