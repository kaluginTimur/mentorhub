# Audit

## Purpose

The record of who changed what in MentorHub, and when. Every audited change leaves one entry, written together with the change, that nobody can alter afterwards. Entries are read only through the history of one subject (a user, later an enrollment), under that subject's own access rules. This capability owns the rules of the record and the catalog of every audited action. Whether an action writes its entry is checked where the action is owned: that capability states the action is audited and cites the catalog (AUD-7). How a subject's history is shown is described by the capability that owns the subject (for users, user administration). Who may call the operations that write entries is described in the authorization capability (PERM).

## Requirements

### AUD-1 A change and its entry are stored together

The system SHALL store the entry of an audited change together with the change: either both are stored, or neither is (decision 0006). An entry SHALL NOT be written without a change that is being stored.

#### AUD-1.1 A change that fails leaves no entry

- GIVEN an audited change whose processing fails after the entry was prepared
- WHEN the failure happens
- THEN the change is not stored
- AND the entry is not stored

#### AUD-1.2 An entry cannot be written outside a change

- GIVEN no change is being stored
- WHEN a module tries to write an audit entry
- THEN the attempt is refused
- AND no entry is stored

### AUD-2 What an entry holds

The system SHALL record, in each entry, the time of the change, the actor, the action, the subject (its type and id) and the details. The actor is a user, or none for a change made by the system. The details hold what a reader needs to understand the change (decision 0006).

#### AUD-2.1 An entry has the actor, the action, the subject and the details

- GIVEN an administrator changes a user's roles
- WHEN the entry of the change is read
- THEN it has the time of the change, the administrator as the actor, the action `USER_ROLES_CHANGED`, the user as the subject, and the roles added and removed as the details

### AUD-3 Entries cannot be changed or deleted

The system SHALL keep the audit log append-only: a stored entry SHALL NOT be updated or deleted by the application or by any statement against the log, whatever the code path (decision 0006).

#### AUD-3.1 An entry cannot be updated or deleted

- GIVEN a stored entry
- WHEN anything tries to update it or to delete it
- THEN the attempt is rejected
- AND the entry is still stored, unchanged

### AUD-4 Only stored changes are audited

The system SHALL write an entry only for a change that was made. A refused request, a change that would alter nothing, and a read SHALL write no entry.

#### AUD-4.1 A refused change writes no entry

- GIVEN an audited operation that the system refuses (for example, an administrator disabling themselves)
- WHEN the administrator calls it
- THEN the response is the refusal
- AND no entry is written

#### AUD-4.2 A change that alters nothing writes no entry

- GIVEN a user who already has exactly the requested roles
- WHEN an administrator sets those roles
- THEN no entry is written

#### AUD-4.3 A read writes no entry

- GIVEN a pending invitation
- WHEN an administrator reads its link
- THEN no entry is written

### AUD-5 Details never copy secrets or protected content

The system SHALL NOT copy into the details of an entry a secret or content that has its own visibility rules. The entry names the subject instead; whoever may see the subject sees the content through the subject. In particular the token of an invitation, and so its link, SHALL NOT appear in any entry (decision 0006).

#### AUD-5.1 The invitation token is not in any entry

- GIVEN an invitation that was created, given a new link, and had an email requested
- WHEN the entries about it are read
- THEN none contains the token or the link of the invitation

### AUD-6 Entries are read only through the history of a subject

The system SHALL offer no operation that reads the entries of several subjects at once. An entry SHALL be readable only through the history of its own subject, served under the access rules of that subject, and a history SHALL hold only the entries about its subject (decision 0006). A history is ordered newest first. The entries about invitations are stored but have no history. A history SHALL leave out an entry whose action the running version does not know, instead of failing, and SHALL log one warning for each such action. An entry of a known action whose details cannot be read SHALL be left out in the same way, with a warning.

#### AUD-6.1 There is no global audit view

- GIVEN the API contract
- WHEN its operations are listed
- THEN none returns audit entries across subjects

#### AUD-6.2 A history holds only entries about its subject

- GIVEN entries about two different users
- WHEN the history of the first user is read
- THEN it holds only the entries about the first user

#### AUD-6.3 A history is newest first

- GIVEN a user with several entries made at different times
- WHEN their history is read
- THEN the entries come newest first

#### AUD-6.4 An action the version does not know is left out

- GIVEN an entry about a user with an action that the application does not know
- WHEN the history of the user is read several times
- THEN the entry is not in the history and the read does not fail
- AND one warning is logged for that action

#### AUD-6.5 An entry whose details cannot be read is left out

- GIVEN an entry of a known action whose details cannot be read
- WHEN the history of its subject is read
- THEN the entry is not in the history and the read does not fail
- AND a warning is logged

### AUD-7 The catalog of audited actions

The system SHALL write exactly the entries of the catalog below, each at the moment named, for the actor named, with the details named. A new audited action SHALL be added to this catalog, and, when a view shows it, to the contract of that view, in the same change. The capability that owns an action states that it is audited and cites this requirement. The roles in an entry are the roles the running version knows. The development users created at startup in development mode, and the outcome of sending an invitation email (sent, failed or skipped), are not audited.

| Action | Subject | Written when | Actor | Details |
|---|---|---|---|---|
| `USER_DISABLED` | user | An administrator disables an active user | the administrator | the reason, and the comment when there is one |
| `USER_ENABLED` | user | An administrator enables a disabled user | the administrator | the reason and the comment the user had been disabled with |
| `USER_ROLES_CHANGED` | user | An administrator sets roles that differ from the user's current roles | the administrator | the roles added and the roles removed |
| `USER_PROFILE_UPDATED` | user | An administrator saves another user's profile, or a user saves their own, and at least one value changes | the person who saved | each changed field (first name, last name, phone) with its old and new value |
| `USER_BOOTSTRAPPED_ADMIN` | user | The administrator bootstrap changes anything | none (the system) | whether it created the user, linked the sign-in identity, enabled the user and added the administrator role |
| `USER_INVITATION_ACCEPTED` | user | A person accepts an invitation | the accepting user | the invitation, the administrator who created it, whether the user was created, the identity linked and the user enabled, and the roles added and removed |
| `INVITATION_CREATED` | invitation | An administrator creates an invitation | the administrator | the email and the roles |
| `INVITATION_LINK_REISSUED` | invitation | An administrator gives an open invitation a new link | the administrator | the new expiry time |
| `INVITATION_REVOKED` | invitation | An administrator revokes an open invitation | the administrator | none |
| `INVITATION_EMAIL_REQUESTED` | invitation | An invitation email is requested: with a new invitation, with a new link, or by sending again | the administrator | the reason: created, link reissued or resent |

#### AUD-7.1 The administrator bootstrap is audited without an actor

- GIVEN the bootstrap creates the user, links the sign-in identity and adds the administrator role
- WHEN the application admits the first sign-in of the bootstrap address
- THEN one entry `USER_BOOTSTRAPPED_ADMIN` is written about the user
- AND it has no actor
- AND the details say that the user was created, the identity linked and the administrator role added

#### AUD-7.2 A bootstrap that changes nothing writes no entry

- GIVEN the bootstrap address belongs to an active administrator whose sign-in identity is already linked
- WHEN that administrator signs in
- THEN no entry is written

## Known limitations

- Entries written in one change have the same time and no defined order among themselves, so a history shows them in an arbitrary order.
- A history is paged before entries are left out, so a page can hold fewer items than its size, and the total counts the entries that are left out.
- A history is a single list per subject: there is no filter by action and no search.

## Out of scope

- A global audit screen or any view across subjects, retention, and export. Each needs a decision on who may see which actions.
- A view of the entries about invitations.
- Audit entries for notes, feedback, enrollments and assignments, which are planned with their features.
