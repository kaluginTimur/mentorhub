# Invitation links: implementation progress

Spec: `docs/features/invitation-links.md`
Branch: `feature/invitation-links`
Started: 2026-10-20

## Steps

| Step | Status | Commit | Updated |
|---|---|---|---|
| 1 contract | approved | (this commit) | 2026-10-20 |
| 2 migration | approved | (this commit) | 2026-10-20 |
| 3 domain | pending | | |
| 4 api | pending | | |
| frontend A (prototype) | skipped | | 2026-10-20 |
| frontend B | pending | | |
| review | pending | | |
| verify | pending | | |

Status: `pending`, `in progress`, `in review` (report shown, waiting for the user), `approved`, `skipped`. Steps 1 and 2 are one checkpoint.

## Decisions

<!-- One line per decision the user made at a checkpoint, in their words: answers to agents' questions, requested changes, accepted deviations from the spec. -->

- 2026-10-20, start: branch `feature/invitation-links` created from `main`.
- 2026-10-20, start: frontend prototype skipped, mock mode is not implemented (`docs/development/frontend.md`); screens are reviewed at the end.
- 2026-10-20, steps 1–2: "Одобряю, оставляем в этом шаге, коммить" (approved; the switch of persistence and services from hashed to plaintext tokens, planned for step 3, stays in this checkpoint because V5 drops `token_hash`).

## Handoff notes

<!-- From each approved step's "Notes for the next step": what a fresh agent or session needs that the spec, decisions and diff do not show (temporary stubs, pitfalls found, conventions adopted, deferred follow-ups). Remove a note when it no longer applies. -->

- Temporary stub: `InvitationsController.getInvitationLink` throws 501 (`ResponseStatusException(NOT_IMPLEMENTED)`); step 4 replaces it.
- Already done in steps 1–2: the storage switch from hash to token (`InvitationRepository` `findByToken`/`lockByToken`/insert/replace, `InvitationTokens.ifWellFormed` replacing `hash`/`hashIfWellFormed`, both services, tests).
- Step 3 still needs: `InvitationProblems.INVITATION_EXPIRED` (`urn:mentorhub:problem:invitation-expired`); a repository read of one invitation's token and status through the single `STATUS` expression with the database `now()`; `InvitationAdministrationService.link(id)` with `@RequirePermission(USERS_INVITE)`, read-only: unknown id → `NotFoundException`, accepted or revoked → `ConflictException(INVITATION_CLOSED)`, expired → `ConflictException(INVITATION_EXPIRED)`, no audit entry; negative authorization tests.
- `Cache-Control` must be set in code: the generated `InvitationsApi` methods return DTOs, not `ResponseEntity`. Step 4 sets `Cache-Control: no-store` itself (e.g. injected `HttpServletResponse`, or an interceptor/advice scoped to these operations) on `createInvitation`, `reissueInvitationLink` and `getInvitationLink`, and tests it (AC-4). The contract documents it as `components.headers.NoStore`.
- Contract: new 409 response `InvitationLinkUnavailable` (`invitation-closed` or `invitation-expired`) and schema `InvitationLink {link, email, expiresAt}`.
- Migration test `InvitationTokenMigrationTests` (AC-6) runs on its own Testcontainers PostgreSQL, migrating to V4 then latest.
- Docs not yet updated (spec Design "Documentation updates"): `docs/development/backend.md` Security (still hashed tokens), `docs/architecture/invariants.md`, `docs/features/invitations.md`, `docs/ui/guidelines.md`, `README.md`. V4's comment says the token is never stored; V4 is applied and stays; V5's header states the new rule.
- Frontend: the generated client gets `getInvitationLink` returning `InvitationLink`, and the `invitation-expired` 409.

## Pending

<!-- Relays sent to an agent and not yet acted on, and findings waiting for the user's decision. Empty at every phase boundary. -->

- none

## Open questions

- none

## Agents

<!-- Agents can be continued only within the session that created them; a new session starts fresh agents from this log. Context is the size from the agent's last completion notification. Model is the `model` parameter of the call, or else the `model:` line of the agent's definition. -->

| Agent | Model | Id | Session | Context |
|---|---|---|---|---|
| backend-dev (steps 1–2) | opus | a3438d265a69ed27c | a6edf05c | 109k |
