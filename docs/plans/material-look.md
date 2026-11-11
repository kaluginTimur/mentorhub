# A coherent look on Angular Material

Branch: `chore/material-look`
Roadmap item: none

<!-- The brief of a change outside the requirements. Its headings are those of the plan template, so a change that moves to the stages gains sections and loses nothing. -->

## Scope

**Goal.** Mentors and curators on the pilot get an application that looks coherent and tidy, in the color scheme of the center's internal corporate tools, before Tracking core adds its screens, so that those screens are built in the final look instead of being restyled later. Fonts and icons are served by the application, not by Google. List columns do not shift between pages.

**Approach.**

- Angular Material stays the UI library. The look is set only through the Material theme and its token overrides in `frontend/src/styles.scss`, never by styling component internals with CSS.
- The reference: the owner's screenshots of three internal corporate tools in `.screens/corp-product-samples/` (git-ignored, local only). There is no brand book. What they share: a dark navy top bar with white top navigation and the active section underlined in amber; bright blue for primary buttons and links; light-gray filled inputs with a small radius; white content surfaces with thin borders or soft shadows; a geometric sans font (Montserrat-like); a denser layout than Material's default.
- The navigation stays in the top toolbar (as IDN-11 describes it).
- One shared page frame instead of the padding and `max-width` each page repeats.
- Light and dark color schemes with a switch button in the toolbar: the system's scheme by default, the choice kept in the browser.
- An avatar with initials on a background color derived from the user, not chosen by the user.
- Fonts and Material Symbols served from npm packages; no requests to Google Fonts at build or run time (issue #12).
- Stable column widths in paginated lists, the actions column sized to its buttons with an accessible header (issues #27, #28); the rule goes into the list page pattern of `docs/ui/guidelines.md`.
- The owner reviews the look in the browser (`ng serve` applies style changes live); the main visual questions are settled first, with variants side by side where a choice is easier to make by eye, then iterations on the running application.

**Out.** A full mobile interface (tables turned into cards, touch-specific layouts). Deep restyling of Material components past their tokens. A user-chosen avatar color (it needs the contract and a migration). Keeping the theme choice in the user's profile on the server.

**Requirements.** Searched: Identity, Authorization, Platform, User administration, Invitations. Colors, fonts and layout are UI details, which `docs/README.md` leaves to the code. IDN-11 and USR-12 describe what the toolbar and the account menu contain (the application name, the allowed links, the menu with name, email, Profile and Sign out); that stays as it is. The theme switch in the account menu and the avatar are UI details.

## Tasks

| Task | Who | Status |
|---|---|---|
| 1. Settle the main visual questions with the owner (font, navigation, background, shapes, density, avatar, theme switch, page width) | lead | done |
| 2. Fonts and Material Symbols from npm, no Google Fonts at build or run time (#12) | frontend-dev | done |
| 3. Theme: palettes, typography, density, shapes and component token overrides, light and dark | frontend-dev | done |
| 3a. Status colors and role chips: a colored dot before the status text in the Users and Invitations lists and on the user page (Active and Accepted green; Pending blue; Disabled, Expired and Revoked gray; Email failed red as now), the text kept; role chips soft filled in the secondary container color, no outline, no per-role colors; a success color of the app's own for both schemes; with tests of the status-to-color mapping | frontend-dev | done |
| 4. Shell: "MentorHub" as a logo link to home, navy top bar, active section indicator, avatar with initials, theme switch button in the toolbar (system, light, dark; system by default, kept in the browser), section links in a menu on narrow screens, with tests | frontend-dev | done |
| 5. Shared page frame (the home footer with name and version at the bottom of the page), fluid width with side gutters, no horizontal page scroll down to 360px (tables scroll inside, filters wrap, dialogs full screen on phones); every page uses it; the rule in `docs/ui/guidelines.md`; the profile page as variant A of the Decisions (one card) | frontend-dev | done |
| 6. Stable column widths in the Users and Invitations lists, actions column with an accessible header (#27, #28); the row actions of the Invitations list in one row menu (variant A of the Decisions); the rule in `docs/ui/guidelines.md` | frontend-dev, lead | done |
| 7. Owner looks at every screen in both schemes on the sample center; fixes (checkpoint) | owner, frontend-dev | done |
| 8. Review and verification | reviewer, verifier | done |

After each frontend task: `manual-tester` looks at the changed screens first, `frontend-dev` fixes what it finds, then the owner looks.

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

<!-- Written by an agent stopped by the context budget, under a heading with its task; a fresh agent continues from it. Deleted when the task is done. -->

## Decisions

<!-- One line per decision of the user, in their words. What is the agent's own stays out of this list. They go into the pull request description before this brief is deleted. -->

- 2026-11-10: Material was a placeholder; the examples of the alternatives (PrimeNG, spartan/ui, ng-zorro, Taiga UI) "all look the same, and rather dull", so take the path of least resistance: keep Material and make something not ugly out of it.
- 2026-11-10: No architecture decision for keeping Material; if it must be mentioned, somewhere else.
- 2026-11-10: The pilot is 100% without a brand book; the reference is screenshots of internal UIs, with an accent on blue.
- 2026-11-10: No `manual-tester` for the look: the owner looks in the browser.
- 2026-11-10: Reversed after the toolbar initials showed half clipped: an agent takes the first look at the screens so defects are fixed before the owner looks.
- 2026-11-10: Dark theme: yes.
- 2026-11-10: The rule about list column widths in `docs/ui/guidelines.md`: yes.
- 2026-11-10: Issues #12, #27 and #28 are closed in this change.
- 2026-11-10: The avatar is not customizable by the user for now.
- 2026-11-10: The goal, the approach and the path `chore/material-look` confirmed.
- 2026-11-10: No mockup in Figma or HTML: the look is built directly in the frontend and compared live.
- 2026-11-10: Theme switch: a button in the toolbar (no setting on the profile page).
- 2026-11-10: Avatar: initials with a color derived from the id.
- 2026-11-10: "The rest by your recommendations": Montserrat for headings and Inter for text and tables; uppercase semibold navigation labels; a gray background with white surfaces; radius about 6px on buttons and fields, about 8px on cards; density -1; only the avatar in the toolbar, name and email inside the account menu; page width 1280px.
- 2026-11-10: Page width: fluid everywhere ("ширина резиновая везде"), replacing 1280px; side gutters only.
- 2026-11-10: "MentorHub" in the toolbar becomes a clickable logo that leads home (there is no way back to home from other pages now). The home footer with the name and version moves to the bottom of the page instead of sitting right under "Welcome, ..." (removing it would change IDN-11).
- 2026-11-10: Basic responsiveness in this change: yes. (Proposed: on narrow screens the section links go into a menu in the toolbar while the avatar and theme button stay; 16px gutters; no horizontal page scroll down to 360px, wide tables scroll inside their block; filters wrap; dialogs full screen on phones; the rule in `docs/ui/guidelines.md`. A full mobile interface stays out.)
- 2026-11-11: The dark scheme lost its blue accent and became gray-black; the dark scheme of the first look (`.screens/material-look/theme/`) is liked more: bring back its hues, keeping the other fixes of the findings.
- 2026-11-11: The top bar the same color as the bar on `template_1_obfuscated.png`; it does not change color when the scheme changes.
- 2026-11-11: Task 3 theme looked at in the browser: "всё ок, давай по рекомендации": field hints muted in this task; bar focus and the menu overlapping the bar in task 4; the Role select panel height left as Material has it.
- 2026-11-11: Field hints: one CSS rule `mat-hint { color: on-surface-variant }` in `styles.scss` (Material has no hint color token), accepted as the one exception to "only the theme and its tokens".
- 2026-11-11: Dark inner panels (cards with history and profile data) "lean gray-green"; make them a bit more blue, in the one blue tone of the bar and the page.
- 2026-11-11: Palette review: "давай все пункты по твоей рекомендации": light fields one step darker, light page with a barely noticeable cool tint; dark panel saturation left as is for now. Statuses should be colored too; for the gray role chips the owner asked what can be done and whether anything should be.
- 2026-11-11: The light scheme accepted with the last tweaks ("текущая светлая тема мне нравится").
- 2026-11-11: Dark panel saturation brought into 20–35% after all ("пункт про насыщенность панелей на тёмной теме тоже делаем в рамках 20-35%").
- 2026-11-11: Task 3a "по твоему предложению": statuses as a colored dot before the text, role chips soft filled without per-role colors.
- 2026-11-11: After the look at task 3a, "давай по рекомендациям": the status next to the name as a dot and text without a chip; disabled controls in dark fixed to the usual disabled look. The owner asked whether the Invite button and similar ones are too bright in dark.
- 2026-11-11: "да, делаем по рекомендациям": filled buttons in dark the same #0059c6 as in light; Material's strong focus indicators app-wide, with a ring visible on the top bar too.
- 2026-11-11: "да, поправь нажатие". The underlined user names in the list and the "Users" link on the user page "create the impression of a bare HTML hyperlink".
- 2026-11-11: After the recheck of links and buttons: "давай поправим «Back to users» и продолжим".
- 2026-11-11: Task 4 (shell) started: "запускай".
- 2026-11-11: After task 4: "иконка аватарки в шапке съехала"; asked whether to show the avatar in the users list and/or in the profile; "остальное по рекомендации" (the IDN-11.1 test finds the trigger by its aria-label, the bar's DOM in visual order, amber #f5b301 kept, then manual-tester).
- 2026-11-11: "да, по рекомендации": the avatar also on the user page header, the profile header and before the name in the users list; the IDN-11.1 test opens the account menu and checks the name shown there.
- 2026-11-11: The user page header "как будто аватар имя и статус вытянулись в одну линию и выглядят немного несбалансированно"; asked how the avatar color is computed and whether the scarce, repeating set of colors is intended. Then "да, по рекомендации": a two-row header (avatar ~56px, the name, the status under it; the same on the profile), a well-mixing hash and a palette of 12 distinct colors.
- 2026-11-11: "да, давай по рекомендации": slate and lime green out of the avatar palette (10 colors), then manual-tester. "Меня немного смущает вид списков. Кажется, будто ряды в списках невысокие, а разделители между ними довольно яркие по сравнению с фоном".
- 2026-11-11: "да, по рекомендации": table rows 52px, softer row dividers, the header line kept, in task 4. "Списки приглашений выглядят плохо с этими кнопками в линию на каждое приглашение и профиль текущего пользователя тоже неказистый. Будем это решать в следующих пунктах?"
- 2026-11-11: "У меня линия под заголовком не ярче разделителей строк." On the profile: "пусть агент предложит варианты лэйаута профиля"; chose variant A: one card about 640px wide, a header like the user page (avatar, name, email under it), a divider, First and Last name in two columns, Phone full width with its hint on one line, Save at the bottom right.
- 2026-11-11: The header line "выглядит странновато - первая линия чуть ярче остальных, но в остальном ничем не выделяется"; asked for the best practice; "да, делаем тонированную шапку": a tinted header row with muted semibold header text, the header line as soft as the row dividers.
- 2026-11-11: "На тёмной теме шапка таблицы мне нравится, а на светлой она почти сливается с фоном".
- 2026-11-11: The light header at #e5e9ef: "сейчас выглядит неплохо. Запускай агента" (manual-tester's look at task 4).
- 2026-11-11: manual-tester's findings of task 4 "да, по рекомендации": the active section marked in the sections menu and a 4px gap between bar buttons now; page scroll at narrow widths, wrapping filters and the profile phone hint in task 5.
- 2026-11-11: Asked whether the Invite button in the Users and Invitations lists is too loud and whether a "+ Invite" link would look better; "да, по рекомендации": a tonal button with the `person_add` icon and "Invite" in both lists, filled buttons kept for confirmations in dialogs.
- 2026-11-11: Task 4 "всё ок", but "кнопка Invite теперь кажется того же цвета, что и пилюли ролей. Это ок?"; "да, по рекомендации": the tonal button in `primary-container`, role chips unchanged.
- 2026-11-11: primary-container equals the chips in light; "давай обводку, т.к. в тёмной теме кнопка стала того же цвета, что и до изменений": Invite as an outlined button in both schemes, the tonal token override removed.
- 2026-11-11: The outlined button: "что-то прозрачный фон кнопки и обвода мне не нравится. Она теперь странно выделяется". "Давай поменяем пилюли": Invite back to a tonal button (`secondary-container`, the Material default), role chips to a neutral tone so they no longer match it.
- 2026-11-11: "ок, исключение для шапки таблиц принимаем": task 4 accepted; the header-row background rule is the second exception to "only the theme and its tokens".
- 2026-11-11: The "Page frame" passage in `docs/ui/guidelines.md`: "с правилом согласен". Asked, with a screenshot at 2560x1440 (`.screens/img_2.png`), whether it is OK that the page body takes all the space of a large screen.
- 2026-11-11: "делаем": page content capped at about 1440px and centred, the top bar full width, fluid below that (partly replacing "ширина резиновая везде"); the search field capped at about 480px with the filters beside it.
- 2026-11-11: "кнопка \"Invite\" теперь повисла в воздухе, т.к. из под неё уехали выпадайки"; "да, по рекомендации": Invite at the right end of the filter row in both lists, the `h1` alone above it; on narrow screens Invite wraps to its own line at the right.
- 2026-11-11: On narrow screens Invite "выглядит плохо", alone at the right under the left-aligned filters; "да, по рекомендации": under 600px Invite in the title row, right of the `h1`. The button shorter than the fields in the filter row: kept as it is ("да, по рекомендации").
- 2026-11-11: "да, по рекомендации": the Tab order on phones stays as it is (Invite last); `frontend-dev` investigates the test timeouts first (main and this branch under the same conditions, fix the cause, no raised timeout to hide it), then `manual-tester` looks at task 5.
- 2026-11-11: After the second WSL crash, "да, запускай по рекомендации": a fresh `frontend-dev` limits the test workers and checks carefully (one run at a time, `free -m` before each, first without the stack and `ng serve`); the comparison with main is not repeated.
- 2026-11-11: After the worker cap (4 green runs): "да, запускай": the stack and `ng serve` up again, `manual-tester` looks at task 5; then one test run under that load.
- 2026-11-11: manual-tester's points of task 5 "да, по рекомендации": under 600px the filter fields full width and above it the search growing to its cap; the status on one line; a scroll hint at the edge of a scrolled table; the dev failure switch over the home footer left as it is.
- 2026-11-11: After the three fixes of task 5, "да, по рекомендации, запускай перепроверку": the tinted header row stays opaque, without the scroll hint on its line; `manual-tester` rechecks the fixes.
- 2026-11-11: Task 5 after the recheck: "всё ок, переходим к задаче 6" (the faint scroll hint in light and Invite at the right end at 768px stay as they are).
- 2026-11-11: Task 6: "вариант A, с правилом согласен, запускай": the row actions of the Invitations list in one row menu (Send email, Copy link, Get a new link, a divider, Revoke; no menu on closed invitations); the passage about column widths and row actions in the "List page" pattern agreed as worded.
- 2026-11-11: After the test-engineer's pass, "да, запускай frontend-dev", in answer to whether the per-action aria-labels may be replaced by the trigger's "Actions for <email>" and whether to start `frontend-dev` on task 6.
- 2026-11-11: The owner's look at task 6 (`.screens/img_4.png`): "рядам у таблицы Invitations как будто не хватает пространства в высоту, а контент колонок Roles, Status и Invited довольно плотный и эти три колонки плотно сидят справа, пока слева больше половины таблицы выделено под email"; asked whether to set "Revoke" apart in the menu, and whether it is OK that revoked and accepted invitations have no menu button, not even a disabled one.
- 2026-11-11: Asked "для колонки email всё-таки оставляем бОльшую часть таблицы?" (a third, not the larger part); then "хорошо, давай всё по рекомендациям": vertical padding in multi-line rows with the 52px minimum kept; Invitations columns Email 32%, Roles 18%, Status 26%, Invited 24%, Actions 72px; "Revoke" in the error colour with the divider kept and no icons; closed invitations stay without a menu button; the Users list columns untouched.
- 2026-11-11: The reworked Invitations list and the Users rows of 57px with a chip: "да, оставляем, запускай" (the one 10px cell padding for both lists stays; `test-engineer` fixes the lint error).
- 2026-11-11: After the lint fix: "запускай" (`manual-tester`'s look at task 6).
- 2026-11-11: manual-tester's two points of task 6 (emails cut and a name wrapped in the Users list at 768 and 360px): "согласен, приступай": a larger minimum width of the Users table, scrolling inside its block.
- 2026-11-11: The look at every screen (task 7): "вроде всё ок"; asked whether the sections button on a narrow screen would be better at the left, as it is "очень близко к кнопкам профиля и темы" and the menu on a wide screen sits at the left too.
- 2026-11-11: The sections button at the left edge of the bar before the logo on narrow screens, in the DOM too: "давай".
- 2026-11-11: After the sections button moved: "пусть агент посмотрит" (`manual-tester`'s short look at the bar).
- 2026-11-11: After the look at the bar: "согласен, продолжай": the sections menu's focus on its first item and the 4px move of the logo across the breakpoint stay; task 8 starts.
- 2026-11-11: Review of task 8, "давай по рекомендациям": four rules accepted as exceptions to "only the theme and its tokens" (the bar menu panel margin, the active item of the bar menu, `padding-block` of table body cells, the host padding of the bar icon buttons); the phone dialog rule gets `width: 100vw !important` and `manual-tester` checks a dialog at 500px; the avatar palette and its text color become `--app-avatar-*` tokens in `styles.scss`; the Styling rule of `docs/development/frontend.md` widened to the `--app-*` tokens of `styles.scss` (answers in the handover); the Detail page avatar header "for a person"; findings 7-13 fixed (stale test title, Invite locator, the unannounced hidden name, the theme button label, two comments, one global `.visually-hidden`).
- 2026-11-11: Finding 3 (avatar palette), asked to choose between keeping it in `avatar.ts` as an exception and moving it to tokens: "доведи до токенов" — `--app-avatar-1..10` in `styles.scss`; `test-engineer` reworks the palette specs first.
- 2026-11-11: How `avatar.spec.ts` reads `styles.scss`, "вариант по рекомендации": without `@types/node`, by a `?raw` import or similar; `@types/node` only if that does not work.
- 2026-11-11: After the report of task 8 (review fixed, verification green) and the proposed commit message: "продолжай" — accepted; stage 8 done.

## Handover

<!-- Overwritten before every stop for the user. A new session reads the brief and what this names, and nothing else, so keep it short and exact. -->

- Waiting for: the owner to ask for the commit, then `/pr`. Task 8 is accepted ("продолжай", 2026-11-11) and stage 8 is done: the guide passages are in the tree and agreed (`frontend.md` Styling, `guidelines.md` List page, Page frame, Detail page); no roadmap item to delete; no architecture decision (the owner's choice). Verification green: format, lint, 290 of 290 tests, build, `check-docs`, requirements tests 64 of 64, trace 206 of 206. Nothing is committed. Answers for the guide passages are in this handover below (List page, Page frame) and in the next bullet (the widened Styling rule); `/pr` takes them and the Decisions, then deletes this brief before the pull request is opened. Four answers for the widened Styling rule: (1) any later component with a status or raised-surface color; (2) hard-coded colors, or components that take the app tokens for forbidden; (3) `styles.scss` defines them but nothing tells a component to use them; (4) yes: the status dots and the navy bar. The stack and `ng serve` may still be running (process group ids and logs in `.screens/material-look/stack/`; stop with `kill -- -<pgid>`); screenshots in `.screens/material-look/`.
- To read: `git diff` of `frontend/src/styles.scss` (`.page`, `.table-scroll`, the phone-dialog rule), the shell pages, `frontend/src/app/shared/profile-form/profile-form.ts`, `docs/ui/guidelines.md`; screenshots in `.screens/material-look/task5/`
- Documents changed: added `docs/plans/material-look.md` (this brief); changed `docs/development/frontend.md`: removed the Verification bullet about Google Fonts and the frontend npm configuration file (mechanical: the fonts are served by the app now); changed `docs/ui/guidelines.md`: added to "List page" the sentences about column widths and row actions; added the section "Page frame" (two bullets; "fluid width up to a maximum, centred" after the owner's 2560px decision), and in "Detail page" replaced "`h1` with a status chip" by the avatar header (mechanical: the page changed in task 4)
- Guide passage of task 6, added to the "List page" bullet of `docs/ui/guidelines.md`, agreed by the owner on 2026-11-11: "Columns keep their widths from page to page: each column has a set width, and a long value wraps or is cut with an ellipsis instead of pushing its neighbours. Row actions sit in the last column, sized to its control, under a visually hidden header "Actions"; a row with more than one action gets a row menu." Four answers: (1) every later list, the cohort and student lists of Tracking core first; (2) columns that shift between pages and a column squeezed by wide actions, an actions column a screen reader announces without a name; (3) the styles of the two lists show how, but nothing tells a new list to do the same; behaviour is not affected, so not a requirement; (4) yes: issues #27 and #28, found by manual-tester on the sample center.
- Guide passage "Page frame", four answers: (1) any later page in the shell — Tracking core's cohort and student screens — would otherwise copy its own padding and max-width, and any later table would widen the page on phones; (2) pages that drift in width and gutters, and sideways page scroll at 360px from a wide table or an unwrapped filter row; (3) the code has `.page` and `.table-scroll` but nothing tells a new page to use them; no requirement holds layout (UI detail), a decision is too heavy; (4) yes: every page repeated `padding`/`max-width: 1100px`, and `/users` scrolled sideways at 599 and 360px (manual-tester, task 4)
- State: task 5 made by `frontend-dev`: `.page` on every shell page's `<main>` (fluid, gutters 16px under 960px and 32px from 960px, 24px above and below, his choice), `.table-scroll` regions (focusable, labelled, `position: relative`), `overflow-wrap: break-word`, dialogs full screen under 600px (`max-width: 100vw !important` against Material's inline width, shape 0 through the token); the home footer at the bottom of the viewport through a full-height shell column, kept in Home; /profile as variant A (card ~640px centred under the "Profile" h1, header avatar/name/email, divider, two-column names, Phone full width, Save bottom right), the shared form gets `showEmail` (default true, the user page keeps its Email line); Profile card columns on the user page with a 32px gap. `scrollWidth` equals the viewport at 360px and 1280px on home, users, user, user edit, invitations, invite dialog, profile, both schemes. Format, lint, 288 tests and the build pass. Known for task 6: Invitations rows 81-101px; the row menu proposal waits for the owner. Tests to tidy at review: two non-id tests find Invite by `textContent.replace('person_add', '')`; the non-id test "is shown next to the heading with USERS_INVITE…" in `users-list.spec.ts` now finds `h1` instead of `.heading h1`, and its title is stale (Invite is in the filter row). Invite moved to the right end of the filter row in both lists (8px top offset to level it with the field boxes), the `h1` alone above; under 600px Invite in the title row right of the `h1` (filters full width below); Tab order on narrow: heading, search, Status, Role, Invite (the wide order kept; one DOM order cannot give both); `scrollWidth` 360 at 360px.
- Open questions: flaky frontend tests. Lead's runs on 2026-11-11 with `ng serve` running: 1 of 3 runs green; the others had 4-5 tests fail with "Test timed out in 5000ms", different each time, all using a real router or app startup (app.routes, app.spec PLT-8.1, auth.service, dev-tools DEV-3.1, unavailable-page PLT-8.4, access.guard, permission.guard); earlier single flakes in `access-channel.spec.ts` and `failure-modes.spec.ts`. Unknown whether main has it too.
  - Found by `frontend-dev` afbc39857debe89da before WSL crashed on 2026-11-11 04:05 (from its transcript; its logs and its worktree of main in `/tmp` are gone, the stale worktree entry is pruned): main has it too. Main at 73e0c02, 3 runs with `ng serve` running: run 1 had 3 tests of 260 time out (`app.spec.ts` PLT-8.1, `dev-tools.spec.ts` DEV-3.1 among them), 20s; runs 2 and 3 green, 13s and 12s. So this change is not the cause. The crash came 14s into the next command, three runs of this branch's tests (exit 137).
  - The lead's guess, not proven: memory. The machine reports 20 cores and 7.8 GB; nothing limits the test workers (`angular.json` has the bare `unit-test` builder, no vitest config); just before the runs swap was 1886 of 2048 MB used with the stack and `ng serve` up. Workers starved of memory would explain both the timeouts on a cold run and the crash.
  - Made by `frontend-dev` a956649df4d301b8e: new `frontend/vitest.config.mts` with `test.maxWorkers: Math.min(4, availableParallelism())`, wired through `runnerConfig` of the `test` target in `frontend/angular.json`. Measured with the stack and `ng serve` down: uncapped, 20 node processes, node RSS about 4.0 GB, machine at 6.2 of 7.8 GB; capped, 5 processes, 4.1-4.2 GB, 4 green runs of 288 tests in a row, 6.4-6.9s against 11s. Format, lint, tests, build pass. No guide is made stale. One run under load (the stack and `ng serve` up, 2.9 GB available before it): 288 of 288 green, 7.4s, swap untouched. The uncapped run was green too, so the memory cause is a strong inference, not proof.
  - Until that is shown: one test run at a time, `free -m` before it, no back-to-back series while the stack and `ng serve` are up.
- On approval: task 8: `reviewer` with the base ref `origin/main` and this brief (the three test tidy-ups of State go to it), the owner decides the fixes, then `verifier` for frontend and docs, one test run at a time
- On remarks: `frontend-dev` fixes, `manual-tester` rechecks visual fixes first

## Agents

<!-- Agents can be continued only within the session that started them; a new session starts fresh ones from this brief. -->

| Agent | Model | Id | Context |
|---|---|---|---|
| frontend-dev | default | ad4d1f82d39eaa7db | task 2 (fonts from npm) |
| frontend-dev | default | ad4d1f82d39eaa7db | task 3 (theme) and its fix round; ended with the session |
| frontend-dev | default | a18526bf26b2cc15e | task 3: verification after the crash; blue-tinted dark scheme, top bar color |
| manual-tester | sonnet | a9eed7d9e7d74fd1a | task 2: first look, clipped toolbar name; task 3: first look |
| manual-tester | sonnet | a6f39800d560eb787 | task 3: recheck of the four fixes |
| manual-tester | sonnet | a2640c5223c779818 | task 3a: recheck of links and the pressed state |
| manual-tester | sonnet | a5a8c33102b34276d | task 3a: recheck of filled buttons and focus rings |
| manual-tester | sonnet | af25c6ac09a08e7d4 | task 3a: short recheck of the status title, dark buttons, disabled controls |
| manual-tester | sonnet | a002f95ccbeac2787 | task 3a and 33% dark panels: first look |
| manual-tester | sonnet | a88d46b67e40216de | task 3: blue-tinted dark scheme and top bar |
| frontend-dev | default | a8fa814bcbc741cff | task 3a: not-found back link inline |
| frontend-dev | default | a673e6d05ffe244b1 | task 4 (shell) |
| test-engineer | default | a8550cb2ef81bbe17 | task 4: IDN-11.1 test locator by aria-label |
| frontend-dev | default | ab426bb21dbd54693 | task 4: shared avatar, bar avatar shift, avatar in list, user page and profile, bar DOM order |
| frontend-dev | default | af839a05eaf506666 | task 4: avatar hash and 12-color palette, two-row page header |
| manual-tester | sonnet | a3a9a222af0614b18 | task 4: first look (bar, menus, keyboard, theme, personas, avatars, lists, profile); recheck |
| frontend-dev | default | a2100ba3426e55d38 | task 5 (page frame, profile variant A, no page scroll to 360px) |
| frontend-dev | default | afbc39857debe89da | test timeouts: main vs branch, cause and fix ; ended by the WSL crash after measuring main only, changed nothing |
| frontend-dev | default | a956649df4d301b8e | test timeouts: cap the test workers, careful single runs |
| manual-tester | sonnet | a8eb443b0b40e849f | task 5: first look (frame, 360px, lists, dialogs, profile) |
| frontend-dev | default | ad15f0fcf1c61d865 | task 5: filter row at 600px, status on one line, table scroll hint |
| manual-tester | sonnet | ad3f2fe84b3cc5c97 | task 5: recheck of the filter row, the status line and the scroll hint |
| test-engineer | default | a8107939092d3e3d3 | task 6: Invitations tests reach the row actions through the row menu |
| frontend-dev | default | ae0ed82329a7b3718 | task 6: row menu in Invitations, set column widths in both lists |
| manual-tester | sonnet | a28da1381b606d0c3 | task 6: first look (column stability, rows, the row menu with mouse and keyboard) |
| frontend-dev | default | a7a674736b21ac163 | task 7: the sections button at the left of the bar on narrow screens |
| manual-tester | sonnet | a790085e95e8a0af5 | task 7: short look at the bar on narrow screens |
| reviewer | default | abbceac29ac608751 | task 8: review of the whole change against origin/main (73e0c02); stopped by the usage limit, no findings |
| reviewer | default | ac0702fab12bbbffd | task 8: review of the whole change against origin/main (73e0c02), second run |
| test-engineer | default | a8816c07b2a661334 | task 8 fix round: spec changes of findings 7-10, and the assertion that the theme button label follows the choice |
| frontend-dev | default | a1ad3ceaeef9301a9 | task 8 fix round: code, styles and guide edits of findings 1-5, 9-13; PARTIAL on 3 |
| test-engineer | default | a95e6b7ec5b7fc4a4 | task 8 fix round: avatar palette specs for `--app-avatar-1..10` (stopped by the user before its report; the specs it wrote are in the tree and were not run by the lead) |
| frontend-dev | default | a3c105bc2b3065435 | task 8 fix round: the avatar palette to tokens (finding 3) |
| test-engineer | default | a4a4ad29e133b3b7f | task 8 fix round: `avatar.spec.ts` reads `styles.scss` without Node types (text import attribute) |
| verifier | default | aff9cd0587f873adc | task 8: frontend and docs verification after the fix round: all green |
