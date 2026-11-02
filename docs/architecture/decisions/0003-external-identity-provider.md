# 0003. Authentication through an external identity provider (Keycloak)

## Context

There are two kinds of users: mentors (employees, corporate SSO in the future) and students (external people, signing in by invitation via email or Google). The company's corporate SSO is built on Keycloak. Users may later be migrated to the corporate identity provider.

## Decision

- The application stores no passwords and does not implement sign-in itself. The backend is an OAuth2 resource server and validates JWTs. The frontend is an OIDC client (Authorization Code + PKCE).
- The identity provider is Keycloak (locally in `infra/compose.yaml`, with the realm stored as JSON in the repository). The corporate provider is later connected to it through identity brokering.
- The internal user ID is separate from the provider's identifier: `users(id)` and `user_identities(user_id, issuer, subject)`. Domain data references only `users.id`, never `sub`.
- Roles are stored in the application database, not in Keycloak (decision 0004).
- The list of trusted issuers is configuration (`JwtIssuerAuthenticationManagerResolver`), so migrating to another provider requires no code changes.
- Registration is by invitation only. The exceptions are the administrator bootstrap (the identity requirements) and development users, which exist only in the development profile.
- Invitation tokens are stored in plaintext, so anyone with database access can read a link. Accepting an invitation therefore requires a verified email equal to the invitation's. That match is the only protection of a link; relaxing it requires hashing the tokens again.

## Why

- Connecting corporate SSO comes down to configuring Keycloak.
- Migrating to another issuer is linking a new `user_identities` row, with no data migration.
- Cost: one more service in the local environment and in operations.

## Rejected alternatives

- **Sign-in implemented in the application.** It would have to cover what the provider already gives: email and Google sign-in now, corporate SSO later.
- **Storing invitation tokens only as hashes** (reversed 2026-10-20, user). The hash protected little, because a token read from the database is useless without the invited person's mailbox, and an administrator who lost a link had to issue a new one, which killed the link the person might already have.
