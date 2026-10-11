# MentorHub

A platform for mentors of a training center (SWE, QA, DevOps tracks): student and cohort tracking, notes and feedback, assignments with automatic checking (SQL first, then code and QA), meeting scheduling.

## Layout

- `api/openapi.yaml`: API contract, **the source of truth** (ADR 0002)
- `backend/`: Java 25, Spring Boot 4.1, Spring Modulith, Maven. See `backend/CLAUDE.md`
- `frontend/`: Angular 22, Angular Material. See `frontend/CLAUDE.md`
- `infra/compose.yaml`: local environment (PostgreSQL 18; Keycloak later)
- `docs/adr/`: architecture decisions. **Read before planning changes**
- `docs/features/`: feature specs (scenarios per role, acceptance criteria)

## Commands

| Task | Command |
|---|---|
| Backend: build, tests, formatting, module checks | `cd backend && ./mvnw verify` |
| Backend: auto-format | `cd backend && ./mvnw spotless:apply` |
| Backend: run (Postgres starts automatically) | `cd backend && ./mvnw spring-boot:run` |
| Backend: run with Postgres from Testcontainers | `TestMentorHubApplication` in `src/test` |
| Frontend: install | `cd frontend && npm ci` |
| Frontend: dev server on :4200 (`/api` proxied to :8080) | `cd frontend && npm start` |
| Frontend: lint, tests, build | `npm run lint && npm test -- --watch=false && npm run build` |
| Frontend: format | `npm run format` |

Node 24 via nvm (`frontend/.nvmrc`). If `node -v` is below 24.15, run `source ~/.nvm/nvm.sh && nvm use`.

## Rules

- **Contract first.** An API change starts in `api/openapi.yaml`; the backend then implements the generated `*Api` interface and the frontend uses the generated client. Never edit generated code by hand.
- Before planning a feature, read the related ADRs and the spec in `docs/features/`. Record significant new architectural decisions as an ADR.
- A feature is a vertical slice: contract, migration, backend, frontend, tests.
- Before considering work done: `./mvnw verify` in `backend/` and `npm run lint && npm test -- --watch=false && npm run build` in `frontend/`.
- Security (ADR 0003, implemented later): the application stores no passwords; domain data references `users.id`, never the token `sub`; access to cohorts is checked at the resource level.
- Student code and SQL are never executed in the backend process or in the application database.
- All project artifacts (code, docs, UI text, commit messages) are in English.
- Comment sparingly: only non-obvious infrastructure or algorithmic points. No comments that restate the code.
