# Identity foundation: implementation progress

Spec: `docs/features/identity-foundation.md`
Branch: `feature/identity-foundation`
Started: 2026-10-15

## Steps

| Step | Status | Commit | Updated |
|---|---|---|---|
| 1 contract | approved | cc86c7f | 2026-10-15 |
| 2 migration | approved | | 2026-10-15 |
| 3 domain | pending | | |
| 4 api | pending | | |
| frontend A (prototype) | skipped | | 2026-10-15 |
| frontend B | in progress (review round 1 fixes) | | 2026-10-15 |
| review | pending | | |
| verify | pending | | |

Status: `pending`, `in progress`, `in review` (report shown, waiting for the user), `approved`, `skipped`.

Frontend A is skipped because mock mode is not implemented (`docs/development/frontend.md`); the screens are reviewed at the end.

## Decisions

- 2026-10-15, step 1: "approved, go with both recommendations": keep `user` absent from `GET /api/v1/me` with a Jackson `NON_NULL` override for the `Me` type only, plus a web test that `user` is absent and `firstName: null` is present (step 4); keep `ClientConfig.issuer` as `format: uri`.
- 2026-10-15, step 2: "step 2 approved, keep both, add the rationale to ADR 0004": keep `check (email <> '')` and `user_roles_role_idx`; record in ADR 0004 why `roles` is a text reference table with a foreign key rather than a Postgres enum type.
- 2026-10-15, frontend B: review round 1 found 4 major findings (library post-callback navigation, discovery failure spinner, guard per descendant, `/unavailable` URL deviation); sent to frontend-dev. Minor findings and nits wait for the user.

## Open questions

- none

## Agents

<!-- Agent ids can be resumed only within the session that created them. -->

| Agent | Id | Session started |
|---|---|---|
| backend-dev | aacd95694900cb658 | 2026-10-15 (cd96bb6a) |
| frontend-dev | a9666742d8b68f688 | 2026-10-15 (cd96bb6a) |
| reviewer (frontend) | abe411b7ef761b487 | 2026-10-15 (cd96bb6a) |
