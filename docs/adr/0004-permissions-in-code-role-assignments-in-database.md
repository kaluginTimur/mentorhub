# 0004. Permissions in code, role assignments in the database

Status: accepted, 2026-10-15

## Context

ADR 0003 keeps roles in the application database rather than in the identity provider, but does not say how roles and their rights are defined.

MentorHub is an internal platform of one training center. Access rules are changed by the same team that writes the code, and a release is cheap. A role editor in the UI (roles and their rights as data, as in products sold to many organizations) would be a feature of its own, would let a misconfiguration silently change who can see student data, and would leave tests unable to guarantee how roles behave in production. A new access rule needs code anyway, and some roles are domain concepts the code relies on (student, mentor), so they could not be edited freely.

Access to a student's data depends on relations that change over time: a mentor is assigned to an enrollment, a graduate becomes a mentor. Roles alone cannot express this (`docs/architecture/invariants.md`).

## Decision

- **Permissions** are a Java enum in the public API of the `users` module, one value per capability that code checks.
- **Roles** are a Java enum that maps each role to a set of permissions. The initial roles are ADMIN, COORDINATOR, MENTOR, STUDENT and OBSERVER. Adding a role or changing its permissions is a code change.
- **Role assignments are data**: `user_roles(user_id, role)`, managed by administrators in the application. A user can have several roles or none; an active user without roles has no permissions.
- **Roles are global, not scoped** to a track or cohort. Which students a user can access is decided by domain relations (enrollment, mentor assignment, track coordination) in resource-level checks.
- **Authorization checks use permissions, never role names.**
- **Enforcement is Spring Security.** On every request the JWT is converted into the current user:
  - `(iss, sub)` is resolved to `users.id` through `user_identities`;
  - unknown and disabled users are rejected with 403;
  - roles are loaded from `user_roles` and expanded into permissions as granted authorities.
- **Capability checks** use method security through a typed annotation, so permission names never appear in strings.
- **Resource-level checks** are explicit service calls that throw `AccessDeniedException`.
- Roles and groups in the token are not used for authorization.
- `GET /api/v1/me` returns the user's roles and effective permissions. The frontend uses them only to hide what the user cannot do; the backend enforces.

Alternatives considered:

- **Roles as data**, editable together with their permissions in the UI. Deferred, not rejected: it is the usual model for products sold to many organizations. Because checks use permissions only, moving the role-to-permission mapping into the database later is a migration plus an admin screen, with no change to any check.
- **Keycloak roles and groups.** Already rejected by ADR 0003. Relations such as enrollments live in our database anyway, so access would have two sources of truth; changes reach the token only after a refresh; roles would be tied to one identity provider.
- **Keycloak Authorization Services, Spring Security ACL.** Every student and note would have to be registered as a resource or covered by ACL entries, kept in sync on every mentor reassignment.
- **OpenFGA, SpiceDB.** A separate service with its own copy of the relations; too much for a modular monolith (ADR 0001). Worth revisiting if relationship rules grow (sharing, delegation).
- **jCasbin.** One more abstraction with string policies and little gain over enums.

## Consequences

- Who can do what is in the code, reviewed and versioned in git. Tests define role behavior deterministically.
- A new role or a change of rights needs a release.
- The role enum and the stored assignments must stay consistent. Removing or renaming a role ships together with a migration of its assignments. A stored role the code does not know (for example after a release rollback) grants nothing and never fails a request.
- One database lookup per request: disabling a user or changing their roles takes effect on the next request, without waiting for a token refresh.
- Automatic provisioning of corporate SSO users with roles mapped from the corporate identity provider stays possible. Keycloak identity provider mappers translate external groups into a token claim, and the JWT converter maps that claim to our roles from configuration. It is an exception to invitation-only registration and needs its own ADR.
- The identity foundation spec defines the initial permissions and their mapping to roles; the tracking core spec defines observer rights.
