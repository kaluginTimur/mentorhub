# Identity

## Purpose

Who may enter MentorHub and how they are recognized. MentorHub stores no passwords and does not sign people in itself: an external identity provider does, and MentorHub accepts only the tokens of the providers it trusts (decision 0003). A signed-in identity is let in only when it is linked to an active user, and it is linked only through an invitation or the administrator bootstrap. This capability also covers the current user's own data, the screens a person sees according to their access, and signing in and out in the client.

What an active user may do is described by the authorization capability (PERM-3). The shape of errors, the client configuration and the unavailable screen are described by the platform capability (PLT-1, PLT-4, PLT-8).

## Requirements

### IDN-1 Every non-public request needs a valid token

The system SHALL answer 401 with the problem type `unauthorized` and a `WWW-Authenticate` header naming the Bearer scheme to a request without a valid access token, for every operation that is not public. A token is valid only when it is unexpired, correctly signed by a key its issuer publishes, issued by a trusted issuer, and meant for MentorHub (its audience contains the MentorHub API). Which operations are public is described in (PLT-4).

#### IDN-1.1 A request without a token is refused

- GIVEN an operation that is not public
- WHEN a caller sends a request without an access token
- THEN the response is 401 with the problem type `unauthorized`
- AND the response has a `WWW-Authenticate` header for the Bearer scheme

#### IDN-1.2 A token that is malformed, expired or wrongly signed is refused

- GIVEN a token that is not well-formed, or whose lifetime has ended, or that claims a trusted issuer but is signed with a key that issuer does not publish
- WHEN a caller sends it to an operation that is not public
- THEN the response is 401 `unauthorized`

#### IDN-1.3 A token of an untrusted issuer or not meant for MentorHub is refused

- GIVEN a token correctly signed by an issuer that is not trusted, or a token of a trusted issuer whose audience does not contain the MentorHub API
- WHEN a caller sends it to an operation that is not public
- THEN the response is 401 `unauthorized`

### IDN-2 Trusted issuers are configuration

The system SHALL accept tokens only from the issuers it is configured to trust, each with the audience it requires, so that moving to another identity provider needs no change of the application. The system MUST refuse to start without at least one trusted issuer, and the failure message SHALL name the setting. Keys that cannot be obtained from a trusted issuer are answered as described in (PLT-1.3).

#### IDN-2.1 Missing trusted issuers stop the application

- GIVEN a configuration without trusted issuers
- WHEN the application starts
- THEN the start fails
- AND the failure message names the setting of the trusted issuers

### IDN-3 Only active users pass the gate

The system SHALL let a request through to an operation only when the identity in the token is linked to a user whose status is active. The access statuses are:

| Status | Identity | Gate |
|---|---|---|
| `ACTIVE` | linked to an active user | passes (an active user without roles passes too, PERM-2) |
| `NOT_INVITED` | not linked to any user | 403 `not-invited` |
| `DISABLED` | linked to a disabled user | 403 `access-ended` |

The status is read on every request, so disabling or enabling a user applies to their next request with the same token. Two operations admit every signed-in caller, whatever their access: reading the current user (IDN-5) and accepting an invitation (see the invitations capability). A refusal for lack of a permission is a different refusal (PERM-3).

#### IDN-3.1 An identity that is not linked is refused and nothing is created

- GIVEN a signed-in identity that is not linked to any user, and no bootstrap applies to it
- WHEN it calls an operation other than reading the current user
- THEN the response is 403 with the problem type `not-invited`
- AND no user, link or role is created

#### IDN-3.2 A disabled user is refused

- GIVEN a signed-in user whose user is disabled
- WHEN the user calls an operation other than reading the current user
- THEN the response is 403 with the problem type `access-ended`

#### IDN-3.3 A change of status applies to the next request

- GIVEN a user who holds a token that has not yet expired and is disabled, and later enabled
- WHEN the user calls an operation with that token after each change
- THEN the response is 403 `access-ended` after the disabling
- AND the request is not refused by the gate after the enabling

### IDN-4 Identities are linked only through an invitation or the bootstrap

The system SHALL identify a person by the issuer and subject in the token, linked to one user, and SHALL reference the user, never the subject, everywhere else. An identity SHALL be linked to a user only by accepting an invitation (see the invitations capability) or by the administrator bootstrap (IDN-6); signing in with an identity that is not linked creates and links nothing unless the bootstrap applies, whatever its email. The profile (email, first name, last name) is copied from the token when a user is created; after that MentorHub owns it, and a later change at the identity provider does not change it. Development users are the only other way to exist without an invitation (IDN-7).

#### IDN-4.1 An email that matches a user is not enough

- GIVEN a user with the email `anna@example.com` and a verified identity with the same email that is not linked to any user
- WHEN that identity signs in and calls an operation
- THEN the response is 403 `not-invited`
- AND the identity is not linked to the user

#### IDN-4.2 A change at the identity provider does not change the profile

- GIVEN an active user whose first name, last name or email has changed at the identity provider since the user was created
- WHEN the user signs in and reads the current user
- THEN the response has the profile as it was stored

### IDN-5 The current user

The system SHALL let every signed-in caller read their own access status and, for an active user, their own data: id (the user's own, never the subject of the token), email, first name, last name, phone, roles, and the union of the permissions of the roles (PERM-2). A caller who is not invited or is disabled SHALL learn only their status and no user. A caller reads only their own data. Without a token the operation answers 401 (IDN-1).

#### IDN-5.1 An active user reads their data

- GIVEN an active user, with or without roles
- WHEN the user reads the current user
- THEN the status is `ACTIVE`
- AND the response has the user's id, email, first name, last name, phone, roles and permissions, which are empty for a user without roles

#### IDN-5.2 A caller without access learns only the status

- GIVEN a signed-in identity that is not linked to any user, or a disabled user
- WHEN it reads the current user
- THEN the status is `NOT_INVITED` or `DISABLED` respectively
- AND the response has no user

### IDN-6 The administrator bootstrap

The system SHALL let the person who controls one configured email address become the first administrator, and recover the installation when no administrator is left, without anyone touching the database (decision 0003). The rule applies only when a bootstrap address is configured, the signed-in identity's token has an email that the provider reports as verified and that equals the bootstrap address (ignoring case), and no active user has the administrator role. A disabled administrator does not count as an active one. It is checked on every authenticated request. When it applies, in one step the system SHALL create the user with that email if there is none (with the profile from the token), link the identity to the user if it is not linked, enable the user (clearing the reason and comment of an earlier disabling) and give the user the administrator role. The rule never moves an identity from one user to another. Each application that changed something is written to the application log as a warning with the user id and is audited (AUD-7); an application that changes nothing is neither logged nor audited (AUD-7.2). Whoever controls the bootstrap mailbox has the power of the rule (see Known limitations).

#### IDN-6.1 The first administrator is created

- GIVEN a bootstrap address, no user at all, and an identity that is not linked, with a verified email equal to the address
- WHEN the identity signs in and calls an operation
- THEN a user with that email and the profile from the token exists, active and with the administrator role
- AND the identity is linked to the user
- AND the request is served as that administrator
- AND one warning with the user id is logged
- AND the bootstrap is audited (AUD-7.1)

#### IDN-6.2 An existing user is recovered

- GIVEN no active administrator, and a user with the bootstrap email who is disabled or lacks the administrator role, with a reason and comment of a disabling if disabled
- WHEN an identity with a verified email equal to the address, linked to that user or not linked at all, signs in and calls an operation
- THEN the identity is linked to that user and no second user is created
- AND the user is active, has the administrator role, and has no reason or comment of a disabling

#### IDN-6.3 An active administrator switches the rule off for an identity that is not linked

- GIVEN an active administrator, and an identity that is not linked, with a verified email equal to the bootstrap address
- WHEN the identity signs in and calls an operation
- THEN the response is 403 `not-invited`
- AND nothing is created

#### IDN-6.4 An active administrator switches the rule off for a linked user

- GIVEN an active administrator, and a disabled user with the bootstrap email whose identity is linked
- WHEN the identity signs in with a verified email equal to the address
- THEN the user stays disabled and keeps their roles

#### IDN-6.5 An email that is not verified grants nothing

- GIVEN no active administrator, and an identity whose email equals the bootstrap address but is not verified
- WHEN the identity signs in and calls an operation
- THEN the response is 403 `not-invited`
- AND nothing is created

#### IDN-6.6 Another email, or no configured address, grants nothing

- GIVEN no active administrator, and an identity with a verified email that differs from the bootstrap address, or no bootstrap address is configured
- WHEN the identity signs in and calls an operation
- THEN the response is 403 `not-invited`
- AND nothing is created

#### IDN-6.7 An identity linked to another user is never moved

- GIVEN no active administrator, and an identity that is linked to a user whose email is not the bootstrap address, with a verified email equal to the address
- WHEN the identity signs in and calls an operation
- THEN the identity stays linked to its user
- AND that user gets nothing
- AND a warning is logged
- AND no user with the bootstrap email is created or changed

#### IDN-6.8 A repeated sign-in changes nothing

- GIVEN the bootstrap has already made the user an active administrator with the identity linked
- WHEN the identity signs in again and calls an operation
- THEN nothing is changed, logged or audited (AUD-7.2)

#### IDN-6.9 Parallel first requests create one administrator

- GIVEN a bootstrap address, no user, and several first requests of the bootstrap identity that arrive at the same time
- WHEN the requests are served
- THEN all of them succeed
- AND exactly one user, one link of the identity and one administrator role exist
- AND exactly one warning is logged

### IDN-7 Development users

The system SHALL, when it runs in development mode, create a set of development users for every access status that match accounts in the local identity provider, so a developer can sign in as each of them without manual setup. Development users are an exception to invitation-only registration (decision 0003): they SHALL NOT exist outside development mode. On every start in development mode the system SHALL bring each development user back to its configured status and exactly its configured roles, and SHALL leave the profile of an existing one unchanged. One local account is deliberately not linked to any user, to try an identity that is not invited.

#### IDN-7.1 Every development user has its status

- GIVEN the application starts in development mode
- WHEN each development user signs in and reads the current user
- THEN the administrator, the mentor and the student are `ACTIVE` with their role, the user without roles is `ACTIVE` with no roles, and the disabled user is `DISABLED`
- AND the local account that is not linked has the status `NOT_INVITED`

#### IDN-7.2 Development users exist only in development mode

- GIVEN the application starts without development mode
- WHEN it has started
- THEN no development user is created

#### IDN-7.3 A restart restores the configured access

- GIVEN a development user whose status or roles were changed since the last start
- WHEN the application starts in development mode
- THEN the user has the configured status and exactly the configured roles

#### IDN-7.4 A developer can sign in as each user (manual)

- GIVEN a clean checkout and the local environment started as the README describes
- WHEN the developer signs in as each development user
- THEN the screen that matches the user's access is shown

### IDN-8 Rules for emails at the identity provider

The identity provider SHALL be set up so that an email in a token that it reports as verified has been confirmed by its owner, because the invitation and the bootstrap rely on it. Self-registration SHALL require the person to confirm the email address, and one email address SHALL be registrable only once. Tokens issued to the MentorHub client SHALL carry the audience of the MentorHub API (IDN-1).

#### IDN-8.1 A new account confirms its email and an email is registered once

- GIVEN the local identity provider
- WHEN a person registers an account, and another person tries to register the same email
- THEN the email is reported as verified only after the first person confirms it
- AND the second registration is refused

#### IDN-8.2 A token for the client carries the API audience

- GIVEN a person signed in to the MentorHub client at the local identity provider
- WHEN the client receives the access token
- THEN the token's audience contains the MentorHub API

### IDN-9 Signing in and out in the client

The client SHALL send a visitor who is not signed in to the identity provider (authorization code with PKCE), so nothing is visible before signing in, and SHALL bring the visitor back to the page they opened. A sign-in callback that returns without a session (for example when the flow was completed in another tab) or with the error `temporarily_unavailable` SHALL be retried once, silently. Any other error, and a second failed callback, SHALL show the unavailable message (PLT-8). A 401 from a non-public API operation SHALL start sign-in again once, keeping the current page; a 401 right after that fresh sign-in SHALL show the unavailable message instead of signing in again. A sign-in that cannot start is described in (PLT-8.1). Signing out ends the person's session at the identity provider and returns them to the application, which asks them to sign in again.

#### IDN-9.1 A visitor is sent to sign in and returns to the page they opened

- GIVEN a visitor who is not signed in
- WHEN the visitor opens an application page and returns from the identity provider with a session
- THEN the page they opened is shown

#### IDN-9.2 A callback without a session or with a temporary error is retried once

- GIVEN a callback that returns without a session, or with the error `temporarily_unavailable`
- WHEN the client handles the callback
- THEN sign-in is started again once, without showing an error

#### IDN-9.3 Any other error, and a second failed callback, are shown

- GIVEN a callback that returns another error such as `access_denied`, or a retried callback that fails as well
- WHEN the client handles the callback
- THEN the unavailable message is shown
- AND sign-in is not started again

#### IDN-9.4 A rejected token starts sign-in again once

- GIVEN a signed-in user on a page
- WHEN a non-public API operation answers 401
- THEN sign-in is started again once
- AND after it the user returns to the same page

#### IDN-9.5 A token rejected right after a fresh sign-in shows the unavailable message

- GIVEN a user who has just signed in again after a 401
- WHEN the API answers 401 again before accepting any request
- THEN the unavailable message is shown
- AND sign-in is not started again

#### IDN-9.6 Signing out ends the session and asks to sign in again (manual)

- GIVEN a signed-in user
- WHEN the user chooses Sign out
- THEN the session at the identity provider ends
- AND the browser returns to the application
- AND the application asks the user to sign in

#### IDN-9.7 A sign-out that fails does not leave the user signed in

- GIVEN a signed-in user
- WHEN signing out at the identity provider fails
- THEN the tokens held by the client are removed
- AND the unavailable message is shown

### IDN-10 Screens follow the access status

The client SHALL reload the current user on every navigation and show the screen that matches the user's access: an active user with at least one role sees the application, and everyone else sees an access screen with a Sign out action and no menu of the application. The access screens are: access by invitation only (not invited), access has ended (disabled), and access is being set up (active without roles); each tells the person what to do next. An access route is open only to the user it describes: an active user with roles who opens one is taken to the home page, and a user in any other state who opens any other route is taken to the screen for their state. A path that does not exist leads to the home page. When the API refuses a request with `not-invited` or `access-ended`, the client SHALL reload the current user and show the screen for the new state. An access screen SHALL recheck access when the browser tab becomes visible again or another tab of the same browser reports that access changed. When the current user cannot be loaded, the unavailable message is shown (PLT-8).

#### IDN-10.1 A user without access sees the screen for their state

- GIVEN a signed-in identity that is not linked, a disabled user, or an active user without roles
- WHEN the application opens
- THEN the screen for the user's state is shown, explaining the state and what to do
- AND the screen offers Sign out
- AND the application's menu is not shown

#### IDN-10.2 Routes follow the state

- GIVEN an active user with roles who opens an access screen's address, a user in an access state who opens any other page, or a signed-in user who opens a path that does not exist
- WHEN the page is opened
- THEN the home page is shown to the first and the last
- AND the screen for their state is shown to the second

#### IDN-10.3 A change of access while working is shown

- GIVEN a user working in the application whose access has changed (disabled, or roles changed by an administrator)
- WHEN the user navigates or the user's next request is answered 403 `access-ended`
- THEN the screen for the new state is shown, or the permissions the client uses are those after the change

#### IDN-10.4 An access screen notices a change of access

- GIVEN an access screen is shown
- WHEN the browser tab becomes visible again, or another tab of the same browser reports that access changed
- THEN the client reloads the current user and shows the screen for the new state, which is the application when the user is now active with roles

### IDN-11 The application shell

The client SHALL show an active user with roles a toolbar with the application name, the links the user's permissions allow (PERM-7), and an account menu. The account menu SHALL show the user's name and email, and offer Sign out; its other entries are described by the capabilities that own them. The name SHALL be the first and last name when both are set, the one that is set when only one is, and the email when neither is; every screen that shows a person's name by this rule refers to this requirement. The home page SHALL greet the user by first name (or the name above when there is none) and show the application name and version (PLT-4.1).

#### IDN-11.1 The name is built from the names that are set

- GIVEN active users with a first and a last name, with only one of them, and with neither
- WHEN the shell is shown
- THEN the account menu shows the first and last name, the one that is set, or the email respectively

#### IDN-11.2 The account menu and the home page

- GIVEN an active user with roles and a first name
- WHEN the user opens the account menu and the home page
- THEN the menu shows the user's name and email, and offers Sign out
- AND the home page greets the user by first name and shows the application name and version

## Known limitations

- Whoever controls the bootstrap mailbox, with the email verified at the identity provider, can become an administrator whenever no active administrator exists. The bootstrap address must belong to a person responsible for the installation, and the identity provider's email verification settings are part of the security of the installation.
- Disabling a user takes effect on their next request, not on a page already open: the user sees the change on their next action.
- A change of a person's name or email at the identity provider never reaches MentorHub. Changes are made in MentorHub (see the user administration capability).

## Out of scope

- Corporate single sign-on, identity brokering and provisioning users with roles mapped from the provider. Roles in a token are never used (PERM-4).
- The deployment configuration of the identity provider in production; only the rules it must satisfy (IDN-8) are stated here.
- Registration, invitations and their landing page (see the invitations capability).
- Enrollments, the relation between a student and a mentor, and revoking access after graduation.
- Rules for personal data such as consent, photos and retention.
