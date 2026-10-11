# 0003. Authentication through an external IdP (Keycloak)

Status: accepted, implementation in a separate step, 2026-10-11

## Context

There are two kinds of users: mentors (employees, corporate SSO in the future) and students (external people, signing in by invitation via email or Google). The company's corporate SSO is built on Keycloak. Users may later be migrated to the corporate IdP.

## Decision

- The application stores no passwords and does not implement sign-in itself. The backend is an OAuth2 resource server and validates JWTs. The frontend is an OIDC client (Authorization Code + PKCE).
- The IdP is Keycloak (locally in `infra/compose.yaml`, with the realm stored as JSON in the repository). The corporate IdP is later connected to it through identity brokering.
- The internal user ID is separate from the provider's identifier: `users(id)` and `user_identities(user_id, issuer, subject)`. Domain data references only `users.id`, never `sub`.
- Roles and per-cohort permissions ("which students does this mentor have access to") are stored in the application database, not in Keycloak roles.
- The list of trusted issuers is configuration (`JwtIssuerAuthenticationManagerResolver`), so migrating to another IdP requires no code changes.
- Registration is by invitation only.

## Consequences

- Connecting corporate SSO comes down to configuring Keycloak.
- Migrating to another issuer is linking a new `user_identities` row, with no data migration.
- One more service in the local environment and in operations.
