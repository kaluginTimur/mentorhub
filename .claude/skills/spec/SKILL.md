---
name: spec
description: Work out a feature spec with the user in the main session and write it to docs/features/<feature>.md (requirements, UI, design), then get it reviewed.
argument-hint: "<feature-name>"
arguments: [feature]
disable-model-invocation: true
---

Work out the spec for the feature `$feature` together with the user. The spec is the task statement for the implementation agents, so it must be complete enough that they never have to guess.

## Prepare

1. Read `AGENTS.md`, `docs/architecture/invariants.md`, every ADR in `docs/adr/`, the existing specs in `docs/features/`, `docs/ui/guidelines.md` if it exists, and the API contract for related endpoints.
2. If `docs/features/$feature.md` exists, continue from it: summarize its state and the open questions, and work on what is missing.
3. Use [template.md](template.md) as the structure.

## Work it out with the user

Go section by section, one topic at a time. For each topic, propose a concrete draft based on what you have read, give your recommendation, and ask only what the user has to decide. Domain rules come from the user: never invent who may see or do what. When something is undecided, record it as an open question instead of guessing.

1. Goal and scope, including what is out of scope. Check it against the product scope in the invariants.
2. Roles and scenarios, then acceptance criteria (AC-n) in Given/When/Then form, each testable and linked to a scenario.
3. Access matrix for every role and action, with cohort and track conditions, past cohorts, mentor reassignment and a graduate who becomes a mentor. Visibility of every data item. Audit events.
4. UI: screens per role, content, actions, all states, text wireframes, patterns from the UI guidelines.
5. Design: modules, data model, API sketch (method, path, purpose, roles, errors), how authorization and visibility are enforced, events between modules. If a decision is architectural (a new convention, a new technology, a cross-cutting rule), propose an ADR via `/adr`.
6. Open questions.
7. Size. Every implementation step carries the context of the step before it, so a large feature costs more per line than two small ones. If the spec has more than about 20 acceptance criteria, adds more than one new module, or mixes application code with new local infrastructure (an identity provider, a mail server), propose a split into features that can each be implemented and reviewed on their own, and let the user decide.

Write the file in English as the sections become clear, so progress is not lost. Keep the status `draft`.

## Review

When the draft is complete, run the `reviewer` subagent in spec mode with the prompt `docs/features/$feature.md`. Show the user its findings, agree on changes and apply them.

The spec becomes `approved` only when the user says so and no open question blocks implementation. Then tell the user that `/feature $feature` starts the implementation.
