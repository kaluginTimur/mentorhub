# Invitations

## Purpose

Registration in MentorHub is by invitation only (decision 0003), so administrators need a way to let new people in without touching the database. An administrator creates an invitation for an email, optionally with roles, and passes the link on however they like. The invited person opens the link, signs in or registers at the identity provider with that email, and is linked to a MentorHub user: a new one, or the existing disabled one, which is reactivated. This capability covers creating, listing and revoking invitations, their links, the public landing page of a link and accepting an invitation. Who is let in at all is described in identity (IDN), roles and permissions in authorization (PERM), recorded changes in audit (AUD), and sending a link by email in the invitation emails capability. The API contract holds the fields, limits and response shapes.

## Requirements

### INV-1 Invitations are managed by holders of USERS_INVITE

The system SHALL let only a caller who holds `USERS_INVITE` create, list and revoke invitations, get a new link and read a link, for every invitation and not only the ones they created. A refusal is 403 `forbidden` and changes nothing (PERM-3, PERM-6). Previewing and accepting a link are described in INV-10 and INV-11.

#### INV-1.1 Every management action needs the permission

- GIVEN an open invitation and an active user who holds no `USERS_INVITE`, such as a mentor
- WHEN the user creates, lists, revokes, asks a new link for, or reads the link of an invitation
- THEN each response is 403 `forbidden`
- AND no invitation or link is changed

#### INV-1.2 Any holder manages any invitation

- GIVEN an invitation created by one administrator
- WHEN another administrator revokes it
- THEN the invitation is revoked

### INV-2 Creating an invitation

The system SHALL let a caller who holds `USERS_INVITE` create a pending invitation for an email with any roles, none included, not limited to the roles the inviter holds, and respond 201 with the invitation and its link. It expires after the link lifetime (INV-4). An invalid request SHALL create nothing (PLT-2). Creating is audited (AUD-7).

#### INV-2.1 An invitation is created with its link and expiry

- GIVEN an administrator
- WHEN the administrator creates an invitation for a new email with the mentor role, or with no roles
- THEN the response is 201 with the invitation, with the status `PENDING` and the roles given
- AND the expiry is the link lifetime after the time of creation
- AND the response has the link of the invitation

#### INV-2.2 The email is trimmed and kept as entered

- GIVEN an administrator
- WHEN the administrator creates an invitation for an email with spaces around it and capital letters in it
- THEN the invitation has the email without the spaces and with the same letters as entered

#### INV-2.3 An invalid email is rejected

- GIVEN an administrator
- WHEN the administrator creates an invitation for an email that is blank, malformed or longer than the longest email
- THEN the response is 400 `validation-failed` and `errors` names the field `email`
- AND no invitation is created

### INV-3 Who may be invited

The system SHALL refuse with 409 to invite an email of an active user or one with an open invitation, pending or expired, comparing ignoring case. Two simultaneous requests for one email SHALL create one invitation. A disabled user is reactivated on acceptance (INV-12).

#### INV-3.1 An active user is not invited

- GIVEN an active user
- WHEN an administrator creates an invitation for the email of the user, with other capital letters
- THEN the response is 409 with the problem type `user-active` and the id of the user in `userId`
- AND no invitation is created

#### INV-3.2 An open invitation blocks another one

- GIVEN a pending, and in another case an expired, invitation for an email
- WHEN an administrator creates an invitation for the same email, with other capital letters
- THEN the response is 409 with the problem type `invitation-exists` and the id of the existing invitation in `invitationId`

#### INV-3.3 A closed invitation or a disabled user does not block

- GIVEN an accepted or a revoked invitation for an email, or a disabled user with the email, and no active user with it
- WHEN an administrator creates an invitation for that email
- THEN the invitation is created

#### INV-3.4 Two parallel invitations for one email create one

- GIVEN no invitation for an email
- WHEN two administrators create an invitation for that email at the same moment
- THEN one invitation is created
- AND the other request is answered 409 `invitation-exists`

### INV-4 The application address and the link lifetime are configuration

The system SHALL build every link from the configured address of the application, and SHALL refuse to start when that address is missing or the link lifetime is not positive, naming the setting (PLT-5).

#### INV-4.1 A missing address or a lifetime that is not positive stops the application

- GIVEN a configuration without the address of the application, or with a link lifetime of zero
- WHEN the application starts
- THEN the start fails
- AND the failure message names the setting at fault

### INV-5 The list of invitations

The system SHALL return, for a caller who holds `USERS_INVITE`, a page of invitations (PLT-3), newest first, that can be narrowed by a text found in the email and by the status. A role the running version does not know is left out of an item (PERM-5). An item never contains the link or the token (INV-9).

#### INV-5.1 An item has its data, newest first

- GIVEN an accepted invitation and two pending ones
- WHEN an administrator lists the invitations
- THEN the newest comes first, and each item has the email, the roles, the status, the creation time, the expiry and the creator
- AND the accepted one also has the time of acceptance and the accepting user

#### INV-5.2 The text and the status narrow the list

- GIVEN invitations for `ivan@example.com`, `a_b@example.com` and `axb@example.com`, an open one whose expiry has been reached, and a pending one
- WHEN an administrator searches for `IVAN`, for `a_b`, and filters by the status `EXPIRED`
- THEN only the invitation for `ivan@example.com` is listed for the first, and only the one for `a_b@example.com` for the second
- AND only the one whose expiry has been reached for the filter, with `totalItems` 1

#### INV-5.3 A text longer than an email is rejected

- GIVEN an administrator
- WHEN the administrator searches with a text of more than 254 characters
- THEN the response is 400 `validation-failed` with `errors` naming the parameter `q`

#### INV-5.4 A role the application does not know is left out of an item

- GIVEN an invitation with a stored role the application does not know and the mentor role
- WHEN an administrator lists the invitations
- THEN the item has the mentor role only

### INV-6 Revoking an invitation

The system SHALL let a caller who holds `USERS_INVITE` revoke an open invitation, pending or expired, after which its link stops working (INV-10). Revoking is audited (AUD-7).

#### INV-6.1 An open invitation is revoked

- GIVEN a pending, and in another case an expired, invitation
- WHEN an administrator revokes it
- THEN the response is the invitation with the status `REVOKED`, the time of revocation and the administrator

#### INV-6.2 A closed invitation is not revoked

- GIVEN an accepted invitation, and a revoked invitation
- WHEN an administrator revokes each
- THEN each is answered 409 `invitation-closed` and stays as it was, including the time and the administrator of the earlier revocation

#### INV-6.3 An unknown invitation is not revoked

- GIVEN an id that no invitation has
- WHEN an administrator revokes it
- THEN the response is 404 `not-found`

### INV-7 Getting a new link

The system SHALL let a caller who holds `USERS_INVITE` give an open invitation, pending or expired, a new link, which replaces the old one and restarts the expiry. This is audited (AUD-7).

#### INV-7.1 A new link has a new token and a new expiry

- GIVEN a pending invitation with a link
- WHEN an administrator asks for a new link
- THEN the response has the invitation and a link that differs from the old one
- AND the expiry is the link lifetime after the time of the request

#### INV-7.2 An expired invitation is renewed

- GIVEN an expired invitation
- WHEN an administrator asks for a new link
- THEN the invitation is pending, and the new link previews and accepts

#### INV-7.3 A closed invitation gets no new link

- GIVEN an accepted invitation, and a revoked invitation
- WHEN an administrator asks for a new link for each
- THEN each response is 409 `invitation-closed`
- AND the invitations are unchanged

#### INV-7.4 The old link stops working

- GIVEN a pending invitation with a link
- WHEN an administrator asks for a new link
- THEN previewing the old link is answered 404 `invitation-invalid`

### INV-8 Reading the link of a pending invitation

The system SHALL let a caller who holds `USERS_INVITE`, not only the one who created the invitation, read the current link of a pending invitation with the invited email and the expiry. An invitation that is not pending has no link to read. Reading changes nothing and is not audited (AUD-4).

#### INV-8.1 A pending invitation returns its link to any administrator

- GIVEN a pending invitation created by one administrator, and later given a new link
- WHEN another administrator reads its link
- THEN the response is 200 with the current link, the invited email and the expiry

#### INV-8.2 An invitation that is not pending has no link to read

- GIVEN an expired, an accepted and a revoked invitation
- WHEN an administrator reads the link of each
- THEN the expired one is answered 409 `invitation-expired`, because a new link renews it
- AND the accepted and the revoked ones are answered 409 `invitation-closed`

#### INV-8.3 An unknown invitation has no link to read

- GIVEN an id that no invitation has
- WHEN an administrator reads its link
- THEN the response is 404 `not-found`

### INV-9 A link is shown only where it is needed

The system SHALL return the link of an invitation only in the responses that create the invitation, give it a new link or read the link of a pending invitation (INV-8), and SHALL forbid caching them. No other response, the list included, SHALL contain the link or the token. The token SHALL NOT be written to an audit entry (AUD-5) or a log.

#### INV-9.1 Other responses carry neither the link nor the token

- GIVEN a pending invitation
- WHEN an administrator lists the invitations, and revokes one
- THEN the list and the revocation contain no link or token

#### INV-9.2 The responses with a link are not cached

- GIVEN a pending invitation
- WHEN an administrator creates an invitation, gets a new link and reads a link
- THEN each of the three responses has `Cache-Control: no-store`

### INV-10 Previewing a link and invalid links

The system SHALL answer a preview of a valid link, signed in or not, with the invited email and the expiry, ignoring any token sent (PLT-4). A link is valid while its invitation is pending and it is the current link; any other token, in a preview or an acceptance, gets the same 404 `invitation-invalid`, except for an invitation the caller already accepted (INV-14).

#### INV-10.1 A visitor previews a valid link

- GIVEN a pending invitation, and a caller who is not signed in or who sends an invalid access token
- WHEN the caller previews its link
- THEN the response is 200 with the invited email and the expiry

#### INV-10.2 Every invalid link is answered alike

- GIVEN an unknown token, a malformed token, a token that a new link replaced, and the links of an expired, a revoked and an accepted invitation
- WHEN anyone previews or accepts with each
- THEN each response is 404 `invitation-invalid`
- AND the responses are identical

### INV-11 Accepting an invitation creates the user

The system SHALL let a signed-in caller of any access status (IDN-3) accept an invitation when the verified email of their token equals the invitation's, ignoring case (INV-13). When no user has that email, it SHALL together or not at all create an active user, link the caller's identity, assign the invitation's roles (PERM-5) and mark the invitation accepted. Audited (AUD-7).

#### INV-11.1 A new person joins

- GIVEN a pending invitation with the mentor role, and a signed-in identity that is not linked to any user, with the same verified email and a name
- WHEN the identity accepts the invitation
- THEN an active user exists with the email of the invitation and the name from the token, the identity is linked to the user, and the user has the mentor role
- AND the invitation is accepted by the user, with the time

#### INV-11.2 The email is compared ignoring case

- GIVEN a pending invitation for `Anna@Example.com`
- WHEN an identity with the verified email `anna@example.com` accepts it
- THEN the invitation is accepted

#### INV-11.3 Acceptance needs a signed-in caller

- GIVEN a pending invitation
- WHEN a caller without a token accepts it
- THEN the response is 401

#### INV-11.4 The next request of the new user passes the gate

- GIVEN an identity that has just accepted an invitation
- WHEN the identity sends its next request with the same token
- THEN the request passes the gate (IDN-3)

#### INV-11.5 An unknown stored role is skipped

- GIVEN a pending invitation with a stored role that the application does not know and the student role
- WHEN an identity with the invited verified email accepts it
- THEN the invitation is accepted, and the user has the student role only

### INV-12 Accepting reactivates or extends an existing user

The system SHALL, when a user has the invitation's email, use that user and leave their profile unchanged. A disabled user is enabled and gets exactly the invitation's roles (decision 0004); an active user only gains roles. Audited (AUD-7).

#### INV-12.1 A disabled user is reactivated

- GIVEN a disabled user with a reason, a comment, a profile and a history, and a pending invitation for their email
- WHEN an identity with the verified email, whose token has other names, accepts the invitation
- THEN the same user is active, the reason and the comment are cleared, and the profile is as it was
- AND the history of the user from before is intact (USR-10)

#### INV-12.2 A second identity is linked to the user

- GIVEN a user with one linked identity, and an invitation for their email
- WHEN another identity with the verified email accepts it
- THEN both identities are linked to the user

#### INV-12.3 An active user only gains roles

- GIVEN an active user with the mentor role, and a pending invitation for their email with the student role
- WHEN the invitation is accepted
- THEN the user has the mentor and the student roles

#### INV-12.4 A reactivated user has exactly the roles of the invitation

- GIVEN a disabled user who held the mentor and administrator roles and a stored role the application does not know, and a pending invitation for their email with the observer role
- WHEN an identity with the verified email accepts the invitation
- THEN the user has the observer role, and the unknown stored role is still stored

#### INV-12.5 An identity that was linked already is not linked twice

- GIVEN a disabled user whose identity is already linked, and an invitation for their email
- WHEN that identity accepts it
- THEN the user is active and the identity is linked once

### INV-13 Accepting needs the invited email

The system SHALL refuse an acceptance unless the token's email is verified and equals the invitation's, and the caller's identity is not linked to another user (IDN-6). Nothing changes and no audit entry is written (AUD-4). The email match is the only protection of a readable link (INV-8).

#### INV-13.1 An email that is not verified or differs is refused

- GIVEN a pending invitation, and an identity whose token has the invited email not verified, and another identity with a verified email that differs
- WHEN each accepts the invitation
- THEN each response is 409 `invitation-email-mismatch`
- AND no user, link or role is created and the invitation is still pending

#### INV-13.2 An identity linked to another user is refused

- GIVEN a pending invitation, and an identity with the invited verified email that is linked to another user, or to a user while no user has that email
- WHEN the identity accepts the invitation
- THEN the response is 409 `identity-linked-elsewhere`
- AND nothing changes and no user is created

### INV-14 Accepting twice

The system SHALL answer an acceptance of an invitation that the caller's own user already accepted with 200, change nothing and write no audit entry (AUD-4), so a retry or a double click is safe.

#### INV-14.1 The same user accepts again

- GIVEN an invitation accepted by the user whose identity makes the request
- WHEN the identity accepts it again
- THEN the response is 200 with the data of the user
- AND nothing changes and no new audit entry is written

### INV-15 Parallel requests on one invitation

The system SHALL treat parallel requests on one invitation as if they came one after another: one wins and the others get the answer they would get after it, never a server error.

#### INV-15.1 Parallel acceptances, revocations and new links

- GIVEN a pending invitation
- WHEN it is accepted twice by one identity, accepted by two identities with the verified invited email, or accepted and, at the same moment, revoked or given a new link
- THEN the user is created once and the invitation is accepted once, or one of the two actions wins
- AND the others get the answer they would get after it: 200 with the user for the same identity, 404 `invitation-invalid` for a later acceptance, 409 `invitation-closed` for the revocation or the new link
- AND none is a server error

### INV-16 The invitations page

The client SHALL offer the invitations page, its link and the action "Invite" only to a user who holds `USERS_INVITE` (PERM-7). The page is a list page (PLT-9) with a search by email and a status filter, and its row actions follow the status of the invitation.

### INV-17 Copying a link from the list

The client SHALL copy the link of a pending invitation on "Copy link" and say so; when the browser does not allow the clipboard, it SHALL show the link selected so the user can copy it by hand. When the invitation turns out to be closed, expired or gone, it SHALL say so and reload the list.

#### INV-17.1 The link is shown for copying when the clipboard is not allowed

- GIVEN a pending invitation, and a browser that does not allow the clipboard
- WHEN the user chooses "Copy link"
- THEN a view shows the link selected, with the email and the expiry

#### INV-17.2 Copying works in the supported browsers (manual)

- GIVEN a pending invitation in the local environment
- WHEN the user chooses "Copy link" in Chrome and in Safari
- THEN the link is copied in each, and opening it as the invited person accepts the invitation

#### INV-17.3 A changed invitation is reported

- GIVEN an invitation that the API answers 409 `invitation-closed`, 409 `invitation-expired` or 404 to the reading of the link
- WHEN the user chooses "Copy link"
- THEN the client says that the invitation is closed, or expired and needs a new link
- AND the list is reloaded

### INV-18 Revoking and getting a new link from the list

The client SHALL ask for a confirmation before revoking an invitation or giving it a new link, saying that the link will stop working, and SHALL do nothing when the user cancels. A new link is shown in the link view (INV-19).

#### INV-18.1 Revoking and a new link are confirmed first

- GIVEN a pending invitation
- WHEN the user chooses "Revoke" or "Get a new link"
- THEN a confirmation names the email and says the link, or the previous link, will stop working

#### INV-18.2 Cancelling sends nothing

- GIVEN the confirmation to revoke an invitation
- WHEN the user cancels
- THEN nothing is sent

#### INV-18.3 A changed invitation is reported

- GIVEN an invitation that the API answers 409 `invitation-closed` or 404 to a revocation or a new link
- WHEN the user confirms
- THEN the client says that the invitation is closed
- AND the list is reloaded

### INV-19 The dialog to invite a person and the link view

The client SHALL let a holder of `USERS_INVITE` invite a person in a dialog with the email and the roles, then show the link view with the link, the invited email and the expiry. It SHALL report a person who has access or an invitation, and keep what was entered after a failure.

#### INV-19.2 A person with access is reported

- GIVEN the API answers 409 `user-active`
- WHEN the user submits the dialog
- THEN the dialog says that the person already has access, with a link to the page of the user

#### INV-19.3 An existing invitation is reported

- GIVEN the API answers 409 `invitation-exists`
- WHEN the user submits the dialog
- THEN the dialog says that there is already an invitation for the email, with a link to the invitations page filtered by that email

#### INV-19.4 The entered values are kept after a conflict

- GIVEN the API answers 409 `user-active`
- WHEN the user submits the dialog
- THEN the email and the chosen roles are still entered

### INV-20 The landing page of a link

The client SHALL serve a link to anyone without sign-in, check it first, accept the invitation when the visitor returns from sign-in, and explain each refusal. It SHALL remove the token from the address as soon as it has read it, and SHALL NOT send it to the identity provider.

#### INV-20.1 An invalid link is explained

- GIVEN the check answers 404 `invitation-invalid`, or the page is opened with no link and no remembered one
- WHEN a visitor opens the page
- THEN the page says that the link is invalid or has expired and to ask for a new one
- AND it offers no sign-in

#### INV-20.3 A mismatch of emails is explained

- GIVEN a signed-in visitor on the page
- WHEN the acceptance answers 409 `invitation-email-mismatch` with different emails
- THEN the page shows both emails and offers another account

#### INV-20.4 The address carries no token

- GIVEN a visitor who opens a link with a token
- WHEN the page loads
- THEN the address of the page is the landing address without the token, and the page still works with the link

#### INV-20.5 An unverified email is explained

- GIVEN a signed-in visitor whose account email equals the invited one ignoring case
- WHEN the acceptance answers 409 `invitation-email-mismatch`
- THEN the page says to verify the email first

#### INV-20.6 An account of another user is explained

- GIVEN a signed-in visitor on the page
- WHEN the acceptance answers 409 `identity-linked-elsewhere`
- THEN the page says that the account belongs to another MentorHub user and to contact the person who invited

#### INV-20.7 The sign-in request carries no token

- GIVEN a visitor who opened a link with a token
- WHEN the visitor chooses to sign in or to create an account
- THEN neither the redirect address nor the sign-in state contains the token

#### INV-20.8 Signing out of an account of another user forgets the link

- GIVEN the page says that the account belongs to another MentorHub user
- WHEN the visitor signs out
- THEN the link is no longer remembered

### INV-21 A person joins through a link (manual)

The system SHALL let an administrator invite a person, pass the link on, and let that person join with the access the invitation gives.

#### INV-21.1 The whole journey works in the local environment (manual)

- GIVEN the local environment and an administrator
- WHEN the administrator invites `stranger@mentorhub.local` with the mentor role, copies the link, signs out and opens the link, and the visitor signs in as the stranger
- THEN the stranger lands on the home page with mentor access
- AND the invitation shows as accepted
- AND the user page of the stranger shows the history entry of the acceptance (USR-11)
- AND opening the same link again shows that the link is invalid

## Known limitations

- The link of every pending invitation is readable by anyone who can read the stored data of the application, and by every holder of `USERS_INVITE`. Accepting therefore always needs the verified email that equals the invitation's (INV-13); that match is the only protection of a link, and relaxing it would let a leaked link grant access.
- Registration at the identity provider is open, and one email can be registered there once (IDN-8). Anyone can register an account there with someone else's email, whether they learned it from a leaked link (the landing page shows the invited email) or guessed it. The squatter gets no MentorHub access, because that email is not verified (INV-13). The real person, however, cannot register with that email and has to recover the account through the mailbox with the identity provider's password reset.
- A "Copy link" that runs while another administrator gives a new link can return the old link just before it is replaced; the administrator then copies a link that no longer works.
- The first request for a link reaches the access log of the server that serves the client, which therefore holds the token of the link.
- The email of a person is compared ignoring case, but is stored as entered; an invitation for `Anna@Example.com` is accepted by `anna@example.com` and creates a user with the email as the invitation had it.

## Out of scope

- Sending the link by email, resending and the delivery status; see the invitation emails capability.
- Anyone but holders of `USERS_INVITE` inviting, such as coordinators or mentors, and invitations tied to an enrollment or a cohort.
- Bulk invitations and import, editing an invitation (revoke it and create a new one), and cleaning up old invitations.
- Accepting automatically on sign-in without the link (an allowlist of emails), and creating users from a corporate sign-in.
- Limiting the rate of previews and acceptances.
- Copying the link of an expired invitation: a new link renews it.
- Making registration and password reset work in the local environment without a mail server.
