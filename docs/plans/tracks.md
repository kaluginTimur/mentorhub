# Tracks

Branch: `feature/tracks`
Roadmap item: **Tracks:** an administrator keeps the list of tracks and names the coordinators of each. Out of scope: enrollments. A new capability; touches authorization and audit.
Requirements approved: 2026-11-15, "требования одобряю" (I approve the requirements)

## Scope

Confirmed by the owner at the path gate on 2026-11-15 ("да, подтверждаю": yes, confirmed).

**Goal:** tracks, and the relation "this user coordinates this track", exist as the ground for enrollments (roadmap item 2) and for the first access decided by a relation. Without them a coordinator has no scope. The administrator keeps them current in the UI, without a developer.

**Approach:** a new module `tracks`. The administrator keeps an editable list of tracks: creates a track, renames it, archives it (no deletion, since enrollments and history will refer to tracks), and names and removes its coordinators. A new permission for the administrator role (such as `TRACKS_MANAGE`), an audit entry for every change, a per-track history view, and a Tracks page for administrators.

**In:** the list of tracks and its changes, coordinators of a track, the new permission, audit actions and the track's history view, the Tracks page.

**Out:** enrollments; a coordinator's own view of their tracks (comes with enrollments); any access a coordinator gains through a track.

Rejected at the path gate: a fixed list of tracks seeded by a migration (a new track would need a release); building tracks together with enrollments (two new modules, past the size of one change).

## Requirements

- New capability `docs/requirements/tracks.md` (TRK-1 to TRK-10), added to the index.
- PERM-1: the administrator gains `TRACKS_READ` and `TRACKS_MANAGE`.
- AUD: the purpose names tracks as a subject; AUD-7 gains six track actions and two user actions for coordinator assignments.
- USR-10.3 (new) and USR-11: the user's history shows when they were named or removed as a coordinator.

## Design

Proposed by the coordinator and approved by the owner at the plan gate on 2026-11-15, with the request schemas split (Decisions).

**Module.** A new module `com.mentorhub.tracks` (decision 0001), persistence with `JdbcClient` like `users` and `audit`, because its rules rest on row locks and `on conflict do nothing`. It depends on `users` and `audit` through their public API only; `users` does not depend on `tracks`. The module's choice of `JdbcClient` goes into the backend guide's **Code** section at stage 8.

**What `users` adds to its public API** (root package `com.mentorhub.users`), so `tracks` never reads the `users` tables:

- A read of users by ids for the coordinators of a track: id, display name (as `HistoryActor.name`), email, whether disabled, whether they hold the coordinator role.
- The candidates: the active users who hold the coordinator role, ordered by display name; `tracks` leaves out the track's coordinators.
- An eligibility check of one id: an active user with the coordinator role (unknown id = not eligible).
- Recording the user-side entries `USER_TRACK_COORDINATOR_ADDED` / `USER_TRACK_COORDINATOR_REMOVED` about the user, joining the caller's transaction, with details `track: {id, code, name}` as given. `users` owns these two actions, their subject type `USER` and their details, so its history view maps them without knowing `tracks` (decision 0005: the writer owns the action; here the subject's module writes it on `tracks`' call).
- `Permission` gains `TRACKS_READ` and `TRACKS_MANAGE`, held by the administrator role (decision 0004, PERM-1).

**Schema** (in `V1__schema.sql`, by the owner's decision below): `tracks` (id uuid, `code` text, `name` text, `status` text checked `ACTIVE`/`ARCHIVED`, `created_at`); `track_coordinators` (`track_id` → `tracks`, `user_id` → `users`, `created_at`, primary key on both). No unique index on code or name (TRK-2.3). Limits after trimming, checked by the service: code 1–20 characters, name 1–100 (my proposal).

**Serialization (TRK-5.4, TRK-6.4).** Every change of a track, archiving and restoring included, first locks the track row with `for no key update` (it is referenced by `track_coordinators`), then checks the status, then eligibility, then writes. So a change and an archiving run one after another; parallel namings serialize on the same lock, and the insert is `on conflict do nothing` besides. Order of answers: 400 (validity, before the service where the contract can), 404 (unknown track), 409 `track-archived`, 409 `coordinator-not-eligible`.

**Audit.** Subject type `TRACK`, actions owned by `tracks`: `TRACK_CREATED` (`code`, `name`), `TRACK_UPDATED` (`changes`: field `code`/`name`, old, new), `TRACK_ARCHIVED`, `TRACK_RESTORED`, `TRACK_COORDINATOR_ADDED` / `TRACK_COORDINATOR_REMOVED` (`userId`). Naming or removing writes the track entry and calls `users` for the user entry, in one transaction. A no-op records nothing.

**Contract** (tag `Tracks`; problems in a `TrackProblems` class of the module):

| Operation | Call | Permission | Answers |
|---|---|---|---|
| `listTracks` | `GET /api/v1/tracks?status=ACTIVE\|ARCHIVED\|ALL` (default `ACTIVE`) | `TRACKS_READ` | 200 `TrackList` `{items: Track[]}`, not paged |
| `createTrack` | `POST /api/v1/tracks` `TrackCreate` `{code, name}` | `TRACKS_MANAGE` | 201 `Track`, 400 |
| `getTrack` | `GET /api/v1/tracks/{id}` | `TRACKS_READ` | 200, 404 |
| `updateTrack` | `PUT /api/v1/tracks/{id}` `TrackUpdate` `{code, name}` | `TRACKS_MANAGE` | 200, 400, 404, 409 `track-archived` |
| `archiveTrack` / `restoreTrack` | `POST /api/v1/tracks/{id}/archive`, `/restore` | `TRACKS_MANAGE` | 200 `Track`, 404 |
| `getTrackHistory` | `GET /api/v1/tracks/{id}/history?page&size` | `TRACKS_READ` | 200 `TrackHistoryPage`, 404 |
| `listTrackCoordinatorCandidates` | `GET /api/v1/tracks/{id}/coordinator-candidates` | `TRACKS_MANAGE` | 200 `{items: CoordinatorCandidate[]}` (id, name, email), 404 |
| `addTrackCoordinator` | `PUT /api/v1/tracks/{id}/coordinators/{userId}` | `TRACKS_MANAGE` | 200 `Track`, 404, 409 `track-archived`, 409 `coordinator-not-eligible` |
| `removeTrackCoordinator` | `DELETE /api/v1/tracks/{id}/coordinators/{userId}` | `TRACKS_MANAGE` | 200 `Track`, 404, 409 `track-archived` |

- `Track`: `id`, `code`, `name`, `status` (`TrackStatus`), `coordinators`: `TrackCoordinator[]` ordered by name: `id`, `name`, `email`, `disabled` (boolean), `coordinatorRole` (boolean). The marks of TRK-4 are `disabled: true` and `coordinatorRole: false`.
- `TrackHistoryEntry`: `id`, `occurredAt`, `action` (`TrackHistoryAction`, the six `TRACK_*`), `actor` (`HistoryActor` or null), and per action `code`/`name` (created), `changes` (`TrackChange[]`: `field` `code`/`name`, `oldValue`, `newValue`), `user` (`HistoryActor`, resolved at read time, for the coordinator actions). Unknown actions are skipped (decision 0005).
- The user's history: `HistoryAction` gains `USER_TRACK_COORDINATOR_ADDED` / `_REMOVED`; `HistoryEntry` gains `track` (`TrackReference`: `id`, `code`, `name`, as stored).
- `Permission` in the contract gains the two permissions.

**Frontend.** A feature folder `frontend/src/app/features/tracks/`: routes `tracks` (list) and `tracks/:id` (track page) behind `requirePermission('TRACKS_READ')`, a "Tracks" link in the shell for `TRACKS_READ`. The list's status filter lives in the query parameter `status` (`ACTIVE` default, `ARCHIVED`, `ALL`). Dialogs: create and edit track (code and name in one form), confirm archive (shared confirm dialog), add coordinator (choice from the candidates). The user page's history sentence (USR-11) names the track as "<code> <name>" from `TrackReference`. UI text and layout follow `docs/ui/guidelines.md` and the users pages.

**Sample center** (`db/sample/tracks.sql`, after `users.sql` in `SampleCenter.SAMPLE_DATA`): active tracks SWE, QA, DevOps; one with two coordinators, one with a disabled coordinator and one with a coordinator whose role was removed (marked), one with no coordinators; an archived track; two tracks with the same code; a track with each kind of history entry and more than one page of history; the matching `USER_TRACK_COORDINATOR_*` entries in the coordinators' histories.

## What the tests rely on

- Backend: the contract above and its generated `TracksApi`; the controller `TracksController` in `com.mentorhub.tracks.internal.web`, stubbed with 501 until task 3; problem types `track-archived` and `coordinator-not-eligible` with the URN scheme of the existing problems.
- Frontend: components `TracksList` (`features/tracks/tracks-list.ts`) and `TrackPage` (`features/tracks/track-page.ts`), routes `/tracks` and `/tracks/:id`, the generated API functions named by the operation ids above, test data in `features/tracks/test-data.ts`. Tests find elements by role and visible text as the users specs do: buttons "Create track", "Edit", "Archive", "Restore", "Add coordinator", "Remove"; the marks read "Disabled" and "No coordinator role"; the status filter is a `select` labelled "Status". The add-coordinator dialog picks a candidate in a select labelled "Coordinator" and confirms with "Add"; a 409 is explained in a snackbar (`docs/ui/guidelines.md`); "Remove" asks no confirmation.

## Tasks

| Task | Scenarios | Who | Status |
|---|---|---|---|
| 1. Contract, schema in `V1__schema.sql`, the two permissions, `TracksController` stub (501), build green; rework: split `TrackInput` into `TrackCreate` and `TrackUpdate` | all (shape) | backend-dev | done |
| 1a. Frontend compiles: `history-text.ts` skips the two new user history actions as unknown, no text | (none) | frontend-dev | done |
| 2. Red tests: backend and frontend | TRK-1.1–TRK-10.1, USR-10.3, PERM-1 | test-engineer | done |
| 3. `users` public API and history mapping; the `tracks` module (checkpoint: backend green) | TRK-1–TRK-7, USR-10.3, PERM-1 | backend-dev | done |
| 4. Sample center tracks | (screens of TRK-9, TRK-10, USR-11) | backend-dev | done |
| 4a. Remove the 501 allowance in `support/OpenApiValidation.java` | (none) | test-engineer | done |
| 5. Navigation, tracks page, track page, user history sentence | TRK-8, TRK-9, TRK-10, TRK-10.1, USR-11 | frontend-dev | done |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

## Notes for later tasks

- From task 1, for task 2: four existing tests expect the administrator to hold only the three old permissions and now fail, out of date with the changed PERM-1: `RoleTests.everyRoleHoldsExactlyThePermissionsOfTheTable` (PERM-1.1), `StoredRolesTests.permissionsOfSeveralRolesAreCombined` (PERM-2.1), `MeEndpointTests.activeUserGetsUsersIdProfileRolesAndTheUnionOfPermissions` (IDN-5.1), `AuthenticationChainTests.unknownStoredRoleGrantsNothing` (PERM-5.1). They are the test engineer's to bring in line with PERM-1.
- From task 1, for task 3: `ContractEnumsTests.userAuditActionsEqualTheContractsHistoryActions` stays red until `UserAuditAction` gains `USER_TRACK_COORDINATOR_ADDED` and `_REMOVED` (in that order, after `USER_INVITATION_ACCEPTED`), each with a `UserChange` record carrying the track `{id, code, name}` and a case in `UsersMapper.historyEntry` filling `HistoryEntry.track`; `HistoryEntryJsonMixin` already leaves `track` out when null.
- From task 1, for task 3: problem types for a `TrackProblems` class in `com.mentorhub.tracks.internal`: `urn:mentorhub:problem:track-archived`, `urn:mentorhub:problem:coordinator-not-eligible`. The database length checks count code points (`char_length`); the service should check with `codePointCount` (or the stricter `length()`).
- From task 1, for task 3: generated `TrackCreate` and `TrackUpdate` fields are `@NotNull`; `listTracks` gets a non-null `status` (default `ACTIVE`); `getTrackHistory` gets `page`/`size` with defaults as `getUserHistory`.
- From task 1a, for task 5: `features/users/history-text.ts` has explicit cases for `USER_TRACK_COORDINATOR_ADDED` / `_REMOVED` returning `''` (comment points at task 5); the row shows with date and actor and an empty sentence. Task 5 replaces them with the USR-11 sentence. Generation writes `models/track-create.ts` and `models/track-update.ts`.
- From task 2, for task 3: the tests are endpoint tests in `backend/src/test/java/com/mentorhub/tracks/internal/web/` (`TracksEndpointTests`, `TracksConcurrencyTests`), data set up by `tracks/internal/TestTracks.java`; `TestUsers.deleteAll` clears `track_coordinators` and `tracks`. `support/OpenApiValidation.java` lets a 501 through so the red tests reach their own assertions; the owner agreed that test-engineer removes that allowance once the backend is green.
- From task 2, for task 5: TRK-10.1 expects a 409 explained in a snackbar (track-archived mentioning "archived"), "Remove" with no confirmation, and a candidate picked in the add-coordinator dialog by a helper that accepts a native select, a Spartan select or a clickable option, then "Add" if present. `features/tracks/track-page.ts` exists as an empty component; test data in `features/tracks/test-data.ts`.
- From task 3, for task 4: audit details as written. Subject `TRACK`, actor the administrator: `TRACK_CREATED` `{"code","name"}`; `TRACK_UPDATED` `{"changes":[{"field":"code"|"name","oldValue","newValue"}]}`, one item per changed field; `TRACK_ARCHIVED` / `TRACK_RESTORED` `{}`; `TRACK_COORDINATOR_ADDED` / `_REMOVED` `{"userId"}`. Subject `USER`: `USER_TRACK_COORDINATOR_ADDED` / `_REMOVED` `{"track":{"id","code","name"}}`.
- From task 3, for stage 8: the backend guide's Code section gets that `tracks` uses `JdbcClient` (Design).
- From task 4, for task 5 and manual-tester: sign in as `admin@mentorhub.local` or `admin.mentor@mentorhub.local`. List: default 4 active tracks (DevOps, QA Automation, Quality Assurance, SWE); `ARCHIVED` shows FE; `ALL` shows 5. Marks: QA Quality Assurance, Laura Chen "Disabled"; DevOps, Daniel Kim "No coordinator role". SWE: two coordinators, candidates Irene Walsh and Milo Mentor-Coordinator. QA Automation: no coordinators (shares code QA). FE: archived, keeps Sarah Lopez. DevOps: 32 history entries (two pages), every kind of entry. USR-11: Irene Walsh and Laura Chen short histories; Daniel Kim paged, track shown as `DEVOPS DevOps`, `DevOps DevOps`, `DevOps DevOps Engineering`. `coordinator@mentorhub.local` sees no tracks.
- From task 5, for the manual tester and the second test pass: TRK-8, TRK-9 and TRK-10 have no scenarios, so only TRK-10.1 and USR-11 have frontend tests; the nav link, the list with its filter, create, empty states, edit, archive and restore are checked only by hand so far. Manual steps are in frontend-dev's task 5 report (TRK-8: admin sees "Tracks", coordinator does not and is sent home from `/tracks`; TRK-9: filter, reload keeps it, create "T1 / Test", empty and 21-character code; TRK-10: DevOps marks, Show more, Edit name only, Archive then Restore, Add and Remove; TRK-10.1: two tabs, archive in one, Remove or Add in the other; FE only Restore; USR-11: Daniel Kim's history; `/tracks/abc` "Track not found"). States not in the sample data: empty lists, failed loads, 409 snackbars (except via two tabs).

- From the second test pass, for later tasks: frontend tests run through the Angular builder, `npm test -- --watch=false --include <path>` (plain `npx vitest` cannot resolve `@spartan-ng/helm/*`); `RouterTestingHarness` allows one harness per test, so cases that differ only by permissions are `it.each`; the create-track test accepts either outcome after saving (a move to `/tracks/<id>` or the new track in the list), as neither the requirements nor the plan say which.

## Red tests

Commit: 3aa1010 (2026-11-15), requirements, contract, migration, task 1 and 1a code, red tests, plan, usage record.

## Decisions

- 2026-11-15: Only a user who holds the coordinator role may be named a coordinator of a track ("согласен" to the recommendation: roles stay global, the relation narrows them; when the role is removed, the assignment stays and grants nothing).
- 2026-11-15: In this change only the administrator sees tracks ("согласен" to the recommendation: a coordinator's view comes with enrollments).
- 2026-11-15: A track gets its history view right away ("пусть сразу будет вью истории": let there be a history view right away).
- 2026-11-15: A track has a name and a short code, and neither is unique (chose "Название + короткий код", then "название не уникально, но и короткий код тоже": the name is not unique, and neither is the short code).
- 2026-11-15: Archiving is reversible; an archived track is read-only until restored, and keeps its coordinators (chose the recommendation "Обратимо, архивный — только чтение").
- 2026-11-15: Two permissions for the administrator, `TRACKS_READ` and `TRACKS_MANAGE` ("TRACKS_READ нужно тоже": TRACKS_READ is needed too).
- 2026-11-15: The code is required and can be changed: the code and the name are edited in one action, each independently, so changing the name leaves the code as it is (chose the recommendation "Обязателен, меняется"; clarified on the owner's question).
- 2026-11-15: Only an active user with the coordinator role may be named; when they are later disabled or lose the role, the assignment stays and grants nothing (chose the recommendation "Только активного").
- 2026-11-15: Such an assignment is shown with a mark, disabled or without the coordinator role, so the administrator can remove it (chose the recommendation "С пометкой").
- 2026-11-15: A track has zero or more coordinators and a user may coordinate several tracks; naming and removing a coordinator shows in both the track's and the user's history (chose "Без ограничений, обе истории"). My proposal for how, within decision 0005: two entries per change, one about the track and one about the user.
- 2026-11-15: The change is not split, at 17 scenarios ("не делим": we do not split).
- 2026-11-15: The owner agreed ("по пунктам 1–6 согласен": agreed on points 1–6) to my proposals: a repeated action answers 200 and records nothing; any change of an archived track is 409 `track-archived`; naming a disabled user, a user without the role or an unknown id is 409 `coordinator-not-eligible`; only archiving asks for a confirmation; the user's history names the track with its code and name at the moment of the change; the sample center data gains tracks with coordinators (no requirement change, a task).

- 2026-11-15: The owner accepted every recommendation on the requirements review ("принимаю все рекомендации": I accept all the recommendations): on an archived track every change except restoring and archiving again is 409 `track-archived`, even a no-op, checked after validity and before eligibility; changes of one track and its archiving are serialized (TRK-5.4); candidates for coordinators are listed with `TRACKS_MANAGE` alone (TRK-6.5); removing an unknown user id is 200 with no change; the list is ordered by code and then by name and its rows show the marks; the tracks page follows PLT-9 apart from paging and search; the cross-permission visibility of coordinators' contacts and of tracks in a user's history, and an assignment raced with disabling, are known limitations.
- 2026-11-15: The tracks schema lives in `V1__schema.sql`, not in a new migration: the owner deleted `V2__tracks.sql` and moved its content into V1 ("v2 миграцию я удалил, контент перенёс в v1").
- 2026-11-15: Listing the candidates of an archived track is 409 `track-archived` (the owner asked "а зачем архивному треку кандидаты, если архивный трек нельзя менять?", then "да, 409 и правь требования"). The coordinator changed TRK-5 and TRK-5.2 accordingly; the owner reads those two lines again. The contract is fixed.
- 2026-11-15: The database does not repeat the service's validation: drop `tracks_code_ck` and `tracks_name_ck`, keep `not null` and the status check (the owner asked "зачем проверять лимиты в базе?", then "да, передай и убери проверки длины"). A review of the rest of the schema for such checks is a separate chore after tracks merges ("первый вариант, после мержа треков").
- 2026-11-15: The owner removed `tracks_code_ck` and `tracks_name_ck` from `V1__schema.sql` themselves ("проверки в миграции я удалил сам").
- 2026-11-15: Create and update get separate request schemas, `TrackCreate` and `TrackUpdate`, as the contract's `<Subject><Verb>` names do (the owner: "я не большой фанат одного и того же dto на две разных операции", then "делим на TrackCreate и TrackUpdate").
- 2026-11-15: Plan approved at the plan gate ("план одобряю"), with the open points as proposed: limits code 1–20 and name 1–100; coordinators and candidates ordered by name then email, the list by code then name ignoring case; `TrackHistoryEntry.user` shows the user's current name; the user-side history entries are written by `users` on `tracks`' call.
- 2026-11-15: The user-side history entries stay, written by `users` on `tracks`' call ("1 оставляем"). Coordinator's proposal for stage 8, not yet answered: one line in decision 0005 that an entry about another module's subject is written by the subject's module through its public API, on the changing module's call.
- 2026-11-15: The tracks a user coordinates, shown on the user page, go to the roadmap, not this change ("2 в roadmap"); at stage 8 the coordinator proposes a separate `/change` for the roadmap item (a staff directory of who coordinates what is a possible idea beside it). Only the administrator sees users and tracks today; others get this through enrollments.
- 2026-11-15: Until task 5, the frontend skips `USER_TRACK_COORDINATOR_ADDED` / `_REMOVED` as unknown actions, with no text, so the build compiles before the red tests ("3 согласен").
- 2026-11-15: Until task 5 a coordinator entry in the user's history shows as a row with an empty sentence ("оставляем пустую строку").
- 2026-11-15: On task 2 ("1 оставляем, 2 согласен, 3 дописывай, коммить"): TRK-1.1 stays as it is, green before the code; test-engineer removes the 501 allowance in `OpenApiValidation` once the backend is green; What the tests rely on gains the add-coordinator select "Coordinator" with "Add", the snackbar for a 409, and "Remove" without confirmation; the red tests are committed.
- 2026-11-15: The `users` read API takes the role as a parameter instead of coordinator-only methods ("давай вариант 1, обобщай роль"): `findActiveUsersWithRole(Role)`, `isActiveWithRole(UUID, Role)`, and `DirectoryUser` carries `roles` instead of `coordinatorRole`; `tracks` checks `COORDINATOR` itself. The name `UserDirectory` stays.
- 2026-11-15: Task 3 approved at the checkpoint ("одобряю, коммить").
- 2026-11-15: Tasks 4 and 4a approved; the disabled coordinator stays a new user, Laura Chen, rather than reworking Paul Becker's history ("ладно, оставляем так, остальное одобряю").
- 2026-11-15: After tracks merges, propose a `/change chore` for manual-tester: before testing on a running stack, compare the backend's start time with the branch's latest backend commit or build, and stop at once with "stack stale, restart it" instead of a wasted run; it still never restarts the owner's stack ("вариант 1, предложи после треков").
- 2026-11-15: On the manual-test findings ("1, 3, 4 делаем, 2 через точку, 6 - заведи issue"): (1) TRK-10.1's GIVEN now also covers the candidates for a new coordinator (the coordinator edited it; the owner rereads it), test-engineer adds the case, frontend-dev handles that 409 like the others; (2) code and name are joined with a middle dot, "DevOps · DevOps Engineering", in titles and in the USR-11 sentence (the USR-11 test changes by the owner's decision); (3) the archive confirmation's button is red, as the disable dialog; (4) a "Tracks" quick-action card on the admin home page; (6) GitHub issue #43 for the users full-name search.
- 2026-11-15: The owner asked for "Remove" of a coordinator as a red outline button like "Archive", for the tracks list as cards instead of a table (asked for HTML mockups to choose from), and for the marked-assignment note as a hint, warning or error rather than plain text. Mockups: canvas https://claude.ai/artifact/J3DK8xZLYoR9WLegghfcBf (source in `.screens/tracks/design/`): A grid of cards, B full-width stacked cards, C grid with an attention summary; note styles 1 plain (now), 2 amber warning, 3 red error. Cards change TRK-9's "as a table" and the list page pattern in `docs/ui/guidelines.md`.
- 2026-11-15: List variant A (grid of cards) and note style 2 (amber warning callout); TRK-10.1's GIVEN approved ("A и 2, TRK-10.1 одобряю"). TRK-9 says "as a list", not "as cards": the kind of list is not the requirement's business (the owner: "почему бы не написать \"as list\"... какой там вид списка - таблица или карточки - это уже дело десятое"); the coordinator changed "as a table ... each row" to "as a list ... each track". The "No coordinator role" and "Disabled" chips stay red. Where cards are allowed goes to `docs/ui/guidelines.md`, pending the owner's agreement on the wording.
- 2026-11-15: `docs/ui/guidelines.md` gains, in the list page pattern: "A short list without paging (for example `/tracks`) may show a grid of cards instead of the table: one card per item with its title as the link, the same filter row and the same loading, empty and failure states." (the owner: "согласен", after the four answers).
- 2026-11-15: The owner read TRK-5 ("TRK-5 я прочёл"). On the re-check: fix (b) the add-coordinator title, which the close button's hover background does overlap, (c) the Archived chip, (d) the stale snackbar ("остальное всё исправить"). New: a coordinator's name on the track page links to their user page (the owner: "почему я не могу перейти в профиль координатора из списка координаторов трека?"); the coordinator proposes the link only for a viewer with `USERS_READ`. The amber callout looks out of the pattern, especially on a phone; the owner suggested an icon with a hover tooltip; options are mocked up before the batch.
- 2026-11-15: The marked-assignment note is option B ("по хинту - вариант B"): the marks become amber chips with a warning icon, and one hint under the "Coordinators" heading, "A marked assignment grants nothing until it is fixed or removed.", shown only when a coordinator is marked; the per-row callout goes. The coordinator applies the amber marks in the list cards too, for one look. Also: the code text and the status chip on a track card are not aligned (the owner). frontend-dev's own details stay as they are (no remarks from the owner).
- 2026-11-15: Dropdowns open with "Drop down" (fade and a 6 px slide from the trigger, no zoom), in the shared select and dropdown menu, both behind `motion-safe:` ("drop down"). The hint under "Coordinators" is plain grey text without an icon, as mockup B, and a long email is cut with an ellipsis, the full address in a tooltip ("с обоими пунктами согласен"). Also from the owner: the hint has too much space above and below it ("между подсказкой и «Coordinators» вообще хватит места ещё на две подсказки"); on a card the code and the status chip still look misaligned, raise the chip slightly.
- 2026-11-15: On the third re-check ("1 делай, 2 и 3 оставляем"): the select slides from the top unconditionally; menus keep closing instantly; the select's first frame needs no action. The chip still looks low to the owner: their DevTools screenshot `.screens/img_5.png` (dark theme, "QA" code and "Active" chip, text boxes highlighted) shows the chip's box centre about 2 px below the code text's centre, with the bottoms of "QA" and "Active" on about the same line, so the chip reads low.
- 2026-11-15: Restore asks for a confirmation too, as enabling a user does (USR-12) ("да, делай подтверждение на Restore"): title "Restore <code> · <name>?", text "It becomes editable again. Its coordinators are kept.", a primary (not destructive) button. TRK-10 now reads "archive it or restore an archived one after a confirmation" (the coordinator edited it; the owner rereads the line).

- 2026-11-15: The owner looked at the screens on the sample center and approved task 5 ("я посмотрел экраны, можно коммитить": I looked at the screens, you can commit). Committed with the TRK-9, TRK-10 and TRK-10.1 edits, the guidelines line, the plan and the usage record.

- 2026-11-15: TRK-9's empty state: the current behavior is accepted, and TRK-9 now says the empty-list message refers to the chosen status ("согласен, правь TRK-9 и коммить": agreed, edit TRK-9 and commit). New sentence: "An empty list SHALL say that no track has the chosen status or, when all statuses are chosen, that no track exists yet." The owner rereads it. The second test pass is committed.

- 2026-11-15: On the review findings ("принимаю все рекомендации": I accept all the recommendations): (1) naming again a coordinator already named, even one no longer eligible, answers 200 and records nothing; (2) TRK-4.2 and PERM-1 name listing the candidates; (3) PLT-9 covers unpaged list pages too, TRK-9 drops the retelling; (4) contract enum checks for tracks; (5) `trackLabel` to `shared/`; (6) `TrackUpdate` in the edit flow; (7a) the TRK-5.4 tests rewritten if they pass without the lock; (7b) the coordinator writes the four answers for the guidelines cards line at stage 8. The coordinator edited TRK-6, TRK-6.3 (GIVEN gains a coordinator since disabled, WHEN "names each of them again"), TRK-4.2, PERM-1, PLT-9 and TRK-9; the owner rereads them.

- 2026-11-15: On the re-review's minor (1) the owner asked to fix it ("почему бы не пофиксить lockWaiters()?"): the TRK-5.4 helper counts only the sessions queued behind the holder; the change of a test with an id is the owner's word.

- 2026-11-15: The re-review's minor (2), long lines, stays as it is ("оставляем, запускай verifier").

## Handover

- Waiting for: stage 8 (below, Next), in progress. The owner accepted the verification and the review round is committed ("да, коммить и переходи к этапу 8").
- Verification (`verifier`, scope `all`, 585b8e0 plus the uncommitted review round): backend verify, frontend format:check, lint, build, check-docs, requirements tests and trace (226 of 226, 4 manual) PASS. Frontend tests failed twice in `verifier` (9 and 6 of 340, unrelated specs, mostly timeouts, swap full); the owner then ran the frontend tests themselves: "я прогнал тесты, все 340 PASSED" (all 340 passed). Cause of the earlier failures: memory pressure suspected, not investigated.
- Re-review (reviewer on sonnet, `git diff 585b8e0`): NON-BLOCKING, all seven previous findings fixed; the "already named" check is race-safe; widened PLT-9 changes nothing for the users and invitations pages. Minor (1) fixed by test-engineer: `lockWaiters()` replaced by `waitersBehind(holderPid)`, a recursive query over `pg_stat_activity` in the current database through `pg_blocking_pids`, the holder records `pg_backend_pid()`; ids, names and assertions unchanged; TRK-5.4 green 3 of 3 on the real code (0.68 s and 0.06 s), both fail with the lock removed; main code restored byte-identical; backend verify PASS. The two tests need 4 connections at once (Hikari default 10).
- Fix round done. backend-dev (findings 1 and 4, verify PASS, `TracksEndpointTests` 35, `TracksConcurrencyTests` 7, new `ContractEnumsTests` 3): `TrackService.addCoordinator` returns the track unchanged, recording nothing, for a user already named, after the lock and the status check and before eligibility; `TrackRepository.isCoordinator`; `api/openapi.yaml` descriptions of `addTrackCoordinator` and the `coordinator-not-eligible` 409 ("the id is not a coordinator of the track and no active user with the `COORDINATOR` role has it"), no shape change; new `backend/.../tracks/internal/domain/ContractEnumsTests.java` (three checks). frontend-dev (findings 5 and 6, format, lint, 340 tests first run, build PASS): `trackLabel` moved to `shared/track-label/track-label.ts`; the dialog's save takes `TrackCreate | TrackUpdate`, `edit` uses `TrackUpdate`. No test changed by the developers; tests changed since 585b8e0 are test-engineer's by the owner's decisions.
- Test part done by test-engineer: TRK-6.3 extended (now green); TRK-4.2 gains the candidates of an unknown track; PLT-9.1 and PLT-9.3 cited in `tracks-list.spec.ts`; the TRK-5.4 tests passed with the lock removed and were rewritten with a helper holding the row lock and waiting for lock waiters in `pg_stat_activity`; they fail without the lock. Frontend timeouts seen by test-engineer with swap full did not recur for frontend-dev: an environment suspicion, not confirmed.
- Second test pass done and committed (585b8e0): no real bug found. By stage 6 of `docs/process.md`, this counts toward removing the pass.
- State: tasks 1–5 and the second pass committed (585b8e0); the requirement edits, the test changes and the fix round uncommitted.
- To read: this plan; `docs/process.md` stage 7.
- Documents changed since 3aa1010: `docs/requirements/tracks.md` (TRK-9 "as a list" and the empty-list sentence, TRK-10 restore after a confirmation, TRK-10.1 GIVEN covers the candidates); `docs/ui/guidelines.md` (cards for a short unpaged list); this plan.
- Next: one fix round for the accepted findings, a sonnet re-review if substantial, `verifier` scope `all`. At stage 8: the 0005 line, the roadmap `/change` for the tracks a user coordinates, the backend guide line on `tracks` using `JdbcClient` with its real reason (`backend.md:62`, decision 0006 line 5 also say "`users` and `audit` use `JdbcClient`"); delete `.screens/tracks/`. After merge: the manual-tester freshness chore, the schema checks chore. Issue #43 is open.

## Agents

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| reviewer | opus | a73d863de1142f2c9 | spec | 54k |
| backend-dev | default | a9760ec3671a0972e | plan (task 1) | 148k |
| backend-dev | default | a0fa00220a058cc0b | plan (task 1 rework) | 37k |
| frontend-dev | default | ab71c1c6c92c7ce3d | plan (task 1a), implementation (task 5 and fixes) | 193k: start a fresh one for further fixes |
| manual-tester | default | a40532779b5fa3157 | implementation (task 5 screens) | 81k |
| manual-tester | haiku | a2a66b3cccf3b51cc | implementation (task 5 checks) | 80k |
| manual-tester | default | a60174a15feebc478 | implementation (task 5 re-check) | 78k |
| manual-tester | default | a6b42d0f11316a973 | implementation (task 5 second re-check) | 63k |
| manual-tester | default | a0b09dd804274b1d3 | implementation (task 5 third re-check) | 60k |
| frontend-dev | default | af19b18eb34bd1b11 | implementation (task 5 fourth batch, Restore confirmation) | 78k |
| manual-tester | haiku | a4e58b0d0551af52d | implementation (Restore confirmation) | 36k |
| manual-tester | default | ad51f8999e5e2c4eb | implementation (task 5 fourth re-check) | 68k |
| test-engineer | default | a6d80bc78d3ea7bfa | tests (task 2) | 167k |
| backend-dev | default | ae4c4905297d33581 | implementation (task 3) | 161k |
| test-engineer | default | a8aaaddae23fa07ea | implementation (task 4a, test changes after manual tests) | 46k |
| backend-dev | default | a90570c455e167010 | implementation (task 4) | 103k |
| test-engineer | default | ac9fec355bae6fea7 | implementation (second test pass) | 172k |
| reviewer | default | a68d02650eae58d2d | review | 202k |
| test-engineer | default | abba0993f18895cfd | review (tests for findings 1, 2, 3, 7a; lockWaiters) | 98k |
| backend-dev | default | ab4244f789e8de985 | review (findings 1, 4) | 57k |
| frontend-dev | default | a6d64e6b22f087e6c | review (findings 5, 6) | 50k |
| reviewer | sonnet | af93510afb8933951 | review (re-review) | 43k |
| verifier | default | a8a6884462f5b28b6 | review (verify all) | 21k |
