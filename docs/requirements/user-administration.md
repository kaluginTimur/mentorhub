# User administration

## Purpose

How administrators see who has access to MentorHub and control it without touching the database: listing and viewing users, disabling and enabling them, assigning roles, correcting profiles, and reading the history of changes to a user. It also covers how every active user keeps their own profile current. Who is let in at all is described by the identity capability (IDN), roles and permissions by the authorization capability (PERM), and the recorded changes by the audit capability (AUD). Creating users by invitation is described by the invitations capability.

## Requirements

### USR-1 Who may do what

The system SHALL require `USERS_READ` for reading users and their history and `USERS_MANAGE` for changing another user. Saving one's own profile needs no permission, only an active user (IDN-3). A refusal is 403 `forbidden` and changes nothing (PERM-3, PERM-6); today only the administrator role holds either permission (PERM-1.1).

| Action | Permission |
|---|---|
| List users, view a user, view a user's history | `USERS_READ` |
| Disable a user, enable a user, set a user's roles, save another user's profile | `USERS_MANAGE` |
| Save one's own profile | none |

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

The system SHALL list users for a caller with `USERS_READ`, as a page (PLT-3), with the id, email, first and last name, status and roles of each. Users SHALL be sorted by last name, then first name, then email, ignoring case, a missing name after the present ones, and by id last so that pages are stable. Only roles the running version knows are listed (decision 0004).

The list can be narrowed by a text, a status and a role, and the narrowing combines. A text matches the email, the first name or the last name when it is contained in one of them, ignoring case, after trimming; `%`, `_` and `\` match themselves. A text longer than 100 characters after trimming is rejected. The role filter takes a role, or `NONE` for users without any role the running version knows.

#### USR-2.1 Users are sorted by name and email

- GIVEN users whose names and emails differ in letter case, and users with a missing last name or first name
- WHEN an administrator lists users
- THEN they come sorted by last name, first name and email, ignoring case
- AND a user without a last name comes after all users with one, and among equal last names a user without a first name comes last

#### USR-2.2 The text matches email and names

- GIVEN users whose email or names contain a text in different letter case, and users whose names contain `%`
- WHEN an administrator lists users with the text surrounded by spaces, and with `%`
- THEN only the users whose email or names contain the text are returned
- AND `%` finds only the users whose names contain `%`

#### USR-2.3 Status and role filters combine

- GIVEN users that differ in text, status and role
- WHEN an administrator lists users with a status, with a role, and with a text, a status and a role together
- THEN each request returns only the users that match everything asked

#### USR-2.4 NONE finds users without a known role

- GIVEN a user with no role, a user whose only stored role the running version does not know, and a user with a known role and an unknown one
- WHEN an administrator lists users with the role filter `NONE`, and then without a filter
- THEN `NONE` returns the first two users and not the third
- AND the third user's roles show only the known role

#### USR-2.5 A text that is too long is rejected

- GIVEN an administrator
- WHEN the administrator lists users with a text longer than 100 characters after trimming
- THEN the response is 400 `validation-failed` with an error naming `q`

### USR-3 One user

The system SHALL return, for a caller with `USERS_READ`, a user's id, email, first and last name, phone, status, roles and creation time. For a disabled user it also returns the reason and, when there is one, the comment; for an active user it returns neither. The reason and comment are for administrators only: the disabled user learns only that access has ended (IDN-3). An unknown id is 404 `not-found` (PERM-6.2).

#### USR-3.1 The reason is shown for a disabled user only

- GIVEN an active user, and a user disabled with a reason and a comment
- WHEN an administrator views each
- THEN the active user has the status `ACTIVE` and no reason and no comment
- AND the disabled user has the status `DISABLED`, the reason and the comment

### USR-4 Disabling a user

The system SHALL let a caller with `USERS_MANAGE` disable an active user with a reason and an optional comment. The reason is one of: graduated, expelled, left the program, no longer on staff, security concern, other. The comment is trimmed, a blank comment is stored as none, it is at most 500 characters after trimming, and it is required when the reason is other. A disabled user is refused on their next request (IDN-3). The change is audited (AUD-7); an invalid or refused request is not (AUD-4). Disabling a user who is already disabled SHALL respond 200 with the user and change nothing, including the stored reason and comment.

#### USR-4.1 A user is disabled with a reason and a comment

- GIVEN an active user other than the caller
- WHEN an administrator disables them with a reason and a comment
- THEN the response is the user with the status `DISABLED`, the reason and the comment

#### USR-4.2 An invalid disabling is rejected

- GIVEN an active user other than the caller
- WHEN an administrator disables them without a reason or with an unknown one, with the reason other and a blank comment, or with a comment over 500 characters
- THEN the response is 400 `validation-failed` with an error naming `reason` or `comment`
- AND the user is unchanged

#### USR-4.3 Disabling a disabled user changes nothing

- GIVEN a user disabled with a reason and a comment
- WHEN an administrator disables them again with another reason
- THEN the response is 200 with the user
- AND the stored reason and comment are the first ones

### USR-5 Enabling a user

The system SHALL let a caller with `USERS_MANAGE` enable a disabled user. The user becomes active, the reason and comment are cleared, and the roles stay as they were. The user passes the gate on their next request (IDN-3.3). The change is audited (AUD-7). Enabling an active user SHALL respond 200 with the user and change nothing.

#### USR-5.1 A user is enabled and keeps the roles

- GIVEN a disabled user with roles, a reason and a comment
- WHEN an administrator enables them, and then enables them again
- THEN the user is `ACTIVE` with no reason and no comment, and the roles are the same as before
- AND the second request responds 200 and changes nothing

### USR-6 Roles of a user

The system SHALL let a caller with `USERS_MANAGE` replace a user's roles: the roles the running version knows become exactly the requested set. A stored role the running version does not know is left untouched and not reported (decision 0004). A change takes effect on the user's next request (PERM-3.3, PERM-3.4). The change is audited with the roles added and removed; setting the roles a user already has is not audited (AUD-7, AUD-4.2). Roles of a disabled user can be changed, so that they can be prepared before the user is enabled.

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

The system SHALL refuse a change that locks the installation out. An administrator cannot disable themselves or remove their own administrator role: the response is 409 with the problem type `self-lockout`. A change that would leave no active administrator is refused with 409 and the problem type `last-admin`. A refused change changes nothing and is not audited (AUD-4.1). The administrator role of a disabled user does not count towards the active administrators, so it may be removed from them. An administrator may add other roles to themselves. Because the caller is always an active administrator, `last-admin` is reached only when administrators change each other at the same moment; the rule holds whatever the order of the requests.

#### USR-7.1 An administrator cannot lock themselves out

- GIVEN an active administrator
- WHEN the administrator disables themselves, or sets their own roles without the administrator role
- THEN the response is 409 with the problem type `self-lockout`
- AND the administrator is still active and keeps all their roles

#### USR-7.2 Two administrators cannot remove each other at the same moment

- GIVEN two active administrators A and B and no other
- WHEN A disables B and B disables A at the same moment, or one request disables B while another removes B's administrator role
- THEN each response is 200 or 409 `last-admin`, never 500
- AND one active administrator remains

### USR-8 Profile values

The system SHALL apply the same rules whoever saves a profile (USR-9 for one's own, an administrator for another user's). The first name, the last name and the phone are optional. Names are trimmed, a blank name is stored as none, and a name is at most 100 characters after trimming. A blank phone is stored as none; otherwise the phone is read with the default region of the installation (Kazakhstan), must be a valid number, and is stored in the international format (`8 701 123 45 67` is stored as `+77011234567`). A phone number with an extension is rejected as invalid. The email is not part of a profile and never changes. An invalid request responds 400 `validation-failed` with one error for each invalid field, and nothing is stored. A save that changes a value is audited (AUD-7); saving the values a user already has changes nothing and is not audited (AUD-4.2).

#### USR-8.1 A profile is saved in its normal form

- GIVEN a user with a first name, a last name and a phone
- WHEN an administrator saves a first name with surrounding spaces, a last name of only spaces and the phone `8 701 123 45 67`
- THEN the response is the user with the first name without the spaces, no last name and the phone `+77011234567`
- AND the email is the one it was before

#### USR-8.2 Invalid values are rejected

- GIVEN a user with a phone
- WHEN an administrator saves a first name over 100 characters, and a phone that is not valid or has an extension
- THEN the response is 400 `validation-failed` with an error naming `firstName` and an error naming `phone`
- AND nothing is stored

### USR-9 One's own profile

The system SHALL let every active user change their own first name, last name and phone, whether or not they hold roles, by the rules in USR-8. The response is the current-user data (IDN-5.1) with the new values. The change is audited with the user as the actor (AUD-7). A user who is not active is refused by the gate (IDN-3).

#### USR-9.1 A user without roles saves their own profile

- GIVEN an active user without roles
- WHEN the user saves a first name, a last name and a phone for themselves
- THEN the response is the current user with the new values
- AND the history of the user has an entry whose actor is the user

### USR-10 The history of a user

The system SHALL return, for a caller with `USERS_READ`, the entries recorded about a user (AUD-7): newest first, a page at a time (PLT-3), holding only that user's entries (AUD-6). An administrator learns from it who changed the user's access or profile, when, and what changed: the reason and comment of a disabling, the roles added and removed, each profile field with its old and new value, what the administrator bootstrap did, and for an acceptance of an invitation the administrator who invited, what happened to the user, and the roles added and removed. Each entry has the time, the action, the actor (id and name, or none for a change made by the system) and these details. An unknown user is 404 `not-found` (PERM-6.2).

#### USR-10.1 An entry has its time, action, actor and details

- GIVEN an administrator who changed a user's roles by adding the student role
- WHEN an administrator reads the history of the user
- THEN the entry has the time of the change and the action `USER_ROLES_CHANGED`
- AND the actor has the administrator's id and name
- AND the details list the student role as added

#### USR-10.2 A change by the system has no actor

- GIVEN the administrator bootstrap created a user, linked the identity and added the administrator role (IDN-6.1)
- WHEN an administrator reads the history of the user
- THEN the entry has no actor and its details say what the bootstrap did

#### USR-10.3 An acceptance entry names the administrator who invited

- GIVEN users who accepted an invitation, one whose inviting administrator can be resolved and one whose cannot
- WHEN an administrator reads the history of each
- THEN each entry has the user as the actor, whether the user was created and enabled, and the roles added and removed
- AND the first gives the administrator who created the invitation, the second is still shown with no inviter

### USR-11 The history on the user page

The client SHALL show the history of the user (USR-10) on the user page, loaded on its own: the entries newest first, each with the time, the name of the actor (or System when there is none) and a sentence describing the change, with Show more while more entries exist. A history without entries says there are no changes yet; one that cannot be loaded says so and offers Retry, and the rest of the page stays. The sentences tell: a disabling with its reason and comment; an enabling; the roles added and removed; each changed profile field from its old to its new value, an empty value shown as empty; the bootstrap, that administrator access was granted by it and what it did (created the user, linked the sign-in identity, enabled the user), or only that it was the bootstrap when it did not add the administrator role; an acceptance of an invitation, that the user joined (or was reactivated, when they had been disabled) by invitation, from the administrator who invited when known, with the roles added and removed.

#### USR-11.1 Each action is told in its own sentence

- GIVEN entries for a disabling with a comment, an enabling, a change of roles, a profile update that set a last name where it was empty, and a bootstrap that created the user and added the administrator role
- WHEN an administrator opens the page of the user
- THEN the history tells each by the sentence of its action, with the reason and comment, the roles added and removed, the last name from empty to the new value, and what the bootstrap did
- AND the entry of the bootstrap shows System as the actor

#### USR-11.2 An acceptance sentence names the inviter

- GIVEN a user who joined by an invitation from an administrator with the mentor and student roles added, and a disabled user enabled by accepting an invitation
- WHEN an administrator opens the page of each
- THEN the history says that the first joined by invitation from that administrator, with the roles added
- AND it says that the second was reactivated by invitation

### USR-12 Navigation to users and to one's profile

The client SHALL offer the Users link in the toolbar only to a user with `USERS_READ`, and take a user without it who opens a users address to the home page (PERM-7). The account menu SHALL offer Profile, which opens the user's own profile page (USR-18), above Sign out (IDN-11).

#### USR-12.1 The Users link follows the permission

- GIVEN an active user with roles and without `USERS_READ`
- WHEN the toolbar is shown
- THEN it has no Users link, and a users address leads to the home page

### USR-13 The users list screen

The client SHALL show the users list as a list page (PLT-9) with a search field, a status filter (all, active, disabled) and a role filter (any role, each role, no roles). Each row shows the name by the rule of the shell (IDN-11), linking to the user page, the email, the roles by name (or that the user has none) and the status as text.

#### USR-13.1 Rows show name, email, roles and status

- GIVEN users with and without a name, with different roles and statuses, and one without roles
- WHEN an administrator opens the list
- THEN each row shows the name (the email when there is no name), the email, the roles and the status as text
- AND the user without roles is shown as having none
- AND the name links to the user page

### USR-14 The user page

The client SHALL show a user's page with the status, the profile (email, names, phone, creation date), the access (with the reason and comment of a disabled user), the roles and the history (USR-11). A back link leads to the users list with its previous search, filters and page. Without `USERS_MANAGE` the page is read-only: no Edit, no Disable user, no Enable user, and the roles as a list. A user that does not exist, and an address whose id is not a valid id, show that the user is not found, with a link back to the list. A failure to load shows the unavailable message with Retry (PLT-8).

#### USR-14.1 The page shows the user and leads back to the list

- GIVEN an administrator who opened a user from a filtered list
- WHEN the page is shown, and the administrator follows the back link
- THEN the page shows the name, the status and the profile of the user
- AND the list is shown with the same search, filters and page

#### USR-14.2 A user that is not found is explained

- GIVEN a user id that does not exist, and an address whose id is not a valid id
- WHEN an administrator opens each
- THEN the page says that the user is not found and links back to the list

#### USR-14.3 Without USERS_MANAGE the page is read-only

- GIVEN an active user with `USERS_READ` and without `USERS_MANAGE`
- WHEN the user opens the page of a user with roles
- THEN there is no Edit, no Disable user and no Enable user
- AND the roles are listed by name and not as choices to change

### USR-15 Changing a user on the user page

The client SHALL let a caller with `USERS_MANAGE` edit the profile with the profile form (USR-18) and offer Disable user on an active user and Enable user on a disabled one. Disable user asks for a required reason and an optional comment, tells that the user loses access on their next action, and does not send the request without a reason or, for the reason other, without a comment. Enable user asks for a confirmation that names the user. A server error for the reason or comment is shown with its field and the values are kept. After a successful change the page shows the updated user, the history is reloaded and a message confirms it.

#### USR-15.1 A user is disabled from the dialog

- GIVEN the disable dialog
- WHEN the administrator chooses Disable without a reason, then with the reason other and no comment, then with a reason and a comment
- THEN the first two are refused in the dialog and no request is sent
- AND the third shows the user as disabled with the reason and the comment, with a message and a new history entry

#### USR-15.2 A user is enabled after the confirmation

- GIVEN a disabled user's page
- WHEN the administrator chooses Enable user and confirms
- THEN no request is sent before the confirmation
- AND the page shows the user as active, with a message and a new history entry

### USR-16 Roles on the user page

The client SHALL let a caller with `USERS_MANAGE` choose a user's roles from the roles (Administrator, Coordinator, Mentor, Student, Observer), with Save, available only when the choice differs from the stored roles, and Reset to the stored roles. When no role is chosen, a hint says the user can sign in but has no access to anything. After a successful save the page shows the updated user, the history is reloaded and a message confirms it.

#### USR-16.1 Saved roles are shown and confirmed

- GIVEN an administrator who chose the mentor role for a user, or cleared every role
- WHEN the administrator chooses Save
- THEN the page shows the mentor role, a message confirms the save and the history shows the new entry
- AND clearing every role showed the hint before saving

### USR-17 Protection on one's own page and conflicts

On an administrator's own page the client SHALL disable Disable user and the Administrator role choice, each with a hint that explains why. After an administrator saves their own roles, the permissions the client uses are current. When the server answers 409 `self-lockout` or `last-admin`, the client SHALL explain the refusal and keep the form as it was.

#### USR-17.1 An administrator cannot start locking themselves out

- GIVEN an administrator on their own page
- WHEN the page is shown
- THEN Disable user and the Administrator choice are disabled, each with its hint

#### USR-17.2 A lockout conflict is explained and keeps the form

- GIVEN the server refuses a change with 409 `self-lockout` or `last-admin`
- WHEN the client receives it, from the disable dialog or the roles save
- THEN a message says that the installation must not be locked out, or that at least one active administrator is needed
- AND the form keeps its values

### USR-18 The profile form and My profile

The client SHALL show a profile form with the email read-only and the first name, last name and phone, all optional. It refuses a name over 100 characters before sending. A server error for a field is shown with that field, and an invalid phone is always explained as such. After a successful save the form shows the values the server stored. An active user with roles has a My profile page, opened from the account menu (USR-12), that shows this form for their own profile; after a save the toolbar shows the new name and a message confirms it. The page never shows the user's status, disable reason or roles. A user without roles can save their profile through the API only (see Known limitations).

#### USR-18.1 Invalid values are explained at the field

- GIVEN the profile form
- WHEN a name over 100 characters is saved, and when the server answers 400 `validation-failed` for the phone
- THEN the first is refused with no request sent
- AND the phone field says the number is not valid and the values stay in the form

#### USR-18.2 My profile is saved and shown

- GIVEN an active user with roles on the My profile page
- WHEN the user saves the phone `8 701 123 45 67` and a new first name
- THEN the phone reads `+77011234567`, the toolbar shows the new name and a message confirms the save
- AND the page shows no status, disable reason or roles

## Known limitations

- When two administrators save the same user's profile or roles at the same moment, the last save wins; each save is still audited with what it changed. There is no warning about a concurrent edit.
- A user without roles sees the access screen (IDN-10.1), which has no link to the profile, so they can change their profile through the API only.
- A history page can hold fewer entries than its size, and its total counts entries that are left out (AUD-6).
- The server's wording of a validation error is not shown for the phone: the client explains an invalid phone in its own words.

## Out of scope

- Creating users, invitations and sending email; see the invitations and invitation emails capabilities.
- Changing a user's email, and showing or unlinking a user's sign-in identities.
- Deleting users.
- A global audit screen and audit retention (AUD).
- Access of coordinators, mentors and observers to user data, and automatic disabling when an enrollment ends.
- Photos, consent and other personal data rules.
