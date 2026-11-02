# Invitation emails

## Purpose

An administrator can pass an invitation link on by hand, or let MentorHub email it to the invited address: when the invitation is created, when a new link is given, and again at any time while the invitation is pending. A mail server is optional and configured per installation; an installation without one works as the invitations capability describes and offers no email options. Sending is asynchronous (decision 0008): a slow or unreachable mail server never fails or delays the administrator's request, a requested email is not lost when the application stops, and the outcome of the latest email of each invitation is shown with the invitation. This capability covers requesting an email, sending it, what it contains, its delivery status and the client parts that request and show it. Creating, listing and revoking invitations, links and accepting are in the invitations capability (INV); who may act is in the authorization capability (PERM); the entries of the audit are in the audit capability (AUD).

## Requirements

### IEM-1 Email is available only with a mail server

The system SHALL offer invitation emails only when a mail server is configured, and SHALL start and work as before when none is configured. Whether emails can be requested is told in the client configuration (PLT-4.1). When no mail server is configured, a request that asks for an email, whether with a new invitation, with a new link or by sending again, SHALL be refused with 409 `email-unavailable` and SHALL change nothing, while a request that does not ask for one (the flag `sendEmail` false or absent) SHALL work as usual. When a mail server is configured, the sender address `mentorhub.mail.from` is required: the application SHALL refuse to start, naming the setting, if it is missing or is not exactly one address, with an optional display name (PLT-5). Without a mail server the sender address is not checked.

#### IEM-1.1 Without a mail server emails are refused and invitations work as before

- GIVEN no mail server is configured
- WHEN an administrator creates an invitation with `sendEmail` true, gives a pending invitation a new link with `sendEmail` true, asks to send the email of a pending invitation, and creates an invitation without asking for an email
- THEN the first three are 409 `email-unavailable` and nothing is created or changed
- AND the last is 201 with the invitation and its link
- AND the client configuration says that invitation emails are not available

#### IEM-1.2 A mail server needs exactly one sender address

- GIVEN a mail server is configured and the sender address is missing, malformed or holds several addresses
- WHEN the application starts
- THEN it fails to start
- AND the failure names `mentorhub.mail.from`

### IEM-2 Requesting an email needs USERS_INVITE

The system SHALL let only a caller who holds `USERS_INVITE` request an invitation email, and SHALL let any such holder do it for any invitation, not only for the ones they created (PERM-3, INV-1). A caller without the permission SHALL be refused with 403 `forbidden` and nothing SHALL change; the refusal precedes any lookup of the invitation (PERM-6). The latest email of an invitation is visible only to holders of the permission, with the invitation (INV-1). Whether email is enabled is public (PLT-4.1).

#### IEM-2.1 Requesting an email needs the permission

- GIVEN email is enabled and a pending invitation
- WHEN a user without `USERS_INVITE` creates an invitation with `sendEmail` true, asks for a new link with `sendEmail` true, or asks to send the email again
- THEN each response is 403 `forbidden`
- AND no invitation is created and no email is requested

#### IEM-2.2 Any holder sends the email of any invitation

- GIVEN email is enabled and a pending invitation created by one administrator
- WHEN another holder of `USERS_INVITE` asks to send the email again
- THEN the response is 202

### IEM-3 Asking for an email with a new invitation or a new link

The system SHALL, when an administrator creates an invitation or gives a new link with the optional flag `sendEmail` true and email is enabled, request an email with the link in the same change, and SHALL respond as it does without the flag, with the invitation carrying the requested email as queued (IEM-9). The flag is false by default, and the request is audited with its reason (AUD-7). When the invitation is not created because the request fails, no email is requested and nothing is audited (AUD-1). When the latest email of the invitation is still queued, a new link with the flag SHALL be issued as usual without a second request and without a second audit entry; the response carries the queued email, which builds its link when it is sent and so carries the new link.

#### IEM-3.1 An email is queued with a new invitation or a new link

- GIVEN email is enabled and a pending invitation
- WHEN an administrator creates an invitation with `sendEmail` true, and gives the pending invitation a new link with `sendEmail` true
- THEN creating answers 201 and the new link answers 200, as without the flag, each with the link
- AND the latest email of each invitation is queued, with the time and the administrator

#### IEM-3.2 Without the flag no email is requested

- GIVEN email is enabled
- WHEN an administrator creates an invitation without the flag, or gives a pending invitation a new link without a request body
- THEN creating answers 201 and the new link answers 200, each with the link
- AND the invitations have no latest email
- AND no email is sent

#### IEM-3.3 A new link while an email is queued requests no second one

- GIVEN email is enabled and an invitation whose latest email is queued
- WHEN an administrator gives it a new link with `sendEmail` true
- THEN the new link is issued and the response carries the queued email
- AND no second email is requested and no second audit entry is written

### IEM-4 Sending the email of a pending invitation again

The system SHALL let a holder of `USERS_INVITE` request, at any time, an email with the current link of a pending invitation, and SHALL respond 202 with the invitation carrying the email as queued. The operation has no request body, changes neither the link nor the expiry, and is audited with its reason (AUD-7). It is refused with 409 `invitation-closed` for an accepted or a revoked invitation, 409 `invitation-expired` for an expired one, as for reading a link (INV-8), and 404 `not-found` for an unknown invitation. While the latest email of the invitation is still queued, it is refused with 409 `email-queued` and changes nothing, so a repeated click sends one email.

#### IEM-4.1 A pending invitation is sent again

- GIVEN email is enabled and a pending invitation
- WHEN an administrator asks to send the email again
- THEN the response is 202 with the invitation
- AND its latest email is queued
- AND its link and expiry are unchanged

#### IEM-4.2 An invitation that is not pending is refused

- GIVEN email is enabled, an accepted, a revoked and an expired invitation, and an id that does not exist
- WHEN an administrator asks to send the email for each
- THEN the accepted and the revoked ones are 409 `invitation-closed`
- AND the expired one is 409 `invitation-expired`
- AND the unknown one is 404 `not-found`

#### IEM-4.3 A queued email is not queued twice

- GIVEN email is enabled and an invitation whose latest email is queued
- WHEN an administrator asks to send the email again
- THEN the response is 409 `email-queued`
- AND no second email is requested

#### IEM-4.4 Two requests at the same moment queue one email

- GIVEN email is enabled and a pending invitation with no queued email
- WHEN two requests to send the email again arrive at the same moment
- THEN exactly one is answered 202 and the other 409 `email-queued`
- AND exactly one email is requested

### IEM-5 The order of refusals

The system SHALL check a request that asks for an email in this order, after the authentication, the gate and the permission (INV-1): the request body (400 `validation-failed`), the invitation (404 `not-found`, then 409 `invitation-closed` or `invitation-expired`; for creating, 409 `user-active` or `invitation-exists`), then whether email is enabled (409 `email-unavailable`), then whether an email is already queued (409 `email-queued`). A request that cannot succeed for a reason of the invitation therefore gets that reason, whether or not email is enabled, and requests no email.

#### IEM-5.1 An invitation or request problem is reported before the missing mail server

- GIVEN no mail server is configured, a revoked invitation and an open invitation for an email
- WHEN an administrator creates an invitation with a malformed email and `sendEmail` true, asks to send the email of the revoked invitation, and creates an invitation for the open one's email with `sendEmail` true
- THEN the responses are 400 `validation-failed`, 409 `invitation-closed` and 409 `invitation-exists`

### IEM-6 Delivery

The system SHALL, after the change that requested an email is stored, send one email to the invitation's address with the link that is current when the email is built, and then record the email as sent with the time. The administrator's request SHALL be answered before the mail server is contacted, also when the mail server does not answer. When the request is rolled back, no email SHALL be sent. Sending never changes the invitation: its status, link and expiry are as they were. When the invitation is no longer pending when the email is about to be sent (accepted, revoked or expired since the request), no email SHALL be sent and the email is recorded as skipped. When the mail server refuses the message or cannot be reached within its time limits, the email is recorded as failed with the time, and the failure is logged without the token or the message (IEM-8). A failed email is not sent again by the system: the administrator sends it again (IEM-4). An email requested before the application stopped and not sent SHALL be sent once after the next start, and an email that is no longer queued SHALL never be sent again. When processing an email fails unexpectedly and leaves it queued, it SHALL be processed again within ten minutes without a restart, so that neither the invitation list nor a new request stays blocked by it (decision 0008). A queued email replayed when no mail server is configured is recorded as failed and nothing is sent.

#### IEM-6.1 A queued email is sent with the current link and recorded

- GIVEN email is enabled and a pending invitation with a queued email, given a new link after the request
- WHEN the email is processed
- THEN one email is delivered to the invitation's address with the new link
- AND the invitation's latest email is sent, with the time
- AND the invitation is unchanged

#### IEM-6.2 The request does not wait for the mail server

- GIVEN email is enabled and a mail server that accepts the connection and does not answer
- WHEN an administrator asks to send the email again
- THEN the response is 202 at once, without waiting for the mail server

#### IEM-6.3 An invitation that is no longer pending is skipped

- GIVEN a queued email of an invitation that was accepted, revoked or has expired since the request
- WHEN the email is processed
- THEN no email is sent
- AND the invitation's latest email is skipped, with the time

#### IEM-6.4 A refused email is recorded as failed and not sent again

- GIVEN a queued email and a mail server that refuses the message
- WHEN the email is processed, then time passes and the application is restarted
- THEN the invitation's latest email is failed, with the time, and the invitation is unchanged
- AND a warning is logged
- AND the email is not sent again

#### IEM-6.5 An email left unsent by a stop is sent once after the next start

- GIVEN an email requested and committed, and the application stopped before sending it
- WHEN the application starts again, and a request to send an already sent, failed or skipped email is delivered once more
- THEN the first email is sent once and recorded as sent
- AND nothing is sent for the email that is already done

#### IEM-6.6 An unexpected failure is processed again

- GIVEN an email left queued because its processing failed unexpectedly
- WHEN ten minutes pass without a restart
- THEN it is processed again
- AND a later request to send the email again is not refused as queued once it is done

#### IEM-6.7 An email replayed without a mail server fails

- GIVEN a queued email, and the application started without a mail server
- WHEN the email is processed
- THEN the invitation's latest email is failed
- AND no email is sent

### IEM-7 What the email contains

The system SHALL send the invitation email as plain text in English, from the configured sender address, to the invitation's email, with a subject that invites the person to MentorHub. The body names the invited address to sign in or create an account with, holds the link alone on its own line, so that no mail client takes punctuation into it, gives the expiry with its time and zone in UTC, and says to ignore the message if the invitation was not expected. The message is impersonal: it names no administrator and holds no data about the invitation beyond the address, the link and the expiry.

#### IEM-7.1 The sender, the recipient and the body

- GIVEN a pending invitation created by a named administrator, with roles, and a configured sender address
- WHEN the email is sent
- THEN it is plain text from the sender address to the invitation's email
- AND the body names the invited address, holds the link alone on its own line and gives the expiry in UTC
- AND it contains neither the administrator's name nor the invitation's roles

#### IEM-7.2 The message reads well in a real mail client (manual)

- GIVEN the local environment with a mail catcher
- WHEN an invitation email is opened in a real mail client
- THEN the link is clickable without any punctuation after it
- AND the message reads as an invitation to sign in with the stated address

### IEM-8 The token stays out of logs and records

The system SHALL NOT write the token or the link of an invitation to any log, to the reason recorded for a failed email, or to an audit entry (AUD-5), and SHALL NOT log a message body. The failure reason is kept for the operator, who reads it in the server log, and is not returned by the API (IEM-9).

#### IEM-8.1 Sending and failing leave no token in the logs

- GIVEN a pending invitation whose email is sent, and another whose email the mail server refuses
- WHEN both are processed
- THEN no log line contains either token or link

### IEM-9 The outcome of the latest email

The system SHALL return, in every response that returns an invitation, the latest email requested for it, when there is one: its status, the time it was requested, who requested it and, once it is no longer queued, when it finished. Earlier emails are not shown and the reason of a failure is not returned. An invitation for which no email was ever requested has no latest email, whatever its status (INV-5). The email never changes the status of the invitation. The status tells the administrator:

| Status | Meaning | What the administrator can do |
|---|---|---|
| queued | requested, not processed yet | wait; sending again is refused |
| sent | accepted by the mail server (not proof that it was read) | send again if the person did not get it |
| failed | the mail server refused it or could not be reached | send again |
| skipped | the invitation was no longer pending when the email was due | give a new link if needed, then send |

#### IEM-9.1 The list shows only the latest email

- GIVEN an invitation whose first email failed and whose second was sent, another whose email is queued, one whose email failed, and one with no email requested
- WHEN an administrator lists the invitations
- THEN the first has the second email, with the status sent, the time requested, who requested it and when it finished
- AND the queued one has no finish time
- AND the failed one has the status failed and no reason of the failure
- AND the last has no latest email

### IEM-10 The client offers the email only when it is enabled

The client SHALL offer every email option (the choice to send with a new invitation, the choice to send with a new link, the action to send an email of a pending invitation and the email line, IEM-13) only when the client configuration says that invitation emails are enabled, and SHALL send the flag `sendEmail` only when it offers the choice. Both choices are on by default. Without a mail server the dialogs and the list work as the invitations capability describes (INV-19, INV-16).

#### IEM-10.1 Email options follow the configuration

- GIVEN a user with `USERS_INVITE`
- WHEN the user opens the invite dialog, the new-link confirmation and a pending row, with email enabled, and again with email disabled
- THEN with email enabled each offers its email option, the two choices being on by default
- AND with email disabled none is offered and the requests carry no flag `sendEmail`

### IEM-11 Asking for an email with a new invitation or a new link in the client

The client SHALL request the email with the invitation or the new link when the choice is on, and SHALL NOT when it is off. After success with the choice on, the link view (INV-19) SHALL say that an email with the link is on its way to the invited address, with the expiry, and that the link can also be copied, including from the list while the invitation is pending; with the choice off, the link view is the usual one. When the server answers 409 `email-unavailable` (the configuration changed after the page loaded), the client SHALL say that sending email is not available and the page should be reloaded: the invite dialog stays open with its values, and the new-link confirmation closes with the message as a notification.

#### IEM-11.1 The choice decides the request and the link view says the email is on its way

- GIVEN email is enabled and a valid invitation, in the invite dialog and in the new-link confirmation
- WHEN the user creates the invitation or confirms the new link with the choice on, and again with the choice off
- THEN with the choice on the request asks for the email and the link view says that an email with the link is on its way to the invited address, and still shows the link and the expiry
- AND with the choice off the request asks for no email and the link view is the usual one

### IEM-12 The row action to send an email

The client SHALL offer the action to send an email on the row of a pending invitation when email is enabled, before copying the link (INV-16.2, INV-17), and on no other row. It sends without a confirmation, shows at once that an email is being sent and says that the email to the address is queued. When the server answers 409 `email-queued`, the client says that an email is already on its way and reloads the list; when it answers 409 `email-unavailable`, it says that sending email is not available and the page should be reloaded; a closed, expired or unknown invitation is explained and the list reloaded, as for copying a link (INV-17).

#### IEM-12.1 The action is offered on pending rows and sends at once

- GIVEN email is enabled, a pending invitation, and an expired, an accepted and a revoked one
- WHEN the list shows their rows and the user sends the email of the pending one
- THEN only the pending row offers the action
- AND no confirmation is asked, the row shows that an email is being sent and the client says that the email to the address is queued

#### IEM-12.2 Conflicts are explained

- GIVEN a pending row and the server answers 409 `email-queued`, 409 `email-unavailable`, 409 `invitation-closed` or 404
- WHEN the user sends the email
- THEN the client tells the matching reason and the user stays on the page
- AND the list reloads for a queued email and for a closed or unknown invitation

### IEM-13 The email line of the invitation status

The client SHALL show, with the status of every invitation that has a latest email, whatever the status of the invitation, an email line that tells the state of the email as the table of IEM-9 defines it: being sent while queued, sent or failed with the date it finished, and not sent when skipped. A failure SHALL be told in words and not by color alone. An invitation without a latest email has no email line. The list does not refresh by itself: it shows a new state when it is loaded again.

#### IEM-13.1 The email line tells the state

- GIVEN invitations whose latest email is queued, sent, failed or skipped, an accepted invitation whose email was sent, and one with no email
- WHEN the list shows their rows
- THEN each row with an email tells its state in words, with the finish date for sent and failed, next to the status of the invitation
- AND the row with no email has no email line

## Known limitations

- Delivery is at least once: an email can be sent twice when the application stops after the mail server accepted it and before the result was recorded.
- The email is built from the invitation as read just before the mail server is contacted, and the invitation is not locked meanwhile. A revoke, an acceptance or a new link in that short time is not seen, and the email can carry a link that is already dead. The administrator sends again.
- A failed email is not retried by the system. Only the administrator retries, from the list.
- The list does not poll: an email shown as being sent stays so until the list is loaded again.
- Delivery assumes a single instance of the application (decision 0008).

## Out of scope

- Delivery tracking beyond the mail server's acceptance: bounces, opens and clicks.
- Emails other than the invitation email, and a choice of language, template or sender per invitation. The emails the identity provider sends for registration and password reset are not MentorHub's.
- A notification to the administrator when an email fails.
- Automatic retry of a failed email.
- Running several instances of the application, which needs a new decision.
