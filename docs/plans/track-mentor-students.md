# Students of the mentors of a track

Branch: `feature/track-mentor-students`
Roadmap item: Next 1, **Students of the mentors of a track:** the track page shows each active mentor of the track with the number of students they lead and, on request, those students, so that a coordinator follows each mentor's students without a page of the mentor. Touches tracks and enrollments.
Requirements approved: 2026-11-22 "одобряю требования, продолжай"

## Scope

Goal, as confirmed at the path gate (2026-11-22): a coordinator, and an administrator, sees on the track page, for each mentor of the track, the number of students that mentor leads and, on expanding, those students. User profile pages are not part of this change: the track page does not replace a page of the mentor, it is what the coordinator needs now. Pages of a user (student, mentor), reached from names on every screen (track, calendar, assignments), will be needed later and go to Ideas in the roadmap.

Approach, as confirmed:

- The number of active enrollments a mentor leads is added to the mentor of a track in the track's response (TRK-4).
- The students under a mentor are loaded on expanding, through a `mentor` filter of the existing list of the enrollments of a track (ENR-4), reusing its order, paging and marks; the same filter is later one of the filters of Next 2 (the enrollments list in full). A separate endpoint for the students of a mentor of a track was rejected as a second copy of the list.
- The expandable list of students under each mentor is a new pattern: at the plan stage `ui-designer` makes an HTML mockup with variants, from the sample center's data, and the owner chooses one; a task adds mentors with different numbers of students (zero and marked mentors included) to the sample center.
- No migration is expected: the change only reads existing relations.

In: the number on each mentor of a track, the mentor filter of the enrollments of a track, the expandable students on the track page, the sample center data, the roadmap edits (the idea of user pages added to Ideas; "without a page of the mentor" removed from the item's line before the item is deleted).

Out: user profile pages; other filters, search and sorting of the enrollments list (Next 2).


## Requirements

- ENR-15 Students of the mentors of a track: added, with scenarios ENR-15.1 (the number), ENR-15.2 (the mentor filter), ENR-15.3 (an empty answer).
- ENR-4: points to the mentor filter (ENR-15); no scenario changes.
- ENR-7 (client): each mentor shows the number of their students and, with `ENROLLMENTS_READ`, the students on request; no scenario.
- TRK-4: each mentor of a track carries the number of students (ENR-15); TRK's known limitation on `TRACKS_READ` widened to the number.
- ENR Out of scope: the line about the number and the students removed; the line about filters narrowed to filters other than by mentor.

## Design

Mockup: `.screens/track-mentor-students/mockups/track-page-mentors.html`, variant A (the accordion, called A2 in rounds 2–6) as of round 8. Its blue "Chosen", "Round 7" notes and tint table are part of the design; where they disagree with this section, this section decides.

Contract (`api/openapi.yaml`):
- `TrackMentor` gains a required `students` (integer, minimum 0): the number of active enrollments on the track the user is a mentor of, whatever the marks of the students (ENR-15). Every operation that returns a track carries it.
- `listTrackEnrollments` gains an optional query parameter `mentor` (uuid): narrows to the enrollments the user is a mentor of, each with all its mentors; order, paging and marks unchanged. An id no user has, or of a user who leads nothing on the track, is 200 with an empty page; a malformed uuid is 400 like other parameters. The checks keep their order: permission 403 (ENR-1.1, filtered or not), 404 for the track (ENR-1.4).
- No migration.

Backend:
- The count is computed for all mentors of a track in one query, not one per mentor. It counts `ACTIVE` enrollments only, so that the end of an enrollment, when it comes, changes it in one place.
- Sample center: the track the personas coordinate gets mentors that show every case of the mockup: one leading more than 8 students (the scroll area and the "End of the list" foot; the mockup uses 22), one with 1–2, one with none, a disabled mentor leading a student, a mentor without the mentor role leading students, and at least one student marked disabled or without the student role under a mentor. Plus one long email to show the wrapping.

Frontend (track page, the Mentors card, ENR-7):
- An accordion: one container, rows divided by lines. Each row: at the left a chevron button (the only toggle; rotates when open), the avatar, the name (a link to the user page with `USERS_READ`, as today), email and marks, the count as plain text, Remove at the right (an icon with a tooltip on the phone).
- A mentor with 0 students has no chevron (the space stays so names align) and never opens. A user without `ENROLLMENTS_READ` sees the counts and no chevrons.
- Several rows can be open at once. Expanding loads all the mentor's enrollments with `mentor=<id>` (size 100, following pages until all are loaded): all of them, not a page, in a scroll area; no sorting control.
- Open row: the header and the table are one tinted block (the L of round 6) with a 4 px accent bar on the left; the table has a fixed layout (Student 36%, Email the rest, Started 120 px) so the columns line up across mentors, long names and emails wrap in their column. No caption over the table, on any width (owner, after the screen runs). When more than 8 students, a foot "End of the list: all N students shown" and a bottom chevron in the gutter, both only once the list has loaded; the bottom chevron hides the list, moves focus to that mentor's top chevron and scrolls it into view.
- Phone (below sm, 640 px): no tinted gutter below the open header, no caption; the 4 px bar runs the whole height of the open mentor; student rows are cards using the full width, marks on their own line, emails break after "@" and "."; the bottom chevron is on the left of the foot row in a tinted 40 px cell; scroll area `max-height: min(26rem, 55dvh)`; on an archived track the gutter column is dropped.
- Loading: a labelled progress bar in the open block; failed: the error and Retry in the block, the mentors stay usable.
- After enrolling, assigning or removing a mentor of an enrollment, or naming or removing a mentor of the track, the track and every open students list reload (ENR-7.1 included).
- Tints and focus ring: the mockup's tint table (mixes of existing tokens), no new theme tokens.

## What the tests rely on

API:
- `TrackMentor.students` (integer) on every track response.
- `GET /api/v1/tracks/{id}/enrollments?mentor=<uuid>`: the `mentor` query parameter.

UI (exact texts, from the mockup):
- Count: "1 student", "N students", and "No students" for 0.
- Top chevron: a button with accessible name "Students of <name>" and `aria-expanded` carrying the state; absent for a mentor with 0 students and for a user without `ENROLLMENTS_READ`.
- Open table headers: "Student", "Email", "Started"; the student's marks "Disabled" and "No student role" as in the enrollments list.
- No caption over the open table (removed by the owner after the screen runs).
- Foot (more than 8 students, after loading): "End of the list: all N students shown"; bottom control named "Hide the students of <name> (back to top)".
- Loading: a progressbar named "Loading the students of <name>" and text "Loading the students of <name>..."; failed: an alert "Could not load the students of <name>." and a "Retry" button.
- Empty answer (a mentor whose count was stale): "<name> leads no students on this track."
- Remove keeps its accessible name "Remove <name>".

## Tasks

| Task | Scenarios | Who | Status |
|---|---|---|---|
| 1. Contract: `TrackMentor.students`, the `mentor` parameter of `listTrackEnrollments` | ENR-15.1–15.3, ENR-1.1, ENR-1.4 | backend-dev | done |
| 2. Red tests, backend and frontend | ENR-15.1–15.3, ENR-1.1, ENR-1.4, ENR-7 (client) | test-engineer | done |
| 3. Backend: the count and the filter; sample center data. Checkpoint: backend green | ENR-15.1–15.3, ENR-1.1, ENR-1.4 | backend-dev | done |
| 4. Frontend: the accordion of mentors on the track page | ENR-7, ENR-7.1 | frontend-dev | done |
| 5. Owner's details: no caption (test, then code), bundle warning budget, a sample student with a long email | ENR-7 | test-engineer, frontend-dev, backend-dev | done |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

## Notes for later tasks

- From task 1: task 2 adds `students` (e.g. `students: 0`) to `trackMentor()` in `frontend/src/app/features/tracks/test-data.ts`; until then `npm run build` fails with TS2322 there (the only error). Task 3 replaces two stubs: the placeholder `0` in `TracksMapper.track(...)` and the 501 branch for `mentor` in `EnrollmentsController.listTrackEnrollments` (generated parameter order `(id, mentor, page, size)`). The 501 sits after `@RequirePermission`, so a red ENR-1.1 test with `mentor` may already pass; ENR-1.4 and the filtering stay red. Frontend: generated `listTrackEnrollments` params accept `mentor?: string`; `TrackMentor.students: number` is required. `students` is `int32`, minimum 0.
- From task 2: backend tests in `EnrollmentsEndpointTests` (ENR-15.2, ENR-15.3, ENR-1.1 and ENR-1.4 extended) and `TrackMentorsEndpointTests` (ENR-15.1, which also covers TRK-4's count). ENR-1.1 with `mentor` already passes (403 before the stub). ENR-15.2, 15.3 and 1.4 fail in the `send` helper's OpenAPI response validation on the stub's undeclared 501, not on an `assertThat`; they turn into real assertions once task 3 answers 200/404. Frontend: `track-mentor-students.spec.ts`, 13 tests without ids (ENR-7 has no scenario; `trace` refuses a requirement id); they assume the foot, caption and bottom control are in the DOM whatever the viewport (jsdom has no media queries), so the phone-only rules are not unit-tested (manual check); the progressbar's name may be `aria-label` or text.

## Red tests

Commit: bbbd21b (requirements, contract, plan, red tests, backend stubs, usage record).

## Decisions

- 2026-11-22: "сейчас мы можем обойтись выпадающими списками студентов под менторами" — expandable lists of students under the mentors on the track page; user profile pages later, not in this change.
- 2026-11-22: The expandable list is a new pattern and gets an HTML mockup with variants before tests rely on it.
- 2026-11-22: "согласен со всеми тремя": every mentor of the track carries the number, marked ones included; the number counts the active enrollments on the track the user is a mentor of, whatever the marks of their students; the number is part of the track (TRK-4) under `TRACKS_READ` with no `ENROLLMENTS_READ` condition, and the students under a mentor need `ENROLLMENTS_READ`.
- 2026-11-22: "п. 1 принимаю": a filtered read of the enrollments of a track by a mentor without `ENROLLMENTS_READ`, narrowed to themselves, is 403 like the unfiltered one (ENR-1.1).
- 2026-11-22: "по п. 2 — пока (а), но надо показать на мокапе, как это будет выглядеть": the students under a mentor are shown all, not a page of them; the mockup must show how that looks.
- 2026-11-22: "остальные тоже по рекомендации": review findings 3–9 applied as recommended.
- 2026-11-22: Mockup round 1: "вариант А самый сбалансированный, но мне не нравится его внешний вид ... я бы хотел видеть ментора как некую карту, которая раскрывается в список и создаёт с ним единую картину, где карта ментора является некой шапкой для выпадающей таблицы".
- 2026-11-22: "ментора без студентов не раскрывать"; "давай без управления сортировкой пока что"; "список прокручивающийся".
- 2026-11-22: Mockup round 2: "можем ли мы взять вариант А2, но кнопкой, триггерящей раскрытие списка, сделать иконку галочки или дерева слева от имени ментора, а количество студентов сделать просто текстом? тем самым можно сохранить возможность кликать на имя ментора и переходить на его профиль без раскрытия списка".
- 2026-11-22: Mockup round 3: "можем ли слегка акцентировать фон всей области за шевроном от левого края списка направо, не доходя до имени ментора и при наведении на шеврон сильнее подсвечивать этот фон, а не делать обводку шеврона?"; "на мобильном списке кнопка Remove уезжает вниз ... лучше сделать иконку, вместо целого слова, и вынести её вправо с выравниванием по высоте с одинаковыми отступами сверху и снизу".
- 2026-11-22: Mockup round 4: "кнопка Collapse сейчас немного выбивается из шаблона, плюс нужно добавить аватарки менторам и студентам и выровнять их по высоте".
- 2026-11-22: Mockup round 5: "емейлы и даты не выровнены по высоте в таблицах"; "кнопку Collapse может стоит заменить таким же шевроном слева, который будет сигналить о возможности свернуть список снизу?"; "нужно пройтись по выбранным цветам на обоих темах и провалидировать их контраст".
- 2026-11-22: Mockup round 6: "хорошая ли идея сделать фон карточки ментора в раскрытом виде таким же, как и фон линии на фоне шевронов слева? подсветка раскрытого списка будет буквой Г" — done; hover only on the gutter.
- 2026-11-22: "вроде неплохо. прогони финальный макет на соответствие UI/UX best practice ... запусти отдельного саб-агента под эту задачу".
- 2026-11-22: "п. 5 — (в), п. 9 принимаю, остальное по рекомендациям"; on the phone: "можем и линию и строку убрать?" — the gutter below the open header and the caption removed on the phone.
- 2026-11-22: "запиши обе идеи в Ideas": quieter dark-theme Helm buttons and `prefers-reduced-motion` for the loading bar added to Ideas in the roadmap, in this branch.
- 2026-11-22: "да, запиши это как идею": measured contrast of mockups added to Ideas in the roadmap, in this branch.
- 2026-11-22: The idea of user pages reached from names on every screen is added to Ideas in the roadmap, in this branch.
- 2026-11-22: Mockup round 8: "надо вернуть и тонкую линию и шеврон обратно на мобильном экране, а обычный экран вообще не трогать" — on the phone the bottom chevron goes back to the left and the thin line of the open list returns; the desktop goes back to round 6 (caption included).
- 2026-11-22: Mockup round 8 rework, from the owner's screenshot `.screens/img.png`: "синяя тонкая линия слева - оставить по всей высоте списка, как на скриншоте. голубой фон под шевроном - убрать, список студентов сдвинуть влево к тонкой синей линии. шеврон снизу оставить таким же, как на скриншоте, то есть слева и с фоном".
- 2026-11-22: Mockup round 8, from `.screens/img_1.png`: "колонки не выровнены между менторами. надо выровнять, контент колонки переносить" — desktop table back to a fixed layout (Student 36%, Email the rest, Started 120 px), long content wraps in its column.
- 2026-11-22: "одобряю макет, подпись не нужна, колонка ок": variant A as of round 8 approved; no caption on the phone; on an archived track the gutter column is dropped on the phone and kept on desktop.
- 2026-11-22: Plan gate: "одобряю план и контракт, продолжай".
- 2026-11-22: "оба по рекомендации, коммить": the three backend tests red on the contract check of the stub's 501 are accepted as red; ENR-7 gets no scenarios, its client tests carry no ids.
- 2026-11-22: Backend checkpoint: "одобряю, коммить и продолжай".
- 2026-11-22: After the screen runs: "бандл по рекомендации, полосу оставить, студента с длинным email добавить. строку под шапкой ... наверное, стоит убрать и с десктопного вида - она не несёт полезной информации": `maximumWarning` raised to about 575 kB; the desktop gutter stays; a sample student with a long email; no caption over the students table on any width. The remove dialog stays as it is (recommendation, no objection).
- 2026-11-22: Screens approved: "одобряю, коммить и запускай ревью"; the second test pass is skipped as a small change.

## Handover

- Waiting for: `reviewer` on the branch against main (review loop step 1).
- To read: this plan; `docs/requirements/enrollments.md` (ENR-1, ENR-4, ENR-7, ENR-15) and `docs/requirements/tracks.md` (TRK-4).
- Documents changed:
  - `docs/requirements/enrollments.md`, changed: ENR-15 added (the number of students of each mentor of a track, the mentor filter of the enrollments of a track; scenarios ENR-15.1 to 15.3); ENR-1.1 and ENR-1.4 cover the filtered list; ENR-4 points to the filter; ENR-7 shows the number with each mentor and, with `ENROLLMENTS_READ`, all the students a mentor leads on request; Out of scope: the number and students line removed, filters narrowed to other than by mentor, the number added to what is decided with the end of an enrollment.
  - `docs/requirements/tracks.md`, changed: TRK-4 gives each mentor the number of students (ENR-15); the known limitation on `TRACKS_READ` covers the number without `ENROLLMENTS_READ`.
  - `docs/requirements/README.md`, changed: the ENR summary names the students of each mentor of a track.
  - `docs/roadmap.md`, changed: Next 1 says "each mentor" and drops "without a page of the mentor"; Ideas gains pages of a user reached from their name.
  - `api/openapi.yaml`, changed: `TrackMentor.students` (required, int32, minimum 0) and the optional `mentor` uuid parameter of `listTrackEnrollments`, with its check order (403, 400, 404).
- State: requirements approved; mockup approved (round 8); Design, What the tests rely on and Tasks written; plan gate passed; task 1 done (contract plus two backend stubs); task 2 done (red commit bbbd21b); task 3 committed (e74a84e); tasks 4 and 5 done and committed after the owner's approval; second test pass skipped (small change); review in progress.
- Open questions: none.
- Next: the owner's choices to `frontend-dev` (fresh: the last one is at 160k), `manual-tester` on the affected states, then the owner looks at the screens; then the review loop (the second test pass may be skipped as a small change).

## Agents

<!-- Agents can be continued only within the session that started them; a new session starts fresh ones from this plan. Context is the size from the agent's last completion notification. Model is the `model` parameter of the call, or else the `model:` line of the agent's definition. -->

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| reviewer | definition | a2a684f2d45f66e66 | spec, requirements review | 41k |
| ui-designer | definition | a648b47272e178ed5 | plan, mockup | 142k |
| general-purpose | definition | ac0839fe2e0f7d375 | plan, UI/UX review of the mockup | — |
| ui-designer | definition | aa17a66bebf9a253b | plan, mockup round 7 | 67k |
| ui-designer | definition | a79709978cc92a447 | plan, mockup round 8 | 60k |
| backend-dev | definition | ae06b59b172aa24c1 | plan, contract (task 1); backend (task 3); sample student (task 5) | 134k |
| test-engineer | definition | a7ba6ef9886a921c1 | tests, first pass (task 2) | 99k |
| frontend-dev | definition | a446d8b90cc60c91c | implementation (task 4), bundle investigation, visual fixes, task 5 | 168k |
| test-engineer | definition | acfa74a6212d51eb5 | implementation, spec fixes | 28k |
| manual-tester | definition | a2c5988725c303357 | implementation, screen run | 66k |
| manual-tester | haiku | a32eb2068bec029fa | implementation, numbered checks 5–6 | — |
| manual-tester | definition | aa69aed72d0592f6c | implementation, re-check of three fixes | — |
| test-engineer | definition | a31890a49f96f5b54 | implementation, no-caption spec (task 5) | 26k |
| manual-tester | definition | a15431e6f0b8d6fba | implementation, re-check task 5 | — |
