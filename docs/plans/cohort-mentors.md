# Mentors of a cohort

Branch: `feature/cohort-mentors`
Roadmap item: Next, 2. **Mentors of a cohort:** mentors assigned to a cohort as a whole see all its students. Touches cohorts, enrollments, authorization and audit.
Requirements approved: 2026-11-25 "Требования одобряю" (I approve the requirements)

## Scope

**Goal.** On QA a group is taught by lectures, with two or three mentors who do not divide the students among themselves. Today a mentor is related to a student only through the student's enrollment, so a coordinator would assign every mentor to every enrollment by hand, and remember to do it again for each student added later. A mentor assigned to a cohort as a whole leads all its students, those in it now and those added later (roadmap domain model: a mentor is related to a student through the enrollment or through the enrollment's cohort).

**Approach (confirmed).** A relation of its own, "mentor of a cohort", with access derived at the time of the request, not copied into the mentors of each enrollment:

- A mentor of a cohort leads the active enrollments in it: a student added to the cohort is led by its mentors at once, a student taken out leaves them at once.
- Only an active user with the mentor role who is a mentor of the track is assigned (as ENR-9).
- A mentor of a cohort is not removed from the track while they lead it (as ENR-13.4).
- A student sees the mentors of their cohort (ENR-5); a mentor sees the students of their cohorts (ENR-11).
- Assigning and removing are audited.

Rejected: assigning to a cohort as a one-off assignment to each of its enrollments through ENR-9; it does not reach students added later and makes removal ambiguous.

**In:** cohorts (COH), enrollments (ENR-5, ENR-11, ENR-13, ENR-15, ENR-4.2), authorization notes (PERM), the audit catalog (AUD-7), the track page.

**Out:** groups inside a cohort; what the end of an enrollment does to its mentors (End of an enrollment).

**Size:** about 10 to 12 scenarios expected. Not split now; if the spec exceeds about 10 scenarios, ENR-15 (counts and the filter by mentor through the cohort) moves to a follow-up change and is recorded as a known limitation until then.

## Requirements

- Cohorts (COH): new COH-9 Mentors of a cohort (9.1 to 9.5), COH-10 The candidates for the mentors of a cohort (10.1, 10.2), COH-4.3, COH-4.4, COH-5.2, COH-7.2; changed COH-1, COH-1.2, COH-1.3, COH-2.4, COH-4, COH-5, COH-7, COH-8, COH-8.2, Purpose, Known limitations, Out of scope.
- Enrollments (ENR): new ENR-4.4, ENR-5.4, ENR-9.5, ENR-10.2, ENR-11.3, ENR-13.8, ENR-13.9, ENR-15.4; changed ENR-4, ENR-5, ENR-7, ENR-7.1, ENR-9, ENR-10, ENR-10.1, ENR-11, ENR-12, ENR-13, ENR-13.4 (title), ENR-15, Purpose, Known limitations, Out of scope.
- Tracks (TRK): changed TRK-5, TRK-5.4, Known limitations.
- Authorization (PERM): the permission table and the notes.
- Audit (AUD-7): new `TRACK_COHORT_MENTOR_ASSIGNED`, `TRACK_COHORT_MENTOR_REMOVED`, `USER_COHORT_MENTOR_ASSIGNED`, `USER_COHORT_MENTOR_REMOVED`; the enrollment mentor entries say "directly".
- 19 new scenarios in all: 297 before, 316 after (COH-9.5 with the bulk assignment, COH-10.2 and ENR-10.2 with the candidates' load, 2026-11-26).

## Design

Mockup approved 2026-11-26: `.screens/cohort-mentors/mockups/track-cohorts.html`.

- **Contract** (mirror the enrollment mentors: `assignEnrollmentMentor`, `removeEnrollmentMentor`, `listMentorCandidates`):
  - `POST /api/v1/tracks/{id}/cohorts/{cohortId}/mentors` with a list of user ids, as `addCohortEnrollments`: assign one or more, all or nothing (400 empty, 409 `mentor-not-eligible`, `track-archived`; 404 for the cohort); `DELETE /api/v1/tracks/{id}/cohorts/{cohortId}/mentors/{userId}`: remove one.
  - `GET /api/v1/tracks/{id}/cohorts/{cohortId}/mentor-candidates`: COH-10, paged, `search`.
  - `Cohort` gains `mentors`, each with name, email and the marks disabled and without the mentor role (COH-5).
  - The mentors of a `TrackEnrollment` gain the through-cohort mark (ENR-4); `MentoredEnrollment` gains the cohort and the led-only-through-the-cohort mark (ENR-11). `MyEnrollment` keeps its shape (ENR-5).
  - The candidates for the mentors of a cohort and of an enrollment share a new schema `MentorAssignmentCandidate` (`MentorCandidate` stays for the track's candidates, ENR-14), which carries the number of active enrollments on the track the user leads, counted as ENR-15, and the number of cohorts of the track they are a mentor of (COH-10, ENR-10).
  - Removing a mentor of a track gains 409 `mentor-leads-cohorts` (ENR-13); the audit action enum gains the four cohort mentor actions (AUD-7).
- **Schema:** a table of the mentors of a cohort, shaped as `enrollment_mentors` (primary key cohort and mentor, which decides parallel assignments, COH-9.4). Deleting a cohort removes its mentor rows in the service, auditing each (COH-4.3). Migration: into `backend/src/main/resources/db/migration/V1__schema.sql`, by the owner's word (see Decisions).
- **Leading is derived at the request** (Scope): the enrollments a mentor leads are those they are a direct mentor of, or whose cohort they are a mentor of; never copied into `enrollment_mentors`.
- **Serialization:** assignments and removals of cohort mentors take the same track lock as the other changes of the track's mentors and cohorts (ENR-13.9, COH-4.4, TRK-5.4).

## What the tests rely on

From the approved mockup (`.screens/cohort-mentors/mockups/track-cohorts.html`, round 9). Placeholders in `<>`; plurals "1 mentor", "2 mentors"; zero written out.

**Who sees what.** May change = may change the cohorts (COH-1) on an active track. A reader and an archived track see every mentors button and popover, but no "Actions for <cohort>", no remove icon in any card, no "Assign mentor for <student>". Desktop has the Cohorts list; the phone has a select labelled "Cohort", so only the chosen cohort has a mentors button.

**Cohorts list (desktop).** `nav` "Cohorts". Each cohort item:
- a select button (name, "Starts <date> · <n> student(s)", `aria-current` when chosen);
- for a cohort with mentors, chosen or not, a button "Mentors of <cohort>: <n> mentor(s)[ · <m> inactive]" (`aria-haspopup="dialog"`), visible "<n> mentor(s)[ · <m> inactive]" with a warning icon when m > 0; it does not choose the cohort; a cohort with none shows the text "No mentors";
- when the user may change, on the chosen cohort, "Actions for <cohort>" (`aria-haspopup="menu"`), menu items "Add students", "Assign mentors", and "Delete" only with 0 students.

**Mentors popover of a cohort.** Dialog "Mentors of <cohort>" (visible title the same): all mentors, inactive last; each card the name, email and marks as words "Disabled", "No mentor role"; when the user may change, an icon button "Remove <name> as a mentor of <cohort>" (tooltip "Remove"); no text "Remove" button in any mentors popover.

**Chosen cohort's mentors above the table.** Group "Mentors of <cohort>", label "Mentors:".
- Desktop: name buttons "<name>[, Disabled|, No mentor role]" (inactive last, warning icon after the name), each opening a dialog "<name>" with the card; overflow "+<k>" named "<k> more mentor(s) of <cohort>[, <m> inactive]", opening "More mentors of <cohort>".
- Phone: one button "Mentors of <cohort>: <n> mentor(s)[ · <m> inactive]" opening "Mentors of <cohort>"; "Actions for <cohort>" next to the select.
- No mentors: "No mentors in <cohort>. Its students are led only by the mentors of their enrollments."

**Enrollments (current view plus the mark).** First mentor button "<name>[, Disabled|, No mentor role][, through cohort]" (icon title "Through cohort"); "+<n>" popover dialog "Mentors of <student>": a mentor through the cohort has the badge "Through cohort" and no remove button; a direct mentor keeps "Remove <name>". Removing a direct mentor who also leads through the cohort: the confirmation says so (ENR-7), text "They stop being a mentor of this student, but still lead <student> through <cohort>. They can be assigned again later." A mentor's students on the track page: badge "Through <cohort>". My students (ENR-12), not drawn: a cohort column with the cohort's name, and the same "Through <cohort>" badge for a student led only through the cohort.

**Dialog "Assign mentors to <cohort>"** (menu item "Assign mentors"). Modal dialog of that name; description "Mentors already in this cohort are not listed. Only active mentors of this track can be assigned."; search "Search mentors" (placeholder "Search by name or email"); rows with a checkbox "Choose <name>", the name, email and load "<s> student(s) · <c> cohort(s)" (this track); status "<n> chosen" or "<n> chosen (<x> on this page, <y> on other pages)", the choice kept across pages and searches; `nav` "Candidate pages" with "Previous", "Page <i> of <n>", "Next" when more than one page. Nothing chosen: hint "Choose at least one mentor." and "Assign mentors" `aria-disabled`; chosen: "Assign <n> mentors" / "Assign 1 mentor"; "Cancel". Empty: `No mentors match "<q>". Check the spelling.`, or without a search "There are no other active mentors on this track to assign to <cohort>." One request for all chosen; the dialog closes on success.

**Dialog "Assign a mentor to <student>"** (existing): each option of the listbox "Mentors" gains the load "<s> student(s) · <c> cohort(s)".

**Confirmations and refusal.**
- "Remove <name> as a mentor of <cohort>?": "They stop leading the students of <cohort> through the cohort. A student they lead through their own enrollment stays with them. They can be assigned again later." Buttons "Cancel", "Remove".
- "Delete <cohort>?" with mentors: "The cohort has no students. Its <n> mentors, <names>, are removed with it. Its entries in the history stay."; without: "The cohort has no students. Its entries in the history stay." Buttons "Cancel", "Delete".
- `mentor-not-eligible`: the dialog closes, the cohorts and enrollments reload, an alert "That user can no longer be assigned: they must be active, hold the mentor role and be a mentor of this track. No mentor was assigned. The cohorts are shown as they are now." with "Dismiss".

## Tasks

| Task | Scenarios | Who | Status |
|---|---|---|---|
| 1. Contract and migration (plan gate) | all | backend-dev | done |
| 2. Red tests, backend and frontend | all new and changed | test-engineer | done |
| 3. Backend (checkpoint: backend green) | COH-1.2, 1.3, 2.4, 4.3, 4.4, 5.2, 7.2, 9.x, 10.1; ENR-4.4, 5.4, 9.5, 10.1, 11.3, 13.8, 13.9, 15.4; TRK-5.4 | backend-dev | pending |
| 4. Sample data: mentors of the sample center's cohorts, one led both directly and through the cohort | (screens) | backend-dev | pending |
| 5. Frontend, after 4: cohorts with mentors, assign and remove, deletion confirmation, through-cohort marks in the enrollments, the mentor's students and My students, refusals | COH-8, COH-8.2, ENR-7, ENR-7.1, ENR-12 | frontend-dev | pending |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

## Notes for later tasks

- Task 1: the table `cohort_mentors` is in `V1__schema.sql`, appended by the owner by hand: the project hook refuses agents' edits of a migration in main. A later change of that table goes through the owner the same way.
- Task 1: the candidates for the mentors of an enrollment and of a cohort share the new schema `MentorAssignmentCandidate` (`students`, `cohorts`); `MentorCandidate` stays as it was, for the candidates for the mentors of a track only.
- Task 1 stubs (task 3 replaces them): `CohortsController.assignCohortMentors`, `removeCohortMentor` and `listCohortMentorCandidates` throw 501; `EnrollmentsMapper` gives placeholders: `EnrollmentMentor.throughCohort` false, `MentoredEnrollment.cohort` null and `throughCohort` false, `Cohort.mentors` empty, the candidates' `students` and `cohorts` 0.
- Task 1: the four audit actions have their detail types and history mapping (`TrackChange.CohortMentorAssigned/Removed`, `UserChange.CohortMentorAssigned/Removed`), as `ContractEnumsTests` requires; nothing writes them yet.
- Task 2: COH-8, ENR-7 and ENR-12 are requirements without scenarios, so their client tests carry no id (`track-cohort-mentors.spec.ts`, `track-enrollments-through-cohort.spec.ts`, `my-students-page.spec.ts`). The changed ENR-10.1 passes already: the existing candidate query lists a cohort mentor.
- Task 2: most backend cohort-mentor tests are red at the response validation of the 501 stubs, not at their own assertion; they reach it once task 3 implements the operations.
- Task 2: the frontend compiles again: `track-history-text.ts` and `users/history-text.ts` have texts for the four cohort mentor actions ("Assigned <mentor> as a mentor of the cohort <cohort>", "Assigned as a mentor of the cohort <cohort> on <track>", and the Removed pair).
- Task 5: AUD-7 says the enrollment mentor entries say "directly"; the existing history texts of `ENROLLMENT_MENTOR_*` actions do not yet. Task 5 adds the word, with the specs that assert those texts.
- Red state at the commit: backend tests of the change red; frontend 10 of 487 red, all in `my-students-page.spec.ts` (1), `track-cohort-mentors.spec.ts` (6), `track-enrollments-through-cohort.spec.ts` (3); lint, format and build pass.

## Red tests

Commit: the first commit of the branch, `test(cohort-mentors): requirements, contract and red tests` (2026-11-26).

## Decisions

- 2026-11-25: Path confirmed: "Подтверждаю, делаем feature/cohort-mentors, запускай /spec" (confirmed, make it feature/cohort-mentors, run /spec): the goal, the derived relation approach and the stages.
- 2026-11-25: Assigning the mentors of a cohort: "Согласен со всем, по 4–6 как ты рекомендуешь" (agreed with everything, 4 to 6 as recommended):
  - who may: as changing the cohorts (COH-1); the mentors of a cohort are read with `ENROLLMENTS_READ` in the cohorts of a track, with marks;
  - who is assigned: an active user with the mentor role who is a mentor of the track, else 409 `mentor-not-eligible`; repeats change and record nothing; candidates with search; archived track 409 `track-archived`;
  - a mentor disabled or without the role later: the assignment stays and grants nothing;
  - a mentor of a cohort, even an empty one, is not removed from the track: new 409 `mentor-leads-cohorts`;
  - a cohort with mentors and no enrollment is deleted, its mentors removed with it and recorded in each mentor's history;
  - audit: `TRACK_COHORT_MENTOR_ASSIGNED/REMOVED` and `USER_COHORT_MENTOR_ASSIGNED/REMOVED` (the mentor), not in the students' histories;
  - races as ENR-13.7; parallel assignments assign once; a deletion and an assignment of mentors one after another.
- 2026-11-25: A mentor's students show the cohort of each enrollment and mark those led only through the cohort: "когорта и пометка". This reverses "A mentor does not see cohorts yet" of the cohorts change (PR #58).
- 2026-11-25: "2–5 согласен" (2 to 5 agreed): a student sees the mentors of their cohort, each once (ENR-5); the enrollments of a track list the mentors of the cohort with each enrollment, once, marked as through the cohort unless also assigned directly (ENR-4); the number of a mentor's students and the narrowing by mentor count the students of their cohorts once (ENR-15); a mentor of the cohort may also be assigned to an enrollment directly (ENR-9).
- 2026-11-25: Size, about 14 new scenarios: "не делим" (no split).
- 2026-11-25: Screens, "1–5 согласен" (1 to 5 agreed): the cohorts on the track page show their mentors with marks and say when a cohort has none; assigning with candidates and search, removing after a confirmation, on an active track for who may change the cohorts; the confirmation of deleting a cohort with mentors says they are removed with it; a mentor through the cohort is marked in the enrollments and not removable there; refusals `mentor-leads-cohorts` (ENR-7.1) and `mentor-not-eligible` (COH-8.2); the layout of mentors in the cohort list is decided by a mockup at the plan stage.
- 2026-11-25: Review findings, "3, 6, 13 как рекомендуешь, 1–14 вноси" (3, 6, 13 as recommended, apply 1 to 14):
  - removing a mentor from the track: `mentor-leads-enrollments` when they are a direct mentor of an active enrollment, `mentor-leads-cohorts` only when cohorts are left;
  - a student of the cohort assigned as its mentor stays allowed: "это не бизнес-кейс. система позволяет такое сделать, но в реальности такого не будет" (not a business case; the system allows it but it will not happen), so no scenario of its own;
  - the mentor's students on the track page carry the through-cohort mark; the confirmation of removing a direct mentor says when they still lead through the cohort.
- 2026-11-25: Mockup round 1 (a separate Cohorts card): "зачем нам отдельное окно когорт с менторами, если мы можем подумать над тем, как совместить существуещее окно энроллментов с менторами?" (why a separate cohorts window when we can combine the existing enrollments view with the mentors).
- 2026-11-25: Migration: "миграцию в V1" (the migration into V1): the schema goes into `V1__schema.sql`, already in main.
- 2026-11-25: Mockup round 2: variant A, with changes: "вместо голубого хинта с именами менторов на окне выбранной когорты, пусть будут пилюли с пунктиром с аватаркой и именем ментора. если все менторы не помещаются в одну строку, то пусть лишние уходят под кнопку с попапом. пусть на мобильном экране кнопки заменятся подходящими иконками, как уже сделано в этом окне сейчас" (dashed pills with avatar and name instead of the blue hint; the overflow beyond one row under a button with a popover; icon buttons on the phone, as the view does today). Round 3 made: variant A only, dashed pills, overflow "+N" with a popover, a popover per pill with "Remove", icon buttons on the phone.
- 2026-11-25: The round 3 mockup rendered empty: "макет буквально пуст, в нём нет экранов, только пояснения"; fixed by `ui-designer` (a script error at 390 px). Roadmap idea added on the owner's word: "запиши в роадмапу, что надо научить дизайнера смотреть в браузер" (teach the designer to look at the mockup in a browser); it goes in this change's pull request.
- 2026-11-25: Mockup round 3 rejected and round 4 asked: "нет, пилюли смотрятся плохо, но и голубой хинт мне не очень нравится. кнопку Assign mentor надо вынести туда же, где и кнопка Assign student для когорты, т.к. это действие над когортой. с той же логикой - если действий больше одного - прятать под иконку трёх точек. для действий над энроллментами вообще никаких дополнительных рисований в мокапе не требуется - в этом чендже нет новых действий над энроллментами и менторы у каждого энроллмента уже показываются. нужно просто показать, как будут отмечаться менторы когорты на текущем виде фронта" (no pills, no blue band; Assign mentor among the cohort's actions, under three dots when more than one; enrollments: only the through-cohort mark on the current view).
- 2026-11-26: Mockup round 5: "решение по иконке и пилюле Through cohort мне нравится, но иконка сильно прилипает к имени ментора на мобильном виде - надо отлепить. ... совместить все три варианта в одном: когда открыта когорта, её менторы показываются над таблицей текстом, как в варианте А; на мобильном экране они отмечаются кнопкой с попапом, как в варианте B; при этом у всех когорт, даже неактивных есть кликабельные кнопки с аватарками менторов, как в варианте C ... менее навязчивой ... на всех попапах с менторами кнопку Remove заменить на соответствующую иконку" (the through-cohort icon and badge kept, spaced on the phone; the three variants combined; Remove as the user-removal icon in every mentors popover).
- 2026-11-26: Round 5 questions: "1–5 согласен" (agreed): on the phone a cohort's mentors only once it is chosen; a cohort with no mentor shows plain "No mentors"; a marked mentor shown by a triangle, words in the card; COH-8 stays as it is; `mentor-not-eligible` reuses the enrollment refusal text. Round 6 remarks: no space before the commas of the names line; every mentors popover lies over the page and pushes nothing, and may cross the line between the cohorts and the enrollments up to a few pixels before the students' names; on the phone too, and the phone's mentors button as on desktop (no outline, chevron, hover highlight); all new icons, triangles and badges aligned vertically.
- 2026-11-26: Round 7: ".screens/img_2 - мне кажется, что две иконки восклицательных знаков в текстовом списке менторов стоят на разной высоте друг от друга. в остальном мне всё нравится, кроме окна ассайна менторов на когорту ... я хочу, чтобы было как с ассайном студентов на когорту - балковое добавление. я так понимаю, нужны новые требование под это" (triangles aligned; the assignment of mentors to a cohort becomes a bulk addition, as adding students; the requirements change).
- 2026-11-26: Round 7 questions: "1–3 согласен" (agreed): the bulk assignment of cohort mentors as proposed (COH-9 all or nothing, COH-9.1, 9.3, 9.4 changed, COH-9.5 new, COH-8 a choice of several); the menu item "Assign mentors"; the refusal text with "No mentor was assigned.". Applied to `docs/requirements/cohorts.md`; the changed parts wait for the owner's reading.
- 2026-11-26: "1–3 согласен, нагрузку делаем тут, считаем нагрузку только текущего трека": the changed COH-8 and COH-9 (bulk assignment) read and approved; the mockup approved, with the two texts; each mentor candidate shows the students and cohorts they lead on the current track only. Applied to both candidate lists (COH-10, new COH-10.2; ENR-10, new ENR-10.2; COH-8 and ENR-7 dialogs). Mockup round 8 adds the load line to both dialogs.
- 2026-11-26: "да, в оба окна" (yes, in both dialogs): the load in the requirements read and approved for both candidate lists. Round 9: "нагрузку на широком экране писать справа, выровненной по высоте; переключатели страниц в попапах должны быть выровнены по правому краю" (on desktop the load on the right of the row, centered; pagers right-aligned).
- 2026-11-26: "макет одобряю" (the mockup approved): `.screens/cohort-mentors/mockups/track-cohorts.html`, round 9.
- 2026-11-26: Plan gate: "согласен, план одобряю" (agreed, the plan approved): the plan, the contract and the migration; the candidate schema split (`MentorAssignmentCandidate` for enrollment and cohort candidates, `MentorCandidate` for track candidates). The migration was appended to `V1__schema.sql` by the owner by hand.

- 2026-11-26: "давай как советуешь и коммить" (do as recommended, and commit): the history texts by a fresh `frontend-dev` before the red commit; the red commit made.

## Handover

- Waiting for: nothing; next is task 3.
- To read: this plan (Design, Notes for later tasks); the red commit's tests.
- Documents changed: everything of the change is in the red commit; nothing uncommitted.
- State: tasks 1 and 2 done; red tests committed; trace passes (312 of 312 scenarios with tests, 4 manual).
- Open questions: none.
- Next: brief a fresh `backend-dev` for task 3 (backend, checkpoint when green), then task 4 (sample data), then `frontend-dev` for task 5.

## Agents

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| reviewer | inherited (session model) | a014c9d39287fbb12 | spec: requirements review | 85k |
| ui-designer | inherited | a7b36d74cf42c85ae | plan: mockup of the cohort mentors, 4 rounds | 215k |
| ui-designer | inherited | a75c285e74524be4f | plan: mockup rounds 5 to 9, test list | 142k |
| backend-dev | inherited | a8dfc214258ebdbb8 | plan: task 1 contract | 150k |
| verifier | sonnet | acf78d2642b1ef9bc | plan: backend after the migration | n/a |
| test-engineer | inherited | a17952dec50a4abfa | tests: first pass | 230k |
| frontend-dev | inherited | affe2cf0229ae2078 | implementation: history texts of the new audit actions | 55k |
| test-engineer | inherited | a5887e53ebded0c2d | implementation: lint fix in a red spec | 28k |
