# Authorization

## Purpose

What an active user may do, and how a refusal looks. A user holds zero or more roles; each role grants permissions; every operation that needs a permission is checked against them. Who is let in at all (the gate) is described in the identity capability (IDN); this capability starts once a user is active.

## Requirements

### PERM-1 Roles grant permissions

The system SHALL grant a user exactly the permissions of the roles assigned to them, as defined below (decision 0004).

| Role | Permissions |
|---|---|
| Administrator | `USERS_READ`, `USERS_MANAGE`, `USERS_INVITE` |
| Coordinator | none |
| Mentor | none |
| Student | none |
| Observer | none |

| Permission | Allows |
|---|---|
| `USERS_READ` | Viewing users and their history |
| `USERS_MANAGE` | Changing another user's profile and roles, disabling and enabling users |
| `USERS_INVITE` | Managing invitations |

The capability that owns an action says which permission it needs.

#### PERM-1.1 A role holds exactly the permissions in the table

- GIVEN an active user with one role
- WHEN the user's permissions are read
- THEN they are exactly the permissions the table gives that role

### PERM-2 A user may have several roles or none

The system SHALL give a user the union of the permissions of all their roles. A user without roles has no permissions but stays active.

#### PERM-2.1 Permissions of several roles are combined

- GIVEN an active user with the mentor role and the administrator role
- WHEN the user's permissions are read
- THEN they are the permissions of the administrator role, each once

#### PERM-2.2 A user without roles has no permissions

- GIVEN an active user without roles
- WHEN the user calls an operation that needs a permission
- THEN the response is 403 `forbidden`

#### PERM-2.3 A user without roles stays active

- GIVEN an active user without roles
- WHEN the user reads their own current-user data
- THEN the response is 200 with no permissions

### PERM-3 Operations are checked by permission

The system SHALL decide access by the permissions the caller holds now, never by a role name. They are read on every request, so a change of roles applies to the next request with the same token.

#### PERM-3.1 A caller without the permission is refused

- GIVEN an active user who does not hold the permission an operation needs
- WHEN the user calls the operation
- THEN the response is 403 `forbidden`
- AND nothing is changed

#### PERM-3.2 Removing a role takes effect on the next request

- GIVEN an administrator who has just lost the administrator role
- WHEN they call an operation that needs `USERS_READ` with the same token as before
- THEN the response is 403 `forbidden`

#### PERM-3.3 Adding a role takes effect on the next request

- GIVEN an active user without roles who has just been given the administrator role
- WHEN they call an operation that needs `USERS_READ` with the same token as before
- THEN the operation is carried out

### PERM-4 Roles in the token grant nothing

The system SHALL NOT use roles or groups carried by the token for authorization; only the roles assigned in the application count (decision 0004).

#### PERM-4.1 Roles and groups in the token are ignored

- GIVEN an active user without roles whose token carries the roles or groups `admin`
- WHEN the user calls an operation that needs a permission
- THEN the response is 403 `forbidden`

### PERM-5 An unknown stored role grants nothing

The system SHALL treat a stored role the running version does not know (for example after a rollback) as granting nothing and SHALL NOT fail requests because of it (decision 0004).

#### PERM-5.1 An unknown role is skipped

- GIVEN an active user with a stored role that the application does not know, and the mentor role
- WHEN the user sends a request
- THEN the request is not failed
- AND the user holds only the permissions of the roles the application knows

### PERM-6 Refusing the action, hiding the resource

The system SHALL check the permission before looking up any resource, so a caller without it learns nothing about what exists. A single resource the caller may not access SHALL be answered as one that does not exist (decision 0004).

#### PERM-6.1 The permission is checked before the resource is looked up

- GIVEN an active user who does not hold the permission an operation needs
- WHEN the user calls the operation for an id that does not exist
- THEN the response is 403 `forbidden`

#### PERM-6.2 A missing resource is not found

- GIVEN an active user who holds the permission an operation needs
- WHEN the user calls the operation for an id that does not exist
- THEN the response is 404 `not-found`

#### PERM-6.3 An inaccessible resource looks like a missing one

- GIVEN a resource that exists but that the caller may not access
- WHEN the caller asks for it
- THEN the response is 404 `not-found`
- AND the response is identical to the one for a resource that does not exist

### PERM-7 The client offers only what the user may do

The client SHALL offer actions, links and pages by the permissions of the current user, never by role names, and SHALL explain a refusal. The server enforces every rule regardless (PERM-3).

#### PERM-7.1 A refusal is explained

- GIVEN a user who still sees an action that their permissions no longer allow
- WHEN the server refuses the action with 403 `forbidden`
- THEN the user sees that they do not have permission to do this
- AND the user stays on the page

## Known limitations

- The coordinator, mentor, student and observer roles grant no permission; what they may do is defined when the features that need them are built.
- No operation yet gives different callers different access to one resource, so PERM-6.3 is checked only against a test operation, and no list leaves items out.

## Out of scope

- Access that depends on relations between people, such as a mentor's students, a student's own data or a track's coordinator. When such access is built, it is decided by the relations current at the time of the request, roles stay global, and lists leave out the items the caller may not access.
- Roles and their permissions edited in the application.
- Role assignments from the identity provider or from groups of a corporate sign-in.
