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
| 4. Sample center tracks | (screens of TRK-9, TRK-10, USR-11) | backend-dev | pending |
| 5. Navigation, tracks page, track page, user history sentence | TRK-8, TRK-9, TRK-10, TRK-10.1, USR-11 | frontend-dev | pending |

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

## Handover

- Waiting for: the owner's go for the next step: test-engineer removes the 501 allowance in `OpenApiValidation`, then task 4 (backend-dev, sample center).
- To read: this plan; Notes for later tasks.
- Documents changed since the red commit: this plan only.
- State: tasks 1, 1a, 2 and 3 done; task 3 committed after 3aa1010 (see `git log`). Backend verification PASS.
- Pending: the owner reads the changed TRK-5 sentence and TRK-5.2 WHEN line.
- Next: the 501 allowance (test-engineer, fresh: the task 2 agent was at 167k), task 4 (backend-dev, fresh: the task 3 agent is at 161k), then task 5 (frontend-dev). At stage 8: the 0005 line, the roadmap `/change`, the backend guide line on `tracks` using `JdbcClient` with its real reason (Decisions, Notes).

## Agents

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| reviewer | opus | a73d863de1142f2c9 | spec | 54k |
| backend-dev | default | a9760ec3671a0972e | plan (task 1) | 148k |
| backend-dev | default | a0fa00220a058cc0b | plan (task 1 rework) | 37k |
| frontend-dev | default | ab71c1c6c92c7ce3d | plan (task 1a) | 51k |
| test-engineer | default | a6d80bc78d3ea7bfa | tests (task 2) | 167k |
| backend-dev | default | ae4c4905297d33581 | implementation (task 3) | 161k |
