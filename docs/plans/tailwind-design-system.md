# Tailwind & Spartan UI design system

Branch: `chore/tailwind-design-system`
Roadmap item: none

## Scope

- **Goal:** Give MentorHub a coherent, clean yet lively corporate design system — top bar navigation, corporate royal blue base with orange highlight, light and dark themes, and soft-gradient initials avatars — replacing the bare Angular Material look so the UI can evolve cleanly for `Tracking core` and later modules.
- **Approach:** Adopt **Tailwind CSS v4 + Spartan UI** (`@spartan-ng/brain` + generated `@spartan-ng/helm` components) with `@ng-icons/lucide` and local `@fontsource-variable` fonts; migrate all existing screens (`shell`, `home`, `users`, `invitations`, `profile`, `access`, `invite`, shared dialogs/toasts/unavailable) off Angular Material and remove `@angular/material` completely; update `docs/development/frontend.md`, `docs/ui/guidelines.md`, and record decision `0007` in `docs/architecture/decisions/`.
- **Design baseline (confirmed at Task 3 checkpoint):**
  - **Palette:** Royal corporate blue (`#153E75` topbar / `#1A56A8` primary action / `#225CA8` hero gradient end in light; `#0F2C54` topbar / `#2B6CB0` primary action in dark) + Orange highlight (`#F97316` light / `#FB923C` dark for active tab marker, focus rings, highlight badges, primary accent CTAs). Surfaces: `#F4F6FA` canvas & `#FFFFFF` cards in light; `#091322` canvas & `#0F1D32` cards in dark.
  - **Typography:** `@fontsource-variable/plus-jakarta-sans` (display/headings), `@fontsource-variable/inter` (body/UI), `@fontsource-variable/jetbrains-mono` (monospace IDs/dates/codes).
  - **Icons:** `@ng-icons/core` + `@ng-icons/lucide`.
  - **Avatars:** Circular soft-gradient initials avatars (`linear-gradient(135deg, ...)` across a deterministic palette with white initials and subtle top inner highlight, WCAG AA legible in both themes).
  - **Themes:** Light / Dark / System switch (`ThemeService`, persisted in `localStorage`, defaulting to system preference).
- **Capabilities searched:** `docs/requirements/README.md` (`identity`, `authorization`, `platform`, `audit`, `user-administration`, `invitations`, `invitation-emails`, `development-mode`). No requirement describes visual styling or UI component library choices, and none should (`docs/README.md`).

## Tasks

| Task | Who | Status |
|---|---|---|
| 1. Check Spartan UI + Tailwind v4 dependency compatibility with Angular 22.2 (and Angular update options) | coordinator | done |
| 2. Evaluate `/uiux-designer` skill: generate palette, typography, and UX rules (navy + orange, light/dark, avatars) | coordinator | done |
| 3. Checkpoint: align design tokens, layout details, full Material removal, and decision 0007 creation with owner | coordinator | done |
| 4. Set up Tailwind v4, `@spartan-ng/brain` + `helm` primitives, `@ng-icons/lucide`, local fonts, CSS tokens, and `ThemeService` | frontend-dev | done |
| 5. Implement `Shell`, `Avatar`, `Home`, and core shared primitives (`Toast`, `Unavailable`, `Loading`, Spartan dialog/menu primitives) | frontend-dev | done |
| 6. Migrate `users`, `invitations`, `profile`, `access`, `invite`, `ConfirmDialog`, remove `@angular/material`, and update tests | frontend-dev | done |
| 7. Manual check on running app / sample center + owner visual sign-off | manual-tester / owner | done |
| 8. Review, verify, and update `docs/development/frontend.md`, `docs/ui/guidelines.md`, and decision 0007 | reviewer / verifier / coordinator | done |
| 9. Close #12 (remove temporary frontend npmrc), fix #27 + #28 (stable column widths across paginated lists + pattern in `docs/ui/guidelines.md`), check pagination, and re-verify | frontend-dev / manual-tester / reviewer / verifier | done |

## Partial work

### Task 4
- **Done:**
  - Installed all required npm dependencies in `frontend/package.json` (`tailwindcss`, `@tailwindcss/postcss`, `postcss`, `@spartan-ng/brain`, `@spartan-ng/cli`, `clsx`, `class-variance-authority`, `tw-animate-css`, `tailwind-merge`, `@ng-icons/core`, `@ng-icons/lucide`, `@fontsource-variable/plus-jakarta-sans`, `@fontsource-variable/inter`, `@fontsource-variable/jetbrains-mono`).
  - Created `frontend/.postcssrc.json` and `frontend/components.json` (`style: "vega"`, `componentsPath: "src/app/shared/ui"`, `importAlias: "@spartan-ng/helm"`).
  - Generated Spartan `helm` v1.9.0 primitives in `frontend/src/app/shared/ui/` for `utils`, `button`, `badge`, `card`, `input`, `label`.
- **Left for Task 4:**
  1. Add `@spartan-ng/helm/*` path mappings in `frontend/tsconfig.json` (`./src/app/shared/ui/*/src/index.ts`) and configure `frontend/eslint.config.js` to allow `hlm` prefix (and Spartan `helm` directive/input conventions) inside `src/app/shared/ui/**/*.ts`, keeping `app` enforced everywhere else.
  2. Configure pure Tailwind CSS v4 entry `frontend/src/styles/tailwind.css` (registered in `frontend/angular.json` alongside `src/styles.scss` so Dart Sass does not choke on `@theme`/`@utility`/`--value(...)` in `@spartan-ng/brain/hlm-tailwind-preset.css`, while Material M3 in `styles.scss` stays working until Task 6), importing `@spartan-ng/brain/hlm-tailwind-preset.css`, local `@fontsource-variable/*` fonts, and Royal blue + Orange highlight tokens (`:root` / `.dark` / `@theme inline`), plus an optional `highlight` (orange) button/badge variant if appropriate. Also remove `color-scheme: light;` from `body` in `styles.scss` so `.dark` on `html` works.
  3. Implement `ThemeService` (`@Injectable({ providedIn: 'root' })`, `'light' | 'dark' | 'system'`, `localStorage`, `matchMedia` safely guarded for jsdom, `DOCUMENT` from `@angular/core`, syncing `.dark` class and `color-scheme` on `document.documentElement`) in `frontend/src/app/core/theme/theme.service.ts` + `theme.service.spec.ts`, and initialize it in `frontend/src/app/app.ts`.
  4. Format (`npm run format`) and pass all verification checks (`format:check`, `lint`, `test -- --watch=false`, `build`).

## Decisions

- 2026-11-14: Go with Spartan + Tailwind direction, top-bar navigation, navy primary + orange highlight, light and dark themes, and colored initials avatars.
- 2026-11-14: Check Spartan + Angular dependency compatibility first (and whether updating Angular helps).
- 2026-11-14: Evaluate the experimental `/uiux-designer` skill during this design work to see if it provides real value or little help.
- 2026-11-14: Corporate blue should be a bit lighter than the near-black navy in the initial mockups; explore alternative initial-avatar styles (without new requirements, API, or integrations); remove `@angular/material` completely; write decision `0007` for switching the frontend UI stack.
- 2026-11-14: Remove experimental `uiux-designer` skill directory (evaluated, limited ongoing value). Select **Royal** corporate blue shade + **Soft Gradient** circular initials avatars.
- 2026-11-14: Approve decision `0007` (use the term "decision", not "ADR"); remove line 103 in `docs/development/frontend.md` and shorten the package list; close all three related issues (`#12`, `#27`, `#28`) in this change.

## Handover

- Waiting for: owner command to commit (and optionally open the PR via `pr`)
- To read: `docs/plans/tailwind-design-system.md`, `docs/architecture/decisions/0007-tailwind-spartan-ui-frontend.md`, `docs/development/frontend.md`, `docs/ui/guidelines.md`
- Documents changed: `docs/architecture/decisions/0007-tailwind-spartan-ui-frontend.md`, `docs/architecture/decisions/README.md`, `docs/development/frontend.md`, `docs/ui/guidelines.md`, `README.md`, `docs/plans/tailwind-design-system.md`, `.claude/hooks/check-frontend.mjs`, `.agents/plugins/mentorhub/hooks/check-frontend.mjs` (plus removed temporary npmrc in `frontend/`)
- State: Tasks 1–9 are DONE.
  - Owner confirmed decision `0007`, guide updates (`docs/development/frontend.md` shortened, `docs/ui/guidelines.md` additions confirmed), and closing `#12`, `#27`, `#28`.
  - `verifier` (`1bc7f4d0`): `frontend` (`format:check`, `lint`, `test -- --watch=false` 25/25 files / 285/285 tests, `build` 523.96 kB + `postbuild`) ALL PASS; post-edit `docs` checks (`check-docs.mjs`, `requirements.test.mjs` 64/64, `requirements.mjs trace` 206/206) ALL PASS.
  - `reviewer` (`69137799`): `NON-BLOCKING` (only minor finding was the backtick reference to the removed npmrc, resolved).
  - `manual-tester` (`b86440b8`, run `task9-tables`): DONE (`0` console/page errors, `0` Google Fonts requests with local `.woff2` fonts loaded, `0px` column width delta between Page 1 and Page 2 on both `/users` and `/invitations` at `1280x800` and `768x1024`, `0` horizontal overflow at `1280x800`, `768x1024`, and `390x844`).
- Open questions:
  - none (ready to commit when owner confirms)
- Guide additions & widenings (`docs/ui/guidelines.md` and `docs/development/frontend.md`):
  1. `docs/ui/guidelines.md:20` — widened List page with `and stable column widths across pages (responsive cards below md)`:
     - **Later change:** Future list pages (`Tracking core` cohorts/students, assignments).
     - **Mistake prevented:** Building `table-layout: auto` paginated tables whose columns shift sideways between pages or get squeezed by row actions (`#27`, `#28`), and desktop-only tables overflowing mobile viewports (`390px`).
     - **Why not elsewhere:** Per-screen templates (`users-list.html`, `invitations-list.html`) do not establish a cross-screen rule; shared list patterns live in `docs/ui/guidelines.md`.
     - **Stumbled on in this change:** Yes — `#27` (Invitations actions squeezing Invited), `#28` (Users columns shifting between pages), and `manual-tester` Run 2 mobile list layout issues at `390x844`.
  2. `docs/ui/guidelines.md:21` — widened Detail page with `action and alert banner where applicable` (alongside removing Material-specific `outlined`):
     - **Later change:** Future entity detail pages with lifecycle actions and status notices (e.g. cohorts, enrollments, assignments).
     - **Mistake prevented:** Creating an almost-empty standalone status/action card (like the removed `Access` card on `user-page.html`) instead of placing the action in the header and a status banner below it.
     - **Why not elsewhere:** Screen composition across features belongs in `docs/ui/guidelines.md`, not hidden in `user-page.html`.
     - **Stumbled on in this change:** Yes — the standalone `Access` card with one button and a hint looked sparse on `user-page.html` and had to be redesigned into header action + top alert banner.
  3. `docs/ui/guidelines.md:26` — widened `disabledInteractive` to `disabledInteractive or aria-disabled="true"`:
     - **Later change:** Any screen disabling a `button[hlmBtn]` (or non-checkbox control) while keeping a readable focusable reason.
     - **Mistake prevented:** Attempting to pass Material's `disabledInteractive` to Spartan `hlmBtn` (which has no such input) or falling back to native `[disabled]` that removes the button from keyboard focus.
     - **Why not elsewhere:** `docs/ui/guidelines.md:26` is already the project's a11y rule for focusable disabled controls; leaving only `disabledInteractive` would mislead developers off Angular Material.
     - **Stumbled on in this change:** Yes — migrating the self-lockout `Disable user` button in `user-page.html` replaced `disabledInteractive` with `aria-disabled="true"` (while `hlm-checkbox.ts` keeps `disabledInteractive`).
  4. `docs/development/frontend.md` (lines 3 and 60–61: `Styling` + summary line) — switched stack to Tailwind CSS v4, Spartan UI (owned Helm primitives in `frontend/src/app/shared/ui/`), Lucide icons, and semantic tokens in `frontend/src/styles/tailwind.css` (`decision 0007`):
     - **Later change:** Any new frontend feature or component (`Tracking core` screens, dialogs, icons, themed surfaces).
     - **Mistake prevented:** Using Angular Material APIs, literal colors/fonts outside `tailwind.css`, or installing duplicate UI/icon libraries instead of using `frontend/src/app/shared/ui/` primitives and semantic tokens.
     - **Why not elsewhere:** Decision `0007` captures the architectural decision and trade-offs; `docs/development/frontend.md` is the area guide read before every frontend task and tells contributors where primitives (`frontend/src/app/shared/ui/`) and tokens (`frontend/src/styles/tailwind.css`) live.
     - **Stumbled on in this change:** Yes — during migration we hit custom `--mh-*` token discipline (banning literal hex values in components) and ESLint `hlm` vs `app` selector boundaries in `shared/ui/`, plus initial bundle size constraints.
  5. `docs/development/frontend.md:76` (`Pitfalls`) — widened `Material and CDK` to `CDK, Spartan UI and overlay`, updated example to `ToastService` in `frontend/src/app/core/auth/error.interceptor.ts`:
     - **Later change:** Any change to `app.config.ts`, route guards, or HTTP interceptors that shows UI feedback or overlays.
     - **Mistake prevented:** Adding a static import of `@spartan-ng/brain` / `@angular/cdk/overlay` UI code into startup code and blowing the `550kB` initial bundle budget (`523.52 kB` currently).
     - **Why not elsewhere:** A comment inside `error.interceptor.ts` would not be read when editing other guards/interceptors; this bundle pitfall already belongs in `Pitfalls`.
     - **Stumbled on in this change:** Yes — `error.interceptor.ts` had to use lazy `import('../toast/toast.service')` to keep the production initial bundle under the `550kB` budget (`523.52 kB`).
- On approval: keep `docs/plans/tailwind-design-system.md` until the `pr` skill extracts decisions and guide answers and removes it before opening the PR; remove any unapproved guide passages; commit on `chore/tailwind-design-system` when asked (include `Closes #12`, `Closes #27`, `Closes #28` in the PR).
- On remarks: edit decision `0007` or docs/UI per feedback and re-verify affected scope.

## Agents

| Agent | Model | Id | Context |
|---|---|---|---|
| `frontend-dev` | `inherit` | `9d347115-7cc9-4689-a245-1ffe9dc66f00` | Task 4 (part 1, PARTIAL at context budget) |
| `frontend-dev` | `inherit` | `8685e2fe-e471-4506-936d-bf9c71f0008a` | Task 4 (part 2, DONE) |
| `frontend-dev` | `inherit` | `b0afa7f4-94f3-4300-9c78-83225f365db3` | Task 5 (DONE) |
| `frontend-dev` | `inherit` | `bb68fe88-0e85-4ec7-8097-5412648687fd` | Task 6 (DONE) |
| `manual-tester` | `inherit` | `b1a3ba96-1a04-4e53-bb3d-6f15c31a94a3` | Task 7 (NOT RUN: stale `:4200` server + Keycloak down) |
| `manual-tester` | `inherit` | `88aa964b-1a04-4e53-bb3d-6f15c31a94a3` | Task 7 (Run 2: DONE, 4 visual issues found) |
| `frontend-dev` | `inherit` | `be3ebc06-0ab8-4b32-bdd4-8556a19c25a2` | Task 7 fixes (landed on disk before WSL restart; verified green) |
| `frontend-dev` | `inherit` | `86ed8fb6-4f26-4508-96eb-da2becc521d3` | Task 7 Round 2 polish (DONE) |
| `frontend-dev` | `inherit` | `69ffa9fa-5b54-4822-8405-043a410c31be` | Task 7 Round 3 polish (DONE) |
| `frontend-dev` | `inherit` | `c818c06b-75ae-4398-993f-e7e1f92cf6f0` | Task 7 Round 4 `/profile` `max-w-6xl` + `UserPage` Option A (DONE) |
| `manual-tester` | `inherit` | `47f43a11-1e30-4e5f-a058-27b63207e568` | Task 7 Run 3 control check (`20261114-0628`: DONE, all green) |
| `verifier` | `inherit` | `3b68fe21-3c95-4ca7-98a6-4e8de9054df3` | Task 8 (`frontend` + `docs`: ALL PASS) |
| `reviewer` | `pro` | `ac05d73d-b1fa-473e-ae93-b1d6b9b669ee` | Task 8 (`CLEAN`, 0 findings) |
| `frontend-dev` | `inherit` | `b1bcac6c-1537-4ad6-aba2-f06e61278f30` | Task 9 (`#27` + `#28` `md:table-fixed` + unit tests, DONE) |
| `manual-tester` | `inherit` | `ca52d6a3-b546-4620-81cb-5e3c900315ee` | Task 9 (`NOT RUN`: `BypassSandbox` prompt timed out after 60s) |
| `verifier` | `inherit` | `1bc7f4d0-b265-40c4-adbf-189c9801f473` | Task 9 (`frontend` ALL PASS; `docs` flagged removed npmrc in brief, now fixed) |
| `reviewer` | `pro` | `69137799-1cb8-4ae2-a810-896dc3cb81b2` | Task 9 (`NON-BLOCKING`: 1 minor doc link finding, already fixed) |
| `manual-tester` | `inherit` | `b86440b8-44d5-45d6-a379-d248da12c24e` | Task 9 Run 2 (`task9-tables`: DONE, 0px column delta, 0 Google Fonts) |
