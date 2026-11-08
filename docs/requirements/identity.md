# Identity

## Purpose

Who may enter MentorHub and how they are recognized. MentorHub stores no passwords and does not sign people in itself: an external identity provider does, and MentorHub accepts only the tokens of the providers it trusts (decision 0003). A signed-in identity is let in only when it is linked to an active user, and it is linked only through an invitation or the administrator bootstrap, or, in development mode only, by the development users and the sample center data (DEV-1, DEV-2). This capability also covers the current user's own data, the screens a person sees according to their access, and signing in and out in the client.

What an active user may do is described by the authorization capability (PERM-3). The shape of errors, the client configuration and the unavailable screen are described by the platform capability (PLT-1, PLT-4, PLT-8). Development users are described by the development mode capability (DEV-1). The API contract holds the access statuses, the fields of the current user and the shape of the refusals.

## Requirements

### IDN-1 Every non-public request needs a valid token

The system SHALL answer 401 `unauthorized` to a request without a valid access token for every operation that is not public (PLT-4). A token is valid only when it is unexpired, correctly signed by a key its issuer publishes, issued by a trusted issuer, and meant for MentorHub.

#### IDN-1.1 A request without a token is refused

- GIVEN an operation that is not public
- WHEN a caller sends a request without an access token
- THEN the response is 401 with the problem type `unauthorized`

#### IDN-1.2 A token that is malformed, expired or wrongly signed is refused

- GIVEN a token that is not well-formed, or whose lifetime has ended, or that claims a trusted issuer but is signed with a key that issuer does not publish
- WHEN a caller sends it to an operation that is not public
- THEN the response is 401 `unauthorized`

#### IDN-1.3 A token of an untrusted issuer or not meant for MentorHub is refused

- GIVEN a token correctly signed by an issuer that is not trusted, or a token of a trusted issuer whose audience does not contain the MentorHub API
- WHEN a caller sends it to an operation that is not public
- THEN the response is 401 `unauthorized`

### IDN-2 Trusted issuers are configuration

The system SHALL accept tokens only from the configured trusted issuers, each with the audience it requires, and MUST refuse to start without at least one.

#### IDN-2.1 Missing trusted issuers stop the application

- GIVEN a configuration without trusted issuers
- WHEN the application starts
- THEN the start fails

### IDN-3 Only active users pass the gate

The system SHALL let a request through only when the identity in the token is linked to an active user; otherwise it answers 403 `not-invited` (not linked) or `access-ended` (disabled). The status is read on every request. Reading the current user (IDN-5) and accepting an invitation admit every signed-in caller. A refusal for lack of a permission is different (PERM-3).

#### IDN-3.1 An identity that is not linked is refused and nothing is created

- GIVEN an active administrator, so that no bootstrap applies (IDN-6), and a signed-in identity that is not linked to any user
- WHEN it calls an operation other than reading the current user
- THEN the response is 403 with the problem type `not-invited`
- AND no user, link or role is created

#### IDN-3.2 A disabled user is refused

- GIVEN a signed-in user whose user is disabled
- WHEN the user calls an operation other than reading the current user
- THEN the response is 403 with the problem type `access-ended`

#### IDN-3.3 Disabling applies to the next request

- GIVEN a user who holds a token that has not yet expired and is then disabled
- WHEN the user calls an operation with that token
- THEN the response is 403 `access-ended`

#### IDN-3.4 Enabling applies to the next request

- GIVEN a disabled user who holds a token that has not yet expired and is then enabled
- WHEN the user calls an operation with that token
- THEN the request is not refused by the gate

### IDN-4 Outside development mode, identities are linked only through an invitation or the bootstrap

The system SHALL link an identity (issuer and subject) to a user only by accepting an invitation or by the administrator bootstrap (IDN-6), whatever the email of the identity, or, in development mode only, by the development users and the sample center data (DEV-1, DEV-2). The profile is copied from the token when a user is created and is then owned by MentorHub.

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

The system SHALL let every signed-in caller read their own access status and, for an active user, their own data and permissions (PERM-2). A caller who is not invited or is disabled learns only the status. The user's id is the user's own, never the subject of the token.

#### IDN-5.1 An active user reads their data

- GIVEN an active user, with or without roles
- WHEN the user reads the current user
- THEN the status is `ACTIVE`
- AND the response has the user's data, roles and permissions, which are empty for a user without roles

#### IDN-5.2 A caller without access learns only the status

- GIVEN a signed-in identity that is not linked to any user, or a disabled user
- WHEN it reads the current user
- THEN the status is `NOT_INVITED` or `DISABLED` respectively
- AND the response has no user

### IDN-6 The administrator bootstrap

The system SHALL let the person who controls the configured bootstrap email become an administrator when no active user has the administrator role, without anyone touching the database (decision 0003). The address is compared ignoring case, and a change is audited (AUD-7). Whoever controls the bootstrap mailbox has this power (see Known limitations).

#### IDN-6.1 The first administrator is created

- GIVEN a bootstrap address, no user at all, and an identity that is not linked, with a verified email equal to the address
- WHEN the identity signs in and calls an operation
- THEN a user with that email and the profile from the token exists, active and with the administrator role
- AND the identity is linked to the user
- AND the request is served as that administrator

#### IDN-6.2 An existing user is recovered

- GIVEN no active administrator, and a user with the bootstrap email who is disabled or lacks the administrator role
- WHEN an identity with a verified email equal to the address, linked to that user or not linked at all, signs in and calls an operation
- THEN the identity is linked to that user and no second user is created
- AND the user is active and has the administrator role

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
- AND no user with the bootstrap email is created or changed

#### IDN-6.8 A repeated sign-in changes nothing

- GIVEN the bootstrap has already made the user an active administrator with the identity linked
- WHEN the identity signs in again and calls an operation
- THEN nothing is changed (AUD-7.2)

#### IDN-6.9 Parallel first requests create one administrator

- GIVEN a bootstrap address, no user, and several first requests of the bootstrap identity that arrive at the same time
- WHEN the requests are served
- THEN all of them succeed
- AND exactly one user, one link of the identity and one administrator role exist

#### IDN-6.10 Recovery clears the reason and comment of a disabling

- GIVEN no active administrator, and a user with the bootstrap email who was disabled with a reason and a comment
- WHEN the identity of that user signs in with a verified email equal to the address
- THEN the user is active and has no reason or comment of a disabling

### IDN-8 Rules for emails at the identity provider

The identity provider SHALL report an email as verified only after its owner confirmed it, because the invitation and the bootstrap rely on it, SHALL allow one email address to be registered only once, and SHALL issue tokens to the MentorHub client with the audience of the MentorHub API (IDN-1).

#### IDN-8.1 A new account confirms its email

- GIVEN the local identity provider
- WHEN a person registers an account
- THEN the email is reported as verified only after the person confirms it

#### IDN-8.2 A token for the client carries the API audience

- GIVEN a person signed in to the MentorHub client at the local identity provider
- WHEN the client receives the access token
- THEN the token's audience contains the MentorHub API

#### IDN-8.3 An email is registered once

- GIVEN the local identity provider and an account with an email
- WHEN another person tries to register the same email
- THEN the registration is refused

### IDN-9 Signing in and out in the client

The client SHALL sign in a visitor through the identity provider (authorization code with PKCE) and return them to the page they opened, SHALL retry a failed sign-in or a rejected token once, and then SHALL show the unavailable message (PLT-8). Signing out ends the session at the identity provider.

#### IDN-9.1 A callback without a session or with a temporary error is retried once

- GIVEN a callback that returns without a session, or with the error `temporarily_unavailable`
- WHEN the client handles the callback
- THEN sign-in is started again once, without showing an error

#### IDN-9.2 Any other error, and a second failed callback, are shown

- GIVEN a callback that returns another error such as `access_denied`, or a retried callback that fails as well
- WHEN the client handles the callback
- THEN the unavailable message is shown
- AND sign-in is not started again

#### IDN-9.3 A rejected token starts sign-in again once

- GIVEN a signed-in user on a page
- WHEN a non-public API operation answers 401
- THEN sign-in is started again once
- AND after it the user returns to the same page

#### IDN-9.4 A token rejected right after a fresh sign-in shows the unavailable message

- GIVEN a user who has just signed in again after a 401
- WHEN the API answers 401 again before accepting any request
- THEN the unavailable message is shown
- AND sign-in is not started again

#### IDN-9.5 Signing out ends the session and asks to sign in again (manual)

- GIVEN a signed-in user
- WHEN the user chooses Sign out
- THEN the session at the identity provider ends
- AND the application asks the user to sign in

#### IDN-9.6 A sign-out that fails does not leave the user signed in

- GIVEN a signed-in user
- WHEN signing out at the identity provider fails
- THEN the tokens held by the client are removed
- AND the unavailable message is shown

### IDN-10 Screens follow the access status

The client SHALL show an active user with at least one role the application, and everyone else an access screen for their state: invitation only, access ended, or access being set up. An address that does not fit the state or does not exist SHALL lead to what the state allows.

#### IDN-10.1 A user without access sees the screen for their state

- GIVEN a signed-in identity that is not linked, a disabled user, or an active user without roles
- WHEN the application opens
- THEN the screen for the user's state is shown, explaining the state and what to do
- AND the screen offers Sign out
- AND the application's menu is not shown

#### IDN-10.2 A change of access is shown on the next navigation

- GIVEN a user working in the application whose access has changed (disabled, or roles changed by an administrator)
- WHEN the user navigates
- THEN the screen for the new state is shown, or the permissions the client uses are those after the change

#### IDN-10.3 An access screen notices a change of access

- GIVEN an access screen is shown
- WHEN the browser tab becomes visible again, or another tab of the same browser reports that access changed
- THEN the client reloads the current user and shows the screen for the new state, which is the application when the user is now active with roles

#### IDN-10.4 A refusal because access ended shows the screen for the new state

- GIVEN a user working in the application whose access has ended or never existed
- WHEN the API refuses a request with `not-invited` or `access-ended`
- THEN the screen for the user's state is shown

### IDN-11 The application shell

The client SHALL show an active user with roles a toolbar with the application name, the links the user's permissions allow (PERM-7), and an account menu with the user's name and email and Sign out. Every screen that shows a person's name SHALL build it as IDN-11.1 says. The home page SHALL greet the user and show the application name and version (PLT-4.1).

#### IDN-11.1 The name is built from the names that are set

- GIVEN active users with a first and a last name, with only one of them, and with neither
- WHEN the shell is shown
- THEN the account menu shows the first and last name, the one that is set, or the email respectively

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
