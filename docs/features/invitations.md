# Invitations

Status: implemented
Stage: tracking core (users, roles, audit log)
Related: ADR 0001, ADR 0002, ADR 0003, ADR 0004, ADR 0005, ADR 0006, ADR 0007, `docs/features/identity-foundation.md`, `docs/features/user-administration.md`. Followed by `invitation-emails` (Mailpit and invitation emails).
Revised: 2026-10-20 by `invitation-links`: the link can be copied again while the invitation is pending (Goal, Scope, AC-1, AC-19, AC-20, Visibility, UI, Decisions). The Design section describes the plaintext token storage of `invitation-links`.

## 1. Requirements

### Goal

Registration is by invitation only (ADR 0003), but today the only ways in are the administrator bootstrap and the development users. Administrators need to let new people in without touching the database. They create an invitation for an email, optionally with roles, and pass the link on however they like. The person signs in or registers in Keycloak with that email, opens the link, and is linked to a MentorHub user. Inviting a disabled user reactivates the same user. Invitations are audited, so "who let this person in?" always has an answer.

### Scope

- Creating an invitation for an email with optional roles. The link is shown when the invitation is created or a new link is issued, and can be copied again from the list while the invitation is pending (revised by `invitation-links`).
- A list of invitations with their status (pending, expired, accepted, revoked), search and a status filter.
- Revoking an open invitation, and issuing a new link for it (which also renews the expiry).
- A public landing page for the link, which leads to sign-in or registration in Keycloak and then accepts the invitation.
- Acceptance:
  - creates the user, or reactivates the existing user with that email;
  - links the caller's identity;
  - adds the invitation's roles.
- Audit entries for invitations, and a history entry on the user who accepted.

### Roles and scenarios

#### Administrator

- S1. As an administrator, I invite a person by email and optionally pick their roles, then copy the link and send it to them, so they can join MentorHub.
- S2. As an administrator, I see all invitations with their status, who created them and when, and who accepted them, so I know who is still expected.
- S3. As an administrator, I revoke an invitation that was sent by mistake or is no longer wanted, so its link stops working.
- S4. As an administrator, I get a new link for an invitation whose link was lost or has expired, so I don't have to create it again.
- S5. As an administrator, I see on a user's page when and through whose invitation they joined or were reactivated.

#### Invited person

- S6. As an invited person without a MentorHub account, I open the link and see that I am invited and with which email. I create an account in Keycloak or sign in with an existing one (Google, email and password), and I land in MentorHub with the access the invitation gives.
- S7. As an invited person who is already signed in, I open the link, see which account I am signed in with, and accept or switch to another account.
- S8. As an invited person signed in with an account whose email differs from the invitation's, I am told to sign in with the invited email, and nothing changes.
- S9. As a former user (disabled) who is invited again, I accept and get back into the same account, with my data and earlier history intact.

### Acceptance criteria

Each criterion is marked **auto** (automated tests) or **manual** (checked by hand in the local environment).

Creating and listing:

- AC-1 (S1, auto): Given an administrator, when they call `POST /api/v1/invitations` with an email and a role set (possibly empty), then:
  - the response is 201 with the invitation (status `PENDING`, expiry = now + the configured lifetime, 7 days by default) and the `link` `<app-url>/invite/<token>`;
  - the link appears in this response, in the response that issues a new link (AC-7) and, for a pending invitation, in `GET /api/v1/invitations/{id}/link` (`invitation-links` AC-1); no other response contains it;
  - an audit entry `INVITATION_CREATED` is written.
- AC-2 (S1, auto): The email is validated as follows, and an invalid request responds 400 `validation-failed` with an error per field, creating nothing:
  - it is trimmed, must be a valid address, and is at most 254 characters;
  - it is stored as entered after trimming and compared ignoring case;
  - every role must be a `Role` value.
- AC-3 (S1, auto): Given the email belongs to an active user (ignoring case), then the response is 409 `user-active` with the user's id in the `userId` extension, and nothing is created. Given an open invitation (pending or expired) for the email exists, then the response is 409 `invitation-exists` with its id in the `invitationId` extension. An email of a disabled user can be invited (S9).
- AC-4 (S2, auto): Given invitations exist, when an administrator calls `GET /api/v1/invitations`, then the response is a page as in user administration:
  - 25 per page by default and at most 100, sorted by creation time, newest first, then by id;
  - each item has the id, email, roles, status, creation time, expiry, who created it, and when relevant when and by whom it was accepted or revoked;
  - `q` filters by email (contains, case-insensitive, `%`, `_` and `\` match themselves), and `status` filters by status;
  - invalid parameters respond 400 `validation-failed` as in user administration (AC-3 there), except that `q` may be up to 254 characters after trimming, not 100: the `invitation-exists` link opens `/invitations?q=<email>`, and an email may be up to 254 characters (AC-2), so a shorter limit would break that link for long emails.
- AC-5 (S1–S4, auto): Given a caller without `USERS_INVITE` (any role other than ADMIN, or no roles), when they call any invitation management operation (create, list, revoke, new link), then the response is 403 `forbidden`.

Revoking and new links:

- AC-6 (S3, auto): Given an open invitation (pending or expired), when an administrator revokes it, then its status is `REVOKED` with the time and the administrator, an audit entry `INVITATION_REVOKED` is written, and its link is invalid (AC-9). Given an accepted or revoked invitation, then the response is 409 `invitation-closed` and nothing changes. An unknown id responds 404 `not-found`.
- AC-7 (S4, auto): Given an open invitation, when an administrator calls `POST /api/v1/invitations/{id}/link`, then:
  - a new token replaces the old one and the expiry becomes now + the lifetime;
  - the response contains the invitation and the new `link`;
  - the old link is invalid;
  - an audit entry `INVITATION_LINK_REISSUED` is written.

  Given an accepted or revoked invitation, then the response is 409 `invitation-closed`.

Landing and acceptance:

- AC-8 (S6, S7, auto): Given a valid link, when anyone, signed in or not, calls `POST /api/v1/invitations/preview` with the token, then the response is 200 with the invited email and the expiry. The endpoint is public and ignores any bearer token.
- AC-9 (S6, auto): Given a token that does not exist, is malformed, was replaced by a new link, or belongs to an expired, revoked or accepted invitation, then preview and accept both respond 404 `invitation-invalid`. The response is the same in every case and reveals nothing about the invitation. (Exception: AC-14.)
- AC-10 (S6, auto): Given a signed-in caller whose identity is not linked to a user, whose token has `email_verified = true` and an email equal to the invitation's (ignoring case), and no user with that email exists, when they call `POST /api/v1/invitations/accept` with the token, then in one transaction:
  - an active user is created with the email from the invitation and the first and last name from the token;
  - the identity is linked to that user;
  - the invitation's roles are assigned;
  - the invitation becomes `ACCEPTED` with the time and the user;
  - the audit entry from AC-16 is written.

  The response is 200 with `Me`, and the caller's next request passes the gate.
- AC-11 (S9, auto): Given the same as AC-10, but a disabled user with that email exists, then:
  - that same user is enabled, and the disable reason and comment are cleared;
  - the identity is linked if it is not linked yet (a user may have several identities);
  - the user's roles become exactly the invitation's: roles they held before being disabled and the invitation lacks are removed, so an empty invitation leaves them with no roles (user decision). Unknown stored roles are left untouched (ADR 0004);
  - the profile stays unchanged.

  Given that the user has become active since the invitation was created, the identity is linked if needed and the invitation's roles are added; no role is removed, so acceptance never takes access away from an active user.
- AC-12 (S8, auto): Given the token's email is not verified or differs from the invitation's email, when the caller accepts, then the response is 409 `invitation-email-mismatch`, nothing changes, and the invitation stays pending.
- AC-13 (auto): Given the caller's identity is already linked to a user other than the user with the invitation's email (for example, the email was changed at the identity provider), then the response is 409 `identity-linked-elsewhere`, nothing changes, and a warning with both user ids is logged. An identity is never moved between users (as in ADR 0005).
- AC-14 (auto): Given an invitation already accepted by the user the caller's identity is linked to (read inside the accepting transaction, so it holds for a double click on a first-time acceptance), when the caller accepts it again, then the response is 200 with `Me` and nothing changes. This is an API guarantee for retries; the landing page runs preview first and shows an accepted link as invalid.
- AC-15 (auto): Concurrency:
  - given two parallel accepts of one token, exactly one changes anything; the other responds 200 (AC-14) or 404 `invitation-invalid`, never 500;
  - given an accept and a revoke or a new link of the same invitation in parallel, one of them wins and the other gets its documented error;
  - given two parallel creates for one email, one succeeds and the other responds 409 `invitation-exists`.
- AC-16 (S5, auto): Given an acceptance, then an audit entry `USER_INVITATION_ACCEPTED` is written about the user, with the user as the actor. Its details are:
  - the invitation id;
  - the administrator who created it;
  - whether the user was created, the identity linked and the user enabled;
  - the roles added and the roles removed.

  `GET /api/v1/users/{id}/history` shows it.
- AC-17 (auto): The accept endpoint requires authentication (no token: 401) and admits callers in every access status (not invited, disabled, active), bypassing the gate the way `GET /api/v1/me` does. A stored role on an invitation that the code does not know (ADR 0004) is skipped on acceptance and on reads.

UI:

- AC-18 (S2, auto): Given a user with `USERS_INVITE`, then:
  - the toolbar shows "Invitations";
  - the invitations page and the users list show "Invite".

  Given a user without it, these are not rendered, and opening `/invitations` redirects to `/`.
- AC-19 (S1, S4, auto): The invite dialog validates the email and lets the administrator pick roles.
  - After creating, the dialog shows the link with "Copy link" and the note that it can be copied again from the invitations list while the invitation is pending.
  - The same view appears after "Get a new link".
  - On 409 `user-active` it shows "This person already has access." with a link to their user page; on 409 `invitation-exists`, "There is already an invitation for this email." with a link to that invitation in the list.
  - On 400, field errors show under their fields.
- AC-20 (S2, S3, auto): The invitations page shows the loading, empty (with and without filters) and error states, and search, status filter and page are kept in the URL. Its row actions depend on the status:
  - pending: "Copy link", "Get a new link" and "Revoke" (after a confirmation); expired: "Get a new link" and "Revoke";
  - accepted: the accepting user's name links to their page;
  - revoked: no actions.
- AC-21 (S6–S9, auto): The landing page `/invite/<token>` works without sign-in and shows:
  - loading, then either the invitation or "This invitation link is invalid or has expired.";
  - not signed in: "Sign in" and "Create an account", both opening Keycloak with the invited email prefilled;
  - after returning from Keycloak: the invitation is accepted without another click;
  - already signed in: the account's email with "Accept" and "Use another account" (signs out and back to this page);
  - on 409 `invitation-email-mismatch`: the invited email and "Use another account";
  - on 409 `identity-linked-elsewhere`: "This account already belongs to another MentorHub user. Contact the person who invited you.";
  - an error state with "Retry".

  After a successful accept, the application opens `/`, and the access guard routes as usual (for example, to "Your access is being set up" for an invitation without roles).
- AC-22 (S5, auto): Given a `USER_INVITATION_ACCEPTED` entry, then the user page history shows "Joined by invitation from <name>", or "Reactivated by invitation from <name>" when the user was enabled. The sentence continues with "; roles added: Mentor, Student" and "; roles removed: Administrator" when roles were added or removed.
- AC-23 (manual): Given the local environment, when `admin@mentorhub.local` invites `stranger@mentorhub.local` with Mentor, copies the link, signs out and opens it, then:
  - signing in as the stranger leads to the home page with mentor access;
  - the invitation shows as accepted;
  - the stranger's user page shows the history entry.

  Opening the same link again shows the invalid link message.

### Access matrix

| Action | admin | coordinator | mentor | student | observer | active, no roles | disabled | not invited | not signed in |
|---|---|---|---|---|---|---|---|---|---|
| Create, list, revoke, issue a new link | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ (gate) | ✗ (gate) | 401 |
| Preview a link (`POST /api/v1/invitations/preview`) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Accept a link (`POST /api/v1/invitations/accept`) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 401 |

- The management operations require the new permission `USERS_INVITE`, which only ADMIN gets for now (user decision). Coordinators can be given it later without changing any check.
- Inviting is not limited to roles the inviter has. Only administrators invite, and they can assign any role on the user page anyway.
- Preview and accept need no permission: the token is the authorization, and accept also requires the email match (AC-12).

### Visibility

| Data | Who sees it |
|---|---|
| Invitations (email, roles, status, times, who created, revoked or accepted) | Holders of `USERS_INVITE` (administrators) |
| The link (token) | Holders of `USERS_INVITE`, while the invitation is pending (in the create and new-link responses and through `GET /api/v1/invitations/{id}/link`). The token is stored in plaintext (`invitation-links`). |
| An invitation's email and expiry | Anyone holding a valid link (preview) |
| The acceptance in a user's history | Administrators (`USERS_READ`), as all user history |

Audit entries about invitations (subject type `INVITATION`) have no read view in this feature: the list shows who created, revoked and accepted each invitation from the `invitations` table. The audit log keeps them for a later view.

### Audit

| Action | Subject | Actor | Details |
|---|---|---|---|
| `INVITATION_CREATED` | `INVITATION` | the administrator | email, roles |
| `INVITATION_LINK_REISSUED` | `INVITATION` | the administrator | new expiry |
| `INVITATION_REVOKED` | `INVITATION` | the administrator | none |
| `USER_INVITATION_ACCEPTED` | `USER` | the accepting user | invitation id, inviter id, `userCreated`, `identityLinked`, `enabled`, roles added, roles removed |

A rejected acceptance (invalid token, mismatch, linked elsewhere) writes no audit entry; AC-13 logs a warning.

### Out of scope

- Sending the invitation by email, Mailpit, and resending by email (feature `invitation-emails`).
- Coordinators or mentors inviting, and invitations tied to an enrollment or cohort (tracking core).
- Bulk invitations and import.
- Editing an invitation (email or roles): revoke it and create a new one.
- Automatic acceptance on sign-in without the link (an email allowlist), and corporate SSO auto-provisioning (its own ADR).
- Cleaning up old invitations; retention of invited emails (postponed personal data rules).
- Rate limiting preview and accept: tokens have 256 bits of entropy.
- Making registration work in the local environment, and password reset in Keycloak ("Forgot password?", `resetPasswordAllowed`): both need a mail server and arrived later with `local-mail` (Mailpit).

### Known limitations

- **Email squatting in Keycloak.** Keycloak registration is open, and the realm forbids duplicate emails. So anyone can register an unverified Keycloak account with someone else's email, whether they learned it from a leaked invitation link (the landing page shows the invited email) or simply guessed it. The squatter gets no MentorHub access, because acceptance requires a verified email (AC-12). But the real person cannot register with their email, and while the realm has no password reset they cannot take the account over either.
  - Since `local-mail` enabled password reset, the real person recovers the account through their mailbox with "Forgot password?". Keycloak 26.8 does not mark the email verified by the reset itself: the squatted account still has its pending email verification, so Keycloak first sends a verification email and only then asks for the new password. Both steps go through the real mailbox, and afterwards the email is verified and the squatter's password is replaced (checked manually in `local-mail`).
  - Closing self-registration, with MentorHub creating Keycloak accounts through the Admin API, is rejected: ADR 0003 keeps the identity provider replaceable.

## 2. UI

### Navigation

- The toolbar gets "Invitations" after "Users", shown only with `USERS_INVITE` and marked current on `/invitations`.
- The users list gets an "Invite" button next to its `h1`, shown only with `USERS_INVITE`; it opens the invite dialog.
- The "Access by invitation only" text changes to: "MentorHub is available to students and staff of the training center by invitation. If you were invited, open your invitation link and sign in with the email it was sent to."

### Invitations

- Route: `/invitations`; roles: holders of `USERS_INVITE`.
- Purpose: see who has been invited, and manage open invitations.
- Content and actions:
  - `h1` "Invitations" and the "Invite" button;
  - a search field "Search by email" (300 ms) and a status select (All, Pending, Expired, Accepted, Revoked);
  - a table with:
    - Email;
    - Roles: chips, or "No roles" in secondary text;
    - Status: text "Pending", "Expired", "Accepted" or "Revoked", with a second line "Expires <date>", "Expired <date>", "Accepted by <name>" (a link to the user page) or "Revoked by <name>";
    - Invited: the date and the inviter's name;
    - row actions: "Copy link", "Get a new link" and "Revoke" as text buttons for a pending invitation; "Get a new link" and "Revoke" for an expired one;
  - a paginator (25 per page);
  - search, status and page are kept in the URL query; `?q=<email>` is also what the `invitation-exists` link opens. The "Accepted by" link to the user page keeps no back-link state for the invitations list.
- "Copy link" copies the link of a pending invitation to the clipboard and shows the snackbar "Link copied"; the errors and the clipboard fallback are in `invitation-links` AC-8.
- "Revoke" opens a confirmation: "Revoke the invitation for <email>? The link will stop working." with "Cancel" and "Revoke". On success the snackbar shows "Invitation revoked" and the row updates.
- "Get a new link" opens a confirmation: "Get a new link for <email>? The previous link will stop working." On success it opens the link view (below).
- 409 `invitation-closed` shows the snackbar "This invitation is already closed." and reloads the list; 404 (an invitation that disappeared cannot happen, but is handled) does the same. Other errors of the row actions show the snackbar "Something went wrong. Try again." and leave the row as it was.
- States: loading (progress bar, previous rows stay), empty ("No invitations match the filters." with "Clear filters", or "No invitations yet."), error (the unavailable card with "Retry").
- Patterns: the list page from `docs/ui/guidelines.md`.

```
+--------------------------------------------------------------------------+
| MentorHub  Users  Invitations                           [Ada Admin   v]  |
+--------------------------------------------------------------------------+
| Invitations                                                   [Invite]   |
| [Search by email            ] [Status: All v]                            |
| Email              Roles     Status                Invited               |
| ivan@example.com   Mentor    Pending               18 Oct 2026           |
|                              Expires 25 Oct 2026   Ada Admin             |
|                         [Copy link] [Get a new link] [Revoke]            |
| maria@example.com  No roles  Accepted              15 Oct 2026           |
|                              Accepted by Maria K.  Ada Admin             |
|                                        25 per page  1-2 of 2  < >        |
+--------------------------------------------------------------------------+
```

### Invite dialog

- Title "Invite a person"; fields: "Email" (required, email format checked on the client too) and "Roles" (a checkbox per role, none selected by default, with the hint "Without roles the person can sign in but has no access to anything."). Actions: "Cancel" and "Create invitation".
- On success the dialog switches to the link view.
- On 409 `user-active`: an inline message "This person already has access." with "Open user" (to `/users/{userId}`). On 409 `invitation-exists`: "There is already an invitation for this email." with "Show invitation" (to `/invitations?q=<email>`). The form keeps its values.
- Server field errors show under their fields. Other errors show the inline message "Something went wrong. Try again." with `role="alert"`, and the form keeps its values.

### Link view

Shown in the dialog after creating an invitation or getting a new link.

- Title "Invitation link"; text "Send this link to <email>. It expires on <date>. You can copy it again from the invitations list while it is pending."
- A read-only field with the link and "Copy link". Copying shows the snackbar "Link copied". If the clipboard is not available, the field is selected so the user can copy it by hand.
- Action "Done". The list reloads when the dialog closes.

```
+-------------------------------------------------+
| Invitation link                                 |
| Send this link to ivan@example.com. It expires  |
| on 25 Oct 2026. You can copy it again from the  |
| invitations list while it is pending.           |
| [http://localhost:4200/invite/Xf3...] [Copy link]|
|                                        [Done]   |
+-------------------------------------------------+
```

### Invitation landing

- Route: `/invite/:token`; public, outside the access guard; uses the access screen layout (a centered card, `h1` focused on open, no account menu).
- Flow:
  - loading: the shared loading state while preview runs (the signed-in account's email comes from the ID token claims, not from `GET /api/v1/me`);
  - invalid (404 `invitation-invalid`): `h1` "Invitation not valid", text "This invitation link is invalid or has expired. Ask the person who invited you for a new link.";
  - not signed in: `h1` "You're invited to MentorHub", text "This invitation is for <email>. Sign in with that email, or create an account if you don't have one." Actions:
    - "Sign in": starts sign-in with `login_hint=<email>`;
    - "Create an account": starts sign-in with the registration page (`prompt=create`) and `login_hint=<email>`;
  - returning from Keycloak to this page: the page accepts automatically;
  - signed in, not yet accepted: text "You're signed in as <account email>." with "Accept invitation" and "Use another account" (signs out locally and at Keycloak, then returns to this page);
  - accepting: a progress indicator; on success, navigation to `/`;
  - 409 `invitation-email-mismatch`: `h1` "Wrong account", text "This invitation is for <email>, but you're signed in as <account email>." with "Use another account". When the emails are equal but the account's email is not verified, the text reads "Verify your email in your account first, then try again.";
  - 409 `identity-linked-elsewhere`: `h1` "Account already in use", text "This account already belongs to another MentorHub user. Contact the person who invited you.", with "Sign out" (which also removes the stored token);
  - other errors: the unavailable card with "Retry".
- The page moves the token into session storage and replaces the address with `/invite` right after reading it; the token is kept across the Keycloak round trip and removed after acceptance or an invalid answer. It never goes into a redirect URI, the OIDC `state`, the logs or analytics (see Authorization and flow). The first request for `/invite/<token>` still reaches the static host's access log; that is accepted.
- Accessibility: the `h1` gets focus on every state change; the progress state sets `aria-busy`; errors use `role="alert"`.

```
+-------------------------------------------+
|                                           |
|       You're invited to MentorHub         |
|                                           |
|  This invitation is for ivan@example.com. |
|  Sign in with that email, or create an    |
|  account if you don't have one.           |
|                                           |
|        [Create an account]  [Sign in]     |
+-------------------------------------------+
```

### User page

- History shows `USER_INVITATION_ACCEPTED` as in AC-22, with the accepting user as the actor.

### Accessibility

- The same rules as user administration: one `h1` per page, labelled filters, column headers, status as text, and dialog focus handling.
- "Copy link" in the link view has the accessible name "Copy invitation link"; the row action has the accessible name "Copy invitation link for <email>"; the link field has the label "Invitation link".
- Snackbars are polite; the dialog's 409 messages are inline with `role="alert"`.

## 3. Design

### Modules

- Invitations live in `com.mentorhub.users` (decision below): `users.internal.invitation` holds the domain and service, and `users.internal.persistence` gets `InvitationRepository` (`JdbcClient`, ADR 0007). No new module and no new module dependency (`users` → `audit` exists).
- `users.internal.web`: a new contract tag `Invitations`, with `InvitationsController` implementing `InvitationsApi` and `InvitationsMapper`, following the per-tag convention. The history mapping in `UsersMapper` gains the new action.
- `Permission` gains `USERS_INVITE`, and ADMIN gets it.
- `com.mentorhub.web`: `ConflictException(type, detail, extensions)` carries Problem extensions (`userId`, `invitationId`), rendered as `ProblemDetail` properties; `NotFoundException(URI type, String detail)` gives a 404 its own problem type and a client-visible detail (used for `invitation-invalid`, types in `InvitationProblems`).
- Frontend:
  - `frontend/src/app/features/invitations/`: the list, the invite dialog and the link view;
  - `frontend/src/app/features/invite/`: the landing page;
  - the "Invite" button on the users list, which lazy-loads the invite dialog;
  - the history sentence on the user page;
  - `AuthService.signIn(returnUrl, {loginHint, register})` passes `login_hint` and `prompt=create` through the OIDC client's `customParams`, and `ensureSignedIn` gains an `invitation` result;
  - `InvitationSession` keeps the token in session storage, with a separate auto-accept key for the round trip.

### Data model

Migrations `V4__invitations.sql` and `V5__invitation_plain_tokens.sql`. V4 created the table with a `token_hash` column (SHA-256 of the token, hex; the token itself was never stored). V5 replaced it with the plaintext `token` (spec `invitation-links`): it gave every existing invitation a new random token in the application's format, because a hash cannot be turned back into a token, and dropped `token_hash` with its check and unique constraints. The table after V5:

- `invitations`:
  - `id uuid` PK default `gen_random_uuid()`;
  - `email text not null`;
  - `token text not null`, unique;
  - `created_at timestamptz not null default now()`, `created_by uuid not null` FK → `users`;
  - `expires_at timestamptz not null`;
  - `accepted_at timestamptz null`, `accepted_user_id uuid null` FK → `users`;
  - `revoked_at timestamptz null`, `revoked_by uuid null` FK → `users`;
  - checks:
    - `(accepted_at is null) = (accepted_user_id is null)`;
    - `(revoked_at is null) = (revoked_by is null)`;
    - not both accepted and revoked;
    - `email <> ''`;
    - `token ~ '^[A-Za-z0-9_-]{43}$'`;
  - partial unique index `invitations_open_email_uq` on `lower(email)` where `accepted_at is null and revoked_at is null`: at most one open invitation per email (AC-3, AC-15);
  - index on (`created_at desc`, `id desc`) for the list.
- `invitation_roles(invitation_id uuid` FK → `invitations` on delete restrict, `role text` FK → `roles`, PK both`)`.
- The status is derived, not stored:
  - `ACCEPTED` when `accepted_at` is set;
  - `REVOKED` when `revoked_at` is set;
  - `EXPIRED` when `expires_at <= now()`;
  - `PENDING` otherwise.

  The list filters on the same expressions.
- Rows are never deleted.

### API

| Method and path | Purpose | Permission | Errors |
|---|---|---|---|
| `POST /api/v1/invitations` | Create `{email, roles}`; returns `CreatedInvitation` (AC-1 to AC-3) | `USERS_INVITE` | 400, 403, 409 `user-active`, 409 `invitation-exists` |
| `GET /api/v1/invitations?q&status&page&size` | Page of invitations (AC-4) | `USERS_INVITE` | 400, 403 |
| `POST /api/v1/invitations/{id}/revoke` | Revoke (AC-6); returns `Invitation` | `USERS_INVITE` | 400, 403, 404, 409 `invitation-closed` |
| `POST /api/v1/invitations/{id}/link` | Issue a new link (AC-7); returns `CreatedInvitation` | `USERS_INVITE` | 400, 403, 404, 409 `invitation-closed` |
| `GET /api/v1/invitations/{id}/link` | The link of a pending invitation; returns `InvitationLink` (`invitation-links`) | `USERS_INVITE` | 400, 403, 404, 409 `invitation-closed`, 409 `invitation-expired` |
| `POST /api/v1/invitations/preview` | `{token}` → `InvitationPreview` (AC-8, AC-9) | public | 400, 404 `invitation-invalid` |
| `POST /api/v1/invitations/accept` | `{token}` → `Me` (AC-10 to AC-14) | authenticated, any status | 400, 404 `invitation-invalid`, 409 `invitation-email-mismatch`, 409 `identity-linked-elsewhere` |

All endpoints except preview answer 401 without a valid bearer token; accept needs only that token, the others also pass the gate (an active user).

The token travels in the request body, never in an API path or query, so it stays out of server access logs. A response that returns a token (create, new link, `GET .../link`) sets `Cache-Control: no-store`.

Schemas:

- `Invitation`:
  - `id`, `email`, `roles`, `status` (`InvitationStatus`: `PENDING`, `EXPIRED`, `ACCEPTED`, `REVOKED`), `createdAt`, `expiresAt`, `createdBy` (`HistoryActor`);
  - `acceptedAt` and `acceptedUser` (`HistoryActor`), and `revokedAt` and `revokedBy`: optional, present only when they apply.
- `CreatedInvitation`: `invitation` (`Invitation`) and `link` (string, URI).
- `InvitationPage`: as `UserPage`.
- `InvitationCreate`: `email` (required), `roles` (required, may be empty).
- `InvitationToken`: `token` (required string, no `pattern`, `maxLength` 200 only to bound the public body). A missing or empty token is 400 `validation-failed`; any other malformed token is the uniform 404 `invitation-invalid` (AC-9).
- `InvitationPreview`: `email`, `expiresAt`.
- `HistoryAction` gains `USER_INVITATION_ACCEPTED`. `HistoryEntry` gains the optional detail `invitation`, which carries `invitationId`, `invitedBy` (`HistoryActor` or `null` when the inviter cannot be resolved), `userCreated`, `identityLinked` and `enabled`; the roles added and removed use the existing `rolesAdded` and `rolesRemoved`.
- `Permission` gains `USERS_INVITE`.
- New problem types (`urn:mentorhub:problem:`):
  - `invitation-invalid` (404);
  - `user-active` (409, extension `userId`);
  - `invitation-exists` (409, extension `invitationId`);
  - `invitation-closed` (409);
  - `invitation-email-mismatch` (409);
  - `identity-linked-elsewhere` (409).

  The create 409s share one schema, `InvitationConflictProblem` (`allOf` `Problem` with the optional `userId` and `invitationId`), instead of a `oneOf`: a problem with extensions gets an `allOf` schema over `Problem`.
- `InvitationCreate.email` is checked with Bean Validation `@Email` after trimming, and the service reports the email and a `null` item in `roles` together, one error per field. An unknown role fails JSON parsing first and is reported alone (accepted limitation).

### Tokens and links

- Each token is 32 bytes from `SecureRandom`, encoded base64url without padding (43 characters).
- The token is stored in plaintext in `invitations.token`. Lookup is by the presented token; a token that is not 43 base64url characters is `invitation-invalid` without a query. Only `InvitationRepository.findLink` reads a stored token back, for the link of a pending invitation; every other read, including the list, leaves it out. Audit details never contain it.
- The link is `<mentorhub.app-url>/invite/<token>`.
- Creating and issuing a new link both set `expires_at = now() + mentorhub.invitations.link-lifetime`. The database `now()` is the one clock for expiry, status and audit.

### Authorization and flow

- The management operations carry `@RequirePermission(USERS_INVITE)` on the controller and on the service methods.
- Gate exemptions:
  - `POST /api/v1/invitations/preview` goes into the public filter chain, which ignores bearer tokens. `SecurityConfiguration` builds GET-only matchers for the public paths today, so preview needs its own POST matcher (tested with an invalid bearer token, AC-8);
  - `POST /api/v1/invitations/accept` is exempt from the gate like `GET /api/v1/me`: it only requires authentication. It carries the "no permission required" marker and reads the caller through `CurrentCaller`, which with its `Caller` record is extended with the token's issuer, subject, email, `email_verified` and names (today it returns only the status and user id).
- Acceptance, in one transaction (AC-10 to AC-15):
  1. look up the invitation by token `for no key update`; missing, expired, revoked → `invitation-invalid`; accepted → AC-14 if `accepted_user_id` is the user the caller's `(issuer, subject)` is linked to, read now inside the transaction (not the user resolved at authentication), returning that user's current state, else `invitation-invalid`;
  2. require `email_verified` and an email equal to the invitation's, ignoring case → else `invitation-email-mismatch`;
  3. read the identity's linked user and the user with the invitation's email; if the identity is linked to a user other than that one (or to any user when none has that email) → `identity-linked-elsewhere`, with a warning naming both ids (or only the linked one), before anything is written;
  4. `insertUserIfAbsent` with the invitation's email and the token's names, then lock the user row by email (`for no key update`, as user administration does); link the identity if it is unlinked; if a concurrent transaction linked it to another user meanwhile, the answer is `identity-linked-elsewhere` and the transaction rolls back;
  5. if the user is disabled: enable it (clears the reason and comment) and set its known roles to exactly the invitation's known roles; if it is active: add the invitation's known roles (`on conflict do nothing`), removing none;
  6. mark the invitation accepted, and write `USER_INVITATION_ACCEPTED` (details: `invitationId`, `invitedBy`, the three flags, roles added and removed).
- The other audit entries: `INVITATION_CREATED` (email, roles), `INVITATION_LINK_REISSUED` (`expiresAt` as an ISO-8601 string), `INVITATION_REVOKED`.
- Lock order: the invitation row, then the user row. Acceptance removes roles only from a disabled user, who does not count as an active administrator, so it needs no administrator lock and the lockout rules cannot be broken by it. Revoke and new link lock only the invitation row, so accept, revoke and new link serialize on that row (AC-15).
- Create: check for an active user with the email, then `insert ... on conflict (lower(email)) where <open> do nothing` and re-read the open invitation; the partial unique index decides races between parallel creates, a conflicting open invitation maps to `invitation-exists`, with up to 3 attempts.
- The bootstrap (ADR 0005) and acceptance can race for the same email. Both use `on conflict do nothing` and re-read, so either order ends in one user.
- Frontend:
  - the `/invite/:token` route sits outside `accessGuard`; the token interceptor treats preview as public (generated `PATH` constants);
  - the "Invitations" link and the route need `USERS_INVITE` through the existing permission check and `requirePermission` guard;
  - one mechanism carries the token across Keycloak, and the token never leaves the browser's session storage:
    - the landing page stores the token in session storage under its own key (`InvitationSession`, plus an auto-accept key set before the Keycloak round trip) and replaces the address with `/invite` (`replaceUrl`), so it leaves browser history and later navigation;
    - the redirect URI and the post-logout redirect URI stay the application origin, never a path with the token, and the token is never put in the OIDC `state`;
    - after the sign-in callback, the access guard (or the initializer that handles the callback) sends the visitor to `/invite` whenever the key is present, whatever their access status. Today it honours the remembered return URL only for active users with roles, so this is a change;
    - "Use another account" signs out and keeps the key (today sign-out clears the return URL); "Sign out" on "Account already in use" removes it; the page removes the key after acceptance or an invalid answer.
  - the landing page shows the signed-in account's email from the ID token claims (the `email` client scope); the claims are not refreshed on token renewal.

### Events

None. The audit entry is written in the accepting transaction (ADR 0006).

### Configuration

| Property | Meaning |
|---|---|
| `mentorhub.app-url` | The frontend's public URL, used to build links. Required: startup fails without it, as with the client configuration. `dev`: `http://localhost:4200`. |
| `mentorhub.invitations.link-lifetime` | How long a link is valid (default `7d`) |

### Local environment

No infrastructure changes. Keycloak allows registration but requires email verification, which cannot complete locally until `invitation-emails`. The README explains how to test acceptance: with `stranger@mentorhub.local`, or with a user created in the Keycloak admin console with "Email verified" on. After the stranger accepts, it stays linked until the database is reset, which deletes all local data.

### Tests

- Negative authorization for every management operation: no roles, each non-admin role, and a disabled caller (gate).
- Acceptance tests run the real authentication chain (the test issuer from identity foundation) for:
  - an unlinked caller, a disabled caller and an active caller;
  - unverified and different emails;
  - an identity linked elsewhere;
  - retry (AC-14);
  - an unknown stored role on reads; on acceptance it is covered by the service tests only.
- AC-15 runs parallel requests against Testcontainers Postgres.
- The preview test sends an invalid bearer token and still gets 200.
- The tests check that the token never appears in the audit details or in the list response.
- Frontend unit tests: every list state, the dialog and its 409 messages, the link view's copy fallback, each landing state, the automatic accept after the round trip, and the history sentence.
- `prompt=create` and `login_hint`: `angular-auth-oidc-client` 22.x passes them through `customParams` (asserted in `auth.service.spec.ts`), and the manual check (AC-23) confirmed that Keycloak 26.8 opens registration with the email prefilled. If `prompt=create` is not supported, "Create an account" opens Keycloak's registration endpoint instead.
- No automated accessibility (axe) test exists in the project; accessibility is checked by review.

### Documentation updates

- `docs/development/backend.md`: gate-exempt endpoints for callers in any status; plaintext token storage and `Cache-Control: no-store`; problems with extensions.
- `docs/development/frontend.md`: public routes outside the access guard; sign-in with a hint and a return URL.
- `docs/ui/guidelines.md`: the "Invitations" toolbar link, the link view (copy, and copy again from the list), and the landing page in the access screen layout.
- `docs/features/identity-foundation.md`: the new "Access by invitation only" text.
- `docs/architecture/invariants.md`: no change. Linking through an invitation with a verified email is already the rule.
- `README.md`: inviting and accepting locally (see Local environment); the development users table no longer calls `stranger@mentorhub.local` permanently "not invited" (it is unlinked until it accepts an invitation).
- `docs/features/identity-foundation.md`: the same note on `stranger@mentorhub.local` in the development users table.
- `docs/development/backend.md`: `ConflictException` with Problem extensions in "Errors"; the required `mentorhub.app-url` (owned by `users`) in "Configuration and profiles".
- `docs/ui/guidelines.md`: the application shell lists the "Invitations" link next to "Users".

### Decisions

- Three features: user administration with audit, invitations with a copyable link, invitation emails (user decision, earlier).
- Only administrators invite, through a new permission `USERS_INVITE` (user decision).
- Acceptance requires a verified email equal to the invitation's (user decision). A forwarded link is useless to anyone else.
- An email of an active user cannot be invited (409). An active user's roles are changed only on the user page. A disabled user's email can be invited, and acceptance reactivates the same `users.id` and adds roles (user decision).
- Links last 7 days, configurable. There is one open invitation per email (user decision). Revised by `invitation-links`: the token is stored in plaintext (not a hash), so the link of a pending invitation can be copied again; a new link is still issued when the invitation has expired.
- A public landing page with "Sign in" and "Create an account", and automatic acceptance after the round trip (user decision).
- Invitations get their own page, `/invitations` (user decision).
- Invitations live in the `users` module, not in a new module (recommendation; the user did not object). Linking identities, reactivation, the gate exemption and the row locks are `users` internals. A separate module would need a public "link an identity to a user" API, which the invariant "linking happens only through an invitation or ADR 0005" argues against.
- Invitations carry no names: a new user's first and last name come from the token at acceptance, as with the bootstrap, and a reactivated user's profile is unchanged.
- Reactivating a disabled user through an invitation sets their roles to exactly the invitation's, so the invitation states everything the person gets back (user decision; refines the earlier "adds roles"). A user who became active after the invitation was created only gains the invitation's roles. "Enable user" on the user page still keeps roles (user administration AC-8).
- Editing an invitation is not supported; revoke and create again.
- The invitation search `q` is limited to 254 characters, the maximum email length, instead of the 100 of `/users`, so the `invitation-exists` link works for every valid email (user decision, steps 1–2).
- Implementation choices accepted by the user at the checkpoints (steps 1–3, frontend B, final review), recorded above: the extra `email` check (the extra `token_hash` check was dropped with the hash by V5); `InvitationConflictProblem` as an `allOf`; the typed 404 through `NotFoundException`; `@Email`; database `now()` as the one clock; `on conflict do nothing` with up to 3 retries for parallel creates; the audit field `invitedBy` and `expiresAt` as an ISO-8601 string; an AC-14 retry returns the current state; a concurrent link ends in `identity-linked-elsewhere` with rollback; the token session with an auto-accept key; the account email from ID token claims; "Sign out" on "Account already in use" clears the token; the "Expired <date>" line; two text row actions instead of a menu; no back-link state; `customParams` for `login_hint` and `prompt=create`; the lazily loaded invite dialog; an unknown role plus an invalid email reports only the parsing error.

## 4. Open questions

None blocking.
