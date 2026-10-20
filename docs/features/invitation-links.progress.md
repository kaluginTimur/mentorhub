# Invitation links: implementation progress

Spec: `docs/features/invitation-links.md`
Branch: `feature/invitation-links`
Started: 2026-10-20

## Steps

| Step | Status | Commit | Updated |
|---|---|---|---|
| 1 contract | approved | 9e7ec7d | 2026-10-20 |
| 2 migration | approved | 9e7ec7d | 2026-10-20 |
| 3 domain | approved | 40e7b2a | 2026-10-20 |
| 4 api | approved | 68665c2 | 2026-10-20 |
| frontend A (prototype) | skipped | | 2026-10-20 |
| frontend B | approved | cd84433 | 2026-10-20 |
| review | approved | | 2026-10-21 |
| verify | approved | | 2026-10-21 |

Status: `pending`, `in progress`, `in review` (report shown, waiting for the user), `approved`, `skipped`. Steps 1 and 2 are one checkpoint.

## Decisions

<!-- One line per decision the user made at a checkpoint, in their words: answers to agents' questions, requested changes, accepted deviations from the spec. -->

- 2026-10-20, start: branch `feature/invitation-links` created from `main`.
- 2026-10-20, start: frontend prototype skipped, mock mode is not implemented (`docs/development/frontend.md`); screens are reviewed at the end.
- 2026-10-20, steps 1–2: "Одобряю, оставляем в этом шаге, коммить" (approved; the switch of persistence and services from hashed to plaintext tokens, planned for step 3, stays in this checkpoint because V5 drops `token_hash`).
- 2026-10-20, step 3 and frontend review: "Одобряю шаг 3, коммить; пункты 1–6 отдай frontend-dev" (all six frontend review findings accepted, including the doc drift in `README.md` and `docs/features/invitations.md`, which frontend-dev updates).
- 2026-10-20, step 4 and frontend: "Одобряю шаг 4, коммить оба; Design — вариант (a)" (step 4 approved; backend and frontend committed separately; the Design section of `docs/features/invitations.md` is updated by the step 4 backend-dev to the plaintext token storage instead of being kept as a historical snapshot). Manual checks AC-8, AC-9, AC-10 remain for the final review.
- 2026-10-20, implementation phase end: "Да, коммить оба" (Design section update of `docs/features/invitations.md` accepted and committed with the log; the missing 401 in its new API row is left for the final review).
- 2026-10-20, final review: "Исправляем все 8, по твоим рекомендациям" (all eight minor findings fixed as recommended: e2e `GET /link` test; 401 as one note under the invitations.md API table; `disabledInteractive` on row buttons and a `Set` of busy ids; frontend tests for 403 and focus/select; AC-6 migration test checks the pending row via `findLink`; header blank line; stale test comment; "show once" line in invitations.md).
- 2026-10-20, final review: "401 из accept убери" (401 removed from the accept row of the invitations.md API table; the note under the table covers it). Follow-ups recorded: self-hosted fonts and response schema conformance in tests.
- 2026-10-21, acceptance: "тестирование провёл, всё работает. не проверял только пункты 8 и 9. думаю, что это low priority и не страшно" (AC-10 checked manually; the clipboard fallback copy view and the Safari clipboard check were not run and are accepted as low priority). Spec Design updated to the implementation, status `implemented`.

## Handoff notes

<!-- From each approved step's "Notes for the next step": what a fresh agent or session needs that the spec, decisions and diff do not show (temporary stubs, pitfalls found, conventions adopted, deferred follow-ups). Remove a note when it no longer applies. -->

- Step 4 done: `getInvitationLink` implemented; `Cache-Control: no-store` set by `InvitationsController.noStore()` (injected `HttpServletResponse`) on create, reissue and get-link success responses only. No stubs remain.
- `InvitationRepository.findLink` (row type `StoredInvitationLink`) is the only read that returns a stored token; keep the token out of every other read path.
- Javadoc is not reflowed by Spotless; wrap long javadoc lines by hand at 120 columns.
- Migration test `InvitationTokenMigrationTests` (AC-6) runs on its own Testcontainers PostgreSQL, migrating to V4 then latest. V4's comment says the token is never stored; V4 is applied and stays; V5's header states the new rule.
- Docs done in step 3: `docs/development/backend.md` Security, `docs/architecture/invariants.md` Identity. `docs/ui/guidelines.md` done by frontend-dev; `README.md` and `docs/features/invitations.md` assigned to frontend-dev.

## Pending

<!-- Relays sent to an agent and not yet acted on, and findings waiting for the user's decision. Empty at every phase boundary. -->

- none

## Follow-ups

<!-- Out of this feature's scope, decided by the user; carried into the PR description. -->

- Self-hosted fonts (#12). The production build inlines CSS from `fonts.googleapis.com` (needs internet at build time), and browsers load Roboto and Material Symbols from `fonts.gstatic.com` at runtime (`src/index.html:9-16`, 27 Roboto URLs in the built `index.html`). Without access to Google, icons render as their ligature names (`content_copy`), and Google sees every user's IP. Fix: `@fontsource/roboto` and `material-symbols` from npm, remove the Google links from `src/index.html`, check font inlining in `angular.json`. Pre-existing, not introduced by this feature. Local workaround for the WSL build: `NODE_OPTIONS="--no-network-family-autoselection --dns-result-order=ipv4first" npm run build`.
- Response schema conformance in tests (#13). Nothing checks that real responses match the models in `api/openapi.yaml`: backend controller and endpoint tests assert single JSON paths (`bodyJson().extractingPath`, `doesNotHavePath`), no test dependency validates against the contract, and frontend specs mock `Api` with hand-written objects. A missing required field, a wrong format or an extra property (e.g. a token in a list item) passes unless a test names that path. Fix: validate responses of the endpoint (and slice) tests against `api/openapi.yaml` with a contract validator (for example a MockMvc result matcher over the spec), and type frontend fixtures with the generated models. Needs a decision on the library; possibly an ADR next to ADR 0002.

## Open questions

- none

## Agents

<!-- Agents can be continued only within the session that created them; a new session starts fresh agents from this log. Context is the size from the agent's last completion notification. Model is the `model` parameter of the call, or else the `model:` line of the agent's definition. -->

| Agent | Model | Id | Session | Context |
|---|---|---|---|---|
| backend-dev (steps 1–2) | opus | a3438d265a69ed27c | a6edf05c | 109k |
| backend-dev (step 3) | opus | ab9a0c6d96d1b4842 | a6edf05c | 92k |
| frontend-dev (step B) | sonnet | a9b009dde2f242570 | a6edf05c | 87k |
| reviewer (frontend) | opus | ad8db77eb7292d917 | a6edf05c | 45k |
| backend-dev (step 4) | sonnet | ae68c4dacc9b7c171 | a6edf05c | 52k |
| reviewer (final) | opus | a2b883a02a4d81bec | a6edf05c | 105k |
| backend-dev (review fixes) | opus | a0eb3cebc0e3d0855 | a6edf05c | 49k |
| frontend-dev (review fixes) | sonnet | a6c6bee17db92cff9 | a6edf05c | 59k |
| reviewer (re-review) | sonnet | abdf7796c2ece70f4 | a6edf05c | 22k |
| verifier (all) | sonnet | a99b86030ed3ed6e1 | a6edf05c | 12k |
