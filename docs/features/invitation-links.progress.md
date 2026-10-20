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
| frontend B | approved | (this commit) | 2026-10-20 |
| review | pending | | |
| verify | pending | | |

Status: `pending`, `in progress`, `in review` (report shown, waiting for the user), `approved`, `skipped`. Steps 1 and 2 are one checkpoint.

## Decisions

<!-- One line per decision the user made at a checkpoint, in their words: answers to agents' questions, requested changes, accepted deviations from the spec. -->

- 2026-10-20, start: branch `feature/invitation-links` created from `main`.
- 2026-10-20, start: frontend prototype skipped, mock mode is not implemented (`docs/development/frontend.md`); screens are reviewed at the end.
- 2026-10-20, steps 1–2: "Одобряю, оставляем в этом шаге, коммить" (approved; the switch of persistence and services from hashed to plaintext tokens, planned for step 3, stays in this checkpoint because V5 drops `token_hash`).
- 2026-10-20, step 3 and frontend review: "Одобряю шаг 3, коммить; пункты 1–6 отдай frontend-dev" (all six frontend review findings accepted, including the doc drift in `README.md` and `docs/features/invitations.md`, which frontend-dev updates).
- 2026-10-20, step 4 and frontend: "Одобряю шаг 4, коммить оба; Design — вариант (a)" (step 4 approved; backend and frontend committed separately; the Design section of `docs/features/invitations.md` is updated by the step 4 backend-dev to the plaintext token storage instead of being kept as a historical snapshot). Manual checks AC-8, AC-9, AC-10 remain for the final review.

## Handoff notes

<!-- From each approved step's "Notes for the next step": what a fresh agent or session needs that the spec, decisions and diff do not show (temporary stubs, pitfalls found, conventions adopted, deferred follow-ups). Remove a note when it no longer applies. -->

- Step 4 done: `getInvitationLink` implemented; `Cache-Control: no-store` set by `InvitationsController.noStore()` (injected `HttpServletResponse`) on create, reissue and get-link success responses only. No stubs remain.
- `InvitationRepository.findLink` (row type `StoredInvitationLink`) is the only read that returns a stored token; keep the token out of every other read path.
- Javadoc is not reflowed by Spotless; wrap long javadoc lines by hand at 120 columns.
- Migration test `InvitationTokenMigrationTests` (AC-6) runs on its own Testcontainers PostgreSQL, migrating to V4 then latest. V4's comment says the token is never stored; V4 is applied and stays; V5's header states the new rule.
- Docs done in step 3: `docs/development/backend.md` Security, `docs/architecture/invariants.md` Identity. `docs/ui/guidelines.md` done by frontend-dev; `README.md` and `docs/features/invitations.md` assigned to frontend-dev.
- Test gap for the final review: end-to-end `InvitationsEndpointTests` has no `GET /link` case; `no-store` is tested only in the `@WebMvcTest` slice.
- Frontend production build not run (no network); rerun `npm run build`.

## Pending

<!-- Relays sent to an agent and not yet acted on, and findings waiting for the user's decision. Empty at every phase boundary. -->

- Sent to backend-dev (step 4): update the Design section of `docs/features/invitations.md` (token_hash, hash lookups, the Decisions entry on the extra token_hash check) to plaintext token storage.

## Open questions

- Frontend production build (`npm run build`) failed only on Google Fonts inlining (no network in the agent's environment); `ng build --configuration development` passes. Rerun with network access.

## Agents

<!-- Agents can be continued only within the session that created them; a new session starts fresh agents from this log. Context is the size from the agent's last completion notification. Model is the `model` parameter of the call, or else the `model:` line of the agent's definition. -->

| Agent | Model | Id | Session | Context |
|---|---|---|---|---|
| backend-dev (steps 1–2) | opus | a3438d265a69ed27c | a6edf05c | 109k |
| backend-dev (step 3) | opus | ab9a0c6d96d1b4842 | a6edf05c | 92k |
| frontend-dev (step B) | sonnet | a9b009dde2f242570 | a6edf05c | 87k |
| reviewer (frontend) | opus | ad8db77eb7292d917 | a6edf05c | 45k |
| backend-dev (step 4) | sonnet | ae68c4dacc9b7c171 | a6edf05c | 41k |
