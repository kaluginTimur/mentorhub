# Invitations: implementation progress

Spec: `docs/features/invitations.md`
Branch: `feature/invitations`
Started: 2026-10-18

## Steps

| Step | Status | Commit | Updated |
|---|---|---|---|
| 1 contract | approved | | 2026-10-18 |
| 2 migration | approved | | 2026-10-18 |
| 3 domain | pending | | |
| 4 api | pending | | |
| frontend A (prototype) | skipped | | 2026-10-18 |
| frontend B | pending | | |
| review | pending | | |
| verify | pending | | |

Status: `pending`, `in progress`, `in review` (report shown, waiting for the user), `approved`, `skipped`. Steps 1 and 2 are one checkpoint.

## Decisions

<!-- One line per decision the user made at a checkpoint, in their words: answers to agents' questions, requested changes, accepted deviations from the spec. -->

- 2026-10-18, start: frontend prototype step skipped because mock mode is not implemented; screens are reviewed at the end.
- 2026-10-18, steps 1–2: `q` limit on `GET /api/v1/invitations`: "go with (a), but note in the spec why 254 limit was choosen": 254 characters after trimming; rationale added to spec AC-4 and Decisions.
- 2026-10-18, steps 1–2: "steps 1–2 approved". Accepted deviations: extra checks `token_hash ~ '^[0-9a-f]{64}$'` and `email <> ''`; create 409 uses one `InvitationConflictProblem` schema (`allOf` `Problem` + optional `userId`, `invitationId`) instead of a `oneOf`. Spec Design to be updated at the end.

## Handoff notes

<!-- From each approved step's "Notes for the next step": what a fresh agent or session needs that the spec, decisions and diff do not show (temporary stubs, pitfalls found, conventions adopted, deferred follow-ups). Remove a note when it no longer applies. -->

From steps 1–2:

- Already done: `Permission.USERS_INVITE` exists and ADMIN has it; `TestUsers.deleteAll` cleans the invitation tables; `HistoryEntryJsonMixin` omits a null `invitation`.
- Step 3: add `USER_INVITATION_ACCEPTED` to `UserAuditAction` (its Javadoc requires it to equal the contract's `HistoryAction`; no test checks it) and a matching `UserChange.InvitationAccepted`. `UsersMapper.historyEntry` then maps it to `HistoryEntry.invitation` (`InvitationAcceptance`; `invitedBy` is a nullable `HistoryActor`) plus `rolesAdded`/`rolesRemoved`.
- Lock the invitation row with `for no key update`, not the spec's literal `for update`: `invitation_roles` references `invitations` (backend guide, Database). Deviation to record in the spec at the end.
- Status derivation order (in the `InvitationStatus` description): ACCEPTED, then REVOKED, then EXPIRED when `expires_at <= now()`, else PENDING. List queries use the same expressions. `q` is at most 254 characters after trimming (field `q`).
- Step 4: `Invitation` has only optional nullable properties (`acceptedAt`, `acceptedUser`, `revokedAt`, `revokedBy`), so a class-level `@JsonInclude(NON_NULL)` mixin works. `InvitationConflictProblem` is documentation only; the backend renders `userId`/`invitationId` as `ProblemDetail` properties once `ConflictException` carries extensions.
- Generated signatures: `InvitationCreate.roles` is `Set<Role>`; `InvitationToken.token` has `@NotNull @Size(min = 1, max = 200)` (empty token → 400 `validation-failed`, field `token`). `InvitationsApi` parameters: `q`, `status`, `page`, `size`, `id`, `invitationCreate`, `invitationToken`.
- Suggested convention to add to `docs/development/backend.md` Errors with the `ConflictException` extension work: a problem with extensions gets an `allOf` schema over `Problem`.
- Frontend: the regenerated client gains `USERS_INVITE`, `USER_INVITATION_ACCEPTED` and the invitation types; no exhaustive `Record<Permission|HistoryAction, …>` was found.

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
