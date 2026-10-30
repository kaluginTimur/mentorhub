# MentorHub

A platform for mentors of a training center: student and cohort tracking, feedback, and assignments with automatic checking.

## Requirements

- JDK 25
- Node 24.15+ (`nvm use` in `frontend/`)
- Docker (Postgres, Keycloak and Mailpit for development, Testcontainers for tests)

## Quick start

```bash
# Backend on :8080 in the dev profile. Postgres, Keycloak and Mailpit from infra/compose.yaml start automatically
cd backend && ./mvnw spring-boot:run

# Frontend on :4200, /api is proxied to the backend
cd frontend && npm ci && npm start
```

- Application: http://localhost:4200 (sign in as a development user, see below)
- Swagger UI (API contract, `dev` profile only): http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Keycloak admin console: http://localhost:8180 (user `admin`, password `admin`)
- Mailpit (all local email: MentorHub invitation emails, Keycloak verification and password reset): http://localhost:8025

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
| `stranger@mentorhub.local` | Keycloak only, not linked until it accepts an invitation ("Access by invitation only") |

- On every start in the `dev` profile, the backend creates or updates these users in its database, so no manual setup is needed.
- `admin@mentorhub.local` is also the administrator bootstrap address in `dev` (`docs/adr/0005-administrator-bootstrap.md`).
- New accounts can be registered at Keycloak ("Register" on the sign-in page). Registration requires email verification: open the verification email in Mailpit. "Forgot password?" also sends its email there.

### Inviting and accepting locally

1. Sign in as `admin@mentorhub.local`, open "Users" and choose "Invite" (or open "Invitations"). Enter the email and the roles to grant. "Send the invitation by email" is checked by default: the email arrives in Mailpit. You can also copy the link from the "Invitation link" dialog, copy it again later with "Copy link", or send it again with "Send email" in the invitations list while it is pending.
2. Sign out, open the link and sign in with the invited email, or choose "Create an account" and verify the email through Mailpit. Accepting needs an email that Keycloak has verified; `stranger@mentorhub.local` is already verified.
3. After accepting, you land on the home page with the invited roles; the user's page shows the acceptance in its history.

Once `stranger@mentorhub.local` has accepted, it stays linked to its user until the dev database is reset; restarting the backend does not unlink it. Resetting the dev database means deleting the `postgres-data` volume, which deletes all local data, not only this user.

### User administration walkthrough

1. Sign in as `admin@mentorhub.local`. The toolbar shows "Users"; other development users do not see it.
2. "Users" (`/users`) lists all users, with search by name or email and filters by status and role.
3. Open a user (`/users/{id}`) to edit the profile, disable or enable the user (disabling asks for a reason), change roles, and read the change history. Your own page does not let you disable yourself or remove your own Administrator role.
4. "Profile" in the account menu (`/profile`) edits your own name and phone; every active user with roles has it.

The backend restores the development users on its next start: a development user disabled locally is enabled again, with its disable reason and comment cleared, and gets back exactly its configured roles (`DevelopmentUserRunner`). Profile changes and the change history stay.

### Profiles

- The `dev` profile is activated only by `./mvnw spring-boot:run` and by `TestMentorHubApplication`. It trusts the local Keycloak, creates the development users and serves Swagger UI, the API docs and the actuator `info` and `modulith` endpoints.
- Without `dev` (production), only health is served, and the application refuses to start until the trusted issuers (`mentorhub.security.trusted-issuers`), the client configuration (`mentorhub.security.client.*`) and the frontend URL for invitation links (`mentorhub.app-url`) are set.
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
