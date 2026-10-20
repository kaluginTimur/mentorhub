# Invitation links: implementation progress

Spec: `docs/features/invitation-links.md`
Branch: `feature/invitation-links`
Started: 2026-10-20

## Steps

| Step | Status | Commit | Updated |
|---|---|---|---|
| 1 contract | approved | 9e7ec7d | 2026-10-20 |
| 2 migration | approved | 9e7ec7d | 2026-10-20 |
| 3 domain | approved | (this commit) | 2026-10-20 |
| 4 api | in progress | | 2026-10-20 |
| frontend A (prototype) | skipped | | 2026-10-20 |
| frontend B | in review | | 2026-10-20 |
| review | pending | | |
| verify | pending | | |

Status: `pending`, `in progress`, `in review` (report shown, waiting for the user), `approved`, `skipped`. Steps 1 and 2 are one checkpoint.

## Decisions

<!-- One line per decision the user made at a checkpoint, in their words: answers to agents' questions, requested changes, accepted deviations from the spec. -->

- 2026-10-20, start: branch `feature/invitation-links` created from `main`.
- 2026-10-20, start: frontend prototype skipped, mock mode is not implemented (`docs/development/frontend.md`); screens are reviewed at the end.
- 2026-10-20, steps 1–2: "Одобряю, оставляем в этом шаге, коммить" (approved; the switch of persistence and services from hashed to plaintext tokens, planned for step 3, stays in this checkpoint because V5 drops `token_hash`).
- 2026-10-20, step 3 and frontend review: "Одобряю шаг 3, коммить; пункты 1–6 отдай frontend-dev" (all six frontend review findings accepted, including the doc drift in `README.md` and `docs/features/invitations.md`, which frontend-dev updates).

## Handoff notes

<!-- From each approved step's "Notes for the next step": what a fresh agent or session needs that the spec, decisions and diff do not show (temporary stubs, pitfalls found, conventions adopted, deferred follow-ups). Remove a note when it no longer applies. -->

- Temporary stub: `InvitationsController.getInvitationLink` throws 501; step 4 replaces it. Map `service.link(id)` (`PendingInvitationLink {URI link, String email, Instant expiresAt}`) to the generated `InvitationLink` in `InvitationsMapper`, converting `Instant` to `OffsetDateTime` as the existing mapper does. The name `PendingInvitationLink` avoids a clash with the generated `com.mentorhub.api.model.InvitationLink`.
- The service already throws the AC-2 errors: `NotFoundException()` (404 `not-found`), `ConflictException` with `INVITATION_CLOSED` or `INVITATION_EXPIRED`; closed beats expired. The controller adds no logic.
- `InvitationRepository.findLink` (row type `StoredInvitationLink`) is the only read that returns a stored token; keep the token out of every other read path.
- `Cache-Control: no-store` is not implemented yet. The generated `InvitationsApi` methods return DTOs, not `ResponseEntity`, so step 4 sets it in code (e.g. injected `HttpServletResponse`, or an interceptor/advice scoped to these operations) on `createInvitation`, `reissueInvitationLink` and `getInvitationLink`, and tests it (AC-4). The contract documents it as `components.headers.NoStore`. Then add to the token bullet in `docs/development/backend.md` (Security): "A response that returns a token sets `Cache-Control: no-store`."
- Web tests still needed for AC-1 to AC-4: 200 with the body shape; 404, 409 `invitation-closed`, 409 `invitation-expired`; 403 without `USERS_INVITE`, and the gate refusing disabled and not invited callers; 400 for a malformed id; `no-store` on all three link responses; the list response never containing the token. Service-level negative permission tests exist; `PermissionDeclarationTests` checks the controller's `@RequirePermission(USERS_INVITE)`.
- Javadoc is not reflowed by Spotless; wrap long javadoc lines by hand at 120 columns.
- Migration test `InvitationTokenMigrationTests` (AC-6) runs on its own Testcontainers PostgreSQL, migrating to V4 then latest. V4's comment says the token is never stored; V4 is applied and stays; V5's header states the new rule.
- Docs done in step 3: `docs/development/backend.md` Security, `docs/architecture/invariants.md` Identity. `docs/ui/guidelines.md` done by frontend-dev; `README.md` and `docs/features/invitations.md` assigned to frontend-dev.
- Frontend: built against the contract; error states and AC-10 can be checked manually only after step 4.

## Pending

<!-- Relays sent to an agent and not yet acted on, and findings waiting for the user's decision. Empty at every phase boundary. -->

- Sent to frontend-dev: frontend review findings 1–6 (aria-label "Copy invitation link for <email>"; busyId cleared only for its own row; Safari sync-write test; 409 closed + list reload test; write-rejected fallback test; "shown only once" drift in `README.md:48`, `docs/features/invitations.md` (lines 15, 118, 251) and the missing "Copy link" a11y name (line 303)).

## Open questions

- Frontend production build (`npm run build`) failed only on Google Fonts inlining (no network in the agent's environment); `ng build --configuration development` passes. Rerun with network access.

## Agents

<!-- Agents can be continued only within the session that created them; a new session starts fresh agents from this log. Context is the size from the agent's last completion notification. Model is the `model` parameter of the call, or else the `model:` line of the agent's definition. -->

| Agent | Model | Id | Session | Context |
|---|---|---|---|---|
| backend-dev (steps 1–2) | opus | a3438d265a69ed27c | a6edf05c | 109k |
| backend-dev (step 3) | opus | ab9a0c6d96d1b4842 | a6edf05c | 92k |
| frontend-dev (step B) | sonnet | a9b009dde2f242570 | a6edf05c | 71k |
| reviewer (frontend) | opus | ad8db77eb7292d917 | a6edf05c | 45k |
