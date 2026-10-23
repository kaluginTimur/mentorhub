# Invitation emails: implementation progress

Spec: `docs/features/invitation-emails.md`
Branch: `feature/invitation-emails`
Started: 2026-10-23

## Steps

| Step | Status | Commit | Updated |
|---|---|---|---|
| 1 contract | approved | cf555bd | 2026-10-23 |
| 2 migration | approved | cf555bd | 2026-10-23 |
| 3a domain and persistence | pending | | |
| 3b services, authorization and audit | pending | | |
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
- 2026-10-23, steps 1–2: pushed as WIP commit `cf555bd` for review on GitHub (user's request), then "одобряю шаги 1–2, переходи к шагу 3".
- 2026-10-23, step 3: "шаг 3 разбей": split into 3a (domain and persistence: `mail` module, dependencies, repository, `lastEmail`) and 3b (services, authorization, audit, the sender listener and the resubmission job), each with a fresh `backend-dev`; 3b is briefed with 3a's handoff notes.

## Handoff notes

<!-- From each approved step's "Notes for the next step": what a fresh agent or session needs that the spec, decisions and diff do not show (temporary stubs, pitfalls found, conventions adopted, deferred follow-ups). Remove a note when it no longer applies. -->

- Temporary stubs that step 4 replaces (marked `TODO(invitation-emails step 4)`): `SystemController.getClientConfig` returns `invitationEmails = false` (should read `MailDelivery.isEnabled()`); `InvitationsController` answers 501 when `createInvitation` or `reissueInvitationLink` gets `sendEmail: true`; `InvitationsController.sendInvitationEmail` always answers 501.
- Generated signatures: `reissueInvitationLink(UUID id, @Nullable InvitationLinkReissue)` with an optional body; `sendInvitationEmail(UUID id)` responds 202; `ClientConfig` has a 4-argument constructor; `InvitationCreate.getSendEmail()` defaults to `false` but is `null` on an explicit JSON `null`: treat `null` as `false`.
- New 409 responses `InvitationLinkReissueConflict` (`invitation-closed`, `email-unavailable`) and `InvitationEmailConflict` (`invitation-closed`, `invitation-expired`, `email-unavailable`, `email-queued`); `InvitationCreateConflict` also lists `email-unavailable`. Revoke and `GET .../link` keep their old responses.
- JSON: `InvitationJsonMixin` (class-level NON_NULL) already omits a null `lastEmail`; the new `InvitationEmail` DTO needs its own NON_NULL mixin so an absent `finishedAt` is omitted.
- Problem types `email-unavailable` and `email-queued` have no constants yet; add them next to `InvitationProblems`.
- V6 schema rules: `failure` set exactly when status is `FAILED`, 1–500 chars; `finished_at` set exactly when status is not `QUEUED` (set it in the same update); `status` defaults to `QUEUED`; a second `QUEUED` row per invitation violates `invitation_emails_queued_uq` → `DuplicateKeyException` → 409 `email-queued`. No DB check keeps the token out of `failure`: the application must (AC-15).
- Registry (option d): step 3 adds `spring-modulith-starter-jdbc` and sets `spring.modulith.events.jdbc.schema-initialization.enabled: true` with completion mode `delete` and republish on restart; confirm property names with `api-lookup` against Spring Modulith 2.1.1. No registry-vs-migration schema test; AC-12/AC-13 tests cover the registry.
- Environment: if verification fails with "Could not find a valid Docker environment", start `dockerd` first.
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
