# User administration: implementation progress

Spec: `docs/features/user-administration.md`
Branch: `feature/user-administration`
Started: 2026-10-18

## Steps

| Step | Status | Commit | Updated |
|---|---|---|---|
| 1 contract | approved | 86737f0 | 2026-10-18 |
| 2 migration | approved | 86737f0 | 2026-10-18 |
| 3 domain | approved | 57988fc | 2026-10-18 |
| 4 api | approved | 28dbc51 | 2026-10-18 |
| frontend A (prototype) | skipped | | 2026-10-18 |
| frontend B | approved | f18d477 | 2026-10-18 |
| review | pending | | |
| verify | pending | | |

Status: `pending`, `in progress`, `in review` (report shown, waiting for the user), `approved`, `skipped`. Steps 1 and 2 are one checkpoint.

## Decisions

<!-- One line per decision the user made at a checkpoint, in their words: answers to agents' questions, requested changes, accepted deviations from the spec. -->

- 2026-10-18, start: frontend prototype step skipped because mock mode is not implemented; screens are reviewed at the end.
- 2026-10-18, steps 1–2: controller layout: "go with variant (a)": single `Users` tag; step 4 renames `MeController` to `UsersController`, serving `getMe`, `updateMyProfile` and the admin operations.
- 2026-10-18, steps 1–2: database checks: "go with your recommendation, drop the weak checks": keep the `disabled_reason` value check and the combined `users_disabled_reason_ck`; drop the `phone` E.164 regex, the `disabled_comment` length check, and the non-empty `action`/`subject_type` and `details` JSON-object checks on `audit_log`. The service still enforces phone and comment rules. Spec Design (lines ~299–302) to be updated at the end.
- 2026-10-18, frontend review: "fix all 11 plus the chip-set check".
- 2026-10-18, step 3 question 1 (phone error text): "variant (a)": server keeps `must be a valid phone number`; the frontend shows its own "Enter a valid phone number" for `phone`.
- 2026-10-18, step 3 question 2 (short history pages): "I am fine with short history pages for now, but I want it to be noted as a known-issue/corner case or something like that."
- 2026-10-18, persistence: "I am fine with JDBC in "core-level" modules like user management and audit, but I want that this can be decided on per module basis, so for what is best for each module." Recorded as ADR 0007.
- 2026-10-18, step 3: "step 3 backend approved".
- 2026-10-18, ADR 0007: "add the rules to ADR 0007, commit both": transaction rules (one transaction manager, one approach per table, flush before SQL reads and mapped constraint errors) added to Consequences; step 3 and frontend committed separately.
- 2026-10-18, step 4: mapper naming: "rename to UsersMapper": `UsersMapper` → `UsersMapper`; convention per tag `Foo`: `FooApi`, `FooController`, `FooMapper`. Mapping stays hand-written; MapStruct may be considered per module when the tracking core is specified.
- 2026-10-18, step 4: "approved".
- 2026-10-18, manual checks: "all manual checks pass". Frontend step B approved.
- 2026-10-18, roles of disabled users: "we can change roles of a disabled user and I don't remember that we discussed it. we can leave it since we may want to change roles of a disabled user before enable them." Keep it; state it in the spec at the end.

## Handoff notes

<!-- From each approved step's "Notes for the next step": what a fresh agent or session needs that the spec, decisions and diff do not show (temporary stubs, pitfalls found, conventions adopted, deferred follow-ups). Remove a note when it no longer applies. -->

- Temporary code marked `TODO(user-administration step 4)`: `MeController` (8 stubs returning 501; `getMe` can now pass `user.phone()` instead of `null`). Step 3 TODOs are resolved.
- `SecurityConfiguration` uses `@EnableMethodSecurity(proxyTargetClass = true)`: interface-based proxies hide `@RestController` in `@WebMvcTest` slices (404 on every endpoint). Add this pitfall to `docs/development/backend.md`.
- Generated `HistoryEntry`: optional lists `rolesAdded`, `rolesRemoved`, `changes` start as `[]`, not `null`; step 4 must set unused ones to `null` before the planned per-class `@JsonInclude(NON_NULL)` mixin can omit them.
- Step 4 validation: the generated `UsersApi` is `@Validated` with `@Min`/`@Max` on `page`/`size`; those failures, a non-UUID `{id}`, and unknown enum values (query parameters and `reason` in the body) must map to `validation-failed` with `field` naming the parameter or property.
- Schema tests: a check failure aborts the test transaction, so each `@Transactional` test can contain at most one rejected statement (use parameterized tests).
- Audit ordering: entries in one transaction share `occurred_at` and `gen_random_uuid()` ids, so their order is arbitrary; consider `uuidv7()` if one change ever writes several entries.
- Frontend: after regenerating the client, `MeUser.phone` is required; fixtures building `MeUser` (`access.guard.spec.ts`, `shell.spec.ts`) need `phone: null`.
- Frontend (step B): forms use Signal Forms with Material fields (`[formField]`, `submit()` returning server errors as field errors); reuse for the invitation forms. `UsersListState` keeps the list's last query for the user page's back link. `history-text.ts` handles missing or empty optional history lists. `updateMyProfile` returns `Me`, stored directly in `CurrentUserService.me`.
- Frontend was verified by unit tests only: once the step 4 controllers exist, first check that error bodies parse as problems (`type`, `errors`); the 409 and 400 handling depends on it.
- Frontend bundle: a static `MatSnackBar` import in `error.interceptor.ts` pulled snack bar and CDK code into the initial bundle; it is now a dynamic `import()`. Keep heavy Material imports out of eagerly loaded core code (noted in `docs/development/frontend.md`).
- Step 4 problem rendering in `ProblemDetailsAdvice`: `ConflictException` → 409 with `exception.type()`, title `Conflict`, detail `exception.detail()`; `ValidationException` → 400 `Problems.VALIDATION_FAILED` with `errors` `[{field, message}]` from `FieldViolation`; bean validation and conversion failures map the same way (see the validation note above).
- Step 4 controller mapping: `listUsers` → `service.list(new UserQuery(q, status, roleFilter, page, size))`, API `RoleFilter.NONE` → `new RoleFilter.WithoutRoles()`, others → `WithRole(Role.valueOf(name))`. `updateMyProfile` → `profileService.updateOwnProfile(...)` (void), then `meService.me()` for the `Me` response. `ManagedUser` already has `disabledReason`/`disabledComment` null unless disabled.
- Step 4 history: map `UserHistoryEntry.change()` by its `UserChange` subtype to `HistoryEntry` details and the subtype's `action()` to `HistoryAction`; role names in `RolesChanged` are already filtered to known ones.
- Service methods also carry `@RequirePermission` (agent decision); service validation also rejects `page`/`size` out of range and `q` over 100 characters (fields `page`, `size`, `q`); the `@Validated` interface catches page and size first, `q` only the service.
- Service tests set the caller with `SecurityContextHolder` plus `TestAuthentications.activeUserAuthentication`. `TestUsers.deleteAll` truncates `audit_log` (the trigger blocks delete, not truncate).
- Advisory lock key for the administrator lock is a fixed constant in `UserRepository`; later features using advisory locks must pick a different key.
- Server validation messages use bean validation wording (e.g. `must be a valid phone number`); the frontend shows its own text for `phone`.
- Known limitation (user decision), to add to the spec at the end: history entries with unknown actions are filtered after paging, so a page can hold fewer than `size` items while `totalItems` counts all entries. Happens only when the log holds actions the running code does not know (e.g. after rolling back a release that wrote a new action).
- Final spec update: Design states persistence via `JdbcClient` for `users` and `audit` per ADR 0007, and adds ADR 0007 to Related.
- `docs/development/backend.md` must be checked in step 4 (the commit hook flagged it): line 23 (`JwtAuthenticationConfiguration` wiring), 33 (`application-dev.yaml` development users now have a disable reason and comment), 34 (dependencies: libphonenumber), 40 (problem types: add `validation-failed`, `self-lockout`, `last-admin`), plus the `proxyTargetClass` pitfall and ADR 0007 persistence note.
- Step 4 API facts for the manual checks: errors are `application/problem+json` with `type`, `title`, `status`, `detail`; 400 `validation-failed` adds `errors: [{field, message}]` and `detail` `The request is invalid.`; 409 details are `You can't lock yourself out.` / `MentorHub must keep at least one active administrator.`; a non-UUID `{id}` is 400 `validation-failed` field `id` (`must be a UUID`); a truncated non-JSON body is 400 `about:blank` without `errors`.
- Controller parameter names must equal request parameter names: `@Validated` on the generated interface disables Spring MVC method validation, and the proxy's `ConstraintViolationException` reports Java parameter names (documented in `docs/development/backend.md`).
- `docs/features/identity-foundation.md:249` still says `MeController` (past spec, left as is).
- Final spec update: state that an administrator can change a disabled user's roles (including removing ADMIN; the last-admin rule counts active administrators only), so roles can be prepared before enabling.

## Pending

<!-- Relays sent to an agent and not yet acted on, and findings waiting for the user's decision. Empty at every phase boundary. -->

- none

## Open questions

- none

## Agents

<!-- Agents can be continued only within the session that created them; a new session starts fresh agents from this log. Context is the size from the agent's last completion notification. -->

| Agent | Id | Session | Context |
|---|---|---|---|
| backend-dev (steps 1–2) | a4887a1e18b011084 | 1 (contract) | 134k |
| backend-dev (step 3) | a2e244985dedfc2dc | 2 (implementation) | 214k |
| frontend-dev (step B) | a812ab410fd19cb9b | 2 (implementation) | 166k |
| backend-dev (step 4) | a4c261bf17e990720 | 2 (implementation) | 185k |
