# Invitation emails: implementation progress

Spec: `docs/features/invitation-emails.md`
Branch: `feature/invitation-emails`
Started: 2026-10-23

## Steps

| Step | Status | Commit | Updated |
|---|---|---|---|
| 1 contract | approved | cf555bd | 2026-10-23 |
| 2 migration | approved | cf555bd | 2026-10-23 |
| 3a domain and persistence | approved | 946e10b | 2026-10-23 |
| 3b services, authorization and audit | pending | | |
| 4 api | pending | | |
| frontend A (prototype) | skipped | | 2026-10-23 |
| frontend B | approved | (this commit) | 2026-10-23 |
| review | pending | | |
| verify | pending | | |

Status: `pending`, `in progress`, `in review` (report shown, waiting for the user), `approved`, `skipped`. Steps 1 and 2 are one checkpoint.

## Decisions

<!-- One line per decision the user made at a checkpoint, in their words: answers to agents' questions, requested changes, accepted deviations from the spec. -->

- 2026-10-23, start: branch named `feature/invitation-emails` (user's request); created from `claude/invitations-email-spec-dlblip`, which carries the approved spec on top of `main`.
- 2026-10-23, steps 1–2: "Вариант d, а из спецификации пункт про миграцию убери": the registry's tables are created by Modulith's schema initialization (`spring.modulith.events.jdbc.schema-initialization.enabled: true`), not by Flyway; spec updated by the coordinator (Design and Decisions).
- 2026-10-23, steps 1–2: pushed as WIP commit `cf555bd` for review on GitHub (user's request), then "одобряю шаги 1–2, переходи к шагу 3".
- 2026-10-23, step 3: "шаг 3 разбей": split into 3a (domain and persistence: `mail` module, dependencies, repository, `lastEmail`) and 3b (services, authorization, audit, the sender listener and the resubmission job), each with a fresh `backend-dev`; 3b is briefed with 3a's handoff notes.
- 2026-10-23, step 3a: "Вариант а по хелс чеку": the mail server stays out of `/actuator/health` (`management.health.mail.enabled: false`); document it in `docs/development/backend.md` (3b) and in the spec's Configuration table (coordinator, at the final spec update).
- 2026-10-23, frontend B review: "все 4 замечания отдай фронтенд агенту": `@Service()` for `ClientConfigService`; update `docs/ui/guidelines.md` and `docs/development/frontend.md`; add `confirm-dialog.spec.ts`; add tests for snackbar politeness, disabled row actions while sending, and replace the weak `not.toContain('mail')` assertion. The frontend commit includes those two docs files.
- 2026-10-23, step 3a: "Все остальное одобряю".
- 2026-10-23, frontend B: review fixes applied (plus Cancel in `confirm-dialog` now closes with `undefined` instead of `''`, and `sessionStorage.clear()` in `access.guard.spec.ts` to fix an order-dependent test); "Коммить оба, фронтенд одобряю, пуш".

## Handoff notes

<!-- From each approved step's "Notes for the next step": what a fresh agent or session needs that the spec, decisions and diff do not show (temporary stubs, pitfalls found, conventions adopted, deferred follow-ups). Remove a note when it no longer applies. -->

- Temporary stubs that step 4 replaces (marked `TODO(invitation-emails step 4)`): `SystemController.getClientConfig` returns `invitationEmails = false` (should read `MailDelivery.isEnabled()`); `InvitationsController` answers 501 when `createInvitation` or `reissueInvitationLink` gets `sendEmail: true`; `InvitationsController.sendInvitationEmail` always answers 501.
- Generated signatures: `reissueInvitationLink(UUID id, @Nullable InvitationLinkReissue)` with an optional body; `sendInvitationEmail(UUID id)` responds 202; `ClientConfig` has a 4-argument constructor; `InvitationCreate.getSendEmail()` defaults to `false` but is `null` on an explicit JSON `null`: treat `null` as `false`.
- New 409 responses `InvitationLinkReissueConflict` (`invitation-closed`, `email-unavailable`) and `InvitationEmailConflict` (`invitation-closed`, `invitation-expired`, `email-unavailable`, `email-queued`); `InvitationCreateConflict` also lists `email-unavailable`. Revoke and `GET .../link` keep their old responses.
- JSON: `InvitationJsonMixin` (class-level NON_NULL) already omits a null `lastEmail`; the new `InvitationEmail` DTO needs its own NON_NULL mixin so an absent `finishedAt` is omitted.
- V6 schema rules: `failure` set exactly when status is `FAILED`, 1–500 chars; `finished_at` set exactly when status is not `QUEUED` (set it in the same update); `status` defaults to `QUEUED`; a second `QUEUED` row per invitation violates `invitation_emails_queued_uq` → `DuplicateKeyException` → 409 `email-queued`. No DB check keeps the token out of `failure`: the application must (AC-15).
- Registry (option d): done in 3a (`spring-modulith-starter-jdbc`, schema initialization, `completion-mode: delete`, republish on restart). Verified by tests: `schema-initialization.enabled`, `management.health.mail.enabled`. Not verified (3a's `backend-dev` had no `api-lookup` tool): `spring.modulith.events.completion-mode`, `republish-outstanding-events-on-restart`, `IncompleteEventPublications.resubmitIncompletePublicationsOlderThan(Duration)`, Modulith 2.x staleness/processing-state settings affecting AC-13, and whether Boot 4.1 Docker Compose support wires Mailpit as a mail connection. 3b confirms them with `api-lookup` (if it lacks the tool, the coordinator runs `api-lookup` and relays). AC-12/AC-13 tests cover the registry.
- 3a API for 3b: `InvitationEmailRepository.insertQueued` / `findQueued` / `lock` (`for no key update`) / `findLatest` / `markSent` / `markSkipped` / `markFailed(id, InvitationEmailFailure)`; `mark*` update only `QUEUED` rows and return whether a row changed. `MailDelivery.isEnabled()` / `send(MailMessage)` (interface in `com.mentorhub.mail`, mockable); `send` while disabled throws `IllegalStateException`, so services check `isEnabled()` first and answer 409 `email-unavailable`. The listener reads address, token and expiry only via `InvitationRepository.findLink(invitationId)`.
- Duplicate queued row: `insertQueued` throws `DuplicateKeyException` and the failed statement aborts the PostgreSQL transaction. In `sendEmail(id)` lock the invitation (`InvitationRepository.lock`) and check `findQueued` first; map a remaining `DuplicateKeyException` to 409 `email-queued`. For `reissueLink` with `sendEmail` while queued (AC-3), check `findQueued` and skip the insert; never rely on catching.
- Failure text (AC-15): build with `InvitationEmailFailure.of(e.getMessage(), token, link.toString())`; the warning log must not contain the token or the body. `MailDeliveryException` messages never contain the text but may contain the recipient and the server reply.
- Still to do in 3b: `@EnableAsync` / `@EnableScheduling` on `MentorHubApplication`; `spring.task.execution.shutdown.await-termination: true` with a 30 s period; `INVITATION_EMAIL_REQUESTED` in `InvitationAuditAction`; problem type constants `email-unavailable` / `email-queued` next to `InvitationProblems`; the listener and the resubmission job; the health-check note in `docs/development/backend.md`.
- Still to do in step 4 (besides the `TODO(invitation-emails step 4)` stubs): map `details.lastEmail()` in `InvitationsMapper.invitation`; NON_NULL `@JacksonMixin` for the `InvitationEmail` DTO; `SystemController` reads `MailDelivery.isEnabled()`.
- Test support: `com.mentorhub.support.Mailpit` (`host()`, `smtpPort()`, `deleteAll()`, `awaitMessages(n)`). Default test config has no mail host (email disabled); enable per test with `spring.mail.host` / `spring.mail.port` / `mentorhub.mail.from`. Tests that commit data through the registry may need to clear `event_publication`; `TestUsers.deleteAll` already clears `invitation_emails`.
- Docker: the first Mailpit pull can exceed Testcontainers' 2-minute limit; run `docker pull axllent/mailpit:v1.31.4` once.
- Environment: if verification fails with "Could not find a valid Docker environment", start `dockerd` first.
- Frontend is done (step A skipped, mock mode not implemented); screens are checked manually per AC after backend step 4, using frontend-dev's manual check list (AC-17, AC-18, AC-19).

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
| backend-dev (3a) | opus | a3c9d5afd83dd21df | implementation | 157k |
| frontend-dev (B) | sonnet | a4f6c753ea793a2d4 | implementation | 138k |
| reviewer (frontend) | opus | a73598105dbd26fbd | implementation | 67k |
