# Validate API responses against the OpenAPI contract in tests

Branch: `chore/openapi-test-validation`
Roadmap item: none (GitHub issue #13)

## Scope

- **Goal:** Provide automated verification in tests that real backend HTTP responses (and opt-in requests) and frontend API test fixtures conform to `api/openapi.yaml` (decision 0002), catching missing required fields, invalid formats/enums, invalid problem schemas, and leaked extra properties automatically.
- **In scope:**
  - Close object DTO schemas in `api/openapi.yaml` with `additionalProperties: false` while keeping `Problem` (and its `allOf` extensions `ValidationProblem` and `InvitationConflictProblem`) open per RFC 9457, so schema strictness lives in the schema rather than in validator configuration.
  - Evaluate and add an OpenAPI 3.1-compatible validator to backend test dependencies, wire automatic response validation into `@ControllerSliceTest` and `@EndpointTest` (`MockMvc`), and provide opt-in request validation where appropriate.
  - Type frontend `Api` test mocks and fixtures with the generated models (`satisfies` or typed factories) so contract changes break fixtures at compile time.
  - Record the library decision (ADR or guide rule, as appropriate at finish).
- **Out of scope:** Changing product requirements, runtime API behavior, or adding new migrations.
- **Requirements searched:** `docs/requirements/README.md`, `platform.md`, `invitations.md`, `invitation-emails.md`, `user-administration.md`, `identity.md`, `audit.md`. No requirement describes test-time OpenAPI validation or test fixture typing, and none should (test/verification mechanics, not user- or API-client-visible product behavior).

## Tasks

| Task | Who | Status |
|---|---|---|
| T1. Evaluate OpenAPI 3.1 validator, update `api/openapi.yaml` `additionalProperties`, wire MockMvc response validation & opt-in request validation | `backend-dev` | done |
| T2. Type frontend `Api` test mocks and fixtures with generated models | `frontend-dev` | done |
| T2b. Replace untyped `Api`/`HttpErrorResponse` mocks with typed test helpers + ESLint enforcement in `*.spec.ts` | `frontend-dev` | done |
| T3. Independent review (`reviewer`), full verification (`verifier`), and finish | `reviewer`, `verifier`, coordinator | done |

## Decisions

- 2026-11-14: Confirmed goal, approach, and path `chore/openapi-test-validation` for GitHub issue #13.
- 2026-11-14: Put `additionalProperties` restrictions in `api/openapi.yaml` (keeping `Problem` / its `allOf` extensions open) rather than encoding schema-specific exceptions in the validator ("делаем ограничение в схеме, иначе специфика самой схемы протечёт в валидатор. как по мне, это нарушение разделения ответственности").
- 2026-11-14: Accepted coordinator recommendations on the remaining points: validate MockMvc responses by default with opt-in request validation (avoiding conflicts with negative tests like `OperationGateTests` and `PLT-2`), and deliver backend and frontend parts together in `chore/openapi-test-validation`.
- 2026-11-14: Rejected adding an ADR for test-time OpenAPI schema validation ("никакого ADR. валидация схемы в тестах не тянет на decision в моём понимании").
- 2026-11-14: Enforce frontend test contract typing via code and ESLint instead of a guide note ("реализуй вариант 1"): typed `Api` stub + `Problem` `HttpErrorResponse` helper and ESLint rules in `*.spec.ts`.

## Handover

- Waiting for: Owner acceptance of the completed change and approval to commit on `chore/openapi-test-validation`
- To read: `docs/plans/openapi-test-validation.md`
- Documents changed: `docs/plans/openapi-test-validation.md`, `api/openapi.yaml` (no ADR and no guide changes, per owner decisions)
- State: T1, T2, T2b, and T3 checks complete. Independent `reviewer` returned `CLEAN` (0 findings). Final `verifier` (`all` at `1362400` + 28 uncommitted files) passed every check (`backend` 353/353 across 54 suites, `frontend` format:check/lint/285/285 across 25 files/build, `docs` check-docs/64/64/206/206 trace).
- Open questions: Approve committing on `chore/openapi-test-validation` (suggested commit message: `chore: validate MockMvc responses and frontend test mocks against OpenAPI`)?
- On approval: Commit changes on `chore/openapi-test-validation` and point the user to `/mentorhub:pr` (leaving `docs/plans/openapi-test-validation.md` until the `pr` skill removes it)
- On remarks: Apply requested changes, re-verify affected scope, and stop again for acceptance

## Agents

| Agent | Model | Id | Context |
|---|---|---|---|
| backend-dev | pro | 3e086c92-3f57-4c37-a40e-a27ef9750599 | T1 (partial): `api/openapi.yaml` `additionalProperties: false`, selected `openapi-request-validator-core:3.0.0` |
| backend-dev | pro | 6977e2d2-6bae-4b5e-b56c-04984e147891 | T1 (continuation): MockMvc response/request validation wiring and verification |
| backend-dev | pro | 7f78358d-60f0-469e-b56c-04984e147891 | T1 (implementation): `OpenApiValidation`, MockMvc wiring, tests, `./mvnw verify` |
| backend-dev | pro | 50729f69-5b89-4bc2-bd4f-09b98b6860f2 | T1 (finalization): implement `OpenApiValidation*`, tests, `./mvnw verify` |
| backend-dev | pro | 19303e8b-32f0-4c15-b999-a536f47ab1fe | T1 (completion): fix 2 test edge cases, add `OpenApiValidationTests`, `./mvnw verify` |
| frontend-dev | inherit | ca26f2ab-c8db-4318-848a-bb5c367386e5 | T2: type frontend `Api` test mocks and fixtures with generated models |
| frontend-dev | inherit | 288dc74c-92c0-49bc-b270-ac0294034ce2 | T2 (completion): finish `user-page.spec.ts`, `users-list.spec.ts`, frontend verification |
| reviewer | pro | b2c68470-0ddb-4f77-bc98-e9d2e6f6eb2a | T3: independent review against `main` |
| verifier | inherit | 9db37bbc-5443-4f93-8f9f-bb73528cf760 | T3: full verification across backend, frontend, docs |
| frontend-dev | inherit | a2f8773a-07b7-4841-b721-12db88182e27 | T2b: implement typed `createApiStub()` / `problemError()` and ESLint rules in `*.spec.ts` |
| frontend-dev | inherit | ac8d1cc1-10eb-4b91-81b5-8820ad4eaab8 | T2b (completion): run frontend tests/build and verify T2b |
| reviewer | pro | ba9c276a-3893-4fb4-8854-516a8fd166fb | T3 (after T2b): review branch changes including `createApiStub`/`problemError` and ESLint rules |
| verifier | inherit | 49bacbae-e5f9-4c79-ae6a-87f82b9ef718 | T3 (final after T2b): full verification across backend, frontend, docs |
