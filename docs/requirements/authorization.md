# Authorization

## Purpose

What an active user may do, and how a refusal looks. A user holds zero or more roles; each role grants permissions; every operation that needs a permission is checked against them. Who is let in at all (the gate) is described in the identity capability (IDN); this capability starts once a user is active.

## Requirements

### PERM-1 Roles grant permissions

The system SHALL define the roles and permissions below, and SHALL grant a user exactly the permissions of the roles assigned to them. Changing what a role grants is a release of the application, not a setting (decision 0004).

| Role | Permissions |
|---|---|
| Administrator | `USERS_READ`, `USERS_MANAGE`, `USERS_INVITE` |
| Coordinator | none |
| Mentor | none |
| Student | none |
| Observer | none |

| Permission | Allows |
|---|---|
| `USERS_READ` | Viewing the users list, a user and a user's history of changes |
| `USERS_MANAGE` | Changing another user's profile and roles, disabling and enabling users |
| `USERS_INVITE` | Managing invitations: creating, listing, revoking, issuing a new link, sending an email |

Which permission each action needs is listed by the capability that owns the action.

#### PERM-1.1 An administrator holds the user permissions

- GIVEN an active user with the administrator role
- WHEN the user's permissions are read
- THEN they are `USERS_READ`, `USERS_MANAGE` and `USERS_INVITE`

#### PERM-1.2 The other roles hold no permission

- GIVEN an active user whose only role is coordinator, mentor, student or observer
- WHEN the user's permissions are read
- THEN the user has no permission

### PERM-2 A user may have several roles or none

The system SHALL give a user the union of the permissions of all their roles. A user without roles SHALL have no permissions but remains an active user.

#### PERM-2.1 Permissions of several roles are combined

- GIVEN an active user with the mentor role and the administrator role
- WHEN the user's permissions are read
- THEN they are the permissions of the administrator role, each once

#### PERM-2.2 A user without roles has no permissions

- GIVEN an active user without roles
- WHEN the user calls an operation that needs a permission
- THEN the response is 403 `forbidden`
- AND the user can still read their own current-user data

### PERM-3 Operations are checked by permission

The system SHALL decide access to every operation that needs a permission by the permissions the caller holds now, never by the name of a role. The permissions are read on every request, so a change of a user's roles applies to their next request with the same token.

#### PERM-3.1 A caller with the permission is served

- GIVEN an active user who holds the permission an operation needs
- WHEN the user calls the operation
- THEN the operation is carried out

#### PERM-3.2 A caller without the permission is refused

- GIVEN an active user who does not hold the permission an operation needs
- WHEN the user calls the operation
- THEN the response is 403 `forbidden`
- AND nothing is changed

#### PERM-3.3 Removing a role takes effect on the next request

- GIVEN an administrator who has just lost the administrator role
- WHEN they call an operation that needs `USERS_READ` with the same token as before
- THEN the response is 403 `forbidden`

#### PERM-3.4 Adding a role takes effect on the next request

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

The system SHALL treat a stored role that the running version does not know (for example after a rollback to an earlier release) as granting nothing, and SHALL NOT fail the user's requests because of it (decision 0004).

#### PERM-5.1 An unknown role is skipped

- GIVEN an active user with a stored role that the application does not know, and the mentor role
- WHEN the user sends a request
- THEN the request is not failed
- AND the user holds only the permissions of the roles the application knows

#### PERM-5.2 An unknown role is reported once

- GIVEN a stored role that the application does not know
- WHEN users holding it send several requests
- THEN one warning is logged for that role name

### PERM-6 Refusing the action, hiding the resource

The system SHALL answer 403 `forbidden` only when the caller lacks the permission for the action as a whole. The permission is checked before any resource is looked up, so a caller without the permission learns nothing about whether a resource exists. A single resource the caller may not access SHALL be answered with 404 `not-found`, identical to the answer for a resource that does not exist (decision 0004).

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

The client SHALL decide what to offer from the permissions of the current user, never from role names. An action or a link that needs a permission SHALL NOT be shown to a user without it, and a page that needs a permission SHALL NOT open for such a user. The server enforces every rule regardless (PERM-3).

#### PERM-7.1 An action without permission is not shown

- GIVEN an active user without `USERS_INVITE`
- WHEN the user views the places that offer invitations
- THEN the link to invitations and the action to invite are not shown

#### PERM-7.2 A page without permission does not open

- GIVEN an active user without `USERS_READ`
- WHEN the user opens the address of the users list
- THEN the user is taken to the home page

#### PERM-7.3 A refusal is explained

- GIVEN a user who still sees an action that their permissions no longer allow
- WHEN the server refuses the action with 403 `forbidden`
- THEN the user sees that they do not have permission to do this
- AND the user stays on the page

## Known limitations

- The observer role grants no permission, as the coordinator, mentor and student roles do not either. What each of these roles may do is defined when the features that need them are built.
- No operation yet gives different callers different access to the same resource: every operation either needs a permission, or acts on the caller's own data. The answers for a resource the caller may not access (PERM-6.3) and the filtering of lists are therefore checked only against a test operation, and no list leaves items out today.

## Out of scope

- Access that depends on relations between people, such as a mentor's students, a student's own data or a track's coordinator. When such access is built, it is decided by the relations current at the time of the request, roles stay global, and lists leave out the items the caller may not access.
- Roles and their permissions edited in the application.
- Role assignments from the identity provider or from groups of a corporate sign-in.
