# Sample center

Branch: `feature/sample-center`
Roadmap item: Next, 1. **Sample center** (was **Mock mode**; rewritten in this change).
Requirements approved: 2026-11-07 "требования одобряю"

## Scope

Why: anyone, a person or an agent, can open any screen as any kind of user, on plausible data and in a reproducible state: to review screens per role, to choose the frontend style, and so that agents can look at screens to check that they are plausible. No scenario tests run on sample center data.

What: the local run of the full application (backend, database, Keycloak, client) gets a fictional training center:

1. A persona for every role of PERM-1, for every access state (not invited, disabled, active without roles), and for the role combinations Administrator + Mentor and Mentor + Coordinator. Sign-in is the ordinary Keycloak form.
2. A plausible data set in the dev database, brought back to its initial state on every start, and extended by every feature.
3. A switch in the client that makes API requests fail with a server error, respond slowly, or fail as if there were no connection.

The sample center exists only in development mode and is absent from the production build.

How it relates to other work:

- It is not part of the tests: tests keep their own data and do not rely on sample center data.
- It is part of a feature's vertical slice (C-3): a feature adds its data to the sample center. This rule goes to `docs/development/`, not to the requirements (C-6).
- Product scope (C-1): a development tool; it adds no product feature.

Out:

- A client mode without the backend, with the API answered from fixtures (the original "mock mode"): rejected, see Decisions.
- End-to-end scenario tests: an idea for the roadmap, to run on the real stack with their own data and not the sample center's.
- Keeping changes across restarts, an empty data set, signing in as a persona without the Keycloak form: later, if needed.

No architecture decision: the choice is easy to reverse and builds on decision 0003 and IDN-7. Decision 0007, proposed for the data source of the backend-less mode, is not needed.

## Requirements

New capability `docs/requirements/development-mode.md` (DEV):

- DEV-1 Development users, moved from IDN-7 and extended with the personas table. IDN-7 is removed from identity; its scenarios become DEV-1.1 (was IDN-7.2) and DEV-1.2 (was IDN-7.3, now restores the table). The tests `DevelopmentUserRunnerProfileTests` and `DevelopmentUserRunnerTests` are to cite the new ids (test engineer's task).
- DEV-1.3 (new): a restart unlinks the persona Not invited.
- DEV-2 The sample center data, with DEV-2.1 and DEV-2.2 (new).
- DEV-3 Simulated failures, with DEV-3.1, DEV-3.2 and DEV-3.3 (new).
- AUD-7: creating and restoring the development users at startup is not audited, worded so that the actions of development users are clearly audited as usual.
- IDN-4 and the identity Purpose: development users are the third way an identity is linked, in development mode only.

Documents this change makes wrong, to update with the code:

- `docs/development/backend.md` (how to run): warn that every start in development mode replaces the dev database with the sample center data.
- `docs/development/`: the rule that a feature adds its data to the sample center, and that the data shows every state a screen shows differently.
- `docs/development/frontend.md`, section Mock mode: replace with the simulated failures switch and the sample center.
- `README.md`: the development users table, "stays linked to its user until the dev database is reset", "Profile changes and the change history stay"; new personas need the realm recreated once.

## Design

No contract change and no migration: nothing here is an API operation or a schema change.

### Backend: restore on every start (DEV-1, DEV-2)

- One dev-only bean, `SampleCenter` (module `system`, package `com.mentorhub.system.internal.dev`, `@Profile("dev")`), with `void restore()`: Flyway `clean()`, `migrate()`, then the sample data SQL. A dev-only `FlywayMigrationStrategy` calls `restore()` instead of the ordinary migrate, so it runs before JPA and anything else touches the database. `spring.flyway.clean-disabled: false` only in `application-dev.yaml`; `application.yaml` keeps clean disabled.
- The sample data is plain SQL in `backend/src/main/resources/db/sample/` (one file per module, applied in a fixed order), never a Flyway migration and never on the ordinary `spring.flyway.locations`. SQL rather than Java through the modules' services: the services would write audit entries and publish events (invitation emails), and the data set must be exactly the sample data.
- The personas move into that SQL with fixed user ids; `DevelopmentUserRunner`, `DevelopmentUsersProperties`, `DevelopmentUsersConfiguration` and `mentorhub.development-users` are removed. Reason: after a clean every start, a second mechanism that "restores" the personas is redundant, and one source of truth is simpler.
- The Spring Modulith event publication table is created by Modulith's own schema initialization, not by Flyway, so `clean()` drops it. After `restore()` it must exist and be empty, so that no publication from before the start is delivered against the new data. T3 checks the order of the initializations and recreates it if needed. (Coordinator's inference from the T1 report, not a user decision.)
- Every start must produce the same rows apart from times (DEV-2.1 compares them): fixed ids and tokens for every sample row, not only the personas. Times inside `audit_log.details` (for example `expiresAt` of `INVITATION_LINK_REISSUED`, AUD-7) are relative to the start like the time columns, and consistent with the rows they describe.
- Times in the sample data are relative to the start (`now() - interval '...'`), so pending invitations stay pending and the history looks recent (DEV-2). Comparisons in tests therefore exclude times: time columns and time values inside JSON details.
- Keycloak realm (`infra/keycloak/mentorhub-realm.json`): add the accounts Coordinator, Observer, Administrator and mentor, Mentor and coordinator with fixed ids `...0007` to `...000a`, password `mentorhub` as the others. `stranger@mentorhub.local` (`...0006`) stays the persona Not invited. The disabled persona gets the role Mentor (decision 2026-11-06).

### The sample data set (DEV-2)

A fictional training center with SWE, QA and DevOps tracks; English names. Since the features so far are users, invitations and audit, the data covers every state their screens show differently:

- Users besides the personas (about 25, identities of the local issuer with subjects that have no Keycloak account): mostly students, several mentors, a coordinator, an observer; users with several roles; disabled users with each disable reason (`OTHER` with a comment); one user whose profile was changed.
- Invitations in every status (pending, one expiring within a day, expired, accepted, revoked), with various roles, and invitation emails in every status (`QUEUED`, `SENT`, `FAILED` with a failure, `SKIPPED`), including a resent and a reissued one.
- An audit history consistent with the data, containing every action of the AUD-7 catalog at least once, on the personas as well, so the history screens are not empty.
- One pending invitation with a fixed token, its link documented in README, so the accept screen can be opened as the persona Not invited.
- Enough users and invitations that a list that pages shows more than one page.

### Frontend: simulated failures (DEV-3)

- Everything lives in `frontend/src/app/core/dev/`: a `FailureMode` service (a signal, in memory only, so a reload turns it off), a `failureInterceptor`, and a `FailureSwitch` component that contains both the switch and the indicator.
- Absent from the production build by file replacement: `src/environments/dev-tools.ts` exports `devToolsProviders` and `devToolsInterceptors` that wire the above; `src/environments/dev-tools.prod.ts` exports both as empty arrays, and the `production` configuration of `angular.json` replaces the first with the second. `app.config.ts` spreads them; the root `App` renders the switch only when the dev providers give it, outside the router outlet and the unavailable message, so it shows on every screen including PLT-8.
- The interceptor is last in the chain (closest to the network), so the real `errorInterceptor` handles simulated failures like real ones. It applies to requests to the MentorHub API (the client configuration included), not to Keycloak.
- Modes: `normal`; `slow`: the request is sent after 3 s; `server-error`: a 500 response with the problem body of PLT-1, the request not sent; `offline`: an error with status 0, as the browser reports no connection, the request not sent.
- Placement and look are the developer's choice, for the list of details: small, fixed in a corner, not covering the toolbar.
- The frontend developer checks once that a production build contains no `failure-switch`, and adds it to the frontend verification only if it is a one-line check.

## What the tests rely on

Backend:

- `SampleCenter` (`com.mentorhub.system.internal.dev`) is a bean only in the `dev` profile; `restore()` simulates a start in development mode. Before T3 it is a stub that does nothing.
- A test context in the `dev` profile runs on Testcontainers, as `TestMentorHubApplication` does; it starts no Keycloak or Mailpit.
- The persona subjects are the Keycloak ids `00000000-0000-4000-8000-000000000001` to `...000a` of the issuer `http://localhost:8180/realms/mentorhub`; `...0006` is the persona Not invited. The persona table is DEV-1.
- "MentorHub's data" for DEV-2.1 is every table of the `public` schema except `flyway_schema_history` and the event publication table (async listeners of earlier test actions may still write to it); times are excluded from comparisons: time columns and time values inside `audit_log.details`.
- `SampleCenter` is package-private: tests that use it are in `com.mentorhub.system.internal.dev`.
- Account ids of the new personas: `...0007` Coordinator, `...0008` Observer, `...0009` Administrator and mentor, `...000a` Mentor and coordinator. The realm has exactly the ten persona accounts (`DevelopmentRealmConsistencyTests`), and each persona user has the email of its realm account (`SampleCenterTests`); the emails of the new accounts are the developer's choice.
- The tests of IDN-7.2 and IDN-7.3 (`DevelopmentUserRunnerProfileTests`, `DevelopmentUserRunnerTests`) test the runner, which T3 removes; DEV-1.1 and DEV-1.2 get new tests against `SampleCenter` and the profile, and the old ones are deleted by the test engineer.

Frontend:

- `devToolsProviders` and `devToolsInterceptors` from `src/environments/dev-tools.ts` (development) and `src/environments/dev-tools.prod.ts` (production, empty). A test of "built for production" uses the second.
- The switch: `data-testid="failure-switch"`, a `<select>` with option values `normal`, `slow`, `server-error`, `offline`. The indicator: `data-testid="failure-indicator"`, present only while a mode other than `normal` is on, and its text names the mode.
- A reload is a fresh `TestBed`.

## Tasks

| Task | Scenarios | Who | Status |
|---|---|---|---|
| T1 Stubs: `SampleCenter` with an empty `restore()`; the two `dev-tools` files, the file replacement and an empty `FailureSwitch` with the planned selectors absent | all, for the red tests | backend-dev, frontend-dev | done |
| T2 Red tests; replace the tests of IDN-7.2 and IDN-7.3 | DEV-1.1 to DEV-3.3 | test-engineer | done |
| T3 Restore on start: clean, migrate, the personas in SQL, removal of the runner and its configuration, the realm accounts | DEV-1.1, DEV-1.2, DEV-1.3, DEV-2.1, DEV-2.2 | backend-dev | done |
| T4 The sample data set (checkpoint: the user looks at the data on the screens) | DEV-2 | backend-dev | done |
| T4a DEV-2.1 test compares times inside `audit_log.details` as times (excluded), not as fixed values | DEV-2.1 | test-engineer | done |
| T5 Simulated failures: service, interceptor, switch and indicator, production exclusion | DEV-3.1, DEV-3.2, DEV-3.3 | frontend-dev | done |
| T6 Documents: `backend.md` (run warning, Sample center section with the rule that a feature adds its data and shows every state a screen shows differently, Configuration and profiles), `README.md` (personas table, the invitation link, realm recreation, no "stays until reset"), `frontend.md` (Mock mode replaced) | | backend-dev (backend, README), frontend-dev (frontend) | done |
| T7 Second test pass: tests of details, the read-back test of the sample data, times inside details match their rows | DEV-1 to DEV-3 | test-engineer | done; found a real bug outside this change (AUD-6.5) |

Order: T1, T2, then T3 and T5 in parallel, T4 after T3, T6 with each area.

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Red tests

Commit: 764ea7e (2026-11-07, the user agreed: "да, коммить").

## Decisions

- 2026-11-06: "Mock mode нужен, чтобы смотреть экраны по ролям, подбирать стиль фронтенда и чтобы агенты смотрели экраны для проверки правдоподобия. Тестов сценариев на данных mock mode нет."
- 2026-11-06: "Персоны: пять ролей из PERM-1, плюс статусы доступа (не приглашён, отключён, активен без ролей), плюс персоны с несколькими ролями сразу." Combinations: Administrator + Mentor and Mentor + Coordinator (Claude's recommendation, accepted).
- 2026-11-06: "Сквозные journey-тесты без бэкенда сейчас не делаем, это идея для Ideas в roadmap."
- 2026-11-06: The sample center's data is part of a feature's vertical slice ("да, пусть входит в вертикальный срез").
- 2026-11-06: No backend-less mode: the client runs against the real backend with prepared data ("запускать без бэкенда и мокать поведение - это хрупко, но замокать авторизацию юзеров мне кажется неплохим компромисом"; "попытка не подключать бэкенд пока выглядит как костыль и не понятно, в чём экономия").
- 2026-11-06: One database: the sample data lives in the dev database ("да, делаем так").
- 2026-11-06: The dev database is brought back to its initial state on every start ("давай восстанавливать исходное состояние базы при каждом запуске. если потребуется сохранять, то сделаем позже").
- 2026-11-06: No idea about parallel agent work in the roadmap ("я, как человек, буду боттлнеком параллельной разработки").
- 2026-11-06: The change is named Sample center, and the branch is renamed to `feature/sample-center` ("Sample center, ветку тоже переименуй").
- 2026-11-06: Work on this branch, not a new one from main, because main does not have the new process yet (docs/workflow-rework.md).

Made for the backend-less mode and dropped with it: changes lasting until the page is reloaded; an explicit "not mocked" error and a check that every operation has a mock handler.

- 2026-11-06: The sample center is what development mode does, not a separate mode; the requirements live in a capability Development mode, and IDN-7 moves there ("ну да, по-сути, мы донастраиваем dev мод. давай вариант 1, IDN-7 переносим").
- 2026-11-06: The disabled persona has the role Mentor, and the simulated failures (DEV-3) are in scope ("роли у отключённой персоны как предложил, SMP-3 делаем").
- 2026-11-06: Leave the mentions of Mock mode in `docs/workflow-rework.md` untouched ("упоминания мок мода в docs/workflow-rework.md не трогай").

- 2026-11-06: The restore replaces the whole database, the audit log included, with an explicit exception in AUD-3 and decision 0005 (option (a) of the review, "1 — a"); the failure mode resets on reload, the switch is available on the unavailable message, the indicator has a scenario ("7 — по рекомендации"); the other review findings accepted ("остальное принимаем"), keeping the warning sentence in the Purpose.

- 2026-11-06: No flag to keep the dev database across starts for now; it can be added later. The local Keycloak is not reset on every start; README describes an explicit reset (the user agreed: "да, согласен").

- 2026-11-07: AUD-7 names what is not audited without ambiguity: only creating and restoring the development users at startup and the restore of the sample center data; the actions of development users are audited as usual ("да, поправь, но `Creating and restoring the development users at startup (DEV-1), the restore of the sample center data (DEV-2) and the outcome of sending an invitation email are not audited` будет достаточно").

- 2026-11-07: No development mode exception in AUD-3 and decision 0005, and the restore is not named in AUD-7: replacing the database is an infrastructure matter, not an exception to the audit rules; DEV-2 alone says the audit log is replaced. This replaces option (a) of 2026-11-06 ("меня смущает то, зачем мы пишем какие-то исключения про дев-режим в требования и решения по аудиту, если перенакатка базы - это инфраструктурный момент, а не исключение бизнес-логики"; "да, вноси").
- 2026-11-07: Sign-in stays the ordinary Keycloak form; a persona picker without the form is later, if needed ("да, оставляем форму Keycloak").
- 2026-11-07: Plan gate passed: the runner is removed and the personas move into the sample SQL, T4 is a checkpoint, the pending invitation with a fixed link stays ("раннер удаляем, T4 чекпоинт, ссылку оставляем").
- 2026-11-07: The statement in `docs/development/backend.md` that a `dev` run freezes migrations, and the hook that relies on it, stay as they are in this change; rewording them and relaxing the hook is a possible later change ("(a)"). T3 and T5 approved and committed ("коммить T3 и T5").

- 2026-11-07: Times inside the sample data's audit details are relative to the start like the other times, and DEV-2 and DEV-2.1 say that times are relative to the start and excluded from "the same on every start" (option 3; "вариант 3, меняй требование"). Replaces the plan's "no times inside `audit_log.details`", which forced a fixed `expiresAt` in `INVITATION_LINK_REISSUED` that would disagree with the invitation's real expiry.

- 2026-11-07: The user read and approved the changed DEV-2 and DEV-2.1, and a permanent test that the sample data reads back in full through the API is added in the second test pass (no scenario id; it checks the data, it runs no scenario on it) ("да, добавляем тест, требование одобряю").

- 2026-11-07: T4 checkpoint passed: the user looked at the sample data on the screens and approved T4, T4a and T6 with the commit ("посмотрел, всё ок, коммить"). The list of details of T4 accepted as it is.

- 2026-11-07: The AUD-6.5 bug found by the second pass is fixed as a separate change, recorded in the roadmap (Next, 2); the second-pass tests are committed ("отдельно, запиши в roadmap и коммить тесты").

- 2026-11-07: Review 1: findings 1, 3, 4, 5, 6, 7 fixed as recommended; finding 2 kept as a display state, explained by a short comment in `users.sql`, not in backend.md or README ("1, 3, 4, 5, 6, 7 по рекомендации; касаемо 2 - ... лучше написать это коротким комментом в sql файле или джавадоке"). The coordinator wrote the IDN-4 and decision 0003 wording; the frontend developer put the production-build check into a `postbuild` script instead of a Verification command, because check-docs requires an allow rule for every Verification command and the permissions were not to be changed.

- 2026-11-07: Result accepted, the IDN-4 and decision 0003 wording included; the production-build check stays a `postbuild` script ("принимаю, postbuild оставляем, коммить").

## Handover

- Waiting for: the user accepts the result (stage 7), reads the IDN-4 wording, and chooses between the `postbuild` check and a Verification command with an allow rule.
- To read: `git diff HEAD` (review fixes, uncommitted) and `frontend/scripts/check-prod-build.mjs`.
- Documents changed (against `workflow/rework`, which main lacks):
  - `docs/requirements/development-mode.md`: added, capability DEV (committed).
  - `docs/requirements/identity.md`: changed, IDN-7 moved to DEV-1; new since the last stop: Purpose and IDN-4 (heading and text) name the development users and the sample center data (DEV-1, DEV-2) as the development-mode exception (uncommitted).
  - `docs/architecture/decisions/0003-external-identity-provider.md`: changed, new since the last stop: the exceptions to invitation-only registration name the sample center data too (uncommitted).
  - `docs/requirements/audit.md`, `docs/requirements/README.md`, `docs/roadmap.md`, `docs/development/backend.md`, `README.md`: changed (committed).
  - `docs/development/frontend.md`: changed; new since the last stop: Verification says `npm run build` ends with the production-build check (uncommitted).
- State: T1 to T7 done; review 1 fixed, re-review clean (sonnet). Verifier, scope all, on the working tree: backend verify PASS; frontend format, lint, test 277/277, build with `postbuild` PASS; check-docs, requirements tests 62/62, `trace` 207/207 PASS. The check-docs reminders (backend.md:42, :60, frontend.md:106) were confirmed accurate by backend-dev and the re-review.
- Open questions:
  1. Keep the production-build check as `postbuild` (recommended) or move it to a Verification command with an allow rule in `.claude/settings.json`.
- On acceptance: commit the review fixes with the plan (proposed message in the last report), then stage 8: move durable "how" to the decisions and guides, delete the roadmap item Sample center and this plan, show the user; commit if asked; suggest `/pr` and `/usage-review`.
- On remarks: the owner of the area, as in the review loop.

## Agents

<!-- Agents can be continued only within the session that started them; a new session starts fresh ones from this plan. Context is the size from the agent's last completion notification. Model is the `model` parameter of the call, or else the `model:` line of the agent's definition. -->

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| reviewer | opus | a0eb979d9e051e7df | spec | 40k |
| backend-dev | definition | a417c47cb7f272e28 | T1, T3 | 116k |
| frontend-dev | definition | ac593873febb30aac | T1, lint fix, T5 | 68k |
| test-engineer | definition | abf2a09217a5c23c2 | T2 | 136k |
| backend-dev | definition | af11e30ac7a78ae64 | T4, T6 | 197k |
| test-engineer | definition | ae4075b294b54f22c | T4a | 64k |
| backend-dev | definition | a1295d41e4f3299ce | T4 rework | 74k |
| test-engineer | definition | a9014d20f66fe408e | second pass | 101k |
| reviewer | definition | a2489d7559dead5ef | review 1 | 147k |
| backend-dev | definition | a59c8f9cfedd1b41f | review fixes | 70k |
| frontend-dev | definition | a74cf71aca1b1b86c | review fixes | 47k |
| test-engineer | definition | a2af1e1c457ad0cba | review fix 7 | 21k |
| reviewer | sonnet | a9357cbcfd6b76f82 | re-review | 24k |
| verifier | definition | a0a8104306c6aa3c6 | verify all | 13k |
