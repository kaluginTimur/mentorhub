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
| 4. Sample center enrollments | (screens of ENR-7, ENR-8) | backend-dev | done |
| 5. Enrollments on the track page, the enroll dialog, My enrollment and its link, the history sentences | ENR-7, ENR-7.1, ENR-8, TRK-10, TRK-10.1 | frontend-dev | done |

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
- From task 4: sample enrollment ids use the prefix `9000`, their audit entries `9100` and `9200`; a later module's script needs another free prefix. The commit c95d4e7 holds an unformatted import in `TrackService.java`; its fix is in the working tree.
- From task 4, the personas for the screens: Cora (`coordinator@mentorhub.local`) coordinates Software Engineering (27 enrollments) and reads Quality Assurance (5, with both marks); Milo (`mentor.coordinator@mentorhub.local`) coordinates DevOps (none); Ada (`admin@mentorhub.local`); Sofia (`student@mentorhub.local`) is enrolled on Quality Assurance. A user without roles (Nora) is stopped by the access screen before `/my-enrollment`; "You are not enrolled yet" shows to a user with roles and no enrollment, such as Ada by the address. "This track has no coordinator to contact" is in the data (Grace Morgan, QA Automation) and no persona sees it.
- From the fixes round of task 5: use `<app-email [value]>` (`shared/email/email.ts`) for every email shown; the calendar's English is set inside the shared calendar primitive (`shared/date/calendar-i18n.ts`), and `@spartan-ng/brain/calendar` must not be imported in `app.config.ts` (about 163 kB more in the initial bundle); in the enroll dialog the date field sits in a `relative` wrapper inside the "Start date" block, to keep when the layout changes for remark 8.
- From the owner's look, for after this change (through `/change`, by the owner's word): roadmap items for bulk enrollment, search and sorting in the enrollments of a track, and My tracks for a coordinator (a "My track" pill, a filter all / mine / not mine, own tracks first, a quick action on the home page, the list in the profile); a chore for a page size chosen by the user over all lists (`docs/ui/guidelines.md` says "a paginator without a page-size selector").
- From the follow-up round of task 5: the Student field is the UI library's autocomplete (`shared/ui/autocomplete`, which needs the generated `input-group` and `textarea` beside it); its value and its search text are separate, the library writes the chosen name into the input itself, and typing after a choice is handled in `onSearch` of the dialog, so the input's text is not bound a second time. The component generator creates `frontend/.nx/`, to delete after it runs. The initial bundle is 541 kB of the 550 kB budget.
- From the manual tester's run 20261116-2329: the owner's dev database holds one enrollment made by the run (James Porter on Software Engineering, 16 Nov 2026) and an Archived and a Restored entry in the history of DevOps Engineering; a `dev` start resets the sample data.
- From the additions to task 4: the long sample emails for checking the wrapping are Mia Foster's, `mia.charlotte.foster@students.northbridge-training-academy.example.com` (a student on Quality Assurance, read by Cora: users list page 1, her user page, the Enrollments card) and Irene Walsh's, `irene.margaret.walsh@qa-faculty.northbridge-training-academy.example.com` (the only active coordinator of Quality Assurance: Sofia's My enrollment, the track's coordinators, the users list, the track histories). They reach the dev database on the next `dev` start. The account menu has no long email, since the personas keep theirs.
- From the third fixes round of task 5: the enroll dialog zooms in for 100 ms, so anything positioned against its elements on open waits for its animations (`getAnimations`); the loading bars of the cards are overlaid (`absolute -top-2`) in the card's gap, not a slot of a fixed height inside `space-y-*`; `word-break` on a table cell overrides the break points of `<app-email>`.
- From the last round of task 5: the panel of candidates opens by `(click)` on the autocomplete input and by `panelState`; the library closes it itself and reports `stateChanged`. `track-enrollments.spec.ts` opens the list through its helper `openStudentList()` (a click on the field labelled "Student").
- From the calendar round of task 5: the month and year grids are the project's own code in the generated `shared/ui/calendar/src/lib/hlm-calendar.ts` (the header buttons, the `_view` state, the two grids); regenerating the calendar with the CLI loses them, so they are re-applied after it.
- From the calendar polish round of task 5: the dark theme's `--accent` in `styles/tailwind.css` went from `#1a3154` to `#294a7a`, which also strengthens the hover of the account menu, the dropdown menus, the select items and the candidates panel; `--sidebar-accent` is unchanged. The calendar's hovers use `accent` and are the project's own classes in `hlm-calendar.ts`, to re-apply with the grids after a regeneration. The library's `BrnMonthYearCalendar` was not used: a fixed page of 12 years with no range, labels outside the calendar's i18n, no Escape handling, a state of its own. A full frontend test run timed out in 8 tests once under machine load and passed on the rerun.
- From the paging round of task 5: the other paged lists get the scroll by `appScrollIntoViewOnChange` (`shared/scroll/`) on their container, with `scroll-mt-20` and the page index as the value; this joins the page-size chore after this change, with the rule in `docs/ui/guidelines.md`.
- From the manual tester's run 20261117-0044, outside this change: the users search "mia foster" finds nobody while "foster" does; the contract of `listUsers` says a substring of the email, the first name or the last name, so it is as specified.
- From the fourth fixes round of task 5: a `[class]` binding on an `hlmBtn` element is silently dropped (the library's `classes()` in `shared/ui/utils/src/lib/hlm.ts` keeps the static `class` and the variant only), so the buttons of the month and year grids are plain buttons with a full class string from `_gridClass` in `hlm-calendar.ts`; before this round the grids never had the accent hover or any own class. Escape of a grid is a host listener of `HlmCalendar` (the lint rule `interactive-supports-focus` rejects a keydown on a plain `div`). Both are the project's own code, to re-apply after a regeneration of the calendar. The line under the Student field reads "Chosen: <email>", without the name.
- From the manual tester's runs 20261117-0104 and 20261117-0112b: no candidate of the sample center has a long email (Mia Foster is enrolled), so the chosen line wrapping in the dialog cannot be seen there.
- From the keyboard rounds of task 5 (the arrows in the grids): the grids' keyboard handling in `hlm-calendar.ts` (`_navigate`, the roving `tabindex` that follows the focused item through `focusin`, the focus on the displayed item when a grid opens) is the project's own code, to re-apply after a regeneration with the grids. An element cannot take the focus while it is `visibility: hidden`, the first frame of a visibility transition included, so `_show` waits one frame and the grid's animations (about 150 ms) before it focuses; the spec cannot catch this, since the test environment has no styles. The roving tab stop following the focus is the coordinator's addition, from an oddity the tester saw, not the owner's decision. Left as they are, shown to the owner and not chosen: the first arrow pressed within about 150 ms of opening a grid is lost; ArrowDown from 2026 in the year grid stays, since the short last row has nothing below it.
- From the keyboard rounds, for the Tab fix: Tab from the last tabbable element of the calendar popup (the days and both grids) goes to the page behind the modal dialog (the "MentorHub" link), after which the popup and the dialog close; the tester saw it in the browser. The cause is the developer's reading of the library code, not reproduced: the date picker's popover renders in its own overlay outside the dialog's DOM and has no focus trap. The Student field's panel is not affected, the focus stays in its input.
- From the keyboard rounds: Playwright with Chromium is in `frontend/node_modules/playwright`; a developer can see one shared component in a real browser by temporarily replacing `src/main.ts` with a bootstrap of it and running `ng serve`, restoring `src/main.ts` afterwards. In the run 20261117-arrows the manual tester's own start of the frontend exited with 127 once (the logs are in that run folder) and it used a stack already running; the next two runs started their own stack without trouble, so the cause was not looked for.
- From the Tab fix of task 5: `onCalendarTab` in `enroll-student-dialog.ts` closes the calendar popup and focuses the "Start date" field when Tab would leave the popup forward or Shift+Tab backward; it lives in the dialog because the date field has no other user, and a second date field with a popup needs the same or a shared wrapper. The popup's stops are computed at the keypress, without what is inside `[inert]` or `visibility: hidden`; the test environment has no `checkVisibility`, so the code reads `getComputedStyle(el).visibility`. In a spec, wait until `document.activeElement` is inside the popup before focusing one of its stops, since the popup's own autofocus races the test.
- From the second pass: it found a real bug (the page past the last one in the enrollments card, PLT-9.2), so the pass earned its place in this change. Pitfalls for later tests: `MockMvcTester.uri(String)` treats the string as a URI template and encodes it, so `?q=%` and spaces are written raw; a mentor holds no `TRACKS_READ`, so a `canEnroll` check as a mentor gives 403.
- From the manual tester's run 20261117-0205, seen and not dug into, shown to the owner with the recommendation to leave them: PageUp and PageDown from day 30 leave the focus on "30"; the calendar popup covers the right part of the Student field while it is open; the year grid of 8 years leaves an empty area in the popup of one height.
- From the second pass, for after this change (through `/change`, by the owner's word): the contract's `additionalProperties: false` is not enforced, a body with an unknown property is accepted (seen on `EnrollmentCreate`, probably every operation); to decide for all operations at once. A body that is not JSON answers 400 with the type `about:blank`, on purpose (`ProblemDetailsAdvice`), and stays.

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
- 2026-11-16: the enroll dialog lists the candidates as soon as it opens, without typing; the test of ENR-7.1 for `track-archived` on the candidates changes for it, by the test engineer: "пусть диалог показывает кандидатов сразу при открытии".
- 2026-11-16: of the manual tester's remarks, one goes to the developer: the dates in the history sentences read as in the enrollments list: "даты в истории сделай как в списке". The other remarks were not chosen and stay as they are.
- 2026-11-16: to the coordinator's recommendations on the other remarks and the developers' details: "остальное по рекомендации". So: the emails in the enrollment rows wrap at a phone width as in the coordinators card; "Choose a student" gets its spacing; the sample email of Emma Brooks is fixed; the email shown twice for a student without a name stays; no persona for "no coordinator to contact"; the developers' details of tasks 4 and 5 are accepted.
- 2026-11-16: to the 11 remarks from the owner's own look at the screens (listed with the coordinator's recommendations in the handover of that stop): "1, 6, 7, 9, 11 делаем, №8 - покажи макеты вариантов, остальное по рекомендации". So: the date picker, the History jerk, the warning look of the archived notice, the My enrollment cards side by side and one way of wrapping emails are fixed here; bulk enrollment (2), search and sorting in the enrollments (3), a page size chosen by the user (4) and My tracks (5) become their own changes after this one; the My enrollment page stays (10). For remark 8 the owner asked for HTML mockups: "нет, мне нужны html макеты".
- 2026-11-16: to the mockups of remark 8 and the open points of the fixes round: "№8 - вариант B, остальное по рекомендации". So: the candidates are a drop-down under the Student field (still requested and shown when the dialog opens, by the earlier decision); the calendar's week starts on Monday; the Enrollments card keeps a fixed slot for its loading bar; the history going back to its first page after Archive or Restore stays; the developer's details of the fixes round are accepted.
- 2026-11-16: to running the manual tester on the screens of the fixes rounds: "запускай manual-tester".
- 2026-11-16: to the manual tester's eight remarks with the coordinator's recommendations: "всё по рекомендации". So: fixed are the mid-word break of an email in the users list (1), the panel off the field on the first open (2), the dialog's size changing on a choice or an error (3), the focus after the clear button (4), the gap under the headings of the cards (7); a sample student gets a long email (8); the bold email as the row title of the invitations list (5) and the calendar popup over the dialog's title (6) stay.
- 2026-11-16: after looking at the dialog: "отрытый сразу выпадающий список студентов смотрится странно, сделай выпадайку по клику. плюс, если закрыть выпавший список и снова кликнуть по полю - список больше не откроется. всё остальное по рекомендации". So: the panel of candidates opens on a click in the Student field (or typing, or the down arrow), not when the dialog opens, which replaces the decision "пусть диалог показывает кандидатов сразу при открытии" in what is visible (the candidates are still requested on open, the coordinator's reading for ENR-7.1); a click on the field reopens a closed panel; the test engineer changes the two `md:break-all` assertions and the dialog's tests; Irene Walsh gets a long sample email; the developer's details of the third round are accepted.
- 2026-11-17: a quick choice of the month and the year in the calendar, as grids and not drop-downs, the years from 5 before to 2 after the current one, and the two cards of My enrollment of one height: "можно ли добавить календарю быстро выбирать месяц и год при нажатии на месяц и год? чтобы при нажатии на месяц открывался список месяцев и аналогично с годом", then "включаем, но стоит посмотреть, есть ли режим именно сетки выбора, а не выпадаек - по моим наблюдениям, сейчас делают именно сетки на таких календарях. диапазон по рекомендации, карточки выровнять".
- 2026-11-17: after looking at the calendar: "фон наведение на элементы календаря почти не виден в тёмной теме. при переключении на сетку месяца\\года размер календаря резко меняется, что выглядит некрасиво. анимации перехода при переключении на сетку тоже никакой нет - выглядит резко. используй context7 чтобы посмотреть, какие элементы библиотеки можно использовать". So: a visible hover in the dark theme, one size of the calendar in its three views, a transition between them; the library's month-year calendar was looked at again.
- 2026-11-17: to the calendar after its polish round: "календарь смотрится ок."
- 2026-11-17: to the paging remark and the coordinator's recommendation (scroll the heading of the Enrollments card into view on a change of page, as a shared piece connected here only to that card; the other paged lists and the guideline in the page-size chore; no animated resize): "по рекомендации, потом запускай manual-tester".
- 2026-11-17: to the five remarks of the manual tester's run 20261117-0044, each with the coordinator's recommendation: "давай всё по рекомендации". So: fixed are Escape in the month and year grids (1), the dialog growing at 400 px after a choice, by showing the email only (2), a mark of the current month and year in the grids (3); the heading ending lower after Previous (4) and the footer range changing before the rows (5) stay. The developer's details listed beside the remarks had no recommendation and are not covered by this answer.
- 2026-11-17: to the seven open points after the fourth fixes round, the first six with the coordinator's recommendation: "всё по рекомендации, проверки пропускаем". So: the mark of the current month and year in the light theme stays; choosing a year goes straight to the days; the arrows in the grids get one more look by the tester and a fix only if they do nothing at all (they did nothing, so they were fixed); the accent hover of the months and years is accepted; the developer's details of the fourth round and of the earlier rounds are accepted; the numbered manual checks are skipped.
- 2026-11-17: to the Tab key leaving the calendar popup for the page behind the dialog (recommended: close the popup on Tab and return the focus to the date field) and to committing tasks 4 and 5 with the plan and the usage record: "остальное по рекомендации и коммить".
- 2026-11-17: to the Tab fix as the developer reported it, with its details (the handler in the dialog, the focus on the date field itself), and to committing it before the manual tester's re-check came back: "всё по рекомендации, коммить".
- 2026-11-17: to starting the second pass once the manual tester's report on the Tab fix is clean: "если отчёт чистый, запускай test-engineer".
- 2026-11-17: to the four open points of the second pass with the coordinator's recommendations and to the commit: "всё по рекомендации и коммить". So: ENR-1 says that an invalid body may be answered with 400 before the permission is checked, as the known limitation of the platform allows, and the code stays; the unknown properties of a body become a change of their own after this one; the `about:blank` type of a body that is not JSON stays; the enrollments card no longer jumps from a page past the end to the first page; the second pass and the fix are committed. The owner has not yet read the new sentence of ENR-1 in the file; it was shown in the conversation after the commit.

## Handover

<!-- Overwritten before every stop for the user, not appended to: it holds the state now. The results of earlier rounds leave it; what lasts is in Decisions, the notes or the commits. A new session reads the plan and what this names, and nothing else, so keep it short and exact. -->

- Waiting for: the report of `reviewer` (in progress; base ref `main`). If no agent is running (a new session), run a fresh `reviewer` with the base ref `main` and this plan.
- To read: nothing beyond this plan.
- Documents changed: `docs/requirements/enrollments.md` since the commit 8315941: ENR-1 gains the exception for an invalid body (400 may come before the permission check), by the owner's decision of 2026-11-17; the owner reads the sentence itself after the commit.
- State: everything is committed: tasks 4 and 5 (0445cdc), the Tab fix (4f3917d), and in the commit after it (`test: enrollments details, and the page past the last one`) the second pass (`EnrollmentsDetailsTests.java`, `track-enrollments-details.spec.ts`), the fix of PLT-9.2 in `track-enrollments.ts` and the edit of ENR-1. As the agents report: `trace` passes; the backend verification passes; frontend format, lint, 384 tests and the build pass (542.31 kB of 550 kB). `verifier` has not run. The fix of PLT-9.2 was not looked at in a browser. Tests with a scenario id changed since the red commit, all by the test engineer for the owner's decisions: `track-enrollments.spec.ts`, `track-history-text.spec.ts`, `users/history-text.spec.ts`, `users-list.spec.ts`, `invitations-list.spec.ts`.
- Open points: none.
- Next: show the owner every finding of the review at once with a recommendation; the accepted fixes go in one batch to the agent of each area; a re-review on `sonnet` if the fixes are substantial; then `verifier` with scope `all` and the stop for acceptance. The reviewer is also asked to check the places `check-docs` named at the commit 0445cdc (line 7 of `docs/ui/guidelines.md`, lines 56 and 60 of `docs/development/frontend.md`, line 9 of decision 0007). At stage 8: the guide line about any-of permissions (agreed, Notes for later tasks) and the roadmap items the owner asked for after this change.

## Agents

<!-- Agents can be continued only within the session that started them; a new session starts fresh ones from this plan. Context is the size from the agent's last completion notification. Model is the `model` parameter of the call, or else the `model:` line of the agent's definition. -->

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| reviewer | from its definition | a3b0b9ab2c6d17d9f | spec, requirements review (2026-11-16) | unknown |
| backend-dev | from its definition | a512e079e4dfb722a | plan, task 1 | 120k |
| frontend-dev | from its definition | af7c3b59a46a2885d | plan, task 1a | 48k |
| test-engineer | from its definition | ab592dbb0dfe5bb8f | plan, task 2 (first pass) | 204k |
| backend-dev | from its definition | a869a76c011aee026 | implementation, task 3 | 177k |
| backend-dev | from its definition | a97c55c129929e249 | implementation, task 4 | 101k |
| frontend-dev | from its definition | af7c3b59a46a2885d | implementation, task 5 (continued from 1a) | 127k |
| test-engineer | from its definition | a293a474f71a64b21 | implementation, the dialog tests | 43k |
| manual-tester | from its definition | a0aedbc54e84b8d5e | implementation, looking at the screens | 58k |
| manual-tester | haiku | a135985716f01291d | implementation, manual checks | unknown |
| manual-tester | from its definition | a76bd347536e677d4 | implementation, the re-check | unknown |
| frontend-dev | from its definition | ad38651ae1f78944a | implementation, the fixes rounds of task 5 (remarks 1, 6, 7, 8, 9, 11) | 167k |
| manual-tester | from its definition | a8589fd2f105e6ec0 | implementation, looking at the fixes rounds (run 20261116-2329) | unknown |
| frontend-dev | from its definition | a0049261fb2a61bd5 | implementation, the third fixes round of task 5 (the manual tester's remarks 1, 2, 3, 4, 7; the list on a click; the calendar grids and their polish; the paging scroll) | 134k |
| backend-dev | from its definition | aef67ee598a031b15 | implementation, the long sample emails (additions to task 4) | unknown |
| test-engineer | from its definition | afbe35897365d3a00 | implementation, the email cell and the list on a click | 26k |
| api-lookup | from its definition | a1519ed9b01e5c9e2 | implementation, the calendar's month and year selects | unknown |
| manual-tester | from its definition | abdb3b6f0e6c01b19 | implementation, the re-check of the fixes rounds (run 20261117-0044) | unknown |
| frontend-dev | from its definition | a479d5ab575d127f6 | implementation, the fourth fixes round of task 5 (Escape in the grids, the chosen line, the mark of the current month and year and its rework) and the keyboard rounds (the arrows, the focus on opening a grid, the roving tab stop) | 130k |
| manual-tester | from its definition | a83d35b3925e8380a | implementation, the re-check of the fourth fixes round and of the keyboard rounds (runs 20261117-0104, 20261117-0112b, 20261117-arrows, 20261117-arrows2, 20261117-arrows3) | unknown |
| frontend-dev | from its definition | ab660e52c2ac35b43 | implementation, the Tab fix of task 5; review, the bug of the second pass (PLT-9.2 in the enrollments card) | about 135k |
| manual-tester | from its definition | a8628e66a3edb2d5a | implementation, the re-check of the Tab fix (run 20261117-0205) | 41k |
| test-engineer | from its definition | a781ba2d43f2d7c8b | review, the second pass (tests of details) | 110k |
