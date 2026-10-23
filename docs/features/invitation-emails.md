# Invitation emails

Status: approved
Stage: tracking core (users, roles, audit log)
Related: ADR 0001, ADR 0002, ADR 0006, ADR 0007, ADR 0008 (planned), `docs/features/invitations.md` (changes its requirements, see below), `docs/features/invitation-links.md`, `docs/architecture/invariants.md`.
Reconstructed: 2026-10-23. The first draft stayed in an unpushed local branch; this version is rebuilt from what the repository records about it, and the user confirmed the proposals of the reconstruction on 2026-10-23 (see Decisions).

## 1. Requirements

### Goal

Administrators create an invitation and today must pass the link on by hand. MentorHub can send the link to the invited address itself: when the invitation is created, when a new link is issued, and again at any time while the invitation is pending, without replacing the link (`invitation-links`). A mail server is optional: an installation without one works exactly as today. Sending is asynchronous, so a slow or unreachable mail server never fails or delays the administrator's request, and the outcome of each invitation's latest email is visible in the invitations list.

### Scope

- An optional outgoing mail server, configured per installation; without it, email features are hidden and the API refuses them.
- "Send the invitation by email" when creating an invitation and when getting a new link.
- "Send email" for a pending invitation: sends the current link again, without replacing it.
- Asynchronous delivery that survives a restart: a request to send is stored in the same transaction as the change that asked for it and is sent after the commit.
- The outcome of the latest email of each invitation in the list (sending, sent, failed, not sent).
- The local environment sends to Mailpit (`infra/compose.yaml`, from `local-mail`).

### Roles and scenarios

#### Administrator

- S1. As an administrator, I create an invitation and let MentorHub email the link to the person, so I don't have to send it myself.
- S2. As an administrator, I send the link of a pending invitation again by email when the person cannot find it, without making the old email's link stop working.
- S3. As an administrator, I get a new link (because the old one leaked or expired) and have it emailed in the same step.
- S4. As an administrator, I see in the invitations list whether the latest email was sent or failed, so I can retry it or copy the link and send it another way.
- S5. As an administrator of an installation without a mail server, I invite people with a copied link as before and see no email options.

#### Invited person

- S6. As an invited person, I receive an email that says I am invited to MentorHub, with which address I have to sign in, the link and its expiry.

#### Operator

- S7. As the person who runs an installation, I enable email by configuring a mail server and a sender address, and I can leave it off.

### Acceptance criteria

Each criterion is marked **auto** (automated tests) or **manual** (checked by hand in the local environment).

Configuration:

- AC-1 (S5, S7, auto): Given no mail server is configured (`spring.mail.host` unset), then the application starts, `GET /api/v1/system/client-config` returns `invitationEmails: false`, and every request that asks to send an email responds 409 `email-unavailable` and changes nothing. Given a mail server is configured, then `invitationEmails` is `true`, and startup fails, naming the property, if `mentorhub.mail.from` is missing or is not exactly one address with an optional display name (strict RFC 5322 parsing, `InternetAddress`). `sendEmail: false` and a missing `sendEmail` are accepted whether or not email is enabled.

Requesting an email:

- AC-2 (S1, auto): Given email is enabled, when an administrator calls `POST /api/v1/invitations` with `sendEmail: true`, then in the same transaction as the invitation:
  - an email request is stored with status `QUEUED`, the time and the administrator;
  - an audit entry `INVITATION_EMAIL_REQUESTED` is written;
  - the response is 201 as today, still with the `link` (invitations AC-1), and its invitation carries `lastEmail` with status `QUEUED`.

  `sendEmail` is optional and defaults to `false`; without it nothing changes against today. If creating the invitation fails (400, 409), no email is requested.
- AC-3 (S3, auto): The same holds for `POST /api/v1/invitations/{id}/link` with the optional body `{sendEmail: true}`: the email carries the new link. Without a body the operation behaves as today. Given the invitation's latest email is still `QUEUED`, the new link is issued as usual, no second request and no `INVITATION_EMAIL_REQUESTED` entry are written, and the response carries the existing `QUEUED` `lastEmail`: the queued email builds its link when it is sent, so it carries the new link (see the accepted edge case under AC-10).
- AC-4 (S2, auto): Given a pending invitation, when an administrator calls `POST /api/v1/invitations/{id}/email`, then an email request is stored and audited as in AC-2, the token and the expiry stay unchanged, and the response is 202 with the invitation. The operation has no request body. An accepted or revoked invitation responds 409 `invitation-closed`, an expired one 409 `invitation-expired` (as `GET .../link`), an unknown id 404 `not-found`. Any administrator can send it, not only the inviter.
- AC-5 (S2, auto): Given the invitation's latest email is still `QUEUED`, when an administrator calls `POST .../email`, then the response is 409 `email-queued` and nothing is stored, so a double click sends one email. Two parallel calls end in one request and one 409 `email-queued`, never a 500. (A new link with `sendEmail` is AC-3.)
- AC-6 (auto): Errors are checked in this order, after authentication, the gate and the permission (403): 400 `validation-failed` → 404 `not-found` → 409 `invitation-closed` or `invitation-expired` (create: 409 `user-active` or `invitation-exists`) → 409 `email-unavailable` → 409 `email-queued`.
- AC-7 (auto): Given a caller without `USERS_INVITE`, then `POST .../email` and the `sendEmail` flag respond 403 `forbidden`; disabled and not invited callers are refused by the gate.

Delivery:

- AC-8 (S1–S3, S6, auto): Given a stored email request, after the requesting transaction commits, the application sends one email to the invitation's address with the link of the invitation's current token, and marks the request `SENT` with the time. The administrator's request never waits for the mail server: the response is returned before any connection to it, also when the mail server hangs (the test holds the SMTP connection open and the request still returns at once).
- AC-9 (auto): Given the requesting transaction rolls back, then no email is sent.
- AC-10 (auto): Given the invitation is no longer pending when the email is about to be sent (accepted, revoked or expired since the request), then no email is sent and the request becomes `SKIPPED`. A request whose link was replaced since the request sends the link that is current when the email is built.

  Accepted edge case: the invitation is read once before the mail server is contacted, not locked during the send. A revoke, acceptance or new link that commits while the email is being handed to the mail server (at most the mail timeouts) is not seen: the email carries the link read just before, which may already be dead. The administrator then sends again (as in `invitation-links`, where a copy can race a new link).
- AC-11 (S4, auto): Given the mail server refuses the message or cannot be reached within the configured timeouts, then the request becomes `FAILED` with the time and a short reason without the token, a warning is logged without the token or the message body, and the invitation is unchanged. There is no automatic retry; the administrator sends again.
- AC-12 (auto): Given the application stops after the commit but before the email was sent, then after the next start the email is sent once (event publication registry, ADR 0008, planned). A request that is no longer `QUEUED` is never sent again, so a publication replayed after `SENT` was recorded sends nothing. An email may be sent twice if the application stops between handing it to the mail server and recording `SENT`; that is accepted.
- AC-13 (auto): Given a request stays `QUEUED` because its processing failed unexpectedly (for example the database was unavailable when the result was recorded), then within 10 minutes, without a restart, it is processed again (stale incomplete publications are resubmitted, see Design), so neither the row nor AC-5 stay blocked.
- AC-14 (S6, auto): The email is plain text in English:
  - From: `mentorhub.mail.from`; To: the invitation's email; Subject: "You're invited to MentorHub";
  - body, exactly these lines (the link stands alone on its line, so no mail client takes punctuation into it):

    ```
    You're invited to MentorHub, the training center's mentoring platform.

    Open this link and sign in or create an account with <email>:

    <link>

    The link expires on <date>.

    If you didn't expect this invitation, ignore this email.
    ```

  - the email is impersonal: it names no administrator and contains no data about the invitation beyond the address, the link and the expiry (user decision);
  - `<date>` is the expiry with time and zone, in UTC: "25 Oct 2026, 14:00 UTC".
- AC-15 (auto): The token and the link never appear in logs, audit details, the event publication registry or the email request rows. JavaMail debug output (`spring.mail.properties.mail.debug`) stays off, because it logs the whole message. The email request row references the invitation; the link is built when the email is sent.

Reading the outcome:

- AC-16 (S4, auto): Each item of `GET /api/v1/invitations` (and every response that returns an `Invitation`) carries `lastEmail` when the invitation has at least one email request: `status` (`QUEUED`, `SENT`, `FAILED`, `SKIPPED`), `requestedAt`, `requestedBy` (`HistoryActor`) and, once it is no longer `QUEUED`, `finishedAt`. Earlier requests stay in the database and are not shown.

UI:

- AC-17 (S1, S3, S5, auto): Given email is enabled, then the invite dialog has the checkbox "Send the invitation by email to this address", checked by default, and the "Get a new link" confirmation has "Send the new link by email", checked by default. After success with the box checked, the link view reads "An email with this link is on its way to <email>. It expires on <date>. You can also copy the link, and copy it again from the invitations list while it is pending." With the box unchecked, or given email is disabled (no checkbox is rendered), the link view and the dialog work as today. On 409 `email-unavailable` (the server's configuration changed after the page loaded) the dialog shows the inline alert "Sending email is not available. Reload the page and try again." and keeps its values; the confirmation closes and the same text shows as a snackbar.
- AC-18 (S2, S4, auto): Given email is enabled, a pending row has "Send email" before "Copy link". It sends without a confirmation, replaces the row with the `Invitation` of the 202 response (so it shows "Sending email…" at once) and shows the snackbar "Email to <email> queued". 409 `email-queued` shows "An email to <email> is already on its way." and reloads the list; 409 `email-unavailable` shows "Sending email is not available. Reload the page and try again."; 409 `invitation-closed`, `invitation-expired`, 404 and other errors behave as for "Copy link" (`invitation-links` AC-8). The Status column shows a further line whenever `lastEmail` is present, whatever the invitation's status: "Sending email…" (`QUEUED`), "Email sent <date>" (`SENT`), "Email failed <date>" (`FAILED`) or "Email not sent" (`SKIPPED`), the date being `finishedAt` in the UI's date format. The list does not poll; it shows the new state when it reloads.
- AC-19 (manual): Given the local environment, an administrator creates an invitation with the email checkbox, the email arrives in Mailpit with a working link, the row shows "Email sent", "Send email" delivers a second email with the same link, and opening it as the invited person accepts the invitation. With Mailpit stopped, "Send email" ends as "Email failed" after a reload, and the administrator still can copy the link.

### Changes to `docs/features/invitations.md`

This feature changes these parts of the invitations spec; they are edited there in the same change, with a "Revised" line:

- Related: "Followed by `invitation-emails`" no longer mentions Mailpit, which shipped with `local-mail`.
- AC-5: the management operations include sending an invitation email.
- AC-20 and the UI section (Invitations): with email enabled, a pending row also has "Send email", and the Status column has the email line.
- The access matrix: the management row includes sending an email.
- The API table: `sendEmail` on create and new link, and `POST /api/v1/invitations/{id}/email`.
- Out of scope: sending and resending by email moves to this feature.

### Access matrix

| Action | admin | coordinator | mentor | student | observer | active, no roles | disabled | not invited | not signed in |
|---|---|---|---|---|---|---|---|---|---|
| Request an invitation email (create, new link, send email) | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ (gate) | ✗ (gate) | 401 |
| See the latest email's outcome in the list | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ (gate) | ✗ (gate) | 401 |
| Whether email is enabled (`client-config`) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

`USERS_INVITE`, as for every invitation management operation. Every holder sees every invitation; there is no resource-level rule beyond an unknown id.

### Visibility

| Data | Who sees it |
|---|---|
| The latest email's status, times and requester | Holders of `USERS_INVITE`, in the invitations list |
| The failure reason | Nobody through the API; the warning in the server log and the request row |
| The link inside the email | The owner of the invited mailbox. The email adds no new reader of the link: acceptance still requires the invited verified email (`docs/architecture/invariants.md`). |
| Whether email is enabled | Anyone (public client configuration) |

### Audit

| Action | Subject | Actor | Details |
|---|---|---|---|
| `INVITATION_EMAIL_REQUESTED` | `INVITATION` | the administrator | `reason`: `CREATED`, `LINK_REISSUED` or `RESENT` |

The outcome (`SENT`, `FAILED`, `SKIPPED`) is a system result, not an administrator's change; it is kept in the email request row and not audited.

### Out of scope

- Other emails (notifications, reminders before expiry, a welcome email after acceptance).
- HTML emails, templates editable by administrators, localization.
- Automatic retries with backoff, bounce handling, delivery receipts; "sent" means accepted by the configured mail server.
- A history of all emails of an invitation in the UI.
- Rate limits per administrator or per address beyond AC-5.
- Configuring the mail server in the UI.

## 2. UI

### Invite dialog

- Below "Roles", when email is enabled: the checkbox "Send the invitation by email to this address" (checked by default).
- On success, the link view as in AC-17 when the box was checked; the existing text otherwise.

```
+-------------------------------------------------+
| Invite a person                                 |
| Email  [ivan@example.com                    ]   |
| Roles  [ ] Administrator [x] Mentor ...         |
| [x] Send the invitation by email to this address|
|                     [Cancel] [Create invitation]|
+-------------------------------------------------+
```

### "Get a new link" confirmation

- Text as today: "Get a new link for <email>? The previous link will stop working." With email enabled, the checkbox "Send the new link by email" (checked by default) below the text.

### Invitations list

- Row actions of a pending invitation, email enabled: "Send email", "Copy link", "Get a new link", "Revoke". Expired: "Get a new link", "Revoke" (as today). Email disabled: as today.
- "Send email" has the accessible name "Send invitation email to <email>". While it runs, the row's actions are disabled (`disabledInteractive`, as for "Copy link").
- Status column, after the existing second line: the `lastEmail` line in secondary text; "Email failed <date>" uses the error color and keeps the words, so the status does not depend on color.

```
| ivan@example.com   Mentor    Pending               18 Oct 2026           |
|                              Expires 25 Oct 2026   Ada Admin             |
|                              Email sent 18 Oct 2026                      |
|              [Send email] [Copy link] [Get a new link] [Revoke]          |
```

### Accessibility

- The checkboxes have visible labels. Success snackbars are polite; the 409 snackbars (`email-queued`, `email-unavailable`) are assertive, as `docs/ui/guidelines.md` requires for 409 messages.

## 3. Design

<Snapshot of the implementation plan; it may drift from the code later. Requirements above stay current.>

### Modules

- New module `com.mentorhub.mail` (planned), an infrastructure module with no domain knowledge:
  - public API: `MailDelivery.isEnabled()` and `MailDelivery.send(MailMessage)` (`to`, `subject`, `text`), which sends synchronously and throws `MailDeliveryException` on failure;
  - internals: a `JavaMailSender` adapter and `MailSenderProperties` (`mentorhub.mail.from`; not `MailProperties`, which is Spring Boot's own class). Whether email is enabled is decided at runtime from `ObjectProvider<JavaMailSender>`, not with `@ConditionalOnMissingBean(JavaMailSender.class)` in application configuration: that condition is evaluated before Boot's mail auto-configuration and would always see no sender;
  - it depends on no other module.
- `com.mentorhub.users` (invitations):
  - `InvitationAdministrationService`: `create` and `reissueLink` take `sendEmail`; new `sendEmail(id)`; each stores the email request and publishes `InvitationEmailRequested(emailRequestId)` in the changing transaction. `sendEmail(id)` first locks the invitation row `for no key update`, as revoke and new link do, so it serializes with them and with acceptance, then checks for a queued request; a `DuplicateKeyException` from the partial unique index still maps to 409 `email-queued`;
  - `InvitationEmailSender` (`users.internal.invitation`): an `@ApplicationModuleListener` for `InvitationEmailRequested`, run after the commit on the async executor, in three steps so that no database transaction or connection is held while the mail server is contacted (deliberate):
    1. a short transaction locks the request row (`for no key update`); a request that is not `QUEUED` is done (AC-12); an invitation that is not pending makes the request `SKIPPED` (AC-10); otherwise it reads the address, the current token and the expiry;
    2. outside any transaction, it builds the text (AC-14) and calls `MailDelivery.send`;
    3. a short transaction records `SENT` or `FAILED` (`update … where status = 'QUEUED'`).

    The event is internal to `users`; the registry is what makes it durable.
  - `InvitationRepository` / a new `InvitationEmailRepository` for the request rows and `lastEmail` in the list query.
- `com.mentorhub.system`: `ClientConfig` gains `invitationEmails`, read from `MailDelivery.isEnabled()` (`system` → `mail`).
- Frontend: the checkboxes, the row action and the status line in `frontend/src/app/features/invitations/`; the client config flag next to the OIDC settings.

### Asynchronous delivery (ADR 0008, planned)

- `spring-modulith-starter-jdbc` adds the event publication registry (JDBC fits ADR 0007; one transaction manager serves both). ADR 0001 expected the JPA starter here; ADR 0008 records the change.
- The registry's `event_publication` table is created by a Flyway migration, not by Modulith's schema initialization.
- Incomplete publications are resubmitted on startup (`spring.modulith.events.republish-outstanding-events-on-restart: true`), which gives AC-12. Completed publications are deleted (`spring.modulith.events.completion-mode: delete`), so the table does not grow.
- A scheduled job (every 5 minutes) resubmits incomplete publications older than 5 minutes (`IncompleteEventPublications.resubmitIncompletePublicationsOlderThan`), which gives AC-13 without a restart. 5 minutes is far longer than one send can take (the mail timeouts), so a send still in progress is not started twice.
- The listener records `FAILED` and completes instead of throwing, so a mail failure is not replayed (AC-11). It throws only on unexpected errors (for example the database is down), which leaves the publication incomplete for the scheduled job or the next start.
- `@EnableAsync` and `@EnableScheduling` on `MentorHubApplication`. Without `@EnableAsync`, `@ApplicationModuleListener` silently runs in the request thread after the commit, and the response waits for the mail server (AC-8). Boot's `applicationTaskExecutor` runs the listener; `spring.task.execution.shutdown.await-termination: true` with a 30 s period lets sends in progress finish on shutdown.
- **Single instance.** Resubmission on startup and by the job does not know about other instances, so with several instances an email in progress could be sent twice. MentorHub runs as one instance; running several needs a new decision (recorded in ADR 0008).
- `spring-boot-starter-mail`. Spring Boot creates the `JavaMailSender` only when `spring.mail.host` is set; that is the "mail server configured" switch.
- Mail timeouts: `spring.mail.properties.mail.smtp.connectiontimeout`, `timeout` and `writetimeout` default to 5 s in `application.yaml`, so a hung server frees the listener thread.

### Data model

Migration `V6__invitation_emails.sql` (planned):

- `invitation_emails`:
  - `id uuid` PK default `gen_random_uuid()`;
  - `invitation_id uuid not null` FK → `invitations`;
  - `reason text not null` check in (`CREATED`, `LINK_REISSUED`, `RESENT`);
  - `requested_at timestamptz not null default now()`, `requested_by uuid not null` FK → `users`;
  - `status text not null` check in (`QUEUED`, `SENT`, `FAILED`, `SKIPPED`);
  - `finished_at timestamptz null`, set exactly when the status is not `QUEUED`;
  - `failure text null`, only with `FAILED`, at most 500 characters, never the token;
  - index on (`invitation_id`, `requested_at desc`) for `lastEmail`;
  - partial unique index on `invitation_id` where `status = 'QUEUED'`: at most one queued email per invitation (AC-5, also under parallel requests).
- `event_publication` as Spring Modulith 2.1's JDBC schema for PostgreSQL requires (copied from the version in use and checked by a test that starts the registry against the migrated schema). Its rows are deleted on completion.
- `invitation_emails` rows are never deleted.

### API

| Method and path | Purpose | Permission | Errors |
|---|---|---|---|
| `POST /api/v1/invitations` | Gains optional `sendEmail` in `InvitationCreate` (AC-2) | `USERS_INVITE` | as today, plus 409 `email-unavailable` |
| `POST /api/v1/invitations/{id}/link` | Gains the optional body `InvitationLinkReissue {sendEmail}` (AC-3) | `USERS_INVITE` | as today, plus 409 `email-unavailable` |
| `POST /api/v1/invitations/{id}/email` | Send the current link by email; no request body; 202 with `Invitation` (AC-4, AC-5) | `USERS_INVITE` | 400, 403, 404, 409 `invitation-closed`, 409 `invitation-expired`, 409 `email-queued`, 409 `email-unavailable` |
| `GET /api/v1/system/client-config` | Gains required `invitationEmails` (AC-1) | public | as today |

- `Invitation` gains optional `lastEmail` (`InvitationEmail`: `status` (`InvitationEmailStatus`), `requestedAt`, `requestedBy` (`HistoryActor`), optional `finishedAt`).
- New problem types: `urn:mentorhub:problem:email-unavailable` (409), `urn:mentorhub:problem:email-queued` (409). The list of MentorHub problem types in the `Problem` description of `api/openapi.yaml` gains both, and the `createInvitation` and `reissueInvitationLink` descriptions mention `sendEmail`.
- No response of the new operation contains the link or the token, so it needs no `Cache-Control: no-store`.

### Authorization

- `@RequirePermission(USERS_INVITE)` on the new controller and service methods, as for the other management operations. The listener runs without a caller and needs no permission; it acts only on stored requests.
- The availability check (`email-unavailable`) runs after the permission check, so a caller without `USERS_INVITE` gets 403 whether or not email is enabled. The full order is AC-6.

### Events

- `InvitationEmailRequested(UUID emailRequestId)`, published and consumed inside `users` through the event publication registry. It carries no address, token or link.

### Configuration

| Property | Meaning |
|---|---|
| `spring.mail.host`, `port`, `username`, `password`, `properties.*` | The outgoing mail server (Spring Boot). Unset host: email disabled. |
| `mentorhub.mail.from` | Sender address, for example `MentorHub <no-reply@example.org>`. Required when the host is set. |
| `dev` profile | `spring.mail.host: localhost`, `port: 1025` (Mailpit), `mentorhub.mail.from: MentorHub <no-reply@mentorhub.local>` |
| `spring.task.execution.shutdown.*` | Await termination on shutdown, 30 s (see Asynchronous delivery) |

`TestMentorHubApplication` also runs `dev` but starts no Mailpit, so emails there end `FAILED`; that is expected and documented, not fixed. Whether Spring Boot 4.1's Docker Compose support wires Mailpit as a mail service connection by itself is checked in the step; if it does, the explicit `dev` settings must not conflict with it.

### Tests

- The listener against a real SMTP server: a Mailpit Testcontainer whose HTTP API the test reads, or GreenMail (decided in the step, checked with `api-lookup`).
- After-commit and rollback behavior (AC-8, AC-9), the restart case (AC-12) and the stale resubmission (AC-13) with `@ApplicationModuleTest` and Modulith's `Scenario`, or the registry's incomplete publications.
- To check in the step with `api-lookup`, against the jars in use: the names of the Modulith properties above, `IncompleteEventPublications`, and the `event_publication` schema of Spring Modulith 2.1.
- AC-19 also checks the received email in one autolinking client (the Mailpit UI is enough) to confirm that the link opens unchanged.
- The "disabled" path runs in the default test configuration (no host); the "enabled" path in its own configuration.
- AC-15: tests check that no log line, audit detail, request row or registry row contains the token.
- Frontend unit tests: both modes of the dialog and the confirmation, the row action and its errors, each status line.

### Documentation updates

- `docs/adr/0008-asynchronous-events.md` (planned): the event publication registry with JDBC, its migration, completion mode and the restart policy.
- `docs/adr/0001-modular-monolith.md`: the consequence line points to ADR 0008.
- `docs/development/backend.md`: the `mail` module, events through the registry (`@EnableAsync` as a pitfall, the single-instance assumption), mail configuration and the optional mail server, the `FAILED` emails of `TestMentorHubApplication`, and `mail.debug` next to the bind-parameter logging warning.
- `docs/features/invitations.md`: the changes listed in "Changes to `docs/features/invitations.md`", with a "Revised" line.
- `api/openapi.yaml`: the descriptions listed under API.
- `docs/ui/guidelines.md`: the "Send email" row action and the email line in the list.
- `README.md`: invitation emails arrive in Mailpit.
- `infra/compose.yaml`: the Mailpit comment also names MentorHub's emails.

### Decisions

Recovered from the repository:

- Invitation emails are their own feature after user administration and invitations (user decision, `docs/features/user-administration.md`, `docs/features/invitations.md`).
- Mailpit and Keycloak's mail settings were split off and shipped first as `local-mail` (PR #10): the spec rule against mixing application code with new local infrastructure.
- Before approval the spec was to be reworked for plaintext tokens, resending without a new link, and asynchronous sending (`docs/features/invitation-links.md`, Documentation updates). The plaintext token makes "Send email" possible without replacing the link.
- The mail server is optional, and sending is asynchronous (the user, 2026-10-23, from memory of the lost draft).

Proposed in this reconstruction and confirmed by the user (2026-10-23):

- Durable asynchronous delivery through the Spring Modulith event publication registry, as ADR 0001 anticipated, but with the JDBC starter instead of the JPA one (ADR 0007); recorded in ADR 0008.
- A separate `mail` infrastructure module; the invitation text and the decision what to send stay in `users`.
- The email request row stores no link; the link is built from the current token when the email is sent, and closed invitations are skipped.
- One queued email per invitation; no automatic retries; failures are shown in the list.
- The availability of email is a public flag in the client configuration.
- The email checkbox is checked by default; the list shows only the latest email of an invitation.
- The email is impersonal and does not name the inviter (user decision, instead of the proposed inviter name).

Accepted after the spec review (user decision, 2026-10-23):

- A new link with `sendEmail` while an email is queued issues the link and lets the queued email carry it, instead of 409.
- Stale incomplete publications are resubmitted every 5 minutes, so a request cannot stay `QUEUED`.
- The send runs outside database transactions; a change that commits during the send is an accepted edge case instead of a lock held for the mail timeouts.
- The link stands on its own line in the email; the expiry shows time and UTC.
- One instance is assumed for delivery.

## 4. Open questions

None.
