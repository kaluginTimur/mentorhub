# Identity foundation

Status: implemented
Stage: tracking core (users, roles)
Related: ADR 0001, ADR 0002, ADR 0003, ADR 0004, ADR 0005

## 1. Requirements

### Goal

MentorHub must be closed to everyone except people the training center has let in, and every later feature needs a trusted current user to check access against. This feature adds sign-in through Keycloak, the user and role assignment model, and a gate that admits only known, active users. Every request then carries the user's `users.id` and permissions.

### Scope

- Keycloak in the local environment, with the realm stored as JSON in the repository and development users for every access status.
- Sign-in and sign-out in the frontend (OIDC Authorization Code + PKCE) and an application shell with the current user.
- Users, external identities and role assignments in the database. Permissions and roles as enums in code (ADR 0004), kept consistent with the stored assignments.
- The gate:
  - an identity that is not linked to a user is refused ("not invited");
  - a disabled user is refused;
  - an active user without roles is admitted but has no permissions.
- The administrator bootstrap (ADR 0005).
- The profile (email, first name, last name) is copied from the token when the user is created. After that the application owns it: later changes at the identity provider do not update it.
- `GET /api/v1/me` with the caller's access status and, for an active user, the user, roles and permissions.
- The building blocks later features use:
  - the typed permission check and the rule that every operation declares its permission;
  - the resource-level rule (inaccessible means 404);
  - Problem Details errors.

### Roles and scenarios

#### Visitor (not signed in)

- S1. As a visitor, I open any MentorHub page and am sent to sign in, so nothing is visible without signing in. After signing in I return to the page I opened.

#### Person who signed in but is not let in

- S2. As a person whose account is not linked to a MentorHub user, I see that access is by invitation only and can sign out to try another account.
- S3. As a disabled user, I see that my access has ended and can sign out. If I am disabled while working, I see this on my next action.

#### Active user (any roles or none)

- S4. As an active user, I sign in and see the application shell with my name and a sign-out action.
- S5. As an active user without roles, I see that my access is being set up and that I should contact an administrator.
- S6. As a user, I sign out and my sign-in session ends, so the next person at the same computer has to sign in again.

#### Bootstrap administrator

- S7. As the person whose email is set as the bootstrap address, I sign in while the installation has no active administrator and become an active administrator, so I can let others in. This works both on a new installation and when every administrator was lost.

#### Developer

- S8. As a developer, I start the local environment and can sign in as a development user in every access status (admin, mentor, student, no roles, disabled, not invited) without any manual setup.

### Acceptance criteria

Each criterion is marked **auto** (covered by automated tests) or **manual** (checked by hand in the local environment; the repository has no browser end-to-end tests).

Sign-in and the gate:

- AC-1 (S1, manual): Given I am not signed in, when I open any application route, then I am redirected to the Keycloak sign-in page, and after signing in I land on the route I opened.
- AC-2 (S1, auto): Given a request without a valid token to any endpoint that is not public (see the access matrix), when it is sent, then the response is 401 with the Problem Details type `unauthorized` and a `WWW-Authenticate: Bearer` header.
- AC-3 (S2, auto): Given I signed in with an identity not linked to any user, then:
  - `GET /api/v1/me` returns status `NOT_INVITED` and no `user`;
  - every other non-public endpoint responds 403 with the type `not-invited`;
  - no database rows are created;
  - the application shows "Access by invitation only" with a sign-out action.
- AC-4 (S3, auto): Given my user is disabled, then `GET /api/v1/me` returns status `DISABLED` and no `user`, and every other non-public endpoint responds 403 with the type `access-ended`. Disabling takes effect on the next request, without waiting for the token to expire.
- AC-5 (S3, auto): Given I am using the application and my user is disabled, when my next API call responds 403 with the type `access-ended`, then the application shows "Your access has ended".
- AC-6 (S4, auto): Given I am an active user with roles, when the application loads, then the shell shows my name built from the names present (first and last, or only the one that is set, or my email if neither is set) and a sign-out action.
- AC-7 (S5, auto): Given I am an active user without roles, then:
  - `GET /api/v1/me` returns status `ACTIVE` with empty roles and permissions;
  - endpoints that require a permission respond 403 with the type `forbidden`;
  - the application shows "Your access is being set up".
- AC-8 (S6, manual): Given I am signed in, when I sign out, then my Keycloak session ends, I return to the application, and it asks me to sign in again.
- AC-9 (S4, auto): Given an active user, when `GET /api/v1/me` is called, then it returns status `ACTIVE`, the user's id, email, first and last name, roles and the union of the roles' permissions.
- AC-10 (S4, auto): Given a user whose first name, last name or email changed at the identity provider after the user was created, when they sign in, then the stored profile is unchanged.

Bootstrap (ADR 0005):

- AC-11 (S7, auto): Given the bootstrap email is configured and no active user has the ADMIN role, when someone signs in with an unlinked identity whose verified email equals it (case-insensitive), then:
  - the user with that email is created (or found) and the identity is linked to it;
  - the user is active and has the ADMIN role;
  - one warning with the user id is logged.
- AC-12 (S7, auto): Given no active user has the ADMIN role and the user with the bootstrap email exists but is disabled or has no ADMIN role, when they sign in with a verified identity with that email that is unlinked or linked to that same user, then they are linked if needed, enabled and given the ADMIN role.
- AC-13 (S7, auto): Given an identity already linked to a user whose email is not the bootstrap address, when its token carries the verified bootstrap email, then the rule grants nothing, the identity stays linked to its user, and a warning is logged.
- AC-14 (S7, auto): Given an active administrator exists, when someone signs in with the bootstrap email, then the rule grants nothing: an unlinked identity gets AC-3, a linked user keeps their status and roles.
- AC-15 (S7, auto): Given the token's email is not verified, or no bootstrap email is configured, then the rule grants nothing.
- AC-16 (S7, auto): Given several first requests of the bootstrap administrator arrive in parallel, then all of them succeed, exactly one user, one identity link and one ADMIN assignment exist afterwards, and exactly one warning is logged.

Enforcement building blocks:

- AC-17 (auto): Given an endpoint that requires a permission, when an active user without that permission calls it, then it responds 403 with the type `forbidden`; with the permission, it succeeds.
- AC-18 (auto): Given every operation the application serves, when the build runs, then a test fails if an operation has neither a permission annotation nor the explicit "no permission required" marker.
- AC-19 (auto): Given a service that throws `NotFoundException` for a resource the caller may not access, then the response is 404 with the type `not-found`, identical to a missing resource.
- AC-20 (auto): Given a stored role assignment whose role the code does not know, when that user sends a request, then the request is not failed by it, the unknown role grants nothing, and a warning is logged once per unknown role name.
- AC-21 (auto): Given the role enum and the `roles` table after all migrations, when the build runs, then a test fails if they differ. The same holds for the `Role` and `Permission` enums of the contract and of the code.
- AC-22 (auto): Given an active user without roles whose token carries roles or groups (for example `realm_access.roles: [admin]`), then the user has no permissions.
- AC-23 (auto): Given a token signed by an untrusted issuer, or a trusted issuer's token without the MentorHub audience, then the request responds 401.
- AC-24 (auto): Given a stale or invalid token, when a public endpoint is called with it, then the endpoint responds as without a token.
- AC-25 (auto): Given the production profile (no `dev`), then Swagger UI, the API docs, `/openapi.yaml` and the actuator endpoints other than health are not served, and health shows no details.
- AC-26 (auto): Given any profile, when trusted issuers or the client configuration are missing, then the application fails to start with a message naming the property. (Implemented for every profile, not only production: bean validation cannot depend on the profile, and `dev` supplies the values; user decision.)

Frontend behavior:

- AC-27 (auto): Given API requests, then the access token is attached only to non-public `/api/` requests and to no other URL.
- AC-28 (auto): Given a 401 from the API, then the application starts sign-in again once, keeping the current route. Given another 401 right after that fresh sign-in, it shows the startup error screen instead of signing in again.
- AC-29 (auto): Given an operation refused with 403 `forbidden`, then the application shows "You don't have permission to do this." and stays on the page.
- AC-30 (S8, manual): Given a clean checkout, when the developer starts the local environment as the README describes, then they can sign in as each development user and see the screen matching its status.

### Access matrix

The only data this feature exposes is the caller's own user. Permissions are added by the features that check them (ADR 0004).

| Action | Not signed in | Not invited / disabled | Active, no roles | Active with roles |
|---|---|---|---|---|
| `GET /api/v1/system/info`, `GET /api/v1/system/client-config` | ✓ | ✓ | ✓ | ✓ |
| `GET /actuator/health`, `/actuator/health/**` (no details) | ✓ | ✓ | ✓ | ✓ |
| `GET /api/v1/me` (own user only) | 401 | ✓ status only | ✓ | ✓ |
| Other `/api/v1/**` operations | 401 | 403 | per permission (none: 403) | per permission |
| `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`, `/openapi.yaml`, actuator `info` and `modulith` | `dev` only | `dev` only | `dev` only | `dev` only |
| Any other path | 401 | 403 | 403 | 403 |

- Public endpoints ignore any token sent with them.
- Every `/api/v1/**` operation declares either a permission or that no permission is required (AC-18). In this feature only `GET /api/v1/me` and the public system endpoints declare "no permission required".
- An unknown `/api/v1/**` path responds 404 to an active user.
- The ADMIN role gets an explicit list of permissions, like every other role: a feature that adds a permission decides which roles get it.
- The resource-level rule for later features (`docs/architecture/invariants.md`): a single resource the caller may not access responds 404, exactly like a missing one; lists leave inaccessible items out.

### Visibility

| Data | Who sees it |
|---|---|
| A user's profile, status, roles and permissions | The user themself through `GET /api/v1/me` |
| A caller's access status (not invited, disabled) | That caller only |

### Audit

None in this feature. The administrator bootstrap is written to the application log at warning level with the user id, only when it changed something. The audit module arrives with invitations and users.

### Identity provider rules

- Self-registration is allowed (invited people create their account at Keycloak) and requires email verification.
- "Trust email" is enabled only for identity providers that verify addresses themselves (Google, the corporate SSO).
- Access tokens for the frontend client carry the MentorHub API audience.
- These rules are part of the realm JSON. Production deployment configuration is out of scope; the rules are listed here for it.

### Out of scope

- Invitations, the user administration screen, role assignment in the UI, enabling and disabling users in the UI (next feature: invitations and users).
- Profile editing, the phone number and the reason a user was disabled (next feature).
- The audit log (next feature).
- Sending email, including verification email in the local environment (Mailpit arrives with invitations). Until then, new accounts cannot be registered locally; the development users are enough.
- Enrollments, the student-mentor relation and automatic revocation after graduation (tracking core).
- Corporate SSO, identity brokering and automatic provisioning with mapped roles (a separate ADR when the details are known).
- The frontend mock mode.
- Personal data rules: consent, photo, retention (postponed).

## 2. UI

This feature creates `docs/ui/guidelines.md` with the application shell, the access screen pattern and the startup error screen below.

### Application shell and home

- Route: `/`; roles: any active user with roles.
- Purpose: the frame every later feature lives in.
- Content and actions:
  - a toolbar with "MentorHub" and a user menu;
  - the user menu shows the name (AC-6) and the email, and has the action "Sign out";
  - the home page shows "Welcome, <first name>" (or the name from AC-6) and, in the footer, the application version from `GET /api/v1/system/info`.
- States:
  - loading: a progress indicator while the sign-in completes and `GET /api/v1/me` loads;
  - error: when the API is unavailable, "MentorHub is unavailable. Try again later." with a "Retry" action that repeats the failed load;
  - an operation refused with 403 `forbidden`: a snackbar "You don't have permission to do this." (AC-29);
  - status other than active with roles: a redirect to the matching access screen.
- Patterns: Angular Material toolbar and menu; colors and typography from the theme tokens only.
- Accessibility:
  - the user menu button has an accessible name ("Account menu for <name>");
  - the page has one `h1`;
  - the error and loading states are announced to screen readers (`role="alert"` and `aria-busy`).

```
+---------------------------------------------------------------+
| MentorHub                                   [Anna Petrova  v] |
+---------------------------------------------------------------+
|                                         | Anna Petrova        |
|   Welcome, Anna                         | anna@example.com    |
|                                         | ------------------- |
|                                         | Sign out            |
|                                                               |
| MentorHub 0.1.0                                               |
+---------------------------------------------------------------+
```

### Access screens

One layout, three messages. Each screen is a centered card without the user menu, with the action "Sign out".

| Route | Shown when | Title | Text |
|---|---|---|---|
| `/access/invitation-only` | identity not linked to a user | Access by invitation only | MentorHub is available to students and staff of the training center by invitation. If you were invited, open your invitation link and sign in with the email it was sent to. |
| `/access/ended` | user disabled | Your access has ended | Contact the training center if you think this is a mistake. |
| `/access/pending` | active user without roles | Your access is being set up | Contact an administrator. |

- Every route, including the access routes, resolves to the screen that matches the current status: an active user with roles who opens an access route is redirected to `/`, and a user in an access status who opens any other route is redirected to the matching access screen.
- Unknown paths redirect to `/`.
- States: loading while `GET /api/v1/me` loads; the same error state as the shell.
- Accessibility: the title is the page's `h1` and receives focus when the screen opens.

```
+-------------------------------------------+
|                                           |
|        Access by invitation only          |
|                                           |
|  MentorHub is available to students and   |
|  staff of the training center by          |
|  invitation. If you were invited, sign in |
|  with the account the invitation was      |
|  sent to.                                 |
|                                           |
|                          [ Sign out ]     |
+-------------------------------------------+
```

### Startup error

Shown as a full page when sign-in cannot start or keeps failing:
- `GET /api/v1/system/client-config` fails;
- Keycloak's discovery document cannot be loaded;
- the sign-in redirect returns an error. A callback without a session (for example the flow was completed in another tab) or with `temporarily_unavailable` is first retried once silently; explicit errors such as `access_denied` show the error at once, and so does a second failed callback;
- the API rejects a freshly obtained token (AC-28).

Text: "MentorHub is unavailable. Try again later." The "Retry" action reloads the page. The message is announced with `role="alert"`.

## 3. Design

### Modules

- `com.mentorhub.users` (new) owns users, identities, role assignments and request authentication. Public API:
  - the `Permission` and `Role` enums (ADR 0004);
  - `CurrentUser` (user id and permissions) and a way to get it in services. The gate guarantees that services other than `GET /api/v1/me` only ever see an active user;
  - the typed permission annotation and the "no permission required" marker.

  Its `internal` package is split into layer subpackages (user decision, now the backend convention in `docs/development/backend.md`), with one-way dependencies `web` → `service` → `security` → `bootstrap` → `persistence` → `domain`:
  - `domain`: `UserStatus`, `AccessStatus`, `Caller`, `StoredRoles`;
  - `persistence`: `UserRepository` (JdbcClient) and its records;
  - `security`: the filter chains and the gate, token validation per trusted issuer, the user resolution, the 401 and 403 handlers, `CurrentCaller`;
  - `bootstrap`: the administrator bootstrap (ADR 0005);
  - `service` and `web`: `MeService` and `MeController`;
  - `dev`: the development user runner, created only in the `dev` profile.
- `com.mentorhub.web` (new, open module like `com.mentorhub.api`) holds what every module with endpoints shares: Problem Details error handling (`ProblemDetailsAdvice`, `ProblemErrorController`, `ProblemResponseWriter`) and `NotFoundException` for missing and inaccessible resources.
- `com.mentorhub.system` gains the client configuration endpoint and owns its own `@ConfigurationProperties` class for `mentorhub.security.client.*`, so it does not depend on `users` internals. Its controller moved to `system/internal/web/SystemController`.
- Frontend:
  - `frontend/src/app/core/auth/`: OIDC setup, the token interceptor, the error interceptor, the guard and the current user service;
  - `frontend/src/app/features/shell/` and `frontend/src/app/features/access/`: the shell, the access screens and the unavailable page;
  - `frontend/src/app/shared/`: the loading state and the unavailable card, loaded lazily.

### Data model

The migration adds:

- `users`:
  - `id uuid` PK default `gen_random_uuid()`;
  - `email text not null check (email <> '')`, stored as received, unique index `users_email_lower_uq` on `lower(email)`; lookups compare `lower(email)`;
  - `first_name text null`, `last_name text null`;
  - `status text not null` with a check constraint (`ACTIVE`, `DISABLED`);
  - `created_at`, `updated_at timestamptz not null default now()`; the application sets `updated_at` on change.
- `user_identities`:
  - `id uuid` PK default `gen_random_uuid()`;
  - `user_id uuid not null` FK → `users`, on delete restrict, indexed;
  - `issuer text not null`, `subject text not null`, unique constraint `user_identities_issuer_subject_uq` (`issuer`, `subject`);
  - `created_at timestamptz not null default now()`.
- `roles`: `name text` PK, a reference table seeded with ADMIN, COORDINATOR, MENTOR, STUDENT, OBSERVER.
- `user_roles`:
  - `user_id uuid not null` FK → `users`, on delete restrict;
  - `role text not null` FK → `roles`, on delete restrict;
  - PK (`user_id`, `role`), `created_at timestamptz not null default now()`;
  - index `user_roles_role_idx` on `role`, for the bootstrap's "is there an active ADMIN" check.

The `roles` table is a `text` reference table with a foreign key rather than a PostgreSQL `enum` type; the rationale is in ADR 0004 (user decision).

Users are never deleted in this feature, so foreign keys restrict deletion. An unknown identity creates no rows. Only the bootstrap rule and the development user runner link identities; invitations follow in the next feature.

### API

| Method and path | Tag | Purpose | Callers | Errors |
|---|---|---|---|---|
| `GET /api/v1/me` | Users | The caller's access status; for an active user also the user, roles and permissions | any signed-in caller | 401 |
| `GET /api/v1/system/client-config` | System | Issuer, client id and scope for the frontend's OIDC setup | public | none |

Schemas:

- `AccessStatus`: string enum `ACTIVE`, `DISABLED`, `NOT_INVITED`.
- `Me`: `status` (`AccessStatus`, required) and `user` (`MeUser`, optional). For `ACTIVE` the user is present; for the other statuses the property is absent from the JSON, not `null` (a `NON_NULL` Jackson mixin on `Me` only, so the nullable names stay explicit `null`; user decision). Every status responds 200.
- `MeUser`: all properties required:
  - `id` (uuid), `email`;
  - `firstName`, `lastName`, nullable (`type: [string, 'null']`). Both generators produce a required nullable property (Java: a constructor parameter without `@NotNull`; TypeScript: `string | null`); recorded in both guides;
  - `roles` (array of `Role`), `permissions` (array of `Permission`).
- `Role` and `Permission`: string enums in the contract, equal to the code enums (AC-21). A new permission is a contract change, which every feature that adds one makes anyway.
- `ClientConfig`: `issuer` (`format: uri`, user decision), `clientId`, `scope` (`openid profile email`), all required. It carries only values that are public by nature (the browser sees them in the sign-in redirect anyway), never a secret.
- `Problem` (RFC 9457, `application/problem+json`):
  - `type`, `title` and `status` are required, `detail` is optional;
  - this feature's `type` values are `urn:mentorhub:problem:unauthorized`, `...:not-invited`, `...:access-ended`, `...:forbidden` and `...:not-found`; other errors (400, 405, 500) use `about:blank`, written explicitly because Spring 7 leaves `type` null and drops it from the JSON while the contract requires it (user decision);
  - errors raised in servlet filters or the container (for example an unreachable identity provider) go through the error dispatch and are rendered by `ProblemErrorController` (`about:blank`, the original status, the path as `instance`, no exception text);
  - the 401 and 403 responses reference it and are reused by every later endpoint;
  - the authentication entry point, the access-denied handler and the gate all produce it. The entry point keeps the `WWW-Authenticate: Bearer` header.

### Authorization

- **URL rules**, in two security filter chains:
  - **public**: the public endpoints from the access matrix (plus the development-only paths in the `dev` profile), without bearer token processing, so a stale token cannot fail them;
  - **API**: `/api/v1/**` requires authentication through the OAuth2 resource server; error dispatches are permitted, so a failure is not turned into a 401;
  - every other path is denied.
- **Method rules**: every `/api/v1/**` operation carries either the typed permission annotation (for example `@RequirePermission(Permission.USERS_MANAGE)`) or the "no permission required" marker. A test scans the handler methods and fails on an operation with neither (AC-18).
  - Permission names never appear in strings.
  - `@RequirePermission` is a meta-annotation over `@PreAuthorize("hasAuthority('{value}')")`, filled from the enum constant by an `AnnotationTemplateExpressionDefaults` bean; permissions are granted as authorities named after the `Permission` constants.
  - The test-only endpoint for AC-17 lives under `/api/v1/test/**` in test sources only.
- **Issuers**:
  - trusted issuers are configuration, each with its required audience (`aud` must contain it);
  - `JwtIssuerAuthenticationManagerResolver` is built with a custom `AuthenticationManagerResolver<String>`, so every trusted issuer uses our converter;
  - decoders are created lazily on the first token of each issuer, so the application starts while Keycloak is still importing the realm;
  - a decoder uses OIDC discovery by default, or a configured `jwk-set-uri` when one is given (tests use this);
  - discovery and JWK set requests use a connect and a read timeout, so an unreachable or hung identity provider fails the request with a 500 Problem instead of holding the request thread.
- **The JWT converter** resolves the caller on every request:
  - `(iss, sub)` → `user_identities` → `users`;
  - the bootstrap rule (ADR 0005, AC-11 to AC-16) runs before the status check;
  - roles from `user_roles` are expanded into permissions as granted authorities; unknown stored roles are skipped (AC-20);
  - the resulting authentication carries the access status and, for a linked user, the `CurrentUser`. For a caller that is not invited it has no user id.
- **Bootstrap writes** are one transaction and idempotent: inserts use `on conflict do nothing` and re-read. The warning is logged only when the transaction changed rows (AC-16).
- **The gate** is an `AuthorizationManager` on `/api/v1/**` that admits only active users; the access-denied handler maps a refused caller's status to 403 `not-invited` or `access-ended`. `GET /api/v1/me` only requires authentication, and reads the caller in any status through `CurrentCaller`; every other service sees only active users through `CurrentUserProvider`.
- **Token claims**: only `iss`, `sub`, `aud`, `email`, `email_verified`, `given_name` and `family_name` are read. Roles and groups in the token are ignored (AC-22).
- **Resource-level checks** in services:
  - a single resource the caller may not access throws `NotFoundException`, the same as a missing one (404);
  - lists filter inaccessible items out;
  - a missing permission is Spring Security's `AccessDeniedException` (403 `forbidden`).
- **Permissions**: the `Permission` enum starts with `USERS_READ` and `USERS_MANAGE`, both on ADMIN only, for the next feature.
- **Refinements of ADR 0004**, noted in its status line:
  - not invited and disabled callers get 200 with their status from `GET /api/v1/me`;
  - inaccessible resources respond 404.

### Configuration

| Property | Meaning |
|---|---|
| `mentorhub.security.trusted-issuers[].issuer-uri` | A trusted issuer |
| `mentorhub.security.trusted-issuers[].audience` | The audience its tokens must carry |
| `mentorhub.security.trusted-issuers[].jwk-set-uri` | Optional; skips discovery (tests) |
| `mentorhub.security.issuer-connect-timeout`, `issuer-read-timeout` | Timeouts for discovery and JWK set requests (default 2s and 5s) |
| `mentorhub.security.bootstrap-admin-email` | ADR 0005; empty disables the rule |
| `mentorhub.security.client.issuer-uri`, `client-id` | Returned by `GET /api/v1/system/client-config` (owned by `system`) |
| `mentorhub.development-users.issuer-uri`, `.users[]` (subject, email, names, status, roles) | `dev` only: the development users the runner keeps in sync |

In every profile, a missing trusted issuer list or client configuration fails startup (AC-26).

### Development profile

- The profile is `dev`. It is activated only by the `spring-boot.run.profiles` property in `backend/pom.xml` (read by `./mvnw spring-boot:run`, not by packaging or the AOT build) and by `TestMentorHubApplication`, never by `application.yaml`. `application-dev.yaml` holds the local Keycloak issuer, the bootstrap email and the development users.
- `TestMentorHubApplication` runs with a throwaway Testcontainers database and Docker Compose turned off, so it does not start Keycloak; the README says to start it with `docker compose -f infra/compose.yaml up -d keycloak`.
- In `dev`:
  - `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`, `/openapi.yaml` and the actuator `info` and `modulith` endpoints are served;
  - a runner creates or updates the development users (identity, status and roles) idempotently on start.

  Nothing about development users is a Flyway migration, so the production migration history stays clean.
- Outside `dev`:
  - springdoc's API docs and Swagger UI are disabled;
  - `/openapi.yaml` is denied by security;
  - only health is exposed, without details.

### Local environment

- Keycloak 26.8.1 in `infra/compose.yaml` on port 8180, in development mode:
  - it imports the realm from `infra/keycloak/mentorhub-realm.json` on start. Import skips an existing realm, so a changed realm file needs the container recreated; the README says how;
  - the hostname is fixed to `http://localhost:8180`, so `iss` is always `http://localhost:8180/realms/mentorhub`;
  - Keycloak and Postgres are published on `127.0.0.1` only (user decision).
- Realm `mentorhub`:
  - client `mentorhub-web`:
    - public, standard flow only, PKCE S256 required, no implicit flow, no direct access grants;
    - redirect URIs `http://localhost:4200/*`, post-logout redirect URI `http://localhost:4200/*`, web origin `http://localhost:4200`;
    - an Audience protocol mapper with the custom audience `mentorhub-api` (no separate client);
    - default client scopes include `basic` (it provides `sub` since Keycloak 25), `profile` and `email`;
  - registration allowed, email verification on.
- Development users (all with verified emails; the password is documented in the README):

  | Email | In MentorHub |
  |---|---|
  | `admin@mentorhub.local` | ADMIN |
  | `mentor@mentorhub.local` | MENTOR |
  | `student@mentorhub.local` | STUDENT |
  | `noroles@mentorhub.local` | active, no roles |
  | `disabled@mentorhub.local` | disabled |
  | `stranger@mentorhub.local` | Keycloak only, not linked until it accepts an invitation |

  The realm gives them fixed ids, so the runner can link their `sub`. The bootstrap email in `dev` is `admin@mentorhub.local`.

### Frontend authentication

- The OIDC client library is `angular-auth-oidc-client` 22.x (peer dependency `@angular/core >=20`; it supports Authorization Code + PKCE, refresh tokens and configuration loaded at runtime). The frontend agent confirms it works with the zoneless setup.
- At startup the application loads `GET /api/v1/system/client-config`, then configures the OIDC client; on failure it shows the startup error.
- The client configuration is cached after the first successful load, so sign-out works while the API is down. Before redirecting to sign-in, the application loads Keycloak's discovery document; a failure, or no redirect within 15 s, shows the startup error. The library's own post-sign-in navigation is off (`triggerAuthorizationResultEvent: true`) so the guard decides where the user lands, and `autoUserInfo` is off because the profile comes from `/me`.
- The token interceptor attaches the access token only to same-origin, non-public `/api/` requests (AC-27); the public paths come from the generated `PATH` constants.
- The guard signs the visitor in when needed, re-loads `GET /api/v1/me` once per navigation that activates a route, and routes by status. When that load fails, it shows the unavailable state without changing the address (`skipLocationChange`), and Retry repeats the failed target with the sign-in callback parameters removed.
- The error interceptor:
  - on 401, starts sign-in again once, keeping the current route; a 401 right after a fresh sign-in shows the startup error (AC-28);
  - on 403 `not-invited` or `access-ended`, re-loads `GET /api/v1/me` and routes to the access screen;
  - on 403 `forbidden`, shows the snackbar.
- Tokens are refreshed with refresh tokens, 30 s before they expire. Sign-out is RP-initiated logout at Keycloak with the application origin as the post-logout redirect (AC-8); if no redirect happens, or the logout fails, the local session is still ended.

### Tests

- Authentication tests run the real chain: decoder, converter, bootstrap and gate.
  - A test issuer with a locally generated RSA key is trusted in the test configuration through `jwk-set-uri`, served by the test.
  - Tests sign their own JWTs with any claims they need.
  - `SecurityMockMvcRequestPostProcessors.jwt()` bypasses the converter, so it must not be used for gate, bootstrap or permission tests.
- `@WebMvcTest` slices import the security configuration (`WebSliceSecurity`) and use `TestAuthentications`, which sets our authentication with a given status and permissions. `SystemInfoControllerTest` became `system/internal/web/SystemControllerTest`.
- MockMvc does not perform the error dispatch, so filter-level failures (an unreachable or hung issuer) are tested with a real server (`IssuerFailureTests`).
- `DevelopmentRealmConsistencyTests` keeps the development users in `application-dev.yaml` equal to the realm users.
- The concurrency test for AC-16 sends parallel first requests against Testcontainers Postgres.
- Frontend: unit tests with the OIDC service and the API replaced, covering the guard routing, both interceptors, the 401 loop guard and every screen state.

### Dependencies

- Backend: the Spring Security OAuth2 resource server starter and its paired test starter. Use the Boot 4 starter names (see the pitfalls in `docs/development/backend.md`).
- Frontend: `angular-auth-oidc-client`.

### Documentation updates

- `docs/development/backend.md`:
  - the `dev` profile and the development user runner;
  - Problem Details and the `type` values;
  - the security conventions: typed permission annotation and "no permission required" marker, `CurrentUser`, `NotFoundException` for inaccessible resources;
  - the authenticated test approach and the `jwt()` pitfall;
  - the pitfall that a catch-all `@ExceptionHandler` in `com.mentorhub.web` turns method security's `AccessDeniedException` into a 500.
- `docs/development/frontend.md`:
  - authentication, both interceptors, 401 and 403 handling;
  - the `resource()` example reference, now `frontend/src/app/features/shell/home.ts`.
- `README.md`: Keycloak, the development users and password, the profile, Swagger being development-only, recreating the Keycloak container after realm changes.
- `docs/ui/guidelines.md`: shell, access screens, startup error.
- ADR 0003: status implemented.

### Events

None.

### Decisions

- Profile data is copied from the token once, when the user is created; afterwards the application owns it (user decision).
- Bootstrap rule: ADR 0005, including recovery when no active administrator exists (user decision). An identity linked to another user is never moved by it.
- Sign-out ends the Keycloak session (user decision).
- Separate screens for "not invited" and "access ended" (user decision).
- ADMIN gets an explicit permission list (user decision).
- Swagger UI and the API docs only in the `dev` profile (user decision).
- An inaccessible single resource responds 404 like a missing one; recorded in `docs/architecture/invariants.md` (user decision).
- Every operation declares its permission or "no permission required", enforced by a test.
- Errors use RFC 9457 Problem Details with fixed `type` values.
- The frontend reads its OIDC configuration from the backend at runtime, so the issuer can change without a rebuild.
- Development users come from a `dev`-profile runner, not from migrations.
- Stored roles are a `text` reference table, not a PostgreSQL `enum`; the rationale is in ADR 0004 (user decision).
- Internal code is split into layer subpackages; this is now the backend convention (user decision).
- Required settings fail startup in every profile, not only outside `dev` (accepted deviation).
- `about:blank` is written explicitly for problems without a specific type (user decision).
- Frontend follow-ups: `/unavailable` sits outside the guard, and routes have no per-route document titles.

## 4. Open questions

None blocking.
