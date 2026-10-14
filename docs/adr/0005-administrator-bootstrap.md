# 0005. Administrator bootstrap

Status: accepted, 2026-10-15

## Context

Registration is by invitation only (ADR 0003), and only an administrator can invite. A fresh installation has no administrator, so the first one needs another way in. The same problem appears if every administrator is disabled or loses the role by mistake: nobody can let anyone in, and without a rule the only way back is a manual change in the database.

The rule must not let anyone become an administrator who does not control a specific mailbox, and it must do nothing as long as the installation has an administrator.

## Decision

- The deployment configures one bootstrap email address. Without it the rule is off.
- The rule applies to a signed-in identity whose token has `email_verified = true` and an email equal to the bootstrap address (case-insensitive), and only while no active user has the ADMIN role.
- When it applies, in one idempotent transaction:
  - the user with that email is created if missing, with the profile from the token;
  - the identity is linked to that user if not linked yet;
  - the user is set active and gets the ADMIN role.

  This covers both the first start and recovery when no active administrator is left.
- An identity already linked to a different user is never moved and that user gets nothing: the rule does not apply, and the attempt is logged.
- Every application of the rule is written to the application log at warning level with the user id.
- Identity providers are trusted for email verification only when they verify addresses themselves: self-registration requires email verification, and "trust email" is enabled only for such brokered providers.
- Development users created by the development profile are a second exception. They exist only when that profile is active, which is never in production.

## Consequences

- A new installation and a locked-out installation are recovered without touching the database.
- Whoever controls the bootstrap mailbox can become an administrator whenever no active administrator exists. The address must belong to a person responsible for the installation, and the email verification settings of the identity provider are part of security.
- Invitations remain the only way in for everyone else.
