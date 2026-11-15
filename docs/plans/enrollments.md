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
| 3. The `users` and `tracks` public API and history mapping, the archiving check, the `enrollments` module (checkpoint: backend green) | ENR-1 to ENR-6, TRK-5, PERM-1 | backend-dev | done |
| 4. Sample center enrollments | (screens of ENR-7, ENR-8) | backend-dev | in progress |
| 5. Enrollments on the track page, the enroll dialog, My enrollment and its link, the history sentences | ENR-7, ENR-7.1, ENR-8, TRK-10, TRK-10.1 | frontend-dev | in progress |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

<!-- Written by an agent stopped by the context budget, under a heading with its task; a fresh agent continues from it. Deleted when the task is done. -->

## Notes for later tasks

<!-- From the developers' reports ("Notes for the next task"): what a fresh agent must know and cannot see in the plan or the diff. One line each, with the task it comes from. -->
- From the spec: the client needs to know whether the current user has an enrollment, to show the My enrollment link (ENR-8); how the contract tells it is designed at the plan stage.
- From task 3, for task 4: an `enrollments` row has `status` defaulting to `ACTIVE` and a required `enrolled_by`. Each enrollment needs both audit entries, with the details as the code writes them: `TRACK_STUDENT_ENROLLED`, subject `TRACK`: `{"userId": "<uuid>", "startDate": "YYYY-MM-DD"}`; `USER_ENROLLED`, subject `USER`: `{"track": {"id", "code", "name"}, "startDate": "YYYY-MM-DD"}`.
- From tasks 1 and 3: a new optional property of a history entry must be `NON_NULL` in its `*JsonMixin`, or every existing history response fails contract validation.
- From task 3: `@RequirePermission` takes several permissions as any-of; a relation is checked in the service with `AccessDeniedException` (403). `CoordinatedTrack` is renamed `HistoryTrack` (the rename is staged by `git mv`). The new pages of `users` sort in SQL by name, email, id.
- From task 3, for stage 8: the developer proposes widening one line of the backend guide's Security section: "`@RequirePermission` takes several permissions as any-of; a permission that needs a relation is checked in the service, with `AccessDeniedException` (403)." Its four answers: (1) mentors or cohorts, any operation open to a "manage all" or a "manage related" permission; (2) a second annotation on one method, or the relation checked as a 404; (3) the annotation's Javadoc says it, but the guide line still shows a single permission; (4) yes, task 1 left a temporary `ENROLLMENTS_MANAGE` because of it. The owner agreed on 2026-11-16; the guide is edited at stage 8.
- From task 2, for task 5: the frontend tests assume, beyond the plan: the start date is a text-settable input labelled "Start date" and the search field is labelled "Student"; a candidate is an element with `role=option`, an `li` or a `button` holding the name; `enrollmentsPage` is a zero-based index; the "My enrollment" link is in the navigation or the account menu; the snackbar of `track-archived` matches `/archived/i` and that of `track-has-active-enrollments` `/enrol/i`; "today" in the dialog is the local date. A different but reasonable control is agreed with `test-engineer`, not bent in the code. `trackAdmin` in the tracks test data holds `ENROLLMENTS_READ` and `ENROLLMENTS_MANAGE`.
- From tasks 2 and 3, for the second pass: the order of the checks (400 before 404, the length of `q` included) and the paging of the enrollments and the candidates are not tested yet.

## Red tests

Commit: 8315941

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
- 2026-11-16: to committing the requirements, the decision, the contract, the schema, the stubs, the plan, the red tests and the usage record together: "коммить".
- 2026-11-16: at the checkpoint of task 3, to the backend, the developer's details, the guide line about any-of permissions, and the commit: "бэкенд одобряю, со строкой в гайде согласен, коммить".

## Handover

<!-- Overwritten before every stop for the user, not appended to: it holds the state now. The results of earlier rounds leave it; what lasts is in Decisions, the notes or the commits. A new session reads the plan and what this names, and nothing else, so keep it short and exact. -->

- Waiting for: the reports of `backend-dev` (task 4) and `frontend-dev` (task 5), running in the session of 2026-11-16. A new session starts fresh agents for whichever task is not done, from `git status` and this plan.
- To read: nothing yet
- Documents changed: none since the commit 8315941.
- State: the backend of task 3 is committed. Tasks 4 and 5 are in progress.
- Open questions: none
- On approval: `/feature enrollments`
- On remarks: `/feature enrollments`

## Agents

<!-- Agents can be continued only within the session that started them; a new session starts fresh ones from this plan. Context is the size from the agent's last completion notification. Model is the `model` parameter of the call, or else the `model:` line of the agent's definition. -->

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| reviewer | from its definition | a3b0b9ab2c6d17d9f | spec, requirements review (2026-11-16) | unknown |
| backend-dev | from its definition | a512e079e4dfb722a | plan, task 1 | 120k |
| frontend-dev | from its definition | af7c3b59a46a2885d | plan, task 1a | 48k |
| test-engineer | from its definition | ab592dbb0dfe5bb8f | plan, task 2 (first pass) | 204k |
| backend-dev | from its definition | a869a76c011aee026 | implementation, task 3 | 177k |
