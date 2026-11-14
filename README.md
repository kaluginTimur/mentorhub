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

MentorHub stores no passwords: sign-in goes through Keycloak (`docs/architecture/decisions/0003-external-identity-provider.md`). Locally, Keycloak runs from `infra/compose.yaml` and imports the `mentorhub` realm from `infra/keycloak/mentorhub-realm.json`.

Every development user has the password `mentorhub`:

| Email | In MentorHub |
|---|---|
| `admin@mentorhub.local` | Active, ADMIN |
| `coordinator@mentorhub.local` | Active, COORDINATOR |
| `mentor@mentorhub.local` | Active, MENTOR |
| `student@mentorhub.local` | Active, STUDENT |
| `observer@mentorhub.local` | Active, OBSERVER |
| `admin.mentor@mentorhub.local` | Active, ADMIN and MENTOR |
| `mentor.coordinator@mentorhub.local` | Active, MENTOR and COORDINATOR |
| `noroles@mentorhub.local` | Active, no roles ("Your access is being set up") |
| `disabled@mentorhub.local` | Disabled, MENTOR ("Your access has ended") |
| `stranger@mentorhub.local` | Keycloak only, not linked until it accepts an invitation ("Access by invitation only") |

- On every start in the `dev` profile, the backend replaces its whole database with the sample center data, these users included (`docs/requirements/development-mode.md`). Everything changed while it ran is discarded: users, roles, profiles, invitations and the change history. Accounts registered at Keycloak stay there, linked to no user.
- `admin@mentorhub.local` is also the administrator bootstrap address in `dev` (`docs/requirements/identity.md`).
- New accounts can be registered at Keycloak ("Register" on the sign-in page). Registration requires email verification: open the verification email in Mailpit. "Forgot password?" also sends its email there.

### The sample center data

Besides the development users, the dev database holds a fictional training center (`backend/src/main/resources/db/sample/`; the header of each script describes it): 26 more users (students, mentors, a coordinator, an observer, users disabled for each reason), invitations in every status with invitation emails in every status, and the change history behind them. The users list and the invitations list have more than one page, and the user page of Daniel Kim has more history than one page. Sign in as `admin@mentorhub.local` or `admin.mentor@mentorhub.local` to see them; the other development users see what their roles allow.

The sample center has a pending invitation for `stranger@mentorhub.local` with the Mentor role and a fixed link:

http://localhost:4200/invite/sample-center-invitation-token-000000000001

Open it signed out to see the landing page, then sign in as `stranger@mentorhub.local` to accept it. The link works until it is accepted or revoked, or for 6 days after the start (it was created a day before); the next start brings it back.

### Inviting and accepting locally

1. Sign in as `admin@mentorhub.local`, open "Users" and choose "Invite" (or open "Invitations"). Enter the email and the roles to grant. To invite `stranger@mentorhub.local` yourself, revoke the sample invitation for it in "Invitations" first: an open invitation blocks another one for the same email. "Send the invitation by email" is checked by default: the email arrives in Mailpit. You can also copy the link from the "Invitation link" dialog, copy it again later with "Copy link", or send it again with "Send email" in the invitations list while it is pending.
2. Sign out, open the link and sign in with the invited email, or choose "Create an account" and verify the email through Mailpit. Accepting needs an email that Keycloak has verified; `stranger@mentorhub.local` is already verified.
3. After accepting, you land on the home page with the invited roles; the user's page shows the acceptance in its history.

Once `stranger@mentorhub.local` has accepted, it stays linked to its user until the backend is started again; the next start unlinks it, so it can accept again.

### User administration walkthrough

1. Sign in as `admin@mentorhub.local`. The toolbar shows "Users"; development users without ADMIN do not see it.
2. "Users" (`/users`) lists all users, with search by name or email and filters by status and role.
3. Open a user (`/users/{id}`) to edit the profile, disable or enable the user (disabling asks for a reason), change roles, and read the change history. Your own page does not let you disable yourself or remove your own Administrator role.
4. "Profile" in the account menu (`/profile`) edits your own name and phone; every active user with roles has it.

The next start of the backend brings back the sample center data: every development user gets back its status and exactly its roles in the table above, and profile changes and the change history made while it ran are gone.

### Profiles

- The `dev` profile is activated only by `./mvnw spring-boot:run` and by `TestMentorHubApplication`. It trusts the local Keycloak, replaces the database with the sample center data on every start and serves Swagger UI, the API docs and the actuator `info` and `modulith` endpoints.
- Without `dev` (production), only health is served, and the application refuses to start until the trusted issuers (`mentorhub.security.trusted-issuers`), the client configuration (`mentorhub.security.client.*`) and the frontend URL for invitation links (`mentorhub.app-url`) are set.
- `TestMentorHubApplication` (in `backend/src/test`) runs `dev` with a throwaway Postgres from Testcontainers and does not start Keycloak. To sign in, start Keycloak first: `docker compose -f infra/compose.yaml up -d keycloak`.

### Changing the realm

Keycloak imports the realm only when it does not exist yet, so a changed `infra/keycloak/mentorhub-realm.json` takes effect only in a recreated container:

```bash
docker compose -f infra/compose.yaml up -d --force-recreate keycloak
```

This deletes Keycloak's own data, which lives inside the container: accounts registered locally, sessions and changes made in the admin console. The development users' accounts come back from the realm file. The dev database is not touched.

A Keycloak container created before an account was added to the realm file lacks that account: recreate it once after pulling such a change (the sample center added `coordinator@`, `observer@`, `admin.mentor@` and `mentor.coordinator@mentorhub.local`).

Do not use `docker compose down -v` for this: it also deletes the `postgres-data` volume, which holds the dev database.

## Layout

| Path | Contents |
|---|---|
| `AGENTS.md` | Instructions for AI coding agents (Claude Code reads it through `CLAUDE.md`) |
| `api/openapi.yaml` | API contract; backend interfaces and the frontend client are generated from it |
| `backend/` | Spring Boot 4 + Spring Modulith |
| `frontend/` | Angular 22 + Tailwind CSS v4 + Spartan UI |
| `infra/` | Docker Compose and the Keycloak realm for the local environment |
| `docs/` | Documentation; `docs/README.md` says which document holds what |

## Checks (same as CI)

```bash
cd backend && ./mvnw verify
cd frontend && npm ci && npm run format:check && npm run lint && npm test -- --watch=false && npm run build
node --test scripts/requirements.test.mjs && node scripts/requirements.mjs trace
```

The pre-commit hook also runs `node scripts/check-docs.mjs`.

## Setup

Enable the repository's git hooks once per clone; they check that documentation still matches the code on every commit (see `docs/development/docs.md`):

```bash
git config core.hooksPath .githooks
```

Install the browser that the manual tester (an agent that looks at the running screens) drives through `playwright-cli`, once per machine and again after `playwright-cli` is upgraded. Screenshots go to `.screens/`, which git ignores:

```bash
cd frontend && npm ci && npx playwright-cli install-browser chromium
```

On WSL or a bare Linux machine, Chromium also needs system libraries. Install them once with `sudo` (it asks for your password); the command is not needed when the check below already works:

```bash
cd frontend && npx playwright-cli install-browser chromium --with-deps
```

Check it (the session needs `--browser=chromium`: the default is an installed Google Chrome):

```bash
cd frontend && npx playwright-cli -s=check open http://example.com --browser=chromium && npx playwright-cli -s=check close
```

In IntelliJ IDEA, delegate the build to Maven (Settings → Build, Execution, Deployment → Build Tools → Maven → Runner → "Delegate IDE build/run actions to Maven"). The backend compiles through Error Prone with NullAway, configured for Maven only (`backend/.mvn/jvm.config`); IDEA's own builder fails with "plug-in not found: ErrorProne", and with it every run and debug from the IDE.

Run backend applications and tests from IDEA with the working directory `backend/` (in each run configuration, and in the JUnit configuration template for new ones), as the Maven commands run: `spring.docker.compose.file` is relative to it, and from the repository root startup fails with "'files' content [../infra/compose.yaml] must exist".
