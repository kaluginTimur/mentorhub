# Enrollments

Branch: `feature/enrollments`
Roadmap item: Next 1, **Enrollments:** a coordinator enrolls a student on their track; the student sees their own enrollment, and the coordinator those of the track.
Requirements approved: 2026-11-16 "требования одобряю"
<!-- Only the user's explicit approval of the requirements as they stand: replace "none" with <YYYY-MM-DD> "<their words>". -->

## Scope

**Goal.** A coordinator needs to record that a student studies on their track, and the student needs to see their own enrollment. Without it the system has no link between a student and a track, on which the rest of the tracking core stands (mentors, cohorts, the end of an enrollment, the access lifecycle). It is also the first access decided by a relation, not by a role alone (decision 0004).

**Approach.** One vertical slice:

- a coordinator enrolls a student on a track they coordinate, and sees the enrollments of that track;
- a student sees only their own enrollment;
- a student has one active enrollment at a time;
- the changes are audited.

**Out:** mentors, cohorts, the end of an enrollment. Not chosen: introducing the relation check on the existing tracks first, as a change of its own.

**Size.** Not split: one new module, no new infrastructure. If the requirements come to noticeably more than 8 to 10 scenarios, the split is proposed during the specification.

## Requirements

- New capability `docs/requirements/enrollments.md` (ENR-1 to ENR-8, 17 scenarios), added to the index.
- TRK: TRK-5 refuses archiving a track with an active enrollment (new TRK-5.5, TRK-5.6); TRK-1.1 and TRK-1.2 change their examples, since a coordinator now holds `TRACKS_READ`; TRK-10 shows the enrollments and TRK-10.1 explains the new refusal.
- PERM-1: the coordinator gains `TRACKS_READ`, `ENROLLMENTS_READ`, `ENROLLMENTS_MANAGE_COORDINATED`; the administrator `ENROLLMENTS_READ`, `ENROLLMENTS_MANAGE`.
- AUD-7 gains `TRACK_STUDENT_ENROLLED` and `USER_ENROLLED`.
- Decision 0004, clarified: 403 for an action a relation does not allow on a readable resource; coordinators and administrators read all students by a permission.

## Design

Proposed by the coordinator and approved by the owner at the plan gate on 2026-11-16.

**Module.** A new module `com.mentorhub.enrollments` (decision 0001), persistence with `JdbcClient`, because its rules rest on a row lock and an `on conflict do nothing` insert. It depends on `tracks`, `users` and `audit` through their public API only. Neither `tracks` nor `users` depends on `enrollments`:

- Archiving must know whether a track has an active enrollment (TRK-5). `tracks` declares an interface in its public API (for example `ActiveEnrollments`, one method: does the track have an active enrollment), and `enrollments` implements it. `tracks` asks it after locking the track row and throws its own problem `track-has-active-enrollments`.
- The client must know whether the current user has an enrollment (ENR-8). `/api/v1/me` is not changed: the shell reads `listMyEnrollments` once and shows the link when the answer is not empty.

**What `tracks` adds to its public API:**

- Locking a track for an enrollment: the same `for no key update` row lock every change of the track takes, returning the track (id, code, name, status) or nothing for an unknown id. So enrolling and archiving run one after another (TRK-5.6).
- Whether a user is a coordinator of a track now.
- Recording `TRACK_STUDENT_ENROLLED` about the track (details: `userId`, `startDate`), joining the caller's transaction. `tracks` owns the action, so its history view maps it (decision 0005), as `users` does for the coordinator entries.
- The coordinators of a track as user ids, for a student's own enrollment (ENR-5).
- `Track.canEnroll` in the contract is computed by `tracks` from the caller's permissions and its own coordinators (ENR-1): `ENROLLMENTS_MANAGE`, or `ENROLLMENTS_MANAGE_COORDINATED` and a coordinator of the track; the status does not matter.

**What `users` adds to its public API**, so `enrollments` never reads the `users` tables:

- `Permission` gains `ENROLLMENTS_READ`, `ENROLLMENTS_MANAGE`, `ENROLLMENTS_MANAGE_COORDINATED`; `Role` maps them and gives the coordinator `TRACKS_READ` (PERM-1).
- An operation that needs one of two permissions (candidates, enrolling): if `@RequirePermission` takes a single permission today, it is widened to any-of, and `PermissionDeclarationTests` with it. The relation is then checked in the service, which refuses with `AccessDeniedException` (403).
- An eligibility check of one id: an active user with the student role (unknown id = not eligible).
- A page of candidates: the active users with the student role, without a given set of ids (the students with an active enrollment, passed by `enrollments`), searched by a part of the name or the email with the rules of `listUsers`, ordered by name then email. The set is bounded by the size of one center, so it is passed as an array.
- A page of users among given ids, ordered by name then email, each with id, name, email, whether disabled, whether they hold the student role: the enrollments of a track (ENR-4).
- Contacts by ids: name, email, phone, whether active and holding the coordinator role (ENR-5).
- Recording `USER_ENROLLED` about the student (details: `track: {id, code, name}` as given, `startDate`), joining the caller's transaction.

**Schema.** `enrollments`: `id` uuid, `student_id` → `users`, `track_id` → `tracks`, `start_date` date, `status` text checked `ACTIVE`, `enrolled_by` → `users`, `created_at`. A unique index on `student_id` where `status = 'ACTIVE'` settles parallel enrollments (ENR-2.6); an index on `track_id`. In `V1__schema.sql`, by the owner's decision below.

**Enrolling** (400 for validity first, before the service where the contract can; then one transaction): lock the track through `tracks`; unknown is 404; the relation, 403; `ARCHIVED` is 409 `track-archived`; eligibility through `users`, 409 `student-not-eligible`; insert `on conflict do nothing` on the unique index, nothing inserted is 409 `student-already-enrolled`; the two audit entries. The candidates answer 404, 403 and 409 `track-archived` the same way, without the lock.

**Contract** (tag `Enrollments`; problems in an `EnrollmentProblems` class of the module, `track-has-active-enrollments` in `TrackProblems`):

| Operation | Call | Permission | Answers |
|---|---|---|---|
| `listTrackEnrollments` | `GET /api/v1/tracks/{id}/enrollments?page&size` | `ENROLLMENTS_READ` | 200 `TrackEnrollmentPage`, 400, 404 |
| `listEnrollmentCandidates` | `GET /api/v1/tracks/{id}/enrollment-candidates?q&page&size` | `ENROLLMENTS_MANAGE`, or `ENROLLMENTS_MANAGE_COORDINATED` and the relation | 200 `EnrollmentCandidatePage` (id, name, email), 400, 403, 404, 409 `track-archived` |
| `enrollStudent` | `POST /api/v1/tracks/{id}/enrollments` `EnrollmentCreate` `{studentId, startDate}` | the same | 201 `TrackEnrollment`, 400, 403, 404, 409 `track-archived`, `student-not-eligible`, `student-already-enrolled` |
| `listMyEnrollments` | `GET /api/v1/me/enrollments` | none, only active | 200 `MyEnrollmentList` `{items}` |
| `archiveTrack` (changed) | | | gains 409 `track-has-active-enrollments` |

- `TrackEnrollment`: `id`, `student` (`id`, `name`, `email`, `disabled`, `studentRole`; the marks of ENR-4 are `disabled: true` and `studentRole: false`), `startDate` (date), `status` (`EnrollmentStatus`, only `ACTIVE` now). Who enrolled is stored and shown only in the history.
- `MyEnrollment`: `id`, `track` (`TrackReference`), `startDate`, `status`, `coordinators`: `name`, `email`, `phone` (string or null), ordered by name.
- `Track` gains the required `canEnroll` (boolean).
- `TrackHistoryAction` gains `TRACK_STUDENT_ENROLLED`; its entry has `user` (the student, resolved at read time) and `startDate`. `HistoryAction` gains `USER_ENROLLED`; its entry has `track` (`TrackReference`, as stored) and `startDate`.
- `Permission` gains the three permissions.

**Frontend.** A feature folder `frontend/src/app/features/enrollments/`:

- `TrackEnrollments`, a component the track page embeds for a user with `ENROLLMENTS_READ`, with the inputs `trackId`, `canEnroll` and `trackStatus`: the paged list, the marks, the empty state, and "Enroll student" when `canEnroll` and the track is active. Its page lives in the query parameter `enrollmentsPage`.
- `EnrollStudentDialog`: a search of the candidates, a choice of one, and a start date, today by default. A 409 is explained in a snackbar and the track and the list are reloaded (ENR-7.1).
- `MyEnrollmentPage` on the route `my-enrollment`, open to every active user, and a "My enrollment" link in the shell when `listMyEnrollments` returns an enrollment.
- The history sentences of the track page and the user page gain the two new actions. The tracks route and link need `TRACKS_READ`, which a coordinator now holds, so nothing changes there.

**Sample center** (`db/sample/enrollments.sql`, after `tracks.sql` in `SampleCenter.SAMPLE_DATA`): a track with more than one page of enrollments; a track with a few, among them a student since disabled and one whose student role was removed; an active track with none; the Student persona enrolled on a track with two coordinators, one of them disabled, and with a phone on one; a student enrolled on a track with no coordinator to contact; students with the role and without an enrollment as candidates, enough to search; the Coordinator persona coordinating one track with enrollments and not another; the two audit entries of every enrollment. No archived track has an enrollment.

## What the tests rely on

- Backend: the contract above and its generated `EnrollmentsApi`; the controller `EnrollmentsController` in `com.mentorhub.enrollments.internal.web`, stubbed with 501 until task 3; `archiveTrack` unchanged until task 3; problem types `student-not-eligible`, `student-already-enrolled` and `track-has-active-enrollments` with the URN scheme of the existing problems.
- Frontend: components `TrackEnrollments` (`features/enrollments/track-enrollments.ts`, inputs `trackId`, `canEnroll`, `trackStatus`), `EnrollStudentDialog` (`features/enrollments/enroll-student-dialog.ts`) and `MyEnrollmentPage` (`features/enrollments/my-enrollment-page.ts`), the route `/my-enrollment`, the generated API functions named by the operation ids above, test data in `features/enrollments/test-data.ts`. Tests find elements by role and visible text, as the tracks specs do: the button "Enroll student"; in the dialog a search field labelled "Student", a date field labelled "Start date" and the button "Enroll"; the marks read "Disabled" and "No student role"; the empty states read "No students enrolled yet", "You are not enrolled yet" and "This track has no coordinator to contact"; the shell link reads "My enrollment"; a 409 is explained in a snackbar.

## Tasks

| Task | Scenarios | Who | Status |
|---|---|---|---|
| 1. Contract, schema in `V1__schema.sql`, the three permissions and the coordinator's `TRACKS_READ`, `EnrollmentsController` stub (501), build green | all (shape) | backend-dev | done |
| 1a. Frontend compiles with the new contract: the new history actions skipped as unknown, `canEnroll` in the tracks test data | (none) | frontend-dev | done |
| 2. Red tests, backend and frontend; the existing tests of the changed TRK-1.1, TRK-1.2, TRK-10.1 and PERM-1.1 follow the requirements | ENR-1.1 to ENR-7.1, TRK-1.1, TRK-1.2, TRK-5.5, TRK-5.6, TRK-10.1, PERM-1.1 | test-engineer | done |
| 3. The `users` and `tracks` public API and history mapping, the archiving check, the `enrollments` module (checkpoint: backend green) | ENR-1 to ENR-6, TRK-5, PERM-1 | backend-dev | pending |
| 4. Sample center enrollments | (screens of ENR-7, ENR-8) | backend-dev | pending |
| 5. Enrollments on the track page, the enroll dialog, My enrollment and its link, the history sentences | ENR-7, ENR-7.1, ENR-8, TRK-10, TRK-10.1 | frontend-dev | pending |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

<!-- Written by an agent stopped by the context budget, under a heading with its task; a fresh agent continues from it. Deleted when the task is done. -->

## Notes for later tasks

<!-- From the developers' reports ("Notes for the next task"): what a fresh agent must know and cannot see in the plan or the diff. One line each, with the task it comes from. -->
- From the spec: the client needs to know whether the current user has an enrollment, to show the My enrollment link (ENR-8); how the contract tells it is designed at the plan stage.
- From task 1: to replace in task 3: every method of `EnrollmentsController` (501); the temporary `ENROLLMENTS_MANAGE` on `enrollStudent` and `listEnrollmentCandidates`, until `@RequirePermission` takes either of two permissions; `canEnroll = false` in `TracksMapper.track`; the empty `StudentEnrolled` case in `TracksMapper` and the empty `Enrolled` case in `UsersMapper`; `TrackService.coordinator()`, which must also return the student of `StudentEnrolled` so that the name is resolved.
- From task 1: a new optional property of a history entry must be `NON_NULL` in its `*JsonMixin`, or every existing history response fails contract validation.
- From task 1: `UserChange.Enrolled` reuses the public `CoordinatedTrack` as its track type; task 3 may give it a neutral name.
- From task 2, for task 3: ENR-1.2 and ENR-1.3 expect 403 from the relation check; today the stub's temporary `ENROLLMENTS_MANAGE` gives that 403, and only their control calls fail. Ten backend tests and the two concurrency tests fail on the response validator (501 is not in the contract), before their own assertions.
- From task 2, for task 5: the frontend tests assume, beyond the plan: the start date is a text-settable input labelled "Start date" and the search field is labelled "Student"; a candidate is an element with `role=option`, an `li` or a `button` holding the name; `enrollmentsPage` is a zero-based index; the "My enrollment" link is in the navigation or the account menu; the snackbar of `track-archived` matches `/archived/i` and that of `track-has-active-enrollments` `/enrol/i`; "today" in the dialog is the local date. A different but reasonable control is agreed with `test-engineer`, not bent in the code. `trackAdmin` in the tracks test data holds `ENROLLMENTS_READ` and `ENROLLMENTS_MANAGE`.
- From task 2, for the second pass: the order of the checks (400 before 404) and the paging of the enrollments and the candidates are not tested yet.

## Red tests

Commit: <the commit holding the requirements, the contract, this plan and the red tests>

## Decisions

<!-- One line per decision of the user, in their words. What is the agent's own stays out of this list. -->

- 2026-11-15: at the path gate, to the goal, the approach (one vertical slice, as above) and the path (the stages, `feature/enrollments`): "подтверждаю".
- 2026-11-15: a coordinator sees the other tracks, and may neither change them nor enroll on them: "я не особо вижу смысл скрывать от координатора инфу по другим трекам. менять другие треки и зачислять на них - да, лучше запретить."
- 2026-11-15: a track with active enrollments should not be archived, proposed by the user and put to the designer as a question: "я не уверен, что трек стоит разрешать архивировать с активными зачислениями в нём... если хочется заархивировать трек, то лучше раскидать его студентов по другим трекам, а потом уже архивировать".
- 2026-11-15: "с остальным я согласен", to the designer's recommendations: an administrator enrolls students and sees the enrollments of any track; only an active user who already holds the student role can be enrolled; an enrollment made by mistake cannot be cancelled in this slice; a coordinator who is disabled, lost the role or was removed from the track loses access at once.
- 2026-11-15: every coordinator sees the students of every track: "да, я имел ввиду студентов тоже. дело в том, что мы собесим студентов всеми направлениями, а потом разбираем понравившихся. большого смысла скрывать студентов от других координаторов в системе я не вижу... да даже от менторов, возможно, не стоит многого скрывать, но это другой вопрос."
- 2026-11-15: "с датой начала и аудитом согласен": a required start date given by whoever enrolls, today by default; an enrollment is audited in the track's and the student's history.
- 2026-11-16: to the 12 findings of the requirements review, each with the designer's recommendation (among them: any start date, past or future, the enrollment active from when it is recorded; the candidates paged with a search by name or email; a user may be enrolled on a track they coordinate): "1-12 согласен."
- 2026-11-16: to accepting, for this slice, that a track with any enrollment cannot be archived and a mistaken enrollment cannot be undone, and to keeping one change of about 19 scenarios: "1, 3 - согласен".
- 2026-11-16: to the details the designer chose (only the active coordinators shown to a student; enrollments ordered by the student's name and paged; marks in the list; no page of one enrollment): "пусть телефон тоже будет виден студенту, с остальным согласен."
- 2026-11-16: to clarifying decision 0004 in two sentences (403 for an action a relation does not allow on a readable resource; coordinators and administrators read the students of every track by a permission): "вариант 1, уточняй 0004".
- 2026-11-16: no permission for reading one's own enrollments: any active user reads their own, and the client shows the My enrollment link to a user who has one. The user asked "а почему у нас отдельный пермишен `ENROLLMENTS_READ_OWN`, а не просто поведение по-умолчанию, что студент может открыть собственный енроллмент?", then: "убираем право".
- 2026-11-16: the check order of enrolling is permission, validity (400), the track (404), the relation (403); ENR-1, ENR-1.2, ENR-1.4 and ENR-2 changed after the approval: "сделай так, как везде. если 400 везде первым, то тут тоже".
- 2026-11-16: the schema of enrollments goes into `V1__schema.sql`: "всё идёт в v1".
- 2026-11-16: the owner appended the schema of enrollments to `V1__schema.sql` themselves, after a hook refused the agent's edit, and read the changed requirements: "я добавил sql в v1. схему посмотрел, требования тоже. можем продолжать".
- 2026-11-16: at the plan gate, to the plan, the contract and the schema: "план одобряю".

## Handover

<!-- Overwritten before every stop for the user, not appended to: it holds the state now. The results of earlier rounds leave it; what lasts is in Decisions, the notes or the commits. A new session reads the plan and what this names, and nothing else, so keep it short and exact. -->

- Waiting for: the user's word on the red-test commit (the requirements, the decision, the contract, the schema, the stubs, the plan, the tests and the usage record together).
- To read: nothing new is required; the tests are in `backend/src/test/java/com/mentorhub/enrollments/` and `frontend/src/app/features/enrollments/*.spec.ts`
- Documents changed (uncommitted; nothing new since the requirements gate, `node scripts/requirements.mjs validate` passes):
  - `docs/requirements/enrollments.md`: added, the capability ENR (ENR-1 to ENR-8, 17 scenarios)
  - `docs/requirements/tracks.md`: changed: the purpose; the examples of TRK-1.1 and TRK-1.2; TRK-5 refuses archiving a track with an active enrollment, new TRK-5.5 and TRK-5.6; TRK-6, TRK-10 and TRK-10.1 cite enrollments; limitations and out of scope
  - `docs/requirements/authorization.md`: changed: PERM-1 gives the coordinator `TRACKS_READ`, `ENROLLMENTS_READ`, `ENROLLMENTS_MANAGE_COORDINATED`, the administrator `ENROLLMENTS_READ`, `ENROLLMENTS_MANAGE`; limitations and out of scope
  - `docs/requirements/audit.md`: changed: `TRACK_STUDENT_ENROLLED` and `USER_ENROLLED` in AUD-7; the purpose and out of scope
  - `docs/requirements/README.md`: changed: the row of Enrollments
  - `api/openapi.yaml`: changed, new since the last stop: the tag `Enrollments` with `listMyEnrollments`, `listTrackEnrollments`, `enrollStudent`, `listEnrollmentCandidates` and their schemas; `Track.canEnroll`; 409 `track-has-active-enrollments` on `archiveTrack`; three permissions; `TRACK_STUDENT_ENROLLED` and `USER_ENROLLED` with `startDate` in the histories
  - `docs/requirements/enrollments.md`: new since the last stop: the check order in ENR-1, ENR-1.2, ENR-1.4, ENR-2
  - `backend/src/main/resources/db/migration/V1__schema.sql`: changed, new since the last stop: the table `enrollments` with the unique index of one active enrollment per student and the index on the track, appended by the owner
  - `docs/architecture/decisions/0004-permissions-in-code-role-assignments-in-database.md`: changed: 403 for an action a relation does not allow on a resource the caller may read; coordinators and administrators read the students of every track by a permission
- State: tasks 1, 1a and 2 are done. Every new or changed scenario has a test; `node scripts/requirements.mjs trace` passes (245 of 245, 4 manual). Red as the test engineer reports: backend 26 fail, all new; frontend 22 fail, 340 pass. The five tests broken by PERM-1 pass again, with only the permission lists changed. Nothing is committed.
- Open questions: none
- On approval: `/feature enrollments`: commit, write the commit into Red tests, then brief `backend-dev` with task 3 (a fresh agent; the checkpoint is the green backend)
- On remarks: `/feature enrollments`, relay to `test-engineer` (a fresh one in a new session)

## Agents

<!-- Agents can be continued only within the session that started them; a new session starts fresh ones from this plan. Context is the size from the agent's last completion notification. Model is the `model` parameter of the call, or else the `model:` line of the agent's definition. -->

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| reviewer | from its definition | a3b0b9ab2c6d17d9f | spec, requirements review (2026-11-16) | unknown |
| backend-dev | from its definition | a512e079e4dfb722a | plan, task 1 | 120k |
| frontend-dev | from its definition | af7c3b59a46a2885d | plan, task 1a | 48k |
| test-engineer | from its definition | ab592dbb0dfe5bb8f | plan, task 2 (first pass) | 204k |
