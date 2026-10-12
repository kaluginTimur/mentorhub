# MentorHub

A platform for mentors of a training center: student and cohort tracking, feedback, and assignments with automatic checking.

## Requirements

- JDK 25
- Node 24.15+ (`nvm use` in `frontend/`)
- Docker (Postgres for development and Testcontainers)

## Quick start

```bash
# Backend on :8080. Postgres from infra/compose.yaml starts automatically
cd backend && ./mvnw spring-boot:run

# Frontend on :4200, /api is proxied to the backend
cd frontend && npm ci && npm start
```

- Application: http://localhost:4200
- Swagger UI (API contract): http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## Layout

| Path | Contents |
|---|---|
| `api/openapi.yaml` | API contract; backend interfaces and the frontend client are generated from it |
| `backend/` | Spring Boot 4 + Spring Modulith |
| `frontend/` | Angular 22 + Angular Material |
| `infra/` | Docker Compose for the local environment |
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
