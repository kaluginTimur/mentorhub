# 0002. Contract-first API with OpenAPI

## Context

The backend and frontend live in one repository and often change in a single task.

## Decision

- The source of truth is `api/openapi.yaml` (OpenAPI 3.1). Every API change starts by editing it.
- Backend: `openapi-generator-maven-plugin` (generator `spring`, `interfaceOnly`, `useSpringBoot4`, `useJackson3`) generates the `*Api` interfaces and DTOs into `com.mentorhub.api` (`target/generated-sources/openapi`). Controllers implement these interfaces. The `com.mentorhub.api` module is open (`Type.OPEN`): every module with endpoints depends on it.
- Frontend: `ng-openapi-gen` generates the client into `frontend/src/app/core/api` before `start`, `build`, `test` and `lint`.
- Generated code is neither committed nor edited by hand.
- Swagger UI (`/swagger-ui.html`) shows the contract itself (`/openapi.yaml`), not a specification derived from the code.

## Why

A mismatch between backend and frontend is caught at build time, not at runtime: if the contract changes but a controller does not, the backend fails to compile, and the frontend fails on types.

## Rejected alternatives

- **Deriving the specification from the code.** The contract would follow the implementation instead of leading it.
