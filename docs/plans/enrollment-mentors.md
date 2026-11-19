# Mentors of an enrollment

Branch: `feature/enrollment-mentors`
Roadmap item: **Mentors of an enrollment:** a coordinator assigns mentors to a student, and a mentor sees the students they lead. Out of scope: mentors of a cohort. Touches enrollments, authorization and audit.
Requirements approved: 2026-11-19 "требования я прочитал, огрехов не нашёл", then, after the TRK-10 sentence was added, "одобряю"
<!-- Only the user's explicit approval of the requirements as they stand: replace "none" with <YYYY-MM-DD> "<their words>". -->

## Scope

Confirmed at the path gate on 2026-11-18.

**Goal.** A coordinator of a track assigns mentors to a particular student, so that it is known who leads them; a mentor sees the students they lead; a student sees the contacts of their mentors. The mentor-enrollment relation is also what will later decide a mentor's access to a student's notes, feedback and assignments (decision 0004).

**Approach.**

- Mentors are data of the enrollment, apart from roles: an enrollment may have several mentors, with no limit.
- Mentors are assigned and removed by whoever may enroll students on the track (ENR-1): an administrator, or a coordinator of that track.
- Only an active user with the mentor role is assigned.
- A mentor sees their students by a new permission of the Mentor role together with the relation at the time of the request. A mentor who loses the role stops seeing their students at once; the assignments stay as data, are marked on the track page (like the marks of ENR-4), and access returns with the role. The permission is not given to coordinators.
- A mentor sees of each student: the name, the email, the track and the start date. More can come later as a separate change.
- A student sees the contacts of their mentors who are active and hold the mentor role (like the coordinators in ENR-5).
- Assigning and removing a mentor are audited, in the history of the track, of the student and of the mentor.
- Client: the mentors in the enrollments of the track page, with assigning and removing; a My students page for a mentor; the mentors' contacts on My enrollment.

**Also in scope** (owner, 2026-11-19): removing a coordinator of a track asks for a confirmation (TRK-10).

**Out of scope.** Mentors of a cohort; anything else a mentor sees or does about a student (notes, feedback, assignments).

**Size.** If the specification grows past about 10 scenarios, the mentor's My students page splits off as a change of its own.

## Requirements

- ENR: ENR-1 (changed; adds ENR-1.5, ENR-1.6), ENR-4 (changed; adds ENR-4.2), ENR-5 (changed; adds ENR-5.3), ENR-6 (changed; adds ENR-6.2), ENR-7 (changed; ENR-7.1 changed), ENR-8 (changed), ENR-9 (new: 9.1 to 9.4), ENR-10 (new: 10.1), ENR-11 (new: 11.1), ENR-12 (new, no scenario); Purpose and Out of scope.
- PERM-1: the Mentor role gets `ENROLLMENTS_READ_MENTORED`; known limitations and out of scope.
- AUD-7: six new actions.
- IDN: one out-of-scope line.

- TRK: TRK-10 (changed, no new scenario): removing a coordinator asks for a confirmation.

## Design

### Contract (`api/openapi.yaml`)

The enrollment is addressed by its own id, not under the track: ENR-1.6 knows only an unknown enrollment. The mentor is addressed in the path (`.../mentors/{userId}`), as the coordinators of a track are, so a missing mentor id cannot be a 400 (review finding 15). Every new operation is in the Enrollments tag.

- `GET /api/v1/enrollments/{id}/mentor-candidates` — `listMentorCandidates`. `q`, `Page`, `Size` exactly as `listEnrollmentCandidates`. Response `MentorCandidatePage` of `{id, name, email}`; sorted by name (case-insensitive), then email, then id. Access as ENR-1 on the enrollment's track; order of checks: the permission (403), the request (400), the enrollment (404), the relation (403). No 409: there is no track-status check (an archived track has no active enrollment, TRK-5).
- `PUT /api/v1/enrollments/{id}/mentors/{userId}` — `assignEnrollmentMentor`. No body. 200 with the enrollment after the change as a `TrackEnrollment`. 409 `mentor-not-eligible` (new problem type) for a disabled user, a user without `MENTOR` and an unknown id alike. Assigning a current mentor again is 200 and records nothing, even when they are no longer eligible.
- `DELETE /api/v1/enrollments/{id}/mentors/{userId}` — `removeEnrollmentMentor`. 200 with the `TrackEnrollment`. Any mentor is removed, eligible or not; a non-mentor or an unknown id is 200 and records nothing. No 409.
- `GET /api/v1/me/students` — `listMyStudents`. Requires `ENROLLMENTS_READ_MENTORED` (403 without it, ENR-11.2). `Page`, `Size`. Response `MentoredEnrollmentPage` of `{id, student: EnrolledStudent, track: TrackReference, startDate, status}`, sorted by the student's name (case-insensitive), then email, then enrollment id. Only enrollments the caller is a mentor of now.
- `TrackEnrollment` gets required `mentors`: array of `EnrollmentMentor` `{id, name, email, disabled, mentorRole}`, sorted by name (case-insensitive), then email; may be empty. All mentors, marked (ENR-4.2).
- `MyEnrollment` gets required `mentors`: array of `EnrollmentContact`, only those active with `MENTOR`, sorted as the coordinators. `EnrollmentContact`'s description widens to cover both.
- `Permission` gets `ENROLLMENTS_READ_MENTORED`.
- The audit action enum and its description get the six actions of AUD-7, with details in the style of `TRACK_STUDENT_ENROLLED` / `USER_ENROLLED`: track entries carry `user` (the student) and `mentor`; `USER_MENTOR_*` carry `mentor` and `track`; `USER_STUDENT_*` carry `user` (the student) and `track`. The track in a user entry has its code and name at that moment.

### Data

- In `V1__schema.sql` (the owner moved it there on 2026-11-19; no database outside the repository has applied V1): table `enrollment_mentors (enrollment_id → enrollments, mentor_id → users, assigned_at, assigned_by → users)`, primary key `(enrollment_id, mentor_id)`, index on `mentor_id` for My students.
- The enrollments module owns the table. Who is eligible (active, holds `MENTOR`) and the marks come from the users module's public API, as the student's eligibility does now.

### Behavior the agents would otherwise guess

- Assigning is idempotent under parallel requests (ENR-9.4): the insert ignores an existing row, and the three audit entries are written only when a row was inserted, in the same transaction. The same for removing: entries only when a row was deleted.
- Access to the three mentor operations resolves the enrollment's track first, then applies exactly the ENR-1 check used for enrolling (reuse it, do not copy it).
- My students checks the permission only; the relation is the filter itself. A mentor who lost the role gets 403 from the permission, since the role is the only source of it (PERM-2).
- An enrollment of a student who is disabled or without the student role still takes mentors (ENR-9); it is refused only from End of an enrollment.

### Client

- Track page, enrollments (`TrackEnrollments`): each enrollment shows its mentors with marks (reuse the marks shown for the student and the coordinators). When `canEnroll` is true: "Assign mentor" on each enrollment opens `AssignMentorDialog` (like `EnrollStudentDialog`: candidates with search, paged), and each mentor has a remove action that asks for a confirmation (shared `confirm-dialog`). After either, the list reloads. A 409 (ENR-7.1) shows a message and reloads the track and the enrollments, as enrolling does now.
- Track page, coordinators (TRK-10): the remove action asks for a confirmation through the same shared dialog.
- My enrollment: a Mentors section like the Coordinators one, with its own empty message.
- My students: a new page, route `/my-students`, a nav link shown only with `ENROLLMENTS_READ_MENTORED`; without it the route goes home (PERM-7), as other permission-guarded routes do. A paged list (shared paginator) with the student's name and email, the track (shared `track-label`), the start date and the student's marks; an empty message.

## What the tests rely on

Frontend tests use host components, the API stub (`core/testing/api-stub`) and the generated client functions, as the existing enrollment specs do.

- Generated client functions: `listMentorCandidates`, `assignEnrollmentMentor`, `removeEnrollmentMentor`, `listMyStudents` in `core/api/fn/enrollments/`; models `EnrollmentMentor`, `MentorCandidate`, `MentorCandidatePage`, `MentoredEnrollment`, `MentoredEnrollmentPage`.
- `TrackEnrollments` (`app-track-enrollments`): inputs unchanged — `trackId`, `canEnroll`, `trackStatus`. Mentor actions are shown when `canEnroll` is true.
- `AssignMentorDialog` (`app-assign-mentor-dialog`) in `features/enrollments/assign-mentor-dialog.ts`, opened with `HlmDialogService` and `context` `{ enrollmentId: string }`, as the project's other dialogs; closes with `true` after a successful assignment.
- `MyStudentsPage` (`app-my-students-page`) in `features/enrollments/my-students-page.ts`, no inputs; route path `my-students`.
- `MyEnrollmentPage`: no new inputs.
- Shell: a "My students" link to `/my-students`.
- Visible texts the tests may query: buttons "Assign mentor" and, per mentor, a remove button with an accessible name containing the mentor's name; the confirmations have a confirm button "Remove".
- Texts the red tests assume (T2; the owner is asked to confirm): the assign dialog's search field is labelled "Mentor", its buttons "Assign" and "Cancel"; a mentor's marks read "Disabled" and "No mentor role"; the My students empty message matches `/no students|any students/i`; the My enrollment empty-mentors message matches `/no mentor/i`, the coordinators' message stays "This track has no coordinator to contact"; the My students paginator's "Next page" asks `listMyStudents` for `page: 1`.
- Test data builders: extend `features/enrollments/test-data.ts` (`trackEnrollment` gets `mentors: []` by default; add `enrollmentMentor`, `mentorCandidate`, `mentoredEnrollment`).

## Tasks

| Task | Scenarios | Who | Status |
|---|---|---|---|
| T1 Contract, the table in `V1__schema.sql`, stubs that keep the build green (new endpoints 501 or empty, `mentors: []`); regenerate the client. Plan gate. | — | backend-dev | done |
| T2 Red tests, backend and frontend | all new and changed | test-engineer | done |
| T3 Backend: mentors of an enrollment — assign, remove, candidates, mentors in the track's enrollments, audit, permission | ENR-1.5, 1.6, 4.2, 6.2, 9.1–9.4, 10.1; PERM-2.1; AUD-7 | backend-dev | done |
| T4 Backend: mentors on one's own enrollment, a mentor's students | ENR-5.3, 11.1, 11.2 | backend-dev | done |
| T5 Backend: sample center — mentors on enrollments covering every state of the screens: an enrollment with several mentors, with none, a mentor since disabled and one without the role; a mentor with students on two tracks, including a disabled student and one without the student role; a mentor with no student; a student whose mentors include one not shown (ENR-5.3). **Checkpoint: backend green.** | — | backend-dev | done |
| T6 Frontend: track page — mentors with marks, assign dialog, removal with confirmation; coordinator removal with confirmation; the six new history actions in the track's and the user's history (replacing T1's temporary fallback) | ENR-7, 7.1; TRK-10; ENR-6.2 as shown in TRK-7, USR-10 | frontend-dev | done |
| T7 Frontend: My enrollment mentors; My students page, its link and route guard | ENR-8, ENR-12 | frontend-dev | done |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

<!-- Written by an agent stopped by the context budget, under a heading with its task; a fresh agent continues from it. Deleted when the task is done. -->

## Notes for later tasks

<!-- From the developers' reports ("Notes for the next task"): what a fresh agent must know and cannot see in the plan or the diff. One line each, with the task it comes from. -->

- T1: stubs left — four methods of `EnrollmentsController` answer 501; `EnrollmentsMapper` gives `mentors` as `List.of()` in two places.
- T1: `Permission.ENROLLMENTS_READ_MENTORED` exists, but `Role.MENTOR` does not grant it yet (T3).
- T1: `UserAuditAction` and `TrackAuditAction` lack the six actions; they need `UserChange`/`TrackChange` records, cases in `UsersMapper`/`TracksMapper` and user-id collection in `TrackService` (about line 303) (T3). Until then `ContractEnumsTests` (users, tracks) and `PathIdConversionTests` stay red.
- T1: `EnrollmentProblems` has no `mentor-not-eligible` constant yet (T3); like the other enrollment problems it is not in the contract's `Problem` type list.
- T1: `Track.canEnroll` now also means assigning and removing mentors; the client relies on it.
- T1: new optional history properties (`HistoryEntry.user`, `.mentor`; `TrackHistoryEntry.mentor`) are `NON_NULL` through the JSON mixins.
- T1: a stale copy of a deleted migration in `backend/target/classes` makes Flyway fail; `./mvnw clean` fixes it.
- T6: replace the `TODO(T6)` fallbacks in `features/users/history-text.ts` and `features/tracks/track-history-text.ts` with texts for the six actions.
- T3: the 409 detail reads "Only an active user with the mentor role can be assigned as a mentor."
- T4: `EnrollmentService.mentored` keys the caller's enrollments by student, assuming one enrollment per student, as `listOfTrack` does; both change with End of an enrollment.
- T4: two agents running the backend verification at once share `backend/target` and break each other's tests (timeouts in `InvitationEmailTests`); run builds one after another. Never wait for a build with a `pgrep -f` loop: it matches its own shell.
- T5: personas for the screens — Mark Mentor: My students with 30 students on two pages, two tracks, disabled and without-role students; Alma Admin-Mentor: 4 students; Milo Mentor-Coordinator: none; Sofia Student: My enrollment shows 2 of her 4 mentors (Mark without a phone, Victor Novak with one); Administrator, Quality Assurance track: Sofia's enrollment with marks "Disabled" (Dan) and "No mentor role" (Thomas Grant), two enrollments without mentors after removals, removals in the track history; Coordinator, Software Engineering: Liam Carter with two mentors, Noah Bennett's disabled mentor; QA Automation: Grace Morgan without mentors; histories of Olivia Hayes, Daniel Kim, Mark Mentor.
- T5: Thomas Grant (not a persona) lost the mentor role in the sample data and is now a user without roles. In sample scripts a mistyped email silently drops a row: check the counts.
- T6: `AssignMentorDialog` closes with `true` on success and `'refused'` on 409/404; assign and remove emit the existing `enrolled` output so the page reloads the history; `PROBLEM.mentorNotEligible` is in `core/problem.ts`; `removeCoordinator` returns `Promise<void>`.
- T7: My students keeps the page in the `page` query parameter; the route uses `requirePermission('ENROLLMENTS_READ_MENTORED')`.
- T2: `MyStudentsPage` exists as an empty component; `AssignMentorDialog` and the `my-students` route do not exist yet (T6, T7).
- T2: backend mentor tests are in `EnrollmentMentorsEndpointTests` and `EnrollmentsConcurrencyTests` (ENR-9.4); `TestEnrollments` has `addMentor`/`removeMentor`, `TestUsers.deleteAll` clears `enrollment_mentors` first.
- T2: ENR-1.1's existing test already covers the reworded scenario and is unchanged; AUD-7's actions are covered by `ContractEnumsTests` and the history assertions of ENR-6.2 and ENR-9.4.

## Red tests

Commit: d597e53

## Decisions

- 2026-11-18: "в жизни обычно между ментором и студентами связь один ко многим, но я не вижу смысла запрещать ассайнить больше одного ментора на энроллмент" (several mentors per enrollment, no limit).
- 2026-11-18: Removing a mentor is part of this change: "да".
- 2026-11-18: Assigning and removing show in the history of the track, the student and the mentor: "по твоей рекомендации".
- 2026-11-18: A mentor sees a student's name, email, track and start date: "давай пока по твоей рекомендации. если пойму, что этого мало - можем расширить возможности отдельным change".
- 2026-11-18: A mentor without the mentor role does not see their students (role and relation, not the relation alone), after the owner asked: "что будет, если с ментора сняли роль ментора, не сняв его с энроллментов? он продолжит видеть студентов?" Confirmed with the path: "подтверждаю, поехали".
- 2026-11-18: Whether a student reads their own enrollment by a permission is decided with the end of an enrollment: "По поводу права студента на просмотр энроллмента давай решим на пункте окончания энроллмента. Grace period пока под вопросом, может, не будем его делать и тогда и конфликта не будет". Noted in the roadmap item End of an enrollment.
- 2026-11-18: Mentors of a track (only a track's mentors are assigned to its enrollments) is the next change, not this one: "Можем сделать менторов следующим ченджом". Added to the roadmap after this item.
- 2026-11-18: No rule that the student role excludes other roles, and no rule against a student leading their own enrollment: "Про несовместимость роли студента и любой другой я имел ввиду именно в контексте ситуации из реального мира. По задумке, роль студента снимется по окончанию энроллмента. Если вчерашний студент возвращается в систему ментором, его приглашают как ментора".
- 2026-11-18: A coordinator sees whom each mentor leads through a filter by mentor in the enrollments of a track, in a separate item: "в роадмапе есть `Search and sorting in the enrollments of a track.`. по-сути, это допиливание списка энроллментов до полноценного вида с поиском, сортировкой и фильтрами. этот пункт надо внести в Next и там будут сделаны фильтры". Added to Next as The enrollments list in full.
- 2026-11-18: Which enrollments a mentor sees after they end: "ок, отложим до End of enrollment".
- 2026-11-18: The mentor's students are paged: "делать список без элементарной пагинации - это экономия на спичках. делаем сразу".
- 2026-11-19: The audit action about the mentor is `USER_STUDENT_*`: "MENTEE звучит непривычно, давай STUDENT".
- 2026-11-19: A mentor is removed after a confirmation: "снятие координатора без предупреждения - это мой недосмотр. и координатор и ментор должны сниматься с предупреждением".
- 2026-11-19: One change, not split: "оставляем одной фичей".
- 2026-11-19: A coordinator of a track is to be removed after a confirmation too, but not in this change: "снятие координатора без предупреждения - это мой недосмотр. и координатор и ментор должны сниматься с предупреждением", then "подтверждение у менторов делаем в этой фиче".
- 2026-11-19: Assigning a mentor to the enrollment of a disabled student, or one without the student role, stays allowed here and is refused from the end of an enrollment: "п.1 запиши запрет в End of enrollment". Noted in the roadmap item.
- 2026-11-19: An ended enrollment keeps its mentors, without access for them after the end: "запиши". Noted in the roadmap item End of an enrollment.
- 2026-11-19: A coordinator follows each mentor's students on the track page, with no page of the mentor: "я хочу, чтобы координатор мог открыть свой трек, увидеть там список активных менторов, у каждого ментора приписка вроде "N students", потом открыть ментора и увидеть его студентов, затем открыть студента и посмотреть какую-нибудь инфу, типа будущих заметок", then "страницу ментора можно и не делать, если она не будет нести в себе ничего, кроме списка его студентов. можно сделать, например, список менторов на треке, который раскрывается при нажати на ментора на список студентов". Put into the roadmap item Mentors of a track; the filter by mentor left the item The enrollments list in full.
- 2026-11-19: Removing a coordinator of a track asks for a confirmation too, in this change: "в прошлый раз я ошибся - оба подтверждения, и для координаторов и для менторов, делаем в этой фиче".
- 2026-11-19: The schema goes into `V1__schema.sql`, not a new migration; the owner moved it themselves: "v2 миграцию я удалил, sql перенёс в v1".
- 2026-11-19: The four enrollment problem types missing from the contract's `Problem` list are added: "допиши".
- 2026-11-19: The new history actions are shown by T6, with a temporary fallback from T1 so the frontend builds; the three contract-match tests stay red until T3: "остальное по рекомендациям".
- 2026-11-19: Plan gate passed with these answers: "остальное по рекомендациям".
- 2026-11-19: The four tests changed to PERM-1 by `test-engineer` after the red commit (PERM-1.1 `RoleTests`, IDN-5.1 `MeEndpointTests`, INV-11.1 `InvitationAcceptanceServiceTests`, PERM-5.1 `AuthenticationChainTests`, now with STUDENT as the known role) stay: "с изменениями тестов согласен".
- 2026-11-19: Backend approved at the checkpoint: "одобряю"; commit: "бэкенд коммитить".
- 2026-11-19: My enrollment: "объеденить карточки Coordinators to contact и Mentors to contact в одну карточку Contacts, а в ней перечислить Coordinators и Mentors. Карточку трека выровнять по высоте только с карточкой контактов - если появится новая карточка, то она по-умолчанию встанет ровно под карточкой трека и контактов".
- 2026-11-19: "в диалогах на ассайн и снятие надо писать имя студента".
- 2026-11-19: No unit tests for the history texts: "тесты на текст не делать".
- 2026-11-19: The console error on the Users page is to be checked: "ошибку в консоли проверить".
- 2026-11-19: The mentors on the track page: "список энроллментов на странице трека абсолютно нечитаемый. под каждым студентом перечислены его менторы ... нужно на карточке студента сделать ненавязчивый список менторов - либо аватарки, которые по наведению высвечивают имя ментора (типа как в соц сетях), либо какой-то всплывающий хинт с именами". Coordinator's choice of the two: avatars.
- 2026-11-19: Run-3 findings: "1-5 исправить, 6 оставить" — fix the avatar overlap and the visibility of the marks, the clipped focus tooltip, the tooltip over an open popover, the doubled accessible names, and the dialog hint ("who are not mentors of this student yet"); keep "Dan Disabled, Disabled".
- 2026-11-19: My enrollment layout: "трек в шапку, контакты в две колонки" — the track (code and name, start date, status) goes into the page header under the title, no track card; one full-width Contacts card with Coordinators and Mentors in two columns on desktop, stacked on a phone.
- 2026-11-19: After looking at the screens: no status chip on My enrollment ("вряд ли студент сможет увидеть другой статус энроллмента, ... зачем студенту эта информация"); the mentors' avatars must not add height to an enrollment row ("между именем студента и датой находится очень большое расстояние"); in the mentor's popover, "Remove" goes to the right on the level of the name and email, as an icon if UX allows ("лучше подобрать иконку, если нет возражений по соображениям UX"). Coordinator's choice: an icon button with a tooltip and an accessible name, the confirmation still follows.
- 2026-11-19: The mentors in the enrollments list are redone as a "Mentors" column with the name as text (mockup E in `.screens/enrollment-mentors/mentor-mockups.html`), since an enrollment almost always has one mentor: "давай E с уточнениями". The refinements: a click on the name opens the popover; an inactive mentor faint with a small ⚠ and a tooltip with the mark; several mentors as "Mark Mentor +1", the "+1" opening the rest; no mentor "—" then "+" (only "—" without the right to assign); on a phone a line "Mentor: …" under the email.
- 2026-11-19: After looking at the Mentors column: the popover's Remove icon centred vertically ("кнопка удаления ментора смещена вверх, надо выровнять по высоте в центр"); the mentor names and the column headers muted as in the mockup, "с оглядкой на правила контраста на обоих темах"; a marked mentor behind "+N" must be visible without opening it ("можно ли как-то подсветить это?") — coordinator's proposal: a small ⚠ on "+N", with the count of inactive ones in its tooltip and accessible name.
- 2026-11-19: The agents' hangs on a dead build are fixed by a separate change: "агентов исправим отдельным ченджем". No sample enrollment with only inactive mentors: "демо данных не надо, я сам посмотрел бледное имя с пометкой". The long coordinator email keeps wrapping: "емейл оставляем". The ⚠ mark looks too faint ("пометка с восклицательным знаком выглядит бледновато", `.screens/img.png`).
- 2026-11-19: No italics for a marked mentor's name ("да, убери курсив"); the screens accepted: "в остальном, вроде, всё ок".
- 2026-11-19: Review findings R1–R6 and the second-pass questions as recommended (fix R1–R6 and "1 more mentor"; keep active mentors first in the Mentors column): "всё по рекомендациям".

## Handover

- Waiting for: the owner accepts the change (stage 7 done), then stage 8 finish.
- To read: the summary of the stop; uncommitted files in `git status`.
- Documents changed: `docs/requirements/README.md` changed (new since the last stop): the Enrollments row names the mentors of an enrollment and a mentor's students. `README.md` changed (new since the last stop): the sample-center paragraph says where mentors show (Mark Mentor, Alma, Milo, Sofia's four mentors). Requirements, contract and V1 committed in d597e53.
- State: review fixes R1–R6, Q1 done (the ENR-4 marks test rewritten on what is perceivable; hidden spans removed; "Remove <name>"; My students paginator past the last page; "1 more mentor"). No re-review: the fixes were small. Second test pass: no real bug found. `verifier` scope all: backend PASS; frontend format, lint, 414/414, build PASS; docs check-docs, requirements tests 64/64, trace 259 of 259 PASS. Uncommitted: the review fixes, the second-pass tests, the two docs.
- Open questions: none.
- On approval: stage 8 — no decision (no cross-cutting choice beyond 0004), a guide passage only if the owner agrees with its four answers; delete the roadmap item, this plan and `.screens/enrollment-mentors/`; commit if the owner asks; suggest `/pr` and then `/usage-review`.
- On remarks: as the owner says.

## Agents

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| reviewer | definition default | a605e2f72efae8ce6 | spec: requirements review | 79k |
| backend-dev | definition default | a80fded15da6ee003 | plan: T1; implementation: T3 | 208k |
| test-engineer | definition default | a644a079adf0c48c3 | tests first: T2 | 169k |
| test-engineer | definition default | a92e56069c3ee2545 | implementation: four tests to PERM-1 | 30k |
| backend-dev | definition default | ae4dd640762432271 | implementation: T4, T5 | 123k |
| verifier | definition default | a3dfc08a0856a675a | implementation: backend | 13k |
| frontend-dev | definition default | ab4373096fff40aa0 | implementation: T6, T7, screen fixes | 144k |
| test-engineer | definition default | ab8fdc507e3a6b8d7 | implementation: removal tests for the popover | 35k |
| manual-tester | definition default | a3a858b4d254114ee | implementation: re-check, run-3 | 45k |
| frontend-dev | definition default | ac806180610113bd2 | implementation: screen fixes after run-3 to run-8 | 135k+ |
| manual-tester | definition default | a84454314f38ff269 | implementation: re-check, run-4 | 45k |
| frontend-dev | definition default | a58e2508a8f8be1ba | implementation: ⚠ mark, italics | 61k |
| test-engineer | definition default | a7be15806a54d0014 | second pass | 76k |
| reviewer | definition default | a14c1a80bceaee82a | review | 178k |
| verifier | definition default | ae943d332b0ce23f6 | review: all | — |
| manual-tester | definition default | add27f14315eca14c | implementation: look at screens, run-1 | 60k |
| manual-tester | haiku | a2f8b80299f7fcd12 | implementation: manual checks, run-2 | — |
