# User administration

## Purpose

How administrators see who has access to MentorHub and control it without touching the database: listing and viewing users, disabling and enabling them, assigning roles, correcting profiles and reading the history of changes to a user. Every active user also keeps their own profile current. Who is let in is described by identity (IDN), roles and permissions by authorization (PERM), recorded changes by audit (AUD), and creating users by the invitations capability. The API contract holds the fields, limits and response shapes.

## Requirements

### USR-1 Who may do what

The system SHALL require `USERS_READ` to read users and their history and `USERS_MANAGE` to change another user. A user saves their own profile with no permission, only active (IDN-3). A refusal is 403 `forbidden` and changes nothing (PERM-3, PERM-6).

#### USR-1.1 Reading users needs USERS_READ

- GIVEN an active user without `USERS_READ`
- WHEN the user lists users, views a user or views a user's history
- THEN each response is 403 `forbidden`

#### USR-1.2 Changing a user needs USERS_MANAGE

- GIVEN an active user with `USERS_READ` and without `USERS_MANAGE`
- WHEN the user disables a user, enables a user, sets a user's roles or saves another user's profile
- THEN each response is 403 `forbidden`
- AND nothing is changed

### USR-2 The users list

The system SHALL list users as a page (PLT-3) for a caller with `USERS_READ`, filtered by text, status and role. Only roles the running version knows are listed (decision 0004); the role filter `NONE` finds users without any.

#### USR-2.1 NONE finds users without a known role

- GIVEN a user with no role, a user whose only stored role the running version does not know, and a user with a known role and an unknown one
- WHEN an administrator lists users with the role filter `NONE`
- THEN it returns the first two users and not the third
- AND the third user's roles show only the known role

### USR-3 One user

The system SHALL return a user, for a caller with `USERS_READ`, with the profile, status, roles and creation time, and for a disabled user the reason and comment. These are for administrators only: the disabled user learns only that access has ended (IDN-3).

#### USR-3.1 The reason is shown for a disabled user only

- GIVEN an active user, and a user disabled with a reason and a comment
- WHEN an administrator views each
- THEN the active user has the status `ACTIVE` and no reason and no comment
- AND the disabled user has the status `DISABLED`, the reason and the comment

### USR-4 Disabling a user

The system SHALL let a caller with `USERS_MANAGE` disable an active user with a reason and, for the reason other, a comment. The user is refused on their next request (IDN-3). The change is audited (AUD-7); an invalid or refused request changes and records nothing (AUD-4).

#### USR-4.1 A user is disabled with a reason and a comment

- GIVEN an active user other than the caller
- WHEN an administrator disables them with a reason and a comment
- THEN the response is the user with the status `DISABLED`, the reason and the comment

#### USR-4.2 An invalid disabling changes nothing

- GIVEN an active user other than the caller
- WHEN an administrator disables them without a reason, with an unknown one, with the reason other and a blank comment, or with a comment over 500 characters
- THEN the response is 400 `validation-failed` with an error naming `reason` or `comment`
- AND the user is unchanged and nothing is recorded

#### USR-4.3 Disabling a disabled user changes nothing

- GIVEN a user disabled with a reason and a comment
- WHEN an administrator disables them again with another reason
- THEN the response is 200 with the user
- AND the stored reason and comment are the first ones

### USR-5 Enabling a user

The system SHALL let a caller with `USERS_MANAGE` enable a disabled user, clearing the reason and comment and keeping the roles (IDN-3.3). The change is audited (AUD-7).

#### USR-5.1 A user is enabled and keeps the roles

- GIVEN a disabled user with roles, a reason and a comment
- WHEN an administrator enables them
- THEN the user is `ACTIVE` with no reason and no comment, and the roles are the same as before

#### USR-5.2 Enabling an active user changes nothing

- GIVEN a user enabled by an administrator
- WHEN an administrator enables them again
- THEN the response is 200 and nothing is changed or audited

### USR-6 Roles of a user

The system SHALL let a caller with `USERS_MANAGE` replace a user's roles with exactly the requested set, leaving a stored role the running version does not know untouched (decision 0004). Roles of a disabled user can be changed. The change applies on the user's next request (PERM-3.2) and is audited, unless nothing changes (AUD-7, AUD-4.2).

#### USR-6.1 The roles become exactly the requested set

- GIVEN a user with the mentor and student roles
- WHEN an administrator sets the roles mentor and observer
- THEN the response lists the mentor and observer roles and the user holds only those

#### USR-6.2 An empty set leaves an active user without roles

- GIVEN an active user with a role
- WHEN an administrator sets no roles
- THEN the user is still active, with no roles and no permissions (IDN-5.1)
- AND on their next request the user is told that access is being set up (IDN-10.1)

#### USR-6.3 A stored role the version does not know stays

- GIVEN a user with the mentor role and a stored role the running version does not know
- WHEN an administrator sets the roles student
- THEN the response lists only the student role
- AND the unknown stored role is still stored

### USR-7 Locking the installation out is refused

The system SHALL refuse, changing and recording nothing (AUD-4.1), a change that disables the caller, removes their own administrator role, or leaves no active administrator, even when requests run at the same moment. The administrator role of a disabled user does not count. Parallel changes of users never end in a server error.

#### USR-7.1 An administrator cannot disable themselves

- GIVEN an active administrator
- WHEN the administrator disables themselves
- THEN the response is 409 with the problem type `self-lockout`
- AND the administrator is still active

#### USR-7.2 Two administrators cannot remove each other at the same moment

- GIVEN two active administrators A and B and no other
- WHEN A disables B and B disables A at the same moment, or A disables B while B removes A's administrator role
- THEN each response is 200 or 409 `last-admin`, never 500
- AND one active administrator remains

#### USR-7.3 An administrator cannot remove their own administrator role

- GIVEN an active administrator
- WHEN the administrator sets their own roles without the administrator role
- THEN the response is 409 with the problem type `self-lockout`
- AND the administrator keeps all their roles

#### USR-7.4 Disabling and demoting the same administrator both succeed

- GIVEN two active administrators A and B
- WHEN A disables B while A removes B's administrator role at the same moment
- THEN both responses are 200
- AND one active administrator remains

#### USR-7.5 Two administrators changing each other at the same moment both succeed

- GIVEN two active administrators A and B
- WHEN A edits B's profile while B edits A's, or each sets the other's roles keeping the administrator role, at the same moment
- THEN both responses are 200, never 500

### USR-8 Profile values

The system SHALL apply the same rules to every profile save, one's own (USR-9) or another user's. A phone is read with the installation's default region (Kazakhstan) and may not have an extension. The email never changes. An invalid save stores nothing; a save that changes a value is audited (AUD-7, AUD-4.2).

#### USR-8.1 A profile is saved in its normal form

- GIVEN a user with a first name, a last name and a phone
- WHEN an administrator saves a first name with surrounding spaces, a last name of only spaces and the phone `8 701 123 45 67`
- THEN the response is the user with the first name without the spaces, no last name and the phone `+77011234567`
- AND the email is the one it was before

### USR-9 One's own profile

The system SHALL let every active user change their own first name, last name and phone by the rules in USR-8, whether or not they hold roles, and audit it (AUD-7).

#### USR-9.1 A user without roles saves their own profile

- GIVEN an active user without roles
- WHEN the user saves a first name, a last name and a phone for themselves
- THEN the response is the current user with the new values

#### USR-9.2 The change is audited with the user as the actor

- GIVEN an active user without roles
- WHEN the user saves their own profile
- THEN the history of the user has an entry whose actor is the user

### USR-10 The history of a user

The system SHALL return, for a caller with `USERS_READ`, the audit entries about a user (AUD-7, AUD-6), newest first and a page at a time (PLT-3), with the actor (none for the system) and what changed.

#### USR-10.1 An acceptance entry names the administrator who invited

- GIVEN a user who accepted an invitation from an administrator
- WHEN an administrator reads the history of the user
- THEN the entry has the user as the actor, whether the user was created and enabled, and the roles added and removed
- AND it gives the administrator who created the invitation

#### USR-10.2 An acceptance entry is shown when the inviter cannot be resolved

- GIVEN a user who accepted an invitation whose inviting administrator cannot be resolved
- WHEN an administrator reads the history of the user
- THEN the entry is shown with no inviter

### USR-11 The history on the user page

The client SHALL show the history of the user (USR-10) on the user page, newest first, each entry with the actor (System when none) and a sentence telling the change, naming the inviter of an accepted invitation.

### USR-12 Navigation to users and to one's profile

The client SHALL offer the Users link only to a user with `USERS_READ` and take anyone else who opens a users address to the home page (PERM-7), and the account menu SHALL offer Profile (USR-18).

### USR-13 The users list screen

The client SHALL show the users list as a list page (PLT-9) with a search field, a status filter and a role filter, each row linking to the user page.

### USR-14 The user page

The client SHALL show a user's status, profile, access, roles and history, read-only without `USERS_MANAGE`, with a back link to the list as it was left, and that the user is not found for an unknown or invalid id.

### USR-15 Changing a user on the user page

The client SHALL let a caller with `USERS_MANAGE` edit the profile, disable an active user with a reason and enable a disabled one after a confirmation.

### USR-16 Roles on the user page

The client SHALL let a caller with `USERS_MANAGE` choose a user's roles and save them.

### USR-17 Protection on one's own page and conflicts

The client SHALL disable on an administrator's own page what would lock them out, and explain a 409 `self-lockout` or `last-admin` refusal without losing the form.

#### USR-17.1 A lockout conflict is explained and keeps the form

- GIVEN the server refuses a change with 409 `self-lockout` or `last-admin`
- WHEN the client receives it, from the disable dialog or the roles save
- THEN a message explains the refusal
- AND the form keeps its values

### USR-18 The profile form and My profile

The client SHALL show a profile form with the email read-only, and a My profile page for an active user with roles, opened from the account menu (USR-12) and never showing status, disable reason or roles. A user without roles can save through the API only (see Known limitations).

## Known limitations

- When two administrators save the same user's profile or roles at the same moment, the last save wins; each save is still audited. There is no warning about a concurrent edit.
- A user without roles sees the access screen (IDN-10.1), which has no link to the profile, so they can change their profile through the API only.
- A history page can hold fewer entries than its size, and its total counts entries that are left out (AUD-6).

## Out of scope

- Creating users, invitations and sending email; see the invitations and invitation emails capabilities.
- Changing a user's email, and showing or unlinking a user's sign-in identities.
- Deleting users.
- A global audit screen and audit retention (AUD).
- Access of coordinators, mentors and observers to user data, and automatic disabling when an enrollment ends.
- Photos, consent and other personal data rules.
