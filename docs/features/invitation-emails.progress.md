# Invitation emails: implementation progress

Spec: `docs/features/invitation-emails.md`
Branch: `feature/invitation-emails`
Started: 2026-10-23

## Steps

| Step | Status | Commit | Updated |
|---|---|---|---|
| 1 contract | in review | | 2026-10-23 |
| 2 migration | in review | | 2026-10-23 |
| 3 domain | pending | | |
| 4 api | pending | | |
| frontend A (prototype) | skipped | | 2026-10-23 |
| frontend B | pending | | |
| review | pending | | |
| verify | pending | | |

Status: `pending`, `in progress`, `in review` (report shown, waiting for the user), `approved`, `skipped`. Steps 1 and 2 are one checkpoint.

## Decisions

<!-- One line per decision the user made at a checkpoint, in their words: answers to agents' questions, requested changes, accepted deviations from the spec. -->

- 2026-10-23, start: branch named `feature/invitation-emails` (user's request); created from `claude/invitations-email-spec-dlblip`, which carries the approved spec on top of `main`.
- 2026-10-23, steps 1–2: "Вариант d, а из спецификации пункт про миграцию убери": the registry's tables are created by Modulith's schema initialization (`spring.modulith.events.jdbc.schema-initialization.enabled: true`), not by Flyway; spec updated by the coordinator (Design and Decisions).

## Handoff notes

<!-- From each approved step's "Notes for the next step": what a fresh agent or session needs that the spec, decisions and diff do not show (temporary stubs, pitfalls found, conventions adopted, deferred follow-ups). Remove a note when it no longer applies. -->

- Steps 1–2 were pushed unapproved as a WIP commit so the user could review them on GitHub (user's request); the approval is recorded here when given.
- Frontend prototype (step A) is skipped: mock mode is not implemented (`docs/development/frontend.md`); screens are reviewed after step B.

## Pending

<!-- Relays sent to an agent and not yet acted on, and findings waiting for the user's decision. Empty at every phase boundary. -->

- none

## Open questions

- none

## Agents

<!-- Agents can be continued only within the session that created them; a new session starts fresh agents from this log. Context is the size from the agent's last completion notification. Model is the `model` parameter of the call, or else the `model:` line of the agent's definition. Phase is `contract`, `implementation` or `review`; the usage record has the session ids. -->

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| backend-dev | opus | ac29c78fe9df0105b | contract | 136k |
