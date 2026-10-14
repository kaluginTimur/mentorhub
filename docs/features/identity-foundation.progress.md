# Identity foundation: implementation progress

Spec: `docs/features/identity-foundation.md`
Branch: `feature/identity-foundation`
Started: 2026-10-15

## Steps

| Step | Status | Commit | Updated |
|---|---|---|---|
| 1 contract | approved | | 2026-10-15 |
| 2 migration | pending | | |
| 3 domain | pending | | |
| 4 api | pending | | |
| frontend A (prototype) | skipped | | 2026-10-15 |
| frontend B | in progress | | 2026-10-15 |
| review | pending | | |
| verify | pending | | |

Status: `pending`, `in progress`, `in review` (report shown, waiting for the user), `approved`, `skipped`.

Frontend A is skipped because mock mode is not implemented (`docs/development/frontend.md`); the screens are reviewed at the end.

## Decisions

- 2026-10-15, step 1: "approved, go with both recommendations": keep `user` absent from `GET /api/v1/me` with a Jackson `NON_NULL` override for the `Me` type only, plus a web test that `user` is absent and `firstName: null` is present (step 4); keep `ClientConfig.issuer` as `format: uri`.

## Open questions

- none

## Agents

<!-- Agent ids can be resumed only within the session that created them. -->

| Agent | Id | Session started |
|---|---|---|
| backend-dev | aacd95694900cb658 | 2026-10-15 (cd96bb6a) |
| frontend-dev | a9666742d8b68f688 | 2026-10-15 (cd96bb6a) |
