# Invitation emails

## Purpose

An administrator can pass an invitation link on by hand, or let MentorHub email it to the invited address: when the invitation is created, when a new link is given, and again at any time while the invitation is pending. A mail server is optional and configured per installation; without one, invitations work as the invitations capability describes and offer no email options. Sending is asynchronous (decision 0006): a slow or unreachable mail server never fails or delays the administrator's request, a requested email is not lost when the application stops, and the outcome of the latest email of each invitation is shown with the invitation. Creating, listing and revoking invitations, links and accepting are in the invitations capability (INV); who may act is in the authorization capability (PERM); audit entries are in the audit capability (AUD). The API contract holds the request flag, the response shapes and the problem types.

## Requirements

### IEM-1 Email is available only with a mail server

The system SHALL offer invitation emails only when a mail server is configured (PLT-4.1). Without one, a request that asks for an email SHALL be refused with 409 `email-unavailable` and change nothing. With one, the application SHALL refuse to start unless `mentorhub.mail.from` is exactly one address (PLT-5).

#### IEM-1.1 Without a mail server emails are refused

- GIVEN no mail server is configured and a pending invitation
- WHEN an administrator creates an invitation with `sendEmail` true, gives the pending invitation a new link with `sendEmail` true, or asks to send its email
- THEN each response is 409 `email-unavailable`
- AND nothing is created, changed or audited

#### IEM-1.2 A mail server needs exactly one sender address

- GIVEN a mail server is configured and the sender address is missing, malformed or holds several addresses
- WHEN the application starts
- THEN it fails to start
- AND the failure names `mentorhub.mail.from`

#### IEM-1.3 Without a mail server invitations work as before

- GIVEN no mail server is configured and a pending invitation
- WHEN an administrator creates an invitation without asking for an email, reads the link of the pending one and gives it a new link
- THEN creating answers 201 and the new link answers 200, each with the link
- AND the invitations have no latest email

### IEM-2 Requesting an email needs USERS_INVITE

The system SHALL let only a holder of `USERS_INVITE` request an invitation email, and SHALL let any holder do it for any invitation, not only their own (PERM-3, INV-1). Others get 403 `forbidden` and nothing changes; the refusal precedes any lookup of the invitation (PERM-6).

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

The system SHALL, when email is enabled and an administrator creates an invitation or gives a new link with `sendEmail` true, also request an email with the link, audited with its reason (AUD-7), and respond as without the flag. While the latest email is still queued, a new link requests no second email and writes no second audit entry.

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

The system SHALL let a holder of `USERS_INVITE` request, at any time, an email with the current link of a pending invitation, leaving the link and the expiry unchanged, audited with its reason (AUD-7). It is refused as reading a link is (INV-8), and with 409 `email-queued` while the latest email is still queued.

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

The system SHALL check a request that asks for an email, after authentication and the permission (INV-1), in this order: the request body, the invitation (including 409 `user-active` and `invitation-exists` when creating), whether email is enabled, whether an email is already queued. A request that cannot succeed for a reason of the invitation gets that reason, whether or not email is enabled.

#### IEM-5.1 An invitation or request problem is reported before the missing mail server

- GIVEN no mail server is configured, a revoked invitation and an open invitation for an email
- WHEN an administrator creates an invitation with a malformed email and `sendEmail` true, asks to send the email of the revoked invitation, and creates an invitation for the open one's email with `sendEmail` true
- THEN the responses are 400 `validation-failed`, 409 `invitation-closed` and 409 `invitation-exists`

### IEM-6 Delivery

The system SHALL, once the request that asked for an email is committed, send one email with the current link, answering the request before the mail server is contacted, and record it as sent, failed (refused, unreachable or no mail server) or skipped (the invitation no longer pending). Only an email still queued is sent, also after a restart or an unexpected failure (decision 0006); sending changes nothing in the invitation.

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

#### IEM-6.4 A refused email is recorded as failed

- GIVEN a queued email and a mail server that refuses the message
- WHEN the email is processed
- THEN the invitation's latest email is failed, with the time
- AND the invitation is unchanged

#### IEM-6.5 An email left unsent by a stop is sent once after the next start

- GIVEN an email requested and committed, and the application stopped before sending it
- WHEN the application starts again
- THEN the email is sent once and recorded as sent

#### IEM-6.6 An unexpected failure is processed again

- GIVEN an email left queued because its processing failed unexpectedly
- WHEN time passes without a restart
- THEN it is processed again and sent

#### IEM-6.7 An email replayed without a mail server fails

- GIVEN a queued email, and the application started without a mail server
- WHEN the email is processed
- THEN the invitation's latest email is failed
- AND no email is sent

#### IEM-6.8 A failed email is not sent again after a restart

- GIVEN an email recorded as failed
- WHEN the application is restarted with a working mail server
- THEN the email is not sent and stays failed

#### IEM-6.9 An email that is already done is never sent again

- GIVEN an email recorded as sent, failed or skipped
- WHEN a request to send that email is delivered once more
- THEN nothing is sent
- AND the email keeps its status

#### IEM-6.10 A rolled-back request sends nothing

- GIVEN a request that created an invitation and asked for an email
- WHEN the change is rolled back
- THEN no email is sent
- AND nothing is stored

### IEM-7 What the email contains

The system SHALL send the invitation email as plain text in English from the configured sender address to the invitation's address. The body names the invited address, holds the link alone on its own line and gives the expiry in UTC; it holds no other data about the invitation, such as its roles or who created it.

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

The system SHALL NOT write the token or the link of an invitation to a log, to the recorded reason of a failed email or to an audit entry (AUD-5), and SHALL NOT log a message body. The reason of a failure is for the operator and is never returned by the API (IEM-9).

#### IEM-8.1 Sending and failing leave no token in the logs

- GIVEN a pending invitation whose email is sent, and another whose email the mail server refuses
- WHEN both are processed
- THEN no log line contains either token or link

### IEM-9 The outcome of the latest email

The system SHALL return, with every invitation it shows to a holder of `USERS_INVITE` (INV-1), the latest email requested for it, when there is one: its status, when and by whom it was requested and, once it is no longer queued, when it finished. Earlier emails and the reason of a failure are not returned, and the email never changes the status of the invitation. The status tells the administrator:

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
- AND the failed one has the status failed
- AND the last has no latest email

#### IEM-9.2 The reason of a failure is not returned

- GIVEN invitations whose emails failed with a reason from the mail server
- WHEN an administrator lists the invitations
- THEN the response holds no reason of any failure

### IEM-10 The client offers the email only when it is enabled

The client SHALL offer every email option (the choice with a new invitation, the choice with a new link, the row action and the email line, IEM-13) only when the client configuration says that invitation emails are enabled, and SHALL send `sendEmail` only when it offers the choice. Both choices are on by default.

### IEM-11 Asking for an email with a new invitation or a new link in the client

The client SHALL request the email when the choice is on, and the link view (INV-19) SHALL then say that an email with the link is on its way to the invited address.

#### IEM-11.1 An unavailable mail server is explained

- GIVEN email is enabled on the page and the server answers 409 `email-unavailable`
- WHEN the user creates an invitation in the invite dialog, or confirms a new link with the choice on
- THEN the client says that sending email is not available and the page should be reloaded
- AND the invite dialog stays open with its values, and the confirmation of the new link closes

### IEM-12 The row action to send an email

The client SHALL offer the action to send an email on the rows of pending invitations only, before copying the link (INV-17), and send without a confirmation, and SHALL explain a refusal.

#### IEM-12.1 A queued email is explained

- GIVEN a pending row and the server answers 409 `email-queued`
- WHEN the user sends the email
- THEN the client says that an email is already on its way
- AND the list reloads

#### IEM-12.2 An unavailable mail server is explained on the row

- GIVEN a pending row and the server answers 409 `email-unavailable`
- WHEN the user sends the email
- THEN the client says that sending email is not available and the page should be reloaded

#### IEM-12.3 A closed or unknown invitation is explained

- GIVEN a pending row and the server answers 409 `invitation-closed` or 404
- WHEN the user sends the email
- THEN the client explains it as for copying a link
- AND the list reloads

### IEM-13 The email line of the invitation status

The client SHALL show, with the status of every invitation that has a latest email, whatever its status, an email line telling the state of the email as the table of IEM-9 defines it, and SHALL tell a failure in words and not by color alone. The list does not refresh by itself: it shows a new state when it is loaded again.

## Known limitations

- Delivery is at least once: an email can be sent twice when the application stops after the mail server accepted it and before the result was recorded.
- The email is built from the invitation as read just before the mail server is contacted, and the invitation is not locked meanwhile. A revoke, an acceptance or a new link in that short time is not seen, and the email can carry a link that is already dead. The administrator sends again.
- A failed email is not retried by the system. Only the administrator retries, from the list.
- The list does not poll: an email shown as being sent stays so until the list is loaded again.
- Delivery assumes a single instance of the application (decision 0006).

## Out of scope

- Delivery tracking beyond the mail server's acceptance: bounces, opens and clicks.
- Emails other than the invitation email, and a choice of language, template or sender per invitation. The emails the identity provider sends for registration and password reset are not MentorHub's.
- A notification to the administrator when an email fails.
- Automatic retry of a failed email.
- Running several instances of the application, which needs a new decision.
