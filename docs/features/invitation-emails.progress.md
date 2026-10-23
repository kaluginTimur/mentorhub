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
| 3b services, authorization and audit | approved | 1046ded | 2026-10-23 |
| 4 api | approved | (this commit) | 2026-10-23 |
| frontend A (prototype) | skipped | | 2026-10-23 |
| frontend B | approved | 95f64fc | 2026-10-23 |
| review | approved | (this commit) | 2026-10-23 |
| verify | in progress (automated PASS; AC-19 manual running) | | 2026-10-23 |

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
- 2026-10-23, step 4: "Одобряю, коммить и пуш".
- 2026-10-23, final review: "Делай правки без учета миграций. Если нужна дополнительная колонка - делай её в v6. Остальное делай по рекомендациям": fix findings 1 (duplicate send on a backlog >5 min; any new column goes into V6, not a new migration), 4 (AC-6 test) and 5 (AC-18 test); findings 2 and 3 (spec "(planned)", `identity-foundation.md` revised note) at the final spec update. The user asked to continue the conversation in Russian (project artifacts stay in English).
- 2026-10-23, final review: "Добавляй тест": frontend also adds a "Send email" 404 case (AC-18).
- 2026-10-23, final review: "Фразу добавляй, коммить и пуш": `backend.md` notes that deliveries waiting on the guard hold executor threads; fix round committed and pushed. "Тест сам я сделать не могу ... Можешь ли ты сам сделать тест через headless браузер и прислать скриншоты?": AC-19 is run by an agent with headless Chromium.
- 2026-10-23, frontend B: review fixes applied (plus Cancel in `confirm-dialog` now closes with `undefined` instead of `''`, and `sessionStorage.clear()` in `access.guard.spec.ts` to fix an order-dependent test); "Коммить оба, фронтенд одобряю, пуш".

## Handoff notes

<!-- From each approved step's "Notes for the next step": what a fresh agent or session needs that the spec, decisions and diff do not show (temporary stubs, pitfalls found, conventions adopted, deferred follow-ups). Remove a note when it no longer applies. -->

- Final review fix round (committed with the review step): finding 1 fixed without a schema change (V6 unchanged) by an in-process per-request guard in `InvitationEmailSender` (regression test `InvitationEmailResubmissionTests`); `resubmit only FAILED` was rejected because `PROCESSING` has no timestamp of its own (Modulith 2.1.1 probed by compiling and at runtime, not via `api-lookup`). Spec text to fix at the final spec update: Design, Asynchronous delivery ("5 minutes is far longer than one send can take..." is wrong; step 1's lock does not cover the send, the sender serializes deliveries per request) and the "Single instance" bullet (guard is per process; backlog re-queueing). Also the reviewer's findings 2 (drop "(planned)" at lines 194, 224) and 3 ("Revised" note in `identity-foundation.md:300,357`).
- Implementation is complete (steps 1–4, frontend B). Next: the `review` phase — review loop on all feature changes, then `verifier` with scope `all`, then the manual checks per AC (frontend-dev's list for AC-17 to AC-19, with the backend running and Mailpit).
- Documentation still owed by the coordinator at the final spec update: ADR 0008 (event publication registry; `docs/development/backend.md` already says "ADR 0008 planned" and no file exists), the ADR 0001 line, `docs/features/invitations.md`, README, the compose comment, and the spec's Configuration table row for `management.health.mail.enabled: false`. Check the spec's Documentation updates section for the full list.
- Step 4 decisions: `InvitationEmailsEndpointTests` holds `MailDelivery.send` with a `@MockitoSpyBean` latch (context sets `spring.mail.host=localhost`, never connects; `@AfterEach` releases the latch); the 202 has no `Cache-Control` assertion because Spring Security always adds one. Step 4's last `backend.md` edit came after its green verify run (docs only).
- Test support and pitfalls: `com.mentorhub.support.Mailpit`; mail-enabled tests clear `event_publication`; `SpringApplicationBuilder.properties(...)` are overridden by `application.yaml` (use `--args`); audit entries in one transaction have no stable order.
- `backend-dev` had no `api-lookup` tool in 3a, 3b and 4; the coordinator ran the one open lookup (no Docker Compose mail connection in Boot 4.1.1; `dev` sets the Mailpit host explicitly).
- Docker: the first Mailpit pull can exceed Testcontainers' 2-minute limit; run `docker pull axllent/mailpit:v1.31.4` once.
- Environment: if verification fails with "Could not find a valid Docker environment", start `dockerd` first.
- Frontend is done (step A skipped, mock mode not implemented); screens are checked manually per AC after backend step 4, using frontend-dev's manual check list (AC-17, AC-18, AC-19).

## Pending

<!-- Relays sent to an agent and not yet acted on, and findings waiting for the user's decision. Empty at every phase boundary. -->

- AC-19 manual check: a `general-purpose` agent (sonnet) runs it in this remote container with a headless browser (user's request), screenshots in the session scratchpad; result not yet reported.

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
| backend-dev (4) | sonnet | a921837e9d96fcc14 | implementation | 98k |
| reviewer (final) | opus | ae9460285578ed345 | review | 150k |
| backend-dev (review fixes) | opus | a86808b9539484de4 | review | 88k |
| frontend-dev (review fixes) | sonnet | a43d2a6f6e9cb5c75 | review | 65k |
| reviewer (re-review) | sonnet | ad4cdb27cc4916bee | review | 41k |
| verifier (all) | sonnet | af480ac05d7d83f6e | review | 15k |
| general-purpose (AC-19) | sonnet | acc5cf06c5250af7a | review | running |
