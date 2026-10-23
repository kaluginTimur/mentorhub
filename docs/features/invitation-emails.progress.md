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
| 3b services, authorization and audit | approved | (this commit) | 2026-10-23 |
| 4 api | pending | | |
| frontend A (prototype) | skipped | | 2026-10-23 |
| frontend B | approved | 95f64fc | 2026-10-23 |
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
- 2026-10-23, step 3b: "Одобряю, доки пусть допишет агент на шаге 4. Коммить и пуш": step 4 also adds the remaining `docs/development/backend.md` items from the spec's Documentation updates (the `mail` module; events through the registry, `@EnableAsync` as a pitfall and the single-instance assumption; mail configuration; the `FAILED` emails of `TestMentorHubApplication`; the `mail.debug` warning).
- 2026-10-23, frontend B: review fixes applied (plus Cancel in `confirm-dialog` now closes with `undefined` instead of `''`, and `sessionStorage.clear()` in `access.guard.spec.ts` to fix an order-dependent test); "Коммить оба, фронтенд одобряю, пуш".

## Handoff notes

<!-- From each approved step's "Notes for the next step": what a fresh agent or session needs that the spec, decisions and diff do not show (temporary stubs, pitfalls found, conventions adopted, deferred follow-ups). Remove a note when it no longer applies. -->

- Step 4 owns: remove the three `TODO(invitation-emails step 4)` 501 stubs in `InvitationsController` (the `false` literals 3b passed are placeholders); `SystemController.getClientConfig` reads `MailDelivery.isEnabled()`; map `details.lastEmail()` in `InvitationsMapper.invitation`; a NON_NULL `@JacksonMixin` for the `InvitationEmail` DTO so an absent `finishedAt` is omitted (`InvitationJsonMixin` already omits a null `lastEmail`); the remaining `docs/development/backend.md` items (see Decisions), each checked against the code.
- Service API for the controllers: `administration.create(email, roles, sendEmail)` → `IssuedInvitation`, pass `Boolean.TRUE.equals(invitationCreate.getSendEmail())` (`null` means false); `administration.reissueLink(id, sendEmail)` → `IssuedInvitation`, body optional: `body != null && Boolean.TRUE.equals(body.getSendEmail())`; `administration.sendEmail(id)` → `InvitationDetails`, answer 202 with `InvitationsMapper.invitation(...)` and no `Cache-Control: no-store`.
- Generated signatures: `reissueInvitationLink(UUID id, @Nullable InvitationLinkReissue)`; `sendInvitationEmail(UUID id)` responds 202; `ClientConfig` has a 4-argument constructor.
- Errors: services throw `ConflictException` with `InvitationProblems.EMAIL_UNAVAILABLE` ("Sending email is not available.") / `EMAIL_QUEUED` ("An email for this invitation is already queued."); `ProblemDetailsAdvice` renders 409. Contract 409s: `InvitationLinkReissueConflict` (`invitation-closed`, `email-unavailable`), `InvitationEmailConflict` (`invitation-closed`, `invitation-expired`, `email-unavailable`, `email-queued`), `InvitationCreateConflict` also lists `email-unavailable`; revoke and `GET .../link` unchanged. Permission comes first (`@RequirePermission(USERS_INVITE)` on controller and service): 403 whether or not email is enabled.
- AC coverage left for step 4: AC-1 (`client-config` flag), web-layer checks of AC-2 to AC-7 (201/202 shapes, `lastEmail` JSON, 403 for the flag and the endpoint, gate refusals), AC-16 serialization. Services, sender, resubmission and AC-8 to AC-15 are covered by 3b tests (`InvitationEmailTests`, `InvitationEmailMailServerTimeoutTests`, `InvitationEmailRestartTests`, `InvitationEmailTextTests`).
- Testing pitfalls: default test config has email disabled, so 409 `email-unavailable` is testable end to end there; `@WebMvcTest` mocks `InvitationAdministrationService`. Mail-enabled `@SpringBootTest` classes reuse `InvitationEmailTests`' pattern (Mailpit started lazily from `@DynamicPropertySource`, `com.mentorhub.support.Mailpit`, clear `event_publication` before each test). `SpringApplicationBuilder.properties(...)` are defaults that `application.yaml` overrides: pass settings as `--args`. Two audit entries in one transaction share `occurred_at`; don't assert their order.
- 3b design: listener `InvitationEmailSender` (`@ApplicationModuleListener(propagation = NOT_SUPPORTED)`, `TransactionTemplate`; lock+read, send outside a transaction, mark) marks a replayed request `FAILED` when no mail server is configured; `InvitationEmailResubmission` resubmits every incomplete registry publication older than 5 min every 5 min. `MailDeliveryException` messages never contain the body; the sender redacts token and link.
- API names verified: `republish-outstanding-events-on-restart` and `completion-mode: delete` effective (tests); `resubmitIncompletePublicationsOlderThan(Duration)` exists; AC-13 needs no staleness settings. `api-lookup`: `spring-boot-docker-compose` 4.1.1 has no mail connection factory, so Mailpit is not wired from compose; `application-dev.yaml` sets `spring.mail.host: localhost`, `port: 1025` explicitly. `backend-dev` had no `api-lookup` tool in 3a and 3b: the coordinator runs lookups on request.
- V6 schema rules: `failure` set exactly when `FAILED`, 1–500 chars; `finished_at` set exactly when not `QUEUED`; one `QUEUED` row per invitation (`invitation_emails_queued_uq`).
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
| backend-dev (3b) | opus | a1c42c4c723af9b6e | implementation | 174k |
| api-lookup | haiku | a7bea7c539bfb1bca | implementation | 31k |
