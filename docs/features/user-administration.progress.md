# User administration: implementation progress

Spec: `docs/features/user-administration.md`
Branch: `feature/user-administration`
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
- 2026-10-18, steps 1–2: controller layout: "go with variant (a)": single `Users` tag; step 4 renames `MeController` to `UsersController`, serving `getMe`, `updateMyProfile` and the admin operations.
- 2026-10-18, steps 1–2: database checks: "go with your recommendation, drop the weak checks": keep the `disabled_reason` value check and the combined `users_disabled_reason_ck`; drop the `phone` E.164 regex, the `disabled_comment` length check, and the non-empty `action`/`subject_type` and `details` JSON-object checks on `audit_log`. The service still enforces phone and comment rules. Spec Design (lines ~299–302) to be updated at the end.

## Handoff notes

<!-- From each approved step's "Notes for the next step": what a fresh agent or session needs that the spec, decisions and diff do not show (temporary stubs, pitfalls found, conventions adopted, deferred follow-ups). Remove a note when it no longer applies. -->

- Temporary code marked `TODO(user-administration step N)`: `MeController` (8 stubs returning 501, `MeUser.phone = null`; step 4), `UserRepository.setStatus` (writes `OTHER`/`Development user` for DISABLED; replace with `enable`/`disable(reason, comment)` in step 3), `DevelopmentUserRunner` (forced ACTIVE insert, no reason/comment config in `mentorhub.development-users`; step 3).
- No database backstop for phone or comment (user decision): step 3 must enforce E.164 phones (libphonenumber) and comments of 1–500 characters after trimming, with service tests for both.
- `SecurityConfiguration` uses `@EnableMethodSecurity(proxyTargetClass = true)`: interface-based proxies hide `@RestController` in `@WebMvcTest` slices (404 on every endpoint). Add this pitfall to `docs/development/backend.md`.
- Generated `HistoryEntry`: optional lists `rolesAdded`, `rolesRemoved`, `changes` start as `[]`, not `null`; step 4 must set unused ones to `null` before the planned per-class `@JsonInclude(NON_NULL)` mixin can omit them.
- Step 4 validation: the generated `UsersApi` is `@Validated` with `@Min`/`@Max` on `page`/`size`; those failures, a non-UUID `{id}`, and unknown enum values (query parameters and `reason` in the body) must map to `validation-failed` with `field` naming the parameter or property.
- Schema tests: a check failure aborts the test transaction, so each `@Transactional` test can contain at most one rejected statement (use parameterized tests).
- Audit ordering: entries in one transaction share `occurred_at` and `gen_random_uuid()` ids, so their order is arbitrary; consider `uuidv7()` if one change ever writes several entries.
- Frontend: after regenerating the client, `MeUser.phone` is required; fixtures building `MeUser` (`access.guard.spec.ts`, `shell.spec.ts`) need `phone: null`.
- Do not run the application against the dev database until V3 is committed and approved (it would apply V3 and freeze it).

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
