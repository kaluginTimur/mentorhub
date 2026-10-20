# Invitation links

Status: implemented
Stage: tracking core (users, roles, audit log)
Related: ADR 0006, ADR 0007, `docs/features/invitations.md` (changes its requirements, see below), `docs/architecture/invariants.md`. Followed by `invitation-emails`.

## 1. Requirements

### Goal

Today an invitation's link is shown once, when it is created or issued again; only its hash is stored. An administrator who lost the link must issue a new one, which kills the link the person may already have. Storing the token in plaintext lets administrators copy the link of a pending invitation at any time, and lets `invitation-emails` send the existing link without replacing it.

The hash protected little: acceptance requires a verified email equal to the invitation's (invitations AC-12), so a token read from the database is useless without the invited person's mailbox (user decision, 2026-10-20).

### Scope

- The token is stored in plaintext instead of its hash; lookups use the token.
- A new operation returns the link of a pending invitation.
- A "Copy link" row action in the invitations list.
- The link view no longer says the link is shown only once.
- "Get a new link" stays: it replaces the token, for a link that went to the wrong person or leaked, and renews an expired invitation.

### Roles and scenarios

#### Administrator

- S1. As an administrator, I copy the link of a pending invitation from the list, so I can send it again through any channel without invalidating it.
- S2. As an administrator, I still issue a new link when the old one must stop working, or the invitation has expired.

### Acceptance criteria

Each criterion is marked **auto** (automated tests) or **manual** (checked by hand in the local environment).

- AC-1 (S1, auto): Given a pending invitation, when a holder of `USERS_INVITE` calls `GET /api/v1/invitations/{id}/link`, then the response is 200 with `{link, email, expiresAt}`, the link being the same as in the create or new-link response. "Pending" is decided with the database `now()` through the repository's single status expression, as everywhere in invitations. Any administrator can do it, not only the one who created the invitation (user decision).
- AC-2 (S1, auto): Given an expired, accepted or revoked invitation, then no link is returned: an accepted or revoked invitation responds 409 `invitation-closed` (as revoke and new link do), and an expired one responds 409 `invitation-expired`. An unknown id responds 404 `not-found`.
- AC-3 (auto): Given a caller without `USERS_INVITE`, then the response is 403 `forbidden`; disabled and not invited callers are refused by the gate.
- AC-4 (auto): The create and new-link responses still return the link (invitations AC-1, AC-7). `GET /api/v1/invitations` and every other response except these three never contain the link or the token. All three responses that contain a link carry `Cache-Control: no-store`.
- AC-5 (auto): Preview and accept find the invitation by the token itself. Every behavior of invitations AC-8 to AC-15 is unchanged, including the uniform 404 `invitation-invalid` and the rejection of a malformed token without a query.
- AC-6 (auto): Given invitations created before this feature, after the migration each of them has a new random token in the same format. Their earlier links no longer work, and "Copy link" on a pending one returns the new link. No database reset is needed. Tested by a Flyway test that migrates an empty database to V4, inserts invitations, then applies V5.
- AC-7 (auto): The token is never written to logs or audit details (unchanged); reading a link writes no audit entry (user decision).

UI:

- AC-8 (S1, auto): A pending invitation's row has the action "Copy link", before "Get a new link" and "Revoke". Expired, accepted and revoked rows don't have it.
  - On success the link is copied to the clipboard and the snackbar "Link copied" shows.
  - The click handler starts the clipboard write synchronously with a pending value (`navigator.clipboard.write([new ClipboardItem({'text/plain': <promise of the link as a Blob>})])`), so browsers that require the user gesture for the write (Safari) accept it although the link is fetched first. If that write is not supported or rejected, `writeText` is tried; if that fails too, the copy view opens (see UI).
  - On 409 `invitation-closed` or 404: the snackbar "This invitation is already closed." and the list reloads, as for the other row actions. On 409 `invitation-expired`: the snackbar "This invitation has expired. Get a new link." and the list reloads. 403 follows the existing row-action handling (the permission snackbar). Other errors: "Something went wrong. Try again."
  - While the request runs, the row's actions are disabled.
- AC-9 (S1, auto): The link view after creating an invitation or getting a new link reads "Send this link to <email>. It expires on <date>. You can copy it again from the invitations list while it is pending." It no longer says the link is shown only once.
- AC-10 (manual): Given the local environment, an administrator creates an invitation, closes the link view, copies the link from the list with "Copy link", and opening it as the invited person accepts the invitation. A second administrator sees and copies the same link. "Copy link" is checked in Chrome and Safari.

Accepted edge case: a "Copy link" that runs while another administrator issues a new link can return the old link just before it is replaced; the administrator then copies a dead link. The list shows the new expiry after a reload.

### Changes to `docs/features/invitations.md`

This feature changes these requirements of the invitations spec; they are edited there in the same change, with a "Revised" line:

- Goal ("one-time link") and Scope ("The link is shown once, to copy."): the link can be copied again while the invitation is pending.
- AC-1: the link is no longer "returned only in this response and the new-link response"; it is also returned by `GET /api/v1/invitations/{id}/link` for a pending invitation.
- AC-19: the link view no longer says the link is shown only once.
- AC-20: pending rows get "Copy link".
- The UI section: the row actions, the link view text and the accessible names.
- Visibility: the link is visible to holders of `USERS_INVITE` while the invitation is pending; the token is stored in plaintext.
- Decisions: "Only the hash is stored" is replaced by this feature's decision.

### Access matrix

| Action | admin | coordinator | mentor | student | observer | active, no roles | disabled | not invited | not signed in |
|---|---|---|---|---|---|---|---|---|---|
| Get the link of a pending invitation | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ (gate) | ✗ (gate) | 401 |

`USERS_INVITE`, as for every invitation management operation. Every holder sees every invitation, so there is no resource-level rule beyond an unknown id.

### Visibility

| Data | Who sees it |
|---|---|
| The link of a pending invitation | Holders of `USERS_INVITE`, on request, one invitation at a time |
| The link of an expired, accepted or revoked invitation | Nobody through the API; it stays in the database |
| The token in the database | Anyone with database access. Acceptance still requires the invited person's verified email, which is now the only protection of a link (`docs/architecture/invariants.md`). |

### Audit

No new entries. Creating and issuing a new link are audited as before (`INVITATION_CREATED`, `INVITATION_LINK_REISSUED`); reading a link is not (user decision).

### Out of scope

- Copying the link of an expired invitation; "Get a new link" renews it.
- Sending links by email (`invitation-emails`).
- Encrypting tokens at rest.
- Squashing the migrations before the first release (a later task).

## 2. UI

### Invitations list

- Row actions of a pending invitation: "Copy link", "Get a new link", "Revoke" (three text buttons, as the existing two). Expired: "Get a new link", "Revoke".
- "Copy link" has the accessible name "Copy invitation link for <email>".

```
| ivan@example.com   Mentor    Pending               18 Oct 2026           |
|                              Expires 25 Oct 2026   Ada Admin             |
|                         [Copy link] [Get a new link] [Revoke]            |
```

### Link view

- Text: "Send this link to <email>. It expires on <date>. You can copy it again from the invitations list while it is pending."
- Copy view, opened from "Copy link" when the clipboard cannot be written: the same `LinkView` component in a second mode with the input `{email, expiresAt, link}` from the `GET` response (not from the list row). Title "Invitation link", text "Copy this link and send it to <email>. It expires on <date>.", the field focused with its text selected, no "Copy link" button, and "Done".

## 3. Design

<Snapshot of the implementation plan; it may drift from the code later. Requirements above stay current.>

### Modules

All changes are in `com.mentorhub.users`:

- `InvitationTokens`: `hash` is removed; generation and the format check stay.
- `InvitationRepository`: lookup, insert and replace use `token`. `findLink` (row `StoredInvitationLink`) returns the token and status of one invitation and is the only read that selects the token; the shared column list of every other read leaves it out.
- `InvitationAdministrationService.link(id)` with `@RequirePermission(USERS_INVITE)`, read-only transaction, returns `PendingInvitationLink`: unknown → `NotFoundException`; accepted or revoked → `ConflictException(INVITATION_CLOSED)`; expired → `ConflictException(INVITATION_EXPIRED)`. Closed takes priority over expired.
- `InvitationAcceptanceService`: looks up by token.
- `InvitationsController` implements the new operation. A private `noStore()` (injected `HttpServletResponse`) sets `Cache-Control: no-store` on the success responses of create, new link and get link.
- Frontend: the row action in `features/invitations/invitations-list.ts`, the link view text and its clipboard fallback mode. Row actions use `disabledInteractive` while busy, so the clicked button keeps focus; busy state is a set of invitation ids, and the table tracks rows by id so focus survives the reload after the copy view.

No new module and no new dependency.

### Data model

Migration `V5__invitation_plain_tokens.sql`:

- add `token text`;
- give every existing row a new random token in the application's format (43 base64url characters), built from two `gen_random_uuid()` values (about 244 random bits; enough for a one-off backfill of test data, and no extension is needed);
- `token` becomes `not null`, unique, with the check `token ~ '^[A-Za-z0-9_-]{43}$'`;
- drop `token_hash` and its constraints.

### API

| Method and path | Purpose | Permission | Errors |
|---|---|---|---|
| `GET /api/v1/invitations/{id}/link` | The link of a pending invitation (AC-1, AC-2) | `USERS_INVITE` | 400 (malformed id), 401, 403, 404, 409 `invitation-closed`, 409 `invitation-expired` |

- Response schema `InvitationLink`: `link` (string, URI), `email`, `expiresAt` (date-time), all required.
- Contract descriptions that say the link is returned only once (`createInvitation`, `reissueInvitationLink`, `CreatedInvitation`) are updated in the contract step.
- New problem type `urn:mentorhub:problem:invitation-expired` (409); `invitation-closed` is reused.
- The `Cache-Control: no-store` response header is declared once as `components/headers/NoStore` and referenced by the create, new-link and get-link responses.
- The token stays out of every path and query; this operation takes only the invitation id.

### Authorization

- `@RequirePermission(USERS_INVITE)` on the controller method and the service method, as for the other management operations.

### Events

None.

### Documentation updates

- `docs/features/invitations.md`: the requirement changes listed above, with a "Revised" line. Its Design section is also updated to plaintext token storage and the new operation (user decision), with one note under the API table on 401.
- `docs/architecture/invariants.md` (Identity): invitation tokens are stored in plaintext, so acceptance must keep requiring a verified email equal to the invitation's; relaxing that rule needs hashing again.
- `docs/development/backend.md` (Security): the secret link tokens bullet. Tokens are stored in plaintext with a format check, lookups use the token, and tokens stay out of paths, queries, logs and audit details. A response that returns a token sets `Cache-Control: no-store`. Do not enable bind-parameter logging (`org.springframework.jdbc` at TRACE, PostgreSQL `log_statement`), which would write tokens to the logs.
- `api/openapi.yaml`: the descriptions listed under API.
- `docs/features/invitation-emails.md`: reworked before its approval (plaintext token, resend without a new link, asynchronous sending).
- `docs/ui/guidelines.md`: the link view is no longer "shown once"; the "Copy link" row action and its clipboard fallback.
- `README.md`: inviting locally no longer says the link is shown only once.

### Decisions

- Tokens are stored in plaintext; the hash added little because acceptance requires the invited person's verified email (user decision, 2026-10-20).
- Any holder of `USERS_INVITE` can copy the link of any pending invitation (user decision).
- Reading a link is not audited (user decision).
- "Copy link" only for pending invitations; expired ones are renewed with "Get a new link" (user decision).
- Existing invitations get new tokens in the migration; no compatibility path for hashed tokens (user decision: only test data exists).
- The link is read through a separate `GET /api/v1/invitations/{id}/link`, so the list never carries links in bulk (user decision).
- An unusable link answers by cause: the existing `invitation-closed` for accepted or revoked, a new `invitation-expired` for expired (user decision).
- `Cache-Control: no-store` on every response that contains a link (user decision).
- `docs/architecture/invariants.md` notes that the verified email match is now the only protection of a link (user decision).

## 4. Open questions

None.
