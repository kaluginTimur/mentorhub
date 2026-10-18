# Invitations: implementation progress

Spec: `docs/features/invitations.md`
Branch: `feature/invitations`
Started: 2026-10-18

## Steps

| Step | Status | Commit | Updated |
|---|---|---|---|
| 1 contract | approved | 5ea2a24 | 2026-10-18 |
| 2 migration | approved | 5ea2a24 | 2026-10-18 |
| 3 domain | approved | e3a88a6 | 2026-10-18 |
| 4 api | pending | | |
| frontend A (prototype) | skipped | | 2026-10-18 |
| frontend B | approved | | 2026-10-18 |
| review | pending | | |
| verify | pending | | |

Status: `pending`, `in progress`, `in review` (report shown, waiting for the user), `approved`, `skipped`. Steps 1 and 2 are one checkpoint.

## Decisions

<!-- One line per decision the user made at a checkpoint, in their words: answers to agents' questions, requested changes, accepted deviations from the spec. -->

- 2026-10-18, start: frontend prototype step skipped because mock mode is not implemented; screens are reviewed at the end.
- 2026-10-18, steps 1–2: `q` limit on `GET /api/v1/invitations`: "go with (a), but note in the spec why 254 limit was choosen": 254 characters after trimming; rationale added to spec AC-4 and Decisions.
- 2026-10-18, steps 1–2: "steps 1–2 approved". Accepted deviations: extra checks `token_hash ~ '^[0-9a-f]{64}$'` and `email <> ''`; create 409 uses one `InvitationConflictProblem` schema (`allOf` `Problem` + optional `userId`, `invitationId`) instead of a `oneOf`. Spec Design to be updated at the end.
- 2026-10-18, frontend B review: "fix frontend findings 1–8, accept the deviations". Accepted frontend deviations (record in the spec at the end): token in session storage with `/invite` redirects and auto-accept key; landing page email from ID token claims, not `/me`; "Sign out" on "Account already in use" clears the token; "Expired <date>" status line; two text row actions instead of a "..." menu; no back-link state for invitations; `login_hint`/`prompt=create` via `customParams`; invite dialog lazy-loaded from the users list. Docs finding 9 deferred to the end.
- 2026-10-18, step 3 and frontend B: "step 3 approved, commit both and proceed to step 4". Accepted step 3 decisions (record in the spec at the end): typed 404 `invitation-invalid` via `NotFoundException(URI type, detail)`; email validity via Bean Validation `@Email`; database `now()` as the one clock; parallel creates via `insert ... on conflict do nothing` with up to 3 retries; audit field `invitedBy`; AC-14 retry returns the currently stored status; identity linked by a concurrent transaction → `identity-linked-elsewhere` with rollback; audit `expiresAt` as ISO-8601 string.

## Handoff notes

<!-- From each approved step's "Notes for the next step": what a fresh agent or session needs that the spec, decisions and diff do not show (temporary stubs, pitfalls found, conventions adopted, deferred follow-ups). Remove a note when it no longer applies. -->

From steps 1–2:

- Already done: `Permission.USERS_INVITE` exists and ADMIN has it; `TestUsers.deleteAll` cleans the invitation tables; `HistoryEntryJsonMixin` omits a null `invitation`.
- Status derivation order (in the `InvitationStatus` description): ACCEPTED, then REVOKED, then EXPIRED when `expires_at <= now()`, else PENDING. List queries use the same expressions. `q` is at most 254 characters after trimming (field `q`).
- Step 4: `Invitation` has only optional nullable properties (`acceptedAt`, `acceptedUser`, `revokedAt`, `revokedBy`), so a class-level `@JsonInclude(NON_NULL)` mixin works. `InvitationConflictProblem` is documentation only; the backend renders `userId`/`invitationId` as `ProblemDetail` properties once `ConflictException` carries extensions.
- Generated signatures: `InvitationCreate.roles` is `Set<Role>`; `InvitationToken.token` has `@NotNull @Size(min = 1, max = 200)` (empty token → 400 `validation-failed`, field `token`). `InvitationsApi` parameters: `q`, `status`, `page`, `size`, `id`, `invitationCreate`, `invitationToken`.
- Suggested convention to add to `docs/development/backend.md` Errors with the `ConflictException` extension work: a problem with extensions gets an `allOf` schema over `Problem`.
- Frontend: the regenerated client gains `USERS_INVITE`, `USER_INVITATION_ACCEPTED` and the invitation types; no exhaustive `Record<Permission|HistoryAction, …>` was found.

From step 3:

- Step 4, `SecurityConfiguration`: `POST /api/v1/invitations/preview` needs a POST matcher in the public filter chain (current public matchers are GET-only); test it with an invalid bearer token. `POST /api/v1/invitations/accept` is exempt from the gate like `GET /api/v1/me` (authenticated only); its controller method carries `@NoPermissionRequired`. Management methods carry `@RequirePermission(USERS_INVITE)` on the controller too.
- Services: `InvitationAdministrationService` `create(email, Set<Role>)`, `list(InvitationQuery)`, `revoke(id)`, `reissueLink(id)`; `InvitationAcceptanceService` `preview(token)`, `accept(token)` returning `MeDetails` (map with `UsersMapper.me`). `IssuedInvitation.link()` is a `URI` like the generated `CreatedInvitation.link`. Reject `null` items in `roles` as `UsersController.setUserRoles` does.
- Problems: `ConflictException.extensions()` holds `userId` or `invitationId` (UUID); the advice renders them as `ProblemDetail` properties but no web test covers it yet. `invitation-invalid` is a `NotFoundException` with type `InvitationProblems.INVITATION_INVALID` and detail "This invitation link is invalid or has expired."
- Test support: `TestAuthentications.tokenAuthentication(status, userId, subject, email, emailVerified)` for service tests; web tests through the real chain use `TestIssuer`. `ContractEnumsTests` checks `UserAuditAction` and `InvitationStatus` against the contract.
- Docs at the end, `docs/development/backend.md`: `mentorhub.app-url` as a required property owned by `users`; `ConflictException` extensions and typed `NotFoundException` (with the `allOf` over `Problem` convention); token hashing; gate-exempt endpoints.

From frontend B:

- Review: `access.guard.ts` (callback plus stored token goes to `/invite`), the invitation branch in `AuthService.ensureSignedIn`, routes `invite/:token` (stores token, `replaceUrl` to `/invite`) and `invite`.
- Landing page relies on the Keycloak client scope including `email` (it does by default in `infra/keycloak/mentorhub-realm.json`); claims are not refreshed on token renewal.
- Manual AC-23 check must confirm Keycloak 26.8 opens registration with `prompt=create` and prefills the email from `login_hint`; spec fallback is the registration endpoint.
- No axe test in the project; accessibility checked by review only.
- Frontend docs to update at the end: `docs/development/frontend.md` (public routes, `login_hint`, `prompt=create`, token session), `docs/ui/guidelines.md` (Invitations link, link view, landing page), "Access by invitation only" text in `docs/features/identity-foundation.md`.

Carried over from `user-administration` (still applicable):

- Controller and mapper naming per tag `Foo`: `FooApi`, `FooController`, `FooMapper`; mapping is hand-written.
- Problem rendering in `ProblemDetailsAdvice`: `ConflictException` → 409 with `exception.type()`; `ValidationException` → 400 `validation-failed` with `errors` `[{field, message}]`; bean validation and conversion failures (`@Min`/`@Max`, non-UUID path ids, unknown enum values) map to `validation-failed` naming the field.
- Advisory lock key for the administrator lock is a fixed constant in `UserRepository`; a new advisory lock must use a different key. Row locks use `for no key update` (`docs/development/backend.md`, Database).
- Schema tests: a check failure aborts the test transaction, so each `@Transactional` test can contain at most one rejected statement (use parameterized tests).
- Audit entries in one transaction share `occurred_at` and random ids, so their order is arbitrary.
- Service tests set the caller with `SecurityContextHolder` plus `TestAuthentications.activeUserAuthentication`. `TestUsers.deleteAll` truncates `audit_log`.
- Frontend: forms use Signal Forms with Material fields (`[formField]`, `submit()` returning server errors as field errors); `UsersListState` keeps the list's last query for the back link; keep heavy Material imports out of eagerly loaded core code.

## Pending

<!-- Relays sent to an agent and not yet acted on, and findings waiting for the user's decision. Empty at every phase boundary. -->

- none

## Open questions

- none

## Agents

<!-- Agents can be continued only within the session that created them; a new session starts fresh agents from this log. Context is the size from the agent's last completion notification. -->

| Agent | Id | Session | Context |
|---|---|---|---|
| `backend-dev` (steps 1–2, Opus) | a1b9d5fd48692ed6e | 2026-10-18 phase contract | 100k |
| `backend-dev` (step 3, Opus) | ad3d2d99f8f76fc34 | 2026-10-18 phase implementation | 228k |
| `frontend-dev` (step B, Opus) | a18d2a8910259a001 | 2026-10-18 phase implementation | 189k |
