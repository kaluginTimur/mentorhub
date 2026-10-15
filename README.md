# MentorHub

A platform for mentors of a training center: student and cohort tracking, feedback, and assignments with automatic checking.

## Requirements

- JDK 25
- Node 24.15+ (`nvm use` in `frontend/`)
- Docker (Postgres and Keycloak for development, Testcontainers for tests)

## Quick start

```bash
# Backend on :8080 in the dev profile. Postgres and Keycloak from infra/compose.yaml start automatically
cd backend && ./mvnw spring-boot:run

# Frontend on :4200, /api is proxied to the backend
cd frontend && npm ci && npm start
```

- Application: http://localhost:4200 (sign in as a development user, see below)
- Swagger UI (API contract, `dev` profile only): http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Keycloak admin console: http://localhost:8180 (user `admin`, password `admin`)

## Sign-in and development users

MentorHub stores no passwords: sign-in goes through Keycloak (`docs/adr/0003-external-identity-provider.md`). Locally, Keycloak runs from `infra/compose.yaml` and imports the `mentorhub` realm from `infra/keycloak/mentorhub-realm.json`.

Every development user has the password `mentorhub`:

| Email | In MentorHub |
|---|---|
| `admin@mentorhub.local` | Active, ADMIN |
| `mentor@mentorhub.local` | Active, MENTOR |
| `student@mentorhub.local` | Active, STUDENT |
| `noroles@mentorhub.local` | Active, no roles ("Your access is being set up") |
| `disabled@mentorhub.local` | Disabled ("Your access has ended") |
| `stranger@mentorhub.local` | Keycloak only, not invited ("Access by invitation only") |

- On every start in the `dev` profile, the backend creates or updates these users in its database, so no manual setup is needed.
- `admin@mentorhub.local` is also the administrator bootstrap address in `dev` (`docs/adr/0005-administrator-bootstrap.md`).
- Registering new accounts at Keycloak does not work locally yet: registration requires email verification, and the local environment sends no email.

### Profiles

- The `dev` profile is activated only by `./mvnw spring-boot:run` and by `TestMentorHubApplication`. It trusts the local Keycloak, creates the development users and serves Swagger UI, the API docs and the actuator `info` and `modulith` endpoints.
- Without `dev` (production), only health is served, and the application refuses to start until the trusted issuers (`mentorhub.security.trusted-issuers`) and the client configuration (`mentorhub.security.client.*`) are set.
- `TestMentorHubApplication` (in `backend/src/test`) runs `dev` with a throwaway Postgres from Testcontainers and does not start Keycloak. To sign in, start Keycloak first: `docker compose -f infra/compose.yaml up -d keycloak`.

### Changing the realm

Keycloak imports the realm only when it does not exist yet, so a changed `infra/keycloak/mentorhub-realm.json` takes effect only in a recreated container:

```bash
docker compose -f infra/compose.yaml up -d --force-recreate keycloak
```

This deletes Keycloak's own data, which lives inside the container: accounts registered locally, sessions and changes made in the admin console. The development users come back from the realm file. The dev database is not touched.

Do not use `docker compose down -v` for this: it also deletes the `postgres-data` volume, which holds the dev database.

## Layout

| Path | Contents |
|---|---|
| `AGENTS.md` | Instructions for AI coding agents (Claude Code reads it through `CLAUDE.md`) |
| `api/openapi.yaml` | API contract; backend interfaces and the frontend client are generated from it |
| `backend/` | Spring Boot 4 + Spring Modulith |
| `frontend/` | Angular 22 + Angular Material |
| `infra/` | Docker Compose and the Keycloak realm for the local environment |
| `docs/adr/` | Architecture decision records |
| `docs/architecture/` | Domain model and invariants |
| `docs/development/` | Development guides (backend, frontend, documentation): conventions, commands, verification |
| `docs/features/` | Feature specifications |

## Checks (same as CI)

```bash
cd backend && ./mvnw verify
cd frontend && npm ci && npm run lint && npm test -- --watch=false && npm run build
node scripts/check-docs.mjs
```

## Setup

Enable the repository's git hooks once per clone; they check that documentation still matches the code on every commit (see `docs/development/docs.md`):

```bash
git config core.hooksPath .githooks
```
