# Cohorts

Branch: `feature/cohorts`
Roadmap item: **Cohorts:** a coordinator gathers the enrollments of one intake on a track and follows its progress as students finish one by one. Out of scope: mentors of a cohort, groups inside a cohort. A new capability; touches enrollments and audit.
Requirements approved: 2026-11-23 "да, добавь. остальные требования принимаю" (with COH-6.1 stating that an enrollment already in the cohort is not a candidate)

## Scope

**Goal.** The coordinator of a track gathers the students of one intake and follows the intake as a whole: how many study in it, and later who has finished. Today a track has only a flat list of enrollments, and the boundary between intakes is not visible.

**Approach (confirmed at the path gate).** A cohort belongs to one track: a name and a start date. An enrollment is in at most one cohort, or in none, since a cohort is optional. A coordinator of the track creates and changes its cohorts, puts an enrollment into a cohort when enrolling or later (SWE adds a promising student to an intake later), moves it to another cohort of the same track, or takes it out. The track page shows the cohorts with their members and counts, and the enrollments of a track can be narrowed to one cohort. The changes show in the histories, as assigning a mentor does. A new capability in the requirements, cited from enrollments, tracks and audit. Whether the code is a module `cohorts` of its own or part of `enrollments` is decided at the plan stage.

**In:** cohorts of a track, creating and changing them, putting an enrollment into a cohort, moving it between cohorts of its track and taking it out, the cohorts and their counts on the track page, the enrollments of a track narrowed to one cohort, the histories.

**Out:** mentors of a cohort and groups inside a cohort (roadmap items of their own); the number of finished students in a cohort, which comes with the end of an enrollment; search, sorting and other filters of the enrollments (the enrollments list in full).

**Size.** About 10 to 12 scenarios are expected, at the threshold of a split. If the requirements grow past it, split into: (1) the cohorts of a track, creating and changing them; (2) putting enrollments into cohorts and the filter by cohort.

## Requirements

- Cohorts (COH), new: COH-1 to COH-8, scenarios COH-1.1 to COH-8.2 (18).
- Enrollments (ENR): ENR-2 (enrolling into a cohort, 409 `cohort-not-eligible`) with ENR-2.7; ENR-4 (the cohort of each enrollment, narrowed to a cohort) with ENR-4.3; ENR-7 and ENR-7.1 (the cohort in the list and in the enroll dialog); Purpose and Out of scope.
- Tracks (TRK): TRK-5 (cohorts on an archived track), TRK-10 (the cohorts on the track page), Known limitations, Out of scope.
- Audit (AUD-7): the cohort in the details of `TRACK_STUDENT_ENROLLED` and `USER_ENROLLED`; `TRACK_COHORT_CREATED`, `TRACK_COHORT_DELETED`, `TRACK_ENROLLMENT_COHORT_CHANGED`, `USER_COHORT_CHANGED`.
- Platform: PLT-10 holds the whole order of refusals; the sentences repeating it leave COH-1, COH-2, COH-3, ENR-1, ENR-2, ENR-9, ENR-13 and TRK-5.
- Authorization: the descriptions of `ENROLLMENTS_READ` and `ENROLLMENTS_MANAGE`.

## Design

**Module.** Cohorts are part of the `enrollments` module (`com.mentorhub.enrollments.internal`, in its layer subpackages), not a module of their own: enrolling names a cohort (ENR-2), the enrollments of a track are narrowed to one (ENR-4), and adding to a cohort checks and changes enrollments, so a separate module would need a cycle with `enrollments`. The track page shows cohorts only through the enrollments module's API.

**Data.** In `V1__schema.sql` (the owner merged the cohorts schema into it):
- `cohorts`: `id uuid` pk, `track_id` fk to `tracks`, `name text not null` (stored trimmed), `start_date date not null`, `created_at timestamptz`.
- `enrollments.cohort_id uuid null`, indexed. A cohort is of the enrollment's own track: enforce it in the database with a composite foreign key `(cohort_id, track_id)` to a unique `(id, track_id)` of `cohorts`, so no code path can break it. No `on delete cascade`: deleting a cohort that still has an enrollment must fail.

**API** (`api/openapi.yaml`, under `/api/v1/tracks/{id}`, `{id}` the track):
- `GET .../cohorts`: all cohorts of the track, not paged, ordered by start date descending, then name (case-insensitive), then id; each `{id, name, startDate, enrollmentCount}`, the count of active enrollments in it (a disabled student's enrollment counts, COH-5.1).
- `POST .../cohorts` `{name, startDate, enrollmentIds}` (`enrollmentIds` optional, may be empty): 201 with the cohort as listed.
- `DELETE .../cohorts/{cohortId}`: 204; 409 `cohort-not-empty`.
- `POST .../cohorts/{cohortId}/enrollments` `{enrollmentIds}` (at least one, else 400): 200 with the cohort as listed, its count updated.
- `DELETE .../cohorts/{cohortId}/enrollments/{enrollmentId}`: 204, also when the enrollment is not in the cohort or does not exist (COH-3.2).
- `GET .../cohort-candidates` (for a new cohort) and `GET .../cohorts/{cohortId}/candidates`: a page (PLT-3) with `q` (part of name or email) and `withoutCohort` (boolean, default false; the client sends true by default, COH-8); each item the enrollment id, the student's id, name, email and marks as in the enrollments list (ENR-4), and its current cohort `{id, name}` or null. Sorted as the enrollment candidates are.
- `GET .../enrollments` gains the query parameter `cohortId`; each enrollment gains `cohort` `{id, name}` or null. A cohort of another track or an unknown id gives an empty page.
- Enrolling (`POST .../enrollments`) gains an optional `cohortId`; a cohort not of the track or unknown is 409 `cohort-not-eligible`.
- New problem types: `enrollment-not-eligible`, `cohort-not-empty`, `cohort-not-eligible`.
- No new flag on the track: `canEnroll` is the same check (COH-1); its description names cohorts too.

**Access.** The same checks as the enrollments of a track: `ENROLLMENTS_READ` to read; `ENROLLMENTS_MANAGE`, or `ENROLLMENTS_MANAGE_COORDINATED` and coordinating the track, for every change and both candidate lists. Order as PLT-10: 400, then the track and the cohort in the path (404, a cohort of another track included), then the relation (403), then 409 (`track-archived` before looking at the cohort's enrollments or the ids given).

**Concurrency.** Every change of cohorts and of an enrollment's cohort, and enrolling, first takes the lock that already serializes the changes of one track with its archiving (TRK-5), then re-reads what it checks. That alone gives COH-3.3, COH-4.2 and TRK-5.4; no new lock key unless that lock does not exist, in which case say so in the report before inventing one.

**Audit** (AUD-7), written in the changing transaction: `TRACK_COHORT_CREATED` and `TRACK_COHORT_DELETED` on the track, with the cohort's id, name and start date; for every enrollment whose cohort changes (creating with members, adding, moving, taking out, not enrolling and not a no-op), `TRACK_ENROLLMENT_COHORT_CHANGED` on the track and `USER_COHORT_CHANGED` on the student, with the cohort left and the cohort joined, each `{id, name}` or null, in the details' existing shape for the student and the track. Enrolling into a cohort adds the cohort `{id, name}` to the details of `TRACK_STUDENT_ENROLLED` and `USER_ENROLLED`. Creating a cohort with members writes the creation first, then one change per member.

**Frontend.** A snackbar that closes itself stays at least 5 s and pauses on hover and focus; every interactive element has the `:focus-visible` ring (the mockup review). The candidate picker inside a `<fieldset>` needs `min-w-0`, or the truncated emails push it wider than the dialog on a phone (mockup round 5). On the track page: the cohorts as the approved mockup shows them; choosing one narrows the enrollments list (the cohort as a query parameter of the page, like its other list state); the enrollments list gains the cohort; the enroll dialog gains a cohort select (none by default). One candidate picker for "Create cohort" and "Add students": search, "Only without cohort" on by default, the current cohort of each candidate, multi-select across pages. Saving with chosen candidates in other cohorts opens a confirmation naming them and the cohorts they leave (COH-8.1). Taking out and deleting confirm first. A refusal (COH-8.2, ENR-7.1) shows the message and reloads the track, its cohorts and its enrollments. The track's and the user's history pages render the four new actions and the cohort in an enrollment entry.

**Sample center.** An active track with three cohorts (two with members, one empty) and enrollments in no cohort; an archived track with a cohort; enough candidates to page.

## What the tests rely on

Source: the approved mockup `.screens/cohorts/mockups/track-page-cohorts.html`, variant C after round 5. The `data-testid`s are new; frontend-dev adds them as named here.

**Track page, Enrollments card** (`enrollments-card`, `h2` "Enrollments"; the cohorts live inside it):
- Header, desktop, a user who may change, active track: "Create cohort" (`create-cohort`) and "Enroll student" (`enroll-student`). At 390 px one dots icon button, `aria-haspopup="menu"`, `aria-label` and tooltip "Track actions" (`track-actions-menu`), with `role="menuitem"` items "Create cohort" (`track-actions-create-cohort`) and "Enroll student" (`track-actions-enroll`). None on an archived track or for a read-only user.
- Cohort list, desktop: `nav` `aria-label="Cohorts"` (`cohort-list`) left of the table, `h3` "Cohorts", buttons: "All enrollments" with "N students" (`cohort-all`) first, then each cohort (`cohort-item`, `data-cohort-id`) newest start first, with the name and "Starts 11 Jan 2027 · 0 students" ("1 student", "N students"). The chosen item has `aria-current="true"`, a decorative "✓", a highlight frame and a bar. Choosing sets the page's query parameter `cohort`; "All enrollments" removes it.
- Cohort actions, inside the chosen row's frame: a cohort with students has an icon button "Add students to <cohort>" (`cohort-add-students`); an empty cohort a dots menu "Actions for <cohort>" (`cohort-actions-menu`) with "Add students" (`cohort-menu-add-students`) and "Delete" (`cohort-menu-delete`). None for read-only users or on an archived track.
- 390 px: a `select` labelled "Cohort" (`cohort-select`), options "All enrollments (28)" and "<name> - N students", the same cohort actions at its right; with a cohort chosen, the line "Starts 7 Sep 2026 · 12 students" (`cohort-info`); an empty cohort, to a user who may change, adds "Use the menu next to the cohort to add students."
- A track with no cohort: no list, no select, no hint; the table fills the card.
- Table, desktop: columns "Student", "Start date", "Cohort", "Mentors", and a visually hidden "Actions" column only for a user who may change. The Cohort cell (`enrollment-cohort`) is a chip with the name, or "No cohort". 390 px: cards with the name, the email (ellipsis, full in `title`), "Started <date>", "Cohort: …", "Mentors: …", the actions icon centered on the right.
- Row actions: dots button "Actions for <student name>" (`enrollment-actions-menu`), items "Assign mentor" (`enrollment-menu-assign-mentor`) and, only for an enrollment in a cohort, "Take out of <cohort>" (`enrollment-menu-take-out`).
- Pager: "Page 1 of N", "Previous" and "Next" on the right.
- Empty states, text only: "No enrollments yet." (`enrollments-empty`), "No students in <cohort> yet." (`cohort-empty`).
- Archived track: the cohorts stay visible and choosable, no actions for anyone.
- A refusal (COH-8.2, ENR-7.1): a snackbar `role="alert"` with the message and "Dismiss"; the track, its cohorts and its enrollments reload.

**"Create cohort" dialog** (`create-cohort-dialog`): title "Create cohort"; "Name" (`cohort-name`) and "Start date" (`cohort-start-date`) on one line; the group "Students (optional)" with the picker; a blank name shows "Enter a name for the cohort." under the field and the dialog stays open; "Cancel" and "Create cohort" (`create-cohort-submit`).

**"Add students" dialog** (`add-students-dialog`): title "Add students to <cohort>", "Students already in this cohort are not listed."; "Cancel" and "Add N student(s)" ("Add 1 student") (`add-students-submit`); with nothing chosen it reads "Add students", `aria-disabled="true"`, focusable, described by "Choose at least one student."

**Picker** (`candidate-picker`): search "Search students", placeholder "Search by name or email" (`candidate-search`); `role="switch"` "Only without cohort", on by default (`only-without-cohort`); per candidate a checkbox "Choose <name>" (`candidate-checkbox`, `data-enrollment-id`), the name, "Disabled" when marked, the email, a chip "In <cohort>" or "No cohort" (`candidate-cohort`); `role="status"` "N chosen", or "N chosen (x on this page, y on other pages)" (`candidate-count`); `nav` "Candidate pages" (`candidate-pager`); empty texts `No students match "<query>". Check the spelling, or turn off "Only without cohort" to search the students of other cohorts too.` (`candidate-empty`) and `Every other student of this track is already in a cohort. Turn off "Only without cohort" to move students from their cohorts.`

**Move confirmation** (COH-8.1, `move-confirm-dialog`): title "Move N students into <cohort>?", "Saving takes them out of their current cohorts."; a scrollable region "Students to move" (`move-list`) of "<name> leaves <cohort>" (`move-item`); then "N more student(s) has/have no cohort and is/are only added."; "Cancel" and "Move and save" (`move-confirm`). Nothing is sent before "Move and save", nothing after "Cancel".

**Take out** (`take-out-dialog`): "Take <student> out of <cohort>?", "<student> stays enrolled on <track name>, in no cohort."; "Cancel" and "Take out" (`take-out-confirm`). **Delete** (`delete-cohort-dialog`): "Delete <cohort>?", "The cohort has no students. Its entries in the history stay."; "Cancel" and "Delete" (`delete-cohort-confirm`).

**Enroll dialog** (ENR-7): "Cohort" (`enroll-cohort`) and "Start date" on one line; "No cohort" first and selected, then "<name> (starts <date>)" newest first; a track with no cohort: the select disabled with "No cohort" and the hint "This track has no cohorts yet." (`aria-describedby`).

**General:** icon buttons have no visible text, their name is `aria-label` and the same tooltip; menus are `aria-haspopup="menu"` buttons with `aria-expanded`; at 390 px targets are at least 44 px and inputs 16 px; no motion.

## Tasks

| Task | Scenarios | Who | Status |
|---|---|---|---|
| T1 Contract and migration, build green with stubs | all API scenarios | backend-dev, frontend-dev (stubs) | in review |
| T2 Mockup of the cohorts on the track page and the dialogs | COH-8, ENR-7 | ui-designer | done |
| T3 Red tests, backend and frontend | all new and changed scenarios | test-engineer | done |
| T4 Cohorts backend: reading, creating, adding, taking out, deleting, candidates, enrolling into a cohort, the filter, the histories; checkpoint | COH-1 to COH-7, ENR-2.7, ENR-4.3, TRK-5.4 | backend-dev | done |
| T5 Sample data for the cohorts | | backend-dev | done |
| T6 Track page: cohorts, filter, dialogs, enroll into a cohort, the histories | COH-8.1, COH-8.2, ENR-7.1, TRK-10 | frontend-dev | done |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

## Notes for later tasks

- T3: backend red tests fail on the contract validator ("Response status 501 not defined"), the stubs' only red; the scenario assertions run only once T4 answers. COH-2.1's last assertion (`cohortOf(track, second)` is null) was not re-run after its edit.
- T3 (for T4): COH-3.3 and COH-7.1 rely on the histories in store order; take the audit time after the track lock, so that parallel changes keep their order in `occurredAt`.
- T3 (for T6): the specs use `TrackPage`; `enroll-cohort` is a native `<select>`; `candidate-checkbox` carries `data-enrollment-id`, `cohort-item` `data-cohort-id`; the move confirmation has "Cancel" and `move-confirm`; dialogs render in the overlay (found by `document.querySelector`); the page calls `listTrackCohorts` and `listNewCohortCandidates` through the `Api` stub, and a refusal reloads with `getTrack`, `listTrackCohorts` and `listTrackEnrollments`.
- T3: shared test helpers `enrollments/internal/TestCohorts.java` (`create`, `put`); `TestUsers.deleteAll` also deletes `cohorts`.

- T1: stubs answer 501 and are marked `TODO(cohorts T4)`: the seven methods of `CohortsController` (their `@RequirePermission` are final), `EnrollmentsController.listTrackEnrollments` with `cohortId`, `enrollStudent` with a `cohortId`; `EnrollmentsMapper.trackEnrollment` passes `null` for `cohort`.
- T1: the lock is `TrackDirectory.lockForEnrollment` (`for no key update` on the track row), taken by `TrackService` for every change and archiving; no new lock key.
- T1: history details are fixed: track entries name the student by `userId`, user entries carry `track` as `HistoryTrack`, cohorts as `{id, name}`; in the contract `cohort` and `cohortChange: {from, to}` (a wrapper, since null details are left out of the JSON). The read side is done in `tracks` and `users`; T4 writes the entries through new methods on `TrackEnrollmentHistory` and `UserEnrollmentHistory`, adds the nullable cohort to `TrackChange.StudentEnrolled` and `UserChange.Enrolled`, and renders it.
- T1: `EnrollmentProblems` needs the constants of `enrollment-not-eligible`, `cohort-not-empty`, `cohort-not-eligible`. A cohort name is trimmed, 1 to 100 characters. Conflict order: `track-archived` first, then `enrollment-not-eligible` / `cohort-not-empty`; in enrolling `cohort-not-eligible` is second.
- T1: the owner deleted `V2__cohorts.sql` and moved its schema into `V1__schema.sql`; schema changes of this change go there.
- T1 (frontend): the history texts of the four new actions are stubs marked `TODO(cohorts T6)`; `trackEnrollment()` in `features/enrollments/test-data.ts` defaults `cohort: null`.
- T4 (for T5): audit rows take their time at insert (`clock_timestamp()`); a cohort created with members writes `TRACK_COHORT_CREATED` first, then a `TRACK_ENROLLMENT_COHORT_CHANGED` / `USER_COHORT_CHANGED` pair per member; write sample rows in that order with increasing times. Details: created/deleted `{"cohort": {id, name}, "startDate"}`; track change `{"userId", "from", "to"}`; user change `{"track": {id, code, name}, "from", "to"}`, `from`/`to` `{id, name}` or null; `TRACK_STUDENT_ENROLLED` / `USER_ENROLLED` carry `"cohort": {id, name}` only when there is one.
- T4: `EnrollmentRepository.findByTrackAndMentor` is replaced by `findByTrack(trackId, mentorId, cohortId)`; shared checks are in `EnrollmentRules`; `users.HistoryCohort` is the one public cohort type of both history APIs.
- T5: sample center (`db/sample/enrollments.sql`): SWE has Intake C (empty, newest), Intake B (9), Intake A (16), and Marcus Lane, Nadia Rahman, Sienna Holt in no cohort; Lucas Price moved A to B, Sienna taken out of B, "Intkae B" created and deleted; Frontend Development (archived) has an empty "Intake A"; QA, DevOps, QA Automation have none. Sign-ins: `coordinator@mentorhub.local` or `sarah.lopez@mentorhub.local` (SWE coordinators), `admin@mentorhub.local`, read-only `mentor.coordinator@mentorhub.local` (DevOps only). Candidates for a new SWE cohort: 28 with the switch off, 3 on.
- T5 (for the test engineer, second pass): `SampleCenterDataTests` reads every user history entry but not every track history entry.
- T6: deviations from the mockup: "No students enrolled yet" instead of "No enrollments yet."; the shared paginator ("1–25 of N"); "—" for no mentor; "Assign mentor" in the row menu. The three action menus use the Spartan dropdown menu (CDK overlay; tests query items with `document.querySelector`). New: `features/enrollments/cohort-candidate-picker.ts`, `cohort-dialogs.ts`, `shared/date-field/date-field.ts`; `ConfirmDialog` takes optional `testIds`.
- T6: details chosen by the developer: a created cohort becomes the chosen one; the create dialog's start date starts blank, required ("Choose a start date."); the picker pages by 10; a cohort load failure shows "Could not load the cohorts." with "Try again"; an unknown `?cohort=` counts as All enrollments; success snackbars 5 s; refusal texts its own; mentor removal in the popover still shows on an archived track (unchanged).
- T6 rework: shared `shared/date-field/date-field.ts` (`app-date-field`) used by the enroll and create-cohort dialogs; the shared dialog content scrolls (`max-h` 100dvh) and its footer is one wrapping row, for every dialog; "All enrollments" takes the total from one extra `listTrackEnrollments` (size 1) while a cohort is chosen (an API field would remove it); the student link only for users with USERS_READ; the Assign mentor icon button (`enrollment-assign-mentor`) keeps a tooltip; `TrackEnrollments` injects `CurrentUserService`.
- T6 rounds 2-3: page scroll lock while a dialog is open is global in `frontend/src/styles/tailwind.css` (overflow hidden with a stable gutter, not a fixed body), so the sticky header stays; the loading spinner is a sticky badge in the enrollments area; at 390 px the row actions cell is layered above the following cards, and the Assign mentor icon sits bottom-right (the mockup centers it).
- Second test pass (2026-11-24): 25 tests of details (`CohortsDetailsTests`, `track-cohorts-details.spec.ts`, two in `SampleCenterDataTests`), uncommitted; no real bug found.

## Red tests

Commit: 6dd4644 "test: red tests for cohorts"

## Decisions

- 2026-11-23: Cohorts are taken before the enrollments list in full (roadmap item 1). "да, подтверждаю, по вопросам как рекомендуешь"
- 2026-11-23: Progress of a cohort is, for now, its members and their number; the number of finished students comes with the end of an enrollment.
- 2026-11-23: An enrollment can be moved from one cohort to another of the same track in one action, recorded once in the history.
- 2026-11-23: "да, всё как рекомендуешь" to: a cohort has a required name, not unique even within a track, and a required start date, and no status; reading cohorts and their members needs `ENROLLMENTS_READ`; creating, renaming, changing the date, deleting a cohort and putting an enrollment into one, moving or taking it out need `ENROLLMENTS_MANAGE`, or `ENROLLMENTS_MANAGE_COORDINATED` on a coordinated track, no new permission; only an empty cohort is deleted, otherwise 409; on an archived track every change of cohorts and members is 409 `track-archived`, and cohorts stay visible; a student sees the name of their cohort in their own enrollment; a mentor does not see cohorts yet.
- 2026-11-23: A cohort is created with its members in one request, and enrollments are added to a cohort in bulk, at creation and later ("по-сути, это балковый АПИ для зачислений в когорту и может использоваться не только на этапе создания когорты, но и при добавлении"). Adding is all or nothing; an enrollment of another track refuses the whole request; one already in the cohort changes nothing; taking out is one enrollment at a time; the history is written per enrollment.
- 2026-11-23: An enrollment in another cohort is moved: "переводить, но нужен будет поп-ап подверждение, если имеет место перевод из когорты в когорту".
- 2026-11-23: "когорту при зачислении не убирай": a cohort may be given when enrolling (ENR-2).
- 2026-11-23: Split accepted ("разбивка подходит"): this change is cohorts with their members (creating with members, adding, taking out, deleting an empty one, the list with counts, the cohort of each enrollment and the enrollments narrowed to a cohort, the histories, the track page). A later small change: renaming a cohort and changing its date, the "without cohort" filter of the enrollments of a track, the cohort in the student's own enrollment (ENR-5).
- 2026-11-23: Enrolling into a cohort writes one entry: the cohort goes into the details of `TRACK_STUDENT_ENROLLED` and `USER_ENROLLED`, no separate change of cohort ("вариант А"). Other findings of the review applied as recommended; no further split.
- 2026-11-23: The order of refusals after 400 (the resource in the path 404, the relation 403, then any conflict 409, in no defined order unless a capability names one, as IEM-5 does) moves into PLT-10, and the capabilities stop repeating it, in this change ("да, делаем сейчас"). The order among several conflicts of one request is a detail ("порядок конфликтов — деталь").
- 2026-11-24: The owner deleted the migration `V2__cohorts.sql` and moved its changes into `V1__schema.sql` ("миграцию v2 удалил, изменения перенёс в v1").
- 2026-11-24: Mockup variant C (a cohort list beside the enrollments table, a select on phones), with the owner's 12 remarks of round 1 (in the mockup's Rounds); approval of the redrawn mockup pending.
- 2026-11-24: "остальные вопросы по рекомендациям": the chosen cohort is kept in the URL and "All enrollments" clears it; no "Clear selection" in the picker; the cohort filter works on an archived track; the enroll dialog's cohort select is disabled with a hint when the track has no cohort; no limit on `enrollmentIds`.
- 2026-11-24: The mockup approved ("утверждаю"): variant C after round 5, `.screens/cohorts/mockups/track-page-cohorts.html`. The decisions the tests and the code rely on are in What the tests rely on. On the way: "Only without cohort" in the singular ("у студента только одна активная когорта"); on a phone, the cohort actions under the dots for any cohort; a single action is a compact icon instead of a menu; the strip of the chosen cohort removed and its actions in a dots menu ("полосу убрать, а экшены над когортой спрятать под выпадайку по аналогии"); the review fixes accepted as recommended ("да, отдавай дизайнеру как предлагаешь").
- 2026-11-24: Plan gate passed: the plan, the contract and the schema approved ("одобряю").
- 2026-11-24: T4 approved at the checkpoint ("одобряю"); the audit clock line of `docs/development/backend.md` changed now: "вариант (a)" (an audit entry takes `clock_timestamp()` at insert, so that changes serialized by a lock keep their order).
- 2026-11-24: The screen run's decisions: the two bugs fixed ("All enrollments" counts the whole track; the picker overflow); at 390 px "сделать как в макете" (buttons in one row, Cancel left); every dialog scrolls when taller than the screen, fields do not stick out ("я не могу листать поп-ап ... похоже, что это верно для всех попапов"); a track with no cohort hides the Cohort column ("колонку скрывать"); student names are links, headings bold as in the mockup; kept: header icons, the picker without one box and without a pager on one page, the X with a visible hover in the dark theme, the move list as a bordered box, "Enroll a student", the T6 deviations; Start date uses the project's own calendar; a row whose only action is Assign mentor shows an icon button instead of the dots ("заменить иконку выпадайки на кнопку Assign mentor, но сделать кнопку подходящей иконкой"; applied to any such row, including an enrollment in no cohort on a track with cohorts); no tooltips on the dots menus ("убрать всплывающие подсказки").
- 2026-11-24: Fix rounds 2-3 ("по всем пунктам как рекомендуешь"): coordinators without USERS_READ see names as plain text; the Assign mentor icon keeps its tooltip; the picker wraps long names and emails; the empty texts placed as the mockup ("исправить как в мокапе"); a centered spinner instead of the loading bar ("заменить на крутящийся кружок в самой середине части окна энроллментов"); the open row menu covers the next card at 390 px (`.screens/img_1.png`); the header stays when a dialog opens on a scrolled page; kept: a dialog title scrolls away with tall content, the range label changes before the rows, "No students enrolled yet" and the shared paginator.
- 2026-11-24: The spinner at the top of the list, paging jumping to the top, the status text as live content: kept ("по всем пунктам как рекомендуешь"). T5 and T6 accepted and committed ("да, коммить").
- 2026-11-24: Review findings: all as recommended ("по всем пунктам как рекомендуешь"): 1-6 fixed, 7 needed no change (already handled), 8 at the finish stage; any 404 on take-out or a cohort's candidates is read as the cohort gone. Finding 9: the hand-rolled `shared/action-menu` had no real reason (the old specs never opened a menu); replaced by the Spartan dropdown menu ("да, заменяй на Spartan").
- 2026-11-24: Re-review minors: 2 and 3 fixed, 4 (the three pasted menu templates) left ("по всем пунктам как рекомендуешь").
- 2026-11-24: The duplicated `UuidArray` stays in this change; the shared helpers go to a common module in a separate change ("общий модуль сделаем отдельным изменением").
- 2026-11-24: Owner's remark for `/usage-review`: the frontend was the pain of the process; after several mockup rounds the final screen still needed several fix rounds with both the developer and the tester.

## Handover

- Waiting for: the owner's word to commit; stage 8 done in the working tree.
- Accepted 2026-11-24 ("принимаю, переходи к этапу 8"). The owner asked to record a visual and technical audit of the frontend: added to `docs/roadmap.md` (Next 7), with the shared backend helpers (Next 8).
- Documents changed: changed `docs/development/backend.md` (the audit clock line, in 8dd7040; its four answers shown at stage 8); changed `docs/roadmap.md` (Cohorts deleted, items 7 and 8 added); requirements, contract and schema as committed in 6dd4644; this plan and `.screens/cohorts/` deleted.
- Second test pass: 25 tests of details, no real bug found (counts toward removing the pass, roadmap: checks of the workflow).
- Next: two commits on the owner's word (this plan's final state with the review fixes, then stage 8); then `/pr` and `/usage-review`.

## Agents

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| reviewer | inherited | afb678fab67ada42f | spec: review of requirements | 70k |
| ui-designer | inherited | a119ee2b592ae483a | plan: T2 mockup, rounds 1-5, approved | 160k |
| backend-dev | inherited | a3b530254e891f2a8 | plan: T1 contract and migration | 167k |
| verifier | inherited | a3fd0a2602e69ed6b | plan: frontend after T1 | 16k |
| frontend-dev | sonnet | ab3ecbc2b8c9ad2bf | plan: T1 frontend stubs | 43k |
| general-purpose | sonnet | a38435cae6da528ae | plan: contrast and UX review of the mockup | 95k |
| verifier | inherited | a4cff8d32ae309481 | plan: all after the V1 merge | 19k |
| verifier | inherited | a9aacf3b18306665f | plan: backend after removing the stale V2 | 30k |
| verifier | inherited | a8f7020b70dcb37b7 | plan: backend after the owner's schema fix | 13k |
| test-engineer | inherited | a63059c1288673fb1 | tests first: T3 | 168k |
| backend-dev | inherited | a83357a9ecb52e047 | implementation: T4 | 196k |
| backend-dev | inherited | aea5b2584ffaf40f0 | implementation: T5 | 98k |
| frontend-dev | inherited | aeae6885a1fbacc1f | implementation: T6 | 194k |
| manual-tester | inherited | a3b7265d1c56022d8 | implementation: screen run, NOT RUN | 20k |
| manual-tester | inherited | ad98166d6979a01e6 | implementation: screen run | 100k |
| manual-tester | haiku | a90f9f8ba4d935741 | implementation: numbered run | see usage |
| frontend-dev | inherited | a97dc3b17b70d6941 | implementation: T6 fix rounds 1-4 | 145k |
| manual-tester | inherited | acd847ccb167c1797 | implementation: fix-round screen run | see usage |
| manual-tester | inherited | a801342f084af8fea | implementation: round 2 run | 91k |
| manual-tester | inherited | ae37072c66960ae2e | implementation: round 3 run | 51k |
| manual-tester | haiku | a3ad987ab84085adb | implementation: spinner steps | see usage |
| test-engineer | inherited | a7524c66671ef6bab | review: second pass | 119k |
| reviewer | inherited | a69c62b02b42bdc61 | review: main...HEAD | 183k |
| api-lookup | inherited | a7de95e5f765fb371 | review: generated DTO item validation | see usage |
| general-purpose | sonnet | a7b61102220801e58 | review: why the hand-rolled menu | 81k |
| test-engineer | inherited | ae6c93452ba751ada | review: tests for the findings | 119k |
| backend-dev | inherited | a19fb5e9c5df5a816 | review: fixes 3-4 | 38k |
| frontend-dev | inherited | a72016e708c79a29f | review: fixes 1-2, Spartan menus | 61k |
| manual-tester | haiku | a13a24a2cbd0e6e71 | review: menus | 78k |
| reviewer | sonnet | a492b474165c9a959 | review: re-review | 43k |
| frontend-dev | inherited | a72016e708c79a29f | review: minor fixes | 64k |
| verifier | inherited | af1f40d5d98137641 | review: all | see usage |
| Explore | sonnet | aa0c1b60dbe2ba3f3 | review: duplicated helpers across modules | 67k |
