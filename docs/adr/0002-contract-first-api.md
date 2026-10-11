# 0002. Contract-first API with OpenAPI

Status: accepted, 2026-10-11

## Context

The backend and frontend live in one repository and often change in a single task. A mismatch between them should be caught at build time, not at runtime.

## Decision

- The source of truth is `api/openapi.yaml` (OpenAPI 3.1).
- Backend: `openapi-generator-maven-plugin` (generator `spring`, `interfaceOnly`, `useSpringBoot4`, `useJackson3`) generates the `*Api` interfaces and DTOs into `com.mentorhub.api` (`target/generated-sources/openapi`). Controllers implement these interfaces.
- Frontend: `ng-openapi-gen` generates the client into `frontend/src/app/core/api` before `start`, `build`, `test` and `lint`.
- Generated code is neither committed nor edited by hand.
- Swagger UI (`/swagger-ui.html`) shows the contract itself (`/openapi.yaml`), not a specification derived from the code.

## Consequences

- Every API change starts by editing `api/openapi.yaml`.
- If the contract changes but a controller does not, the backend fails to compile; the frontend fails on types.
- The `com.mentorhub.api` module is open (`Type.OPEN`): every module with endpoints depends on it.
