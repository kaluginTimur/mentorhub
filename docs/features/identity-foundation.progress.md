# Identity foundation: implementation progress

Spec: `docs/features/identity-foundation.md`
Branch: `feature/identity-foundation`
Started: 2026-10-15

## Steps

| Step | Status | Commit | Updated |
|---|---|---|---|
| 1 contract | approved | cc86c7f | 2026-10-15 |
| 2 migration | approved | b3cc411 | 2026-10-15 |
| 3 domain | approved | 6f6e0f5 | 2026-10-15 |
| 4 api | approved | 849b74d | 2026-10-15 |
| 4b local environment | approved | ecd85fc | 2026-10-15 |
| frontend A (prototype) | skipped | | 2026-10-15 |
| frontend B | approved | 84f1007 | 2026-10-15 |
| review | approved | 177b8fb | 2026-10-15 |
| verify | done | | 2026-10-15 |

Status: `pending`, `in progress`, `in review` (report shown, waiting for the user), `approved`, `skipped`.

Frontend A is skipped because mock mode is not implemented (`docs/development/frontend.md`); the screens are reviewed at the end.

## Decisions

- 2026-10-15, step 1: "approved, go with both recommendations": keep `user` absent from `GET /api/v1/me` with a Jackson `NON_NULL` override for the `Me` type only, plus a web test that `user` is absent and `firstName: null` is present (step 4); keep `ClientConfig.issuer` as `format: uri`.
- 2026-10-15, step 2: "step 2 approved, keep both, add the rationale to ADR 0004": keep `check (email <> '')` and `user_roles_role_idx`; record in ADR 0004 why `roles` is a text reference table with a foreign key rather than a Postgres enum type.
- 2026-10-15, frontend B: review round 1 found 4 major findings (library post-callback navigation, discovery failure spinner, guard per descendant, `/unavailable` URL deviation); sent to frontend-dev. Minor findings and nits wait for the user.
- 2026-10-15, frontend B: round 1 fixes reported done (53 tests, build without warnings); round 2 review started.
- 2026-10-15, frontend B: round 2 review: all 4 round 1 majors fixed; 2 new majors (`failedUrl` after a callback loses the return route; startup-error Retry reloads a stale callback URL) sent to frontend-dev as the last fix round. Minors so far wait for the user (round 1: 5-16; round 2: docs overstate reload-as-retry, second discovery fetch race, interceptor 401 path, weak guard tests).
- 2026-10-15, frontend B: round 2 fixes reported done (56 tests, build without warnings). Review rounds exhausted; remaining minors shown to the user.
- 2026-10-15, frontend B: "can we fix now all small findings and start a reviewer again?": all remaining minor findings and nits (round 1: 6-16; round 2: 3-6) sent to frontend-dev, then a new review.
- 2026-10-15, frontend B: minor fixes reported done (59 tests, initial bundle 542 kB, budget lowered to 550 kB; 15 s watchdog for `authorize()` is the agent's choice). Round 3 review started.
- 2026-10-15, frontend B: round 3 full review is non-blocking: 1 major (Home reads `resource().value()` without an error check, and the guide points to it as the example), 6 minors, 6 nits; waiting for the user.
- 2026-10-15, step 3: "I don't like cluttered classes folders. there are repositories in the same folder as DTOs, handlers and such. I want file structure to consider separation of concerns". Chose "by layer" subpackages under `<module>.internal` (domain, persistence, security, bootstrap; web and service from step 4), applied now as part of step 3, recorded as a backend convention.
- 2026-10-15, step 3 / frontend B: "keep about:blank, split step 4, fix your recommended set": keep writing `type: about:blank` explicitly (the contract keeps `type` required); step 4 = API layer and dev/prod configuration (`/me`, client-config, `@NoPermissionRequired`, AC-18, AC-25, AC-26), step 4b = Keycloak in compose, realm JSON, dev user runner, README, ADR 0003 status; frontend round 3: fix 1, 2, 3, 5, 6, 7, 10, 11, 12, fix 4 if simple, correct the docs for 9; 8 (`/unavailable` outside the guard) and 13 (route titles) are follow-ups.
- 2026-10-15, frontend B: round 3 fixes reported done (67 tests, initial bundle 543.9 kB of 550 kB). Agent choices: renewal 30 s; logoff failure clears local tokens and shows the startup error; no `@error` on the loading defer block; guard doc reworded instead of `runGuardsAndResolvers: always`.
- 2026-10-15, step 3: restructure reported done (57 tests). Agent moved `SignInClaims` to `bootstrap/` and creates `AdministratorBootstrap` as a `@Bean` in `security/` to avoid a package cycle; shared test support stays in `users/internal/security/` (test sources) so security internals stay package-private.
- 2026-10-15, step 3 and frontend B: "approve both, commit both".
- 2026-10-15, step 4: "approve step 4, accept stricter validation, commit and go 4b": required security settings fail startup in every profile (accepted deviation from "outside `dev`"); `spring-boot:run` is fixed in step 4b.
- 2026-10-15, step 4b: user ran `spring-boot:run` and `npm start` and reported the manual checks (AC-1, AC-8, AC-30 and the frontend screens): "I've done manual testing, seems ok".
- 2026-10-15, step 4b: "approve 4b, commit".
- 2026-10-15, review: final review non-blocking, 4 minors (plugin-level `dev` profile leaks into AOT; filter-chain errors not rendered as Problem; AC-12 linked+disabled untested; spec drift) and 5 nits; waiting for the user.
- 2026-10-15, review: "go with your recommendation": backend-dev fixes 1, 2, 3, 5, 6, 7, 8; frontend-dev fixes 9; the coordinator updates the spec (4) after the feature is accepted; then verifier.
- 2026-10-15, review: fixes reported done (91 backend tests). Item 2 done with a Problem-rendering `ErrorController` instead of a filter failure handler (covers non-authentication errors too); item 5 bound only Keycloak to 127.0.0.1 (changing Postgres ports would recreate the dev DB container). Focused review of the fix diff and verifier started.
- 2026-10-15, verify: all PASS (backend verify, frontend format/lint/test 67/build, docs check). The verifier counted 143 backend tests from surefire reports, which include stale reports from before the package moves; backend-dev counts 91 in the current run.
- 2026-10-15, verify: user asked "run clean verify": `./mvnw clean verify` PASS, 21 suites, 91 tests, 0 failures, 0 errors, 0 skipped.
- 2026-10-15, review: focused review of the fix diff non-blocking, 6 minors (error path property renamed in Boot 4.1; `title` not asserted; backend.md drift on the `dev` activation and on `ProblemErrorController`; Postgres binding rationale; no timeout on JWKS fetch). Waiting for the user.
- 2026-10-15, review: "fix 1-4 and 6, bind postgres to localhost too": error path property, `title` assertion, backend.md drift (dev activation, `ProblemErrorController`), connect/read timeouts on the JWKS/discovery fetch (production and test), Postgres bound to 127.0.0.1 (next `up` recreates the container; the `postgres-data` volume is kept). Correction: the earlier note that recreating the container was a reason not to bind Postgres was wrong; the volume survives.
- 2026-10-15, review: fixes reported done (92 tests, clean verify): Boot 4.1 error path property, `title` asserted, backend.md updated, `issuer-connect-timeout` 2 s and `issuer-read-timeout` 5 s for discovery and JWKS (tests 1 s, hanging-issuer test), Postgres on 127.0.0.1. Final verifier started.
- 2026-10-15, verify: final verifier all PASS (backend clean verify 92/92, frontend format/lint/test 67/67/build, docs check).
- 2026-10-15, feature: "go on": the user accepted the feature; the spec's Design section was updated to match the implementation and its status set to `implemented`.
- 2026-10-15, feature: "Commit review fixes and spec".

## Open questions

- none

## Agents

<!-- Agent ids can be resumed only within the session that created them. -->

| Agent | Id | Session started |
|---|---|---|
| backend-dev | aacd95694900cb658 | 2026-10-15 (cd96bb6a) |
| frontend-dev | a9666742d8b68f688 | 2026-10-15 (cd96bb6a) |
| reviewer (frontend) | abe411b7ef761b487 | 2026-10-15 (cd96bb6a) |
| reviewer (frontend round 2) | ae497cee9ce40b05e | 2026-10-15 (cd96bb6a) |
| reviewer (frontend round 3) | acdbde34aea9ee9bb | 2026-10-15 (cd96bb6a) |
| reviewer (final) | a4d438dd9785e1ff1 | 2026-10-15 (cd96bb6a) |
| verifier (all) | a24e569aba5c1186c | 2026-10-15 (cd96bb6a) |
| reviewer (fix diff) | a6819c2d017c781e5 | 2026-10-15 (cd96bb6a) |
| verifier (final) | a00eb783c7732d240 | 2026-10-15 (cd96bb6a) |
