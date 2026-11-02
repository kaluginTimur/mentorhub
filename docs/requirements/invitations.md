# Invitations

## Purpose

Registration in MentorHub is by invitation only (decision 0003), so administrators need a way to let new people in without touching the database. An administrator creates an invitation for an email, optionally with roles, and passes the invitation link on however they like. The invited person opens the link, signs in or registers at the identity provider with that email, and is linked to a MentorHub user: a new one, or the existing disabled one, which is reactivated. This capability covers creating, listing and revoking invitations, their links and how a link ages, the public landing page of a link, and accepting an invitation. Who is let in at all is described in the identity capability (IDN), roles and permissions in the authorization capability (PERM), and the recorded changes in the audit capability (AUD); sending a link by email is described in the invitation emails capability.

## Requirements

### INV-1 Invitations are managed by holders of USERS_INVITE

The system SHALL let only a caller who holds `USERS_INVITE` manage invitations, and SHALL let such a caller manage every invitation, not only the ones they created. A caller without the permission SHALL be refused with 403 `forbidden` and nothing SHALL change (PERM-3, PERM-6). A caller who is not signed in, not invited or disabled is refused before the permission is considered (IDN-3). Invitations, with their emails, roles and who created, revoked and accepted them, are visible to holders of `USERS_INVITE` only; the email and the expiry of an invitation are also visible to anyone who holds a valid link (INV-10).

| Action | Needs |
|---|---|
| Create, list, revoke an invitation; get a new link; read the link of a pending one; request an invitation email (see the invitation emails capability) | `USERS_INVITE` |
| Preview a link | nothing, public; the token is the authorization |
| Accept an invitation | a signed-in caller of any access status (IDN-3), with the invited email (INV-13) |

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

The system SHALL create, for a caller who holds `USERS_INVITE`, an invitation for an email with a set of roles that may be empty, and SHALL respond 201 with the invitation and its link. The invitation is pending and expires at the time of creation plus the link lifetime, 7 days unless configured otherwise (INV-4). The link is the configured application address without a trailing slash, followed by `/invite/` and the token. The roles may be any roles; inviting is not limited to the roles the inviter holds. The email is trimmed, must be a valid address, is stored as entered after trimming, and is compared ignoring case. An invalid request, including a role outside the set of roles, SHALL be answered as described in (PLT-2) with an error for each invalid field and SHALL create nothing. Creating an invitation is audited (AUD-7).

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

The system SHALL refuse to create an invitation for an email that belongs to an active user, and for an email that already has an open invitation, pending or expired. Both comparisons ignore case, and both responses are 409 and create nothing. The email of a disabled user can be invited, which reactivates that user when the invitation is accepted (INV-12). The email of an accepted or revoked invitation can be invited again. When two requests invite one email at the same moment, exactly one SHALL create the invitation and the other SHALL be refused as the email already has an open invitation.

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

The system SHALL build every link from the address of the application as configured, and SHALL refuse to start, in every profile, when that address is missing or the link lifetime is not positive, with a failure message that names the setting (PLT-5).

#### INV-4.1 A missing address or a lifetime that is not positive stops the application

- GIVEN a configuration without the address of the application, or with a link lifetime of zero
- WHEN the application starts
- THEN the start fails
- AND the failure message names the setting at fault

### INV-5 The list of invitations

The system SHALL return, for a caller who holds `USERS_INVITE`, a page of invitations (PLT-3), newest first by creation time and then by id. Each item has the id, the email, the roles, the status, the creation time, the expiry, who created it, when and by whom it was accepted or revoked when it was, and the latest invitation email when there is one (see the invitation emails capability). The status is `ACCEPTED` when the invitation was accepted; `REVOKED` when it was revoked and not accepted; `EXPIRED` when it is open and its expiry has been reached; `PENDING` otherwise. The text `q` keeps the invitations whose email contains it, ignoring case, after trimming; the characters `%`, `_` and `\` match themselves; `q` may have up to 254 characters, the length of the longest email, and a longer text is rejected (PLT-2). The `status` keeps the invitations with that status. A stored role that the running version does not know is left out of the roles of an item (PERM-5). An item never contains the link or the token (INV-9).

#### INV-5.1 An item has its data, newest first

- GIVEN an invitation accepted by a user, a pending one with two roles, and one with a stored role the application does not know
- WHEN an administrator lists the invitations
- THEN the newest comes first, and each item has the email, the roles, the status, the creation time, the expiry and the creator
- AND the accepted one also has the time of acceptance and the accepting user
- AND the unknown role is not among the roles of its item

#### INV-5.2 The text and the status narrow the list

- GIVEN invitations for `ivan@example.com`, `a_b@example.com` and `axb@example.com`, an open one whose expiry has been reached, and a pending one
- WHEN an administrator searches for `IVAN`, for `a_b`, filters by the status `EXPIRED`, and searches with a text of more than 254 characters
- THEN only the invitation for `ivan@example.com` is listed for the first, and only the one for `a_b@example.com` for the second
- AND only the one whose expiry has been reached for the filter, with `totalItems` 1
- AND the long text is answered 400 `validation-failed` with `errors` naming the parameter `q`

### INV-6 Revoking an invitation

The system SHALL let a caller who holds `USERS_INVITE` revoke an open invitation, pending or expired. The invitation then has the status `REVOKED` with the time and the administrator, and its link stops working (INV-10). Revoking an accepted or a revoked invitation SHALL be refused with 409 `invitation-closed`, and nothing SHALL change. An unknown invitation is answered 404 `not-found`. Revoking is audited (AUD-7).

#### INV-6.1 An open invitation is revoked

- GIVEN a pending, and in another case an expired, invitation
- WHEN an administrator revokes it
- THEN the response is the invitation with the status `REVOKED`, the time of revocation and the administrator

#### INV-6.2 A closed or unknown invitation is not revoked

- GIVEN an accepted invitation, a revoked invitation, and an id that no invitation has
- WHEN an administrator revokes each
- THEN the first two are answered 409 `invitation-closed` and stay as they were, including the time and the administrator of the earlier revocation
- AND the unknown one is answered 404 `not-found`

### INV-7 Getting a new link

The system SHALL let a caller who holds `USERS_INVITE` give an open invitation, pending or expired, a new link. A new token replaces the old one, the expiry becomes the time of the request plus the link lifetime, so an expired invitation is pending again, and the response has the invitation and the new link. The old link stops working (INV-10). A new link for an accepted or revoked invitation SHALL be refused with 409 `invitation-closed` and nothing SHALL change. Getting a new link is audited (AUD-7).

#### INV-7.1 A new link replaces the old one

- GIVEN a pending invitation with a link
- WHEN an administrator asks for a new link
- THEN the response has the invitation and a link that differs from the old one
- AND the expiry is the link lifetime after the time of the request
- AND previewing the old link is answered 404 `invitation-invalid`

#### INV-7.2 An expired invitation is renewed

- GIVEN an expired invitation
- WHEN an administrator asks for a new link
- THEN the invitation is pending, and the new link previews and accepts

#### INV-7.3 A closed invitation gets no new link

- GIVEN an accepted invitation, and a revoked invitation
- WHEN an administrator asks for a new link for each
- THEN each response is 409 `invitation-closed`
- AND the invitations are unchanged

### INV-8 Reading the link of a pending invitation

The system SHALL let a caller who holds `USERS_INVITE`, not only the one who created the invitation, read the current link of a pending invitation, together with the invited email and the expiry. The link is the same as in the response that created the invitation or last gave it a new link. Reading a link changes nothing and is not audited (AUD-4). An accepted or a revoked invitation has no link to read and the answer is 409 `invitation-closed`; an expired one answers 409 `invitation-expired`, because a new link renews it; an unknown invitation is answered 404 `not-found`.

#### INV-8.1 A pending invitation returns its link to any administrator

- GIVEN a pending invitation created by one administrator, and later given a new link
- WHEN another administrator reads its link
- THEN the response is 200 with the current link, the invited email and the expiry

#### INV-8.2 An invitation that is not pending has no link to read

- GIVEN an expired, an accepted and a revoked invitation, and an id that no invitation has
- WHEN an administrator reads the link of each
- THEN the expired one is answered 409 `invitation-expired`
- AND the accepted and the revoked ones are answered 409 `invitation-closed`
- AND the unknown one is answered 404 `not-found`

### INV-9 A link is shown only where it is needed

The system SHALL return the link of an invitation only in the response that creates the invitation, the response that gives it a new link, and the response that reads the link of a pending invitation (INV-8). Every other response, the list included, SHALL contain neither the link nor the token. Each of these three responses SHALL forbid caching (`Cache-Control: no-store`). The token SHALL NOT be written to an audit entry (AUD-5) or to a log.

#### INV-9.1 The link appears only in three responses, and they are not cached

- GIVEN a pending invitation
- WHEN an administrator lists the invitations, revokes one, and in other cases creates an invitation, gets a new link and reads a link
- THEN the list and the revocation contain no link or token
- AND each of the three responses with a link has `Cache-Control: no-store`

### INV-10 Previewing a link and invalid links

The system SHALL answer a preview of a valid link, for anyone signed in or not, with the invited email and the expiry. The preview is public: a token sent with the request is ignored, so an invalid one cannot make it fail (PLT-4). A link is valid while its invitation is pending and the link is its current one. The system SHALL answer a preview and an acceptance with 404 and the problem type `invitation-invalid` when the token does not exist, is malformed, was replaced by a new link, or belongs to an expired, revoked or accepted invitation. The answer SHALL be the same in every case and SHALL reveal nothing about the invitation. The one exception is an invitation that the caller already accepted (INV-14).

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

The system SHALL let a signed-in caller of any access status accept an invitation with its token (IDN-3). The caller needs a token with a verified email equal to the invitation's, ignoring case (INV-13). When no user has the invitation's email, acceptance SHALL do all of the following together or nothing at all: create an active user with the email of the invitation and the first and last name from the token of the caller, link the caller's identity to the user, assign the roles of the invitation, and mark the invitation accepted with the time and the user. The response is 200 with the current user data of the caller (IDN-5), and the caller's next request passes the gate (IDN-3). A role stored on the invitation that the running version does not know is skipped (PERM-5). Accepting is audited (AUD-7).

#### INV-11.1 A new person joins

- GIVEN a pending invitation with the mentor role, and a signed-in identity that is not linked to any user, with the same verified email and a name
- WHEN the identity accepts the invitation
- THEN an active user exists with the email of the invitation and the name from the token, the identity is linked to the user, and the user has the mentor role
- AND the invitation is accepted by the user, with the time
- AND the response is 200 with the data of the user, and the next request with the same token passes the gate

#### INV-11.2 The email is compared ignoring case, and an unknown stored role is skipped

- GIVEN a pending invitation for `Anna@Example.com` with a stored role that the application does not know and the student role
- WHEN an identity with the verified email `anna@example.com` accepts it
- THEN the invitation is accepted, and the user has the student role only

#### INV-11.3 Acceptance needs a signed-in caller

- GIVEN a pending invitation
- WHEN a caller without a token accepts it
- THEN the response is 401

### INV-12 Accepting reactivates or extends an existing user

The system SHALL, when a user with the invitation's email exists, use that user instead of creating one, and SHALL leave their profile unchanged. The caller's identity is linked to the user when it is not linked yet; a user may have several identities. When the user is disabled, acceptance SHALL enable them, clear the reason and the comment of the disabling, and make their roles exactly the roles of the invitation: roles they held that the invitation lacks are removed, so an invitation without roles leaves them with no roles, and a stored role the running version does not know is left untouched (decision 0004). When the user is active, because they were enabled since the invitation was created, acceptance SHALL add the roles of the invitation and SHALL NOT remove any role, so accepting never takes access away from an active user. Accepting is audited (AUD-7).

#### INV-12.1 A disabled user is reactivated with the roles of the invitation

- GIVEN a disabled user with a reason, a comment and a profile, who held the mentor and student roles and a stored role the application does not know, and a pending invitation for their email with the observer role
- WHEN an identity with the verified email, whose token has other names, accepts the invitation
- THEN the same user is active, the reason and the comment are cleared, and the profile is as it was
- AND the user has the observer role, and the unknown stored role is still stored
- AND the history of the user from before is intact (USR-10)

#### INV-12.2 A second identity is linked to the user

- GIVEN a user with one linked identity, and an invitation for their email
- WHEN another identity with the verified email accepts it
- THEN both identities are linked to the user, and an identity that was linked already is not linked twice

#### INV-12.3 An active user only gains roles

- GIVEN an active user with the mentor role, and a pending invitation for their email with the student role
- WHEN the invitation is accepted
- THEN the user has the mentor and the student roles

### INV-13 Accepting needs the invited email

The system SHALL refuse an acceptance when the email in the caller's token is not verified or differs from the invitation's, with 409 and the problem type `invitation-email-mismatch`. Nothing SHALL change and the invitation SHALL stay pending. It SHALL also refuse, with 409 `identity-linked-elsewhere`, a caller whose identity is already linked to a user other than the user who has the invitation's email, including when no user has that email (for example, after the email was changed at the identity provider). Nothing SHALL change then, a warning that names the users involved SHALL be logged, and an identity is never moved from one user to another (IDN-6). A refused acceptance writes no audit entry (AUD-4). The verified email that equals the invitation's is the only protection of a link, because a link is readable (INV-8): a link without the invited person's mailbox grants nothing.

#### INV-13.1 An email that is not verified or differs is refused

- GIVEN a pending invitation, and an identity whose token has the invited email not verified, and another identity with a verified email that differs
- WHEN each accepts the invitation
- THEN each response is 409 `invitation-email-mismatch`
- AND no user, link or role is created and the invitation is still pending

#### INV-13.2 An identity linked to another user is refused

- GIVEN a pending invitation, and an identity with the invited verified email that is linked to another user, or to a user while no user has that email
- WHEN the identity accepts the invitation
- THEN the response is 409 `identity-linked-elsewhere`
- AND nothing changes, no user is created and a warning is logged

### INV-14 Accepting twice

The system SHALL answer an acceptance of an invitation that the caller's own user already accepted with 200 and the data of the user, and SHALL change nothing and write no audit entry (AUD-4), so a retry or a double click is safe. The landing page shows an accepted link as invalid, because it checks the link first (INV-20).

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

The client SHALL show the link "Invitations" in the toolbar, the action "Invite" on the invitations page and the action "Invite" on the users list only to a user who holds `USERS_INVITE`, and SHALL NOT open the invitations page for anyone else (PERM-7). The invitations page is a list page (PLT-9) with the email, roles (or that there are none), status, and who invited and when, a search by email and a status filter. The status is told as text, with who accepted it (linking to their page) or revoked it, and when a pending invitation expires or an expired one expired. A row of a pending invitation offers "Copy link", "Get a new link" and "Revoke" (and the action to send an email, see the invitation emails capability), a row of an expired one "Get a new link" and "Revoke", and a row of an accepted or a revoked one nothing.

#### INV-16.1 Invitations are offered with the permission only

- GIVEN a user with `USERS_INVITE`, and a user with `USERS_READ` and without `USERS_INVITE`
- WHEN each views the toolbar and the users list, and opens the address of the invitations page
- THEN the first sees "Invitations" and "Invite", and the page opens
- AND the second sees neither and is taken to the home page

#### INV-16.2 Rows show the invitation and its actions

- GIVEN a pending, an expired, an accepted and a revoked invitation
- WHEN a user with `USERS_INVITE` opens the page
- THEN each row shows the email, the roles, the status as text with its date or who accepted or revoked it, and who invited it and when
- AND the actions offered follow the status

### INV-17 Copying a link from the list

The client SHALL copy the link of a pending invitation to the clipboard on "Copy link", and say that it did. When the browser does not allow the clipboard, the client SHALL open a view with the link selected, so the user can copy it by hand. When the invitation turns out to be closed, expired or no longer existing, the client SHALL say so, saying for an expired one that a new link is needed, and reload the list; another failure shows a general message and leaves the row as it was.

#### INV-17.1 The link is copied or shown for copying, and a changed invitation is reported

- GIVEN a pending invitation, and in other cases an API answer of 409 `invitation-closed`, 409 `invitation-expired` or 404 to the reading of the link
- WHEN the user chooses "Copy link", in a browser that allows the clipboard and in one that does not
- THEN the link is on the clipboard and the client says it was copied, or a view shows the link selected, with the email and the expiry
- AND for the API answers the client says that the invitation is closed or expired, and the list is reloaded

#### INV-17.2 Copying works in the supported browsers (manual)

- GIVEN a pending invitation in the local environment
- WHEN the user chooses "Copy link" in Chrome and in Safari
- THEN the link is copied in each, and opening it as the invited person accepts the invitation

### INV-18 Revoking and getting a new link from the list

The client SHALL ask for a confirmation before revoking, which says that the link will stop working, and before giving a new link, which says that the previous link will stop working. On confirming, it SHALL revoke the invitation and say so, or give the new link and show it in the link view (INV-19), and update the list; on cancelling, it SHALL do nothing. When the invitation turns out to be closed or no longer exists, the client SHALL say so and reload the list; another failure shows a general message and leaves the row as it was.

#### INV-18.1 Revoking and a new link are confirmed first

- GIVEN a pending invitation
- WHEN the user chooses "Revoke" or "Get a new link", cancels, and in another case confirms
- THEN a confirmation names the email and says the link, or the previous link, will stop working
- AND cancelling sends nothing
- AND confirming revokes the invitation and says so, or shows the new link in the link view and reloads the list when the view closes

### INV-19 The dialog to invite a person and the link view

The client SHALL let a user with `USERS_INVITE` invite a person in a dialog with the email and a choice of roles, none chosen by default, and SHALL tell that a person without roles can sign in but has no access to anything. The dialog checks the email before sending. On success the dialog shows the link view: the link in a field that can be read, a way to copy it, the invited email and the expiry, and the note that the link can be copied again from the invitations list while the invitation is pending. When the person already has access (409 `user-active`), the dialog says so and offers a link to their page. When an invitation for the email exists (409 `invitation-exists`), it says so and offers a link to the invitations page filtered by that email. A field error from the server is shown under its field, and any other failure is shown as a general message. The dialog keeps what the user entered after a failure.

#### INV-19.1 An invitation is created from the entered values

- GIVEN the dialog with an email with spaces around it and two roles chosen
- WHEN the user submits
- THEN the invitation is created with the trimmed email and the two roles
- AND the link view shows the link, the email, the expiry and the note, and does not say that the link is shown only once
- AND an email that is blank, not valid or too long is explained at the field and nothing is sent

#### INV-19.2 A person with access or an existing invitation is reported

- GIVEN the API answers 409 `user-active`, and in another case 409 `invitation-exists`
- WHEN the user submits
- THEN the dialog says that the person already has access, with a link to the page of the user
- AND for the second, that there is already an invitation for the email, with a link to the invitations page filtered by that email
- AND the entered values are kept

### INV-20 The landing page of a link

The client SHALL serve the address of an invitation link to anyone, without sign-in, outside the access screens of IDN-10, and SHALL check the link before showing anything else. It then tells the visitor, in each distinct situation: the link is invalid or has expired, or the page is opened with no link at all, to ask the person who invited for a new link, with no way to sign in (the same when an acceptance answers `invitation-invalid`, and the page then forgets the link); not signed in, the invited email, with a way to sign in and a way to create an account, each sending the visitor to the identity provider with the invited email filled in, after which the invitation is accepted without another click; signed in, the email of the account and the invited email, with a way to accept and a way to use another account, which signs the visitor out and keeps the link; after a successful acceptance, the application opens at the home page, where the access of the new user decides the screen (IDN-10), and the page forgets the link; an email that does not match (409 `invitation-email-mismatch`), both emails and a way to use another account, or, when they are equal ignoring case, that the account's email is not verified and must be verified first; an account of another user (409 `identity-linked-elsewhere`), that it belongs to another MentorHub user and to contact the person who invited, with a way to sign out, which makes the page forget the link; any other failure, that MentorHub is unavailable, with Retry for the step that failed.

The client SHALL remove the token from the address of the landing page as soon as it has read it, so that it does not stay in the address bar or in the browser history, and SHALL NOT send it to the identity provider in a redirect address or in the sign-in state. The page keeps the token only for as long as the visitor needs it: through the round trip to the identity provider, until an acceptance or an invalid answer.

#### INV-20.1 An invalid link is explained

- GIVEN the check answers 404 `invitation-invalid`, or the page is opened with no link and no remembered one
- WHEN a visitor opens the page
- THEN the page says that the link is invalid or has expired and to ask for a new one
- AND it offers no sign-in

#### INV-20.2 A visitor signs in or registers, and is accepted on return

- GIVEN a valid link, a visitor who is not signed in, and the same visitor returning from the identity provider
- WHEN the visitor opens the page, chooses to sign in or to create an account, and returns signed in
- THEN the visitor is sent to the identity provider with the invited email filled in
- AND on return the invitation is accepted without another click, and the application opens at the home page

#### INV-20.3 A refused acceptance is explained

- GIVEN a signed-in visitor on the page
- WHEN the acceptance answers 409 `invitation-email-mismatch` with different emails, with equal emails, or 409 `identity-linked-elsewhere`
- THEN the page shows both emails and offers another account, or says to verify the email first, or says the account belongs to another MentorHub user and to contact the person who invited
- AND the link stays remembered, except after the last, where signing out forgets it

#### INV-20.4 The address and the sign-in request carry no token

- GIVEN a visitor who opens a link with a token
- WHEN the page loads, and the visitor chooses to sign in or to create an account
- THEN the address of the page is the landing address without the token, and the page still works with the link
- AND neither the redirect address nor the sign-in state contains the token

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
