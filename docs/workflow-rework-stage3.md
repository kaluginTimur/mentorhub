# Workflow rework: stage 3 working notes

Status: temporary. Collected by the subagents that move the feature specs into `docs/requirements/`. Disputed points go to the user; the map is the input for stage 4. Deleted at the end of stage 4.

## Disputed points

User decision, 2026-11-03: every point below keeps the code as it is, and the documents follow it. PERM-D1 is stated as a known limitation. AUD-D1 is a known limitation. AUD-D2, IDN-D1, USR-D1 and IEM-D1 stay as behavior in the requirements. The user also accepted three points the subagents settled without a row: the bootstrap is audited, the current-user data includes the phone, and changing users needs `USERS_MANAGE`.

| Id | Requirements | What the sources say | What the code does | Provisional text | Options |
|---|---|---|---|---|---|
| PERM-D1 | PERM-6; platform, Known limitations | docs/features/invitation-emails.md:60 says errors are checked in the order authentication, gate, permission (403), then 400 | `backend/src/main/java/com/mentorhub/users/internal/web/UsersController.java:81` and `backend/src/main/java/com/mentorhub/users/internal/web/InvitationsController.java:53` check the permission with method security on the controller; Spring binds and validates a request body before that runs, so an invalid body from a caller without the permission probably gets 400; no test covers it | 403 is checked before the resource is looked up; the order against a body validation error is not stated | State 403 before 400 and change the code, or state that body validation may answer 400 first and edit the older text |
| AUD-D1 | AUD-6 | docs/features/user-administration.md:97 says a history is 25 per page and leaves out entries with an unknown action | `backend/src/main/java/com/mentorhub/users/internal/service/UserAdministrationService.java:117` reads a page of entries and then drops the unknown ones; `totalItems` counts all stored entries including the dropped ones (`backend/src/main/java/com/mentorhub/audit/internal/persistence/AuditLogRepository.java:63`) | A page may hold fewer items than its size, and `totalItems` may exceed the entries that can be shown; stated as a known limitation of AUD | Leave as is and state it as a limitation; or filter before paging so pages are full and totals match |
| AUD-D2 | AUD-6 | docs/features/user-administration.md:97 and decision 0006 mention only entries with an unknown action being skipped | `backend/src/main/java/com/mentorhub/users/internal/persistence/UserAuditTrail.java:78` also skips, with a warning, an entry of a known action whose details cannot be read | AUD-6 also leaves out an unreadable entry, with a warning (AUD-6.5) | Add the rule that an unreadable entry is skipped with a warning; or make an unreadable entry fail the history so it is noticed |
| IDN-D1 | IDN-10 statement, IDN-10.4 | docs/features/identity-foundation.md:208 (access screens: states are loading and the error state only) and docs/features/identity-foundation.md:45 (S2, S3, S5 mention no recheck) | `frontend/src/app/features/access/access-screen.ts:61` reloads the current user when the browser tab becomes visible and when another tab reports a change of access; `frontend/src/app/core/auth/access-channel.ts:5` | An access screen rechecks access on tab focus and on a report from another tab | Keep it as a requirement; or drop the behavior and leave the recheck to a manual reload |
| USR-D1 | USR-8 | docs/features/user-administration.md:89 (AC-18) says the phone must be a valid number and is stored in E.164; extensions are not mentioned | `backend/src/main/java/com/mentorhub/users/internal/service/ProfileRules.java:57` rejects a valid number that has an extension | A phone with an extension is rejected as invalid | Keep the rejection and state it as a rule; or accept extensions and decide how they are stored |
| IEM-D1 | IEM-6.7 | docs/features/invitation-emails.md does not say what happens to a queued email replayed when no mail server is configured (AC-12, AC-8 only describe sending) | `backend/src/main/java/com/mentorhub/users/internal/invitation/InvitationEmailSender.java:126` records such an email as failed with a warning and sends nothing; no test covers it | The email is recorded as failed without sending | Keep it as a requirement; or leave the email queued until a mail server is configured again |

## Acceptance criteria map

Every criterion of the five specs, by spec. Scenarios, or the reason it was not moved.

| Spec | Criterion | Where it went |
|---|---|---|
| identity-foundation | AC-7 | PERM-2.2 (the API part); the access screen "Your access is being set up" is for identity |
| identity-foundation | AC-17 | PERM-3.1, PERM-3.2 |
| identity-foundation | AC-18 | not moved: a build check of a code convention (every operation declares its permission); belongs in the backend guide |
| identity-foundation | AC-19 | PERM-6.3 |
| identity-foundation | AC-20 | PERM-5.1, PERM-5.2 |
| identity-foundation | AC-21 | not moved: build checks that code enums equal the contract and the stored roles; belongs in the backend guide (the role and permission lists are in PERM-1) |
| identity-foundation | AC-22 | PERM-4.1 |
| identity-foundation | AC-29 | PERM-7.3 |
| identity-foundation | AC-2 | not moved to platform: the 401 Problem and its header belong to identity |
| identity-foundation | AC-24 | PLT-4.2 |
| identity-foundation | AC-25 | PLT-6.1, PLT-6.2 (the development mode scenario is dropped: tooling only, not a user-facing rule) |
| identity-foundation | AC-26 | PLT-5.1 (the other required settings go to their own capabilities) |
| identity-foundation | AC-27 | PLT-7.1, PLT-7.2 |
| identity-foundation | AC-28 | PLT-8.1 (the startup error screen; the retry from a clean address is in the same scenario); the sign-in retry rule is for identity |
| user-administration | AC-3 | PLT-3.3 (the rule for page and size); the list itself stays with user-administration |
| user-administration | AC-1 | PLT-3.1, PLT-3.2 (paging only, page past the last included); the list content and order stay with user-administration |
| invitations | AC-4 | PLT-3.1, PLT-3.2 (paging only, page past the last included); the list content and order stay with invitations |
| invitation-emails | AC-1 | IEM-1.1, IEM-1.2 (the flag true is PLT-4.1; a sender address with a display name is stated in IEM-1, no scenario) |
| user-administration | AC-6 | audit part: AUD-7; the status change stays with user-administration |
| user-administration | AC-8 | audit part: AUD-7; the rest stays with user-administration |
| user-administration | AC-9 | audit part: AUD-4.2 (no entry when nothing changes, shown with roles); the rest stays with user-administration |
| user-administration | AC-10 | audit part: AUD-7, AUD-4.2; the rest stays with user-administration |
| user-administration | AC-12 | audit part: AUD-7; the rest stays with user-administration |
| user-administration | AC-16 | audit part: AUD-7; the rest stays with user-administration |
| user-administration | AC-17 | audit part: AUD-7; the rest stays with user-administration |
| user-administration | AC-19 | the rules of a view: AUD-6.2, AUD-6.3, AUD-6.4, AUD-6.5; what the history shows to an administrator stays with user-administration |
| user-administration | AC-20 | AUD-7.1, AUD-7.2; clearing the reason and comment on enable stays with identity |
| user-administration | AC-21 | AUD-1.1, AUD-3.1 |
| user-administration | AC-13 | audit part: AUD-4.1; the refusal stays with user-administration |
| invitations | AC-1 | audit part: AUD-7; the rest stays with invitations |
| invitations | AC-6 | audit part: AUD-7; the rest stays with invitations |
| invitations | AC-7 | audit part: AUD-7; the rest stays with invitations |
| invitations | AC-16 | AUD-7, AUD-4.1 (rejected and repeated acceptance); the rest stays with invitations |
| invitation-links | AC-7 | AUD-5, AUD-4 (reading a link is not audited); the token outside the audit stays with invitations |
| invitation-emails | AC-2 | IEM-3.1, IEM-3.2, IEM-5.1 (a refused creation requests no email; the audit part is AUD-7) |
| invitation-emails | AC-3 | IEM-3.1, IEM-3.2, IEM-3.3 |
| invitation-emails | AC-4 | IEM-4.1, IEM-4.2 (the entry is AUD-7) |
| invitation-emails | AC-15 | IEM-8.1 (logs; the audit part is AUD-5); the mail debug setting is not moved: it describes how |
| invitation-emails | AC-11 | IEM-6.4; the failure reason without the token is IEM-8 |
| identity-foundation | (Audit section) | AUD-7.1 (via IDN-6.1): the bootstrap is audited since user-administration AC-20; the older text saying no audit is replaced |
| identity-foundation | AC-1 | IDN-9.1 (the manual part is covered by the automated client scenarios) |
| identity-foundation | AC-2 | IDN-1.1 (identity part; the Problem document shape stays with platform) |
| identity-foundation | AC-3 | IDN-3.1, IDN-5.2, IDN-10.1 |
| identity-foundation | AC-4 | IDN-3.2, IDN-3.3, IDN-5.2 |
| identity-foundation | AC-5 | IDN-10.3, IDN-10.1 |
| identity-foundation | AC-6 | IDN-11.1 |
| identity-foundation | AC-7 | IDN-5.1, IDN-10.1 (identity part; the API part is in PERM-2.2) |
| identity-foundation | AC-8 | IDN-9.6 (manual), IDN-9.7 |
| identity-foundation | AC-9 | IDN-5.1 (the phone is returned since user-administration AC-17; not a dispute) |
| identity-foundation | AC-10 | IDN-4.2 |
| identity-foundation | AC-11 | IDN-6.1 (the case-insensitive comparison is in the IDN-6 statement) |
| identity-foundation | AC-12 | IDN-6.2 |
| identity-foundation | AC-13 | IDN-6.7 |
| identity-foundation | AC-14 | IDN-6.3, IDN-6.4 |
| identity-foundation | AC-15 | IDN-6.5, IDN-6.6 |
| identity-foundation | AC-16 | IDN-6.9, IDN-6.8 |
| identity-foundation | AC-23 | IDN-1.3 |
| identity-foundation | AC-26 | IDN-2.1 (trusted issuers; the client configuration part is PLT-5.1) |
| identity-foundation | AC-28 | IDN-9.4, IDN-9.5 and the callback retry rule IDN-9.2, IDN-9.3 (identity part; the screen is PLT-8.1; a sign-in that never reaches the provider is covered by PLT-8.1) |
| identity-foundation | AC-30 | IDN-7.1, IDN-7.4 (manual) |
| user-administration | AC-20 | clearing the reason and comment when the bootstrap enables a user: IDN-6.2 |
| user-administration | AC-1 | USR-2 (content and order, USR-2.1; paging stays PLT-3) |
| user-administration | AC-2 | USR-2.2, USR-2.3, USR-2.4 |
| user-administration | AC-3 | USR-2.5 (the text); an unknown status or role and an id that is not a UUID are PLT-2.2 |
| user-administration | AC-4 | USR-3.1; an unknown user is PERM-6.2 (USR-3) |
| user-administration | AC-5 | USR-1.1, USR-1.2 (the text said USERS_READ for every operation; the code and the contract need USERS_MANAGE for changes, as the access matrix says; not a dispute) |
| user-administration | AC-6 | USR-4.1 (the next request is IDN-3.3) |
| user-administration | AC-7 | USR-4.2 |
| user-administration | AC-8 | USR-5.1 |
| user-administration | AC-9 | USR-4.3, USR-5.1 |
| user-administration | AC-10 | USR-6.1 |
| user-administration | AC-11 | USR-6.2 |
| user-administration | AC-12 | USR-6.3; roles in an entry are the known ones (AUD-7) |
| user-administration | AC-13 | USR-7.1 |
| user-administration | AC-14 | USR-7 (the rule; reachable only by a race, USR-7.2; a disabled administrator may lose the role, stated in USR-7) |
| user-administration | AC-15 | USR-7.2 |
| user-administration | AC-16 | USR-8.1, USR-8.2 (the entry is AUD-7) |
| user-administration | AC-17 | USR-9.1; an invalid own profile follows USR-8.2 |
| user-administration | AC-18 | USR-8.1, USR-8.2 |
| user-administration | AC-19 | USR-10.1, USR-10.2, USR-10.3 (an unknown user: PERM-6.2, stated in USR-10) |
| user-administration | AC-20 | USR-10.2 (the bootstrap entry as the history shows it) |
| user-administration | AC-22 | USR-12.1, USR-14.3 (the link marked as current and a user page without USERS_READ: dropped as UI detail, PERM-7.2) |
| user-administration | AC-23 | USR-13 and PLT-9 (query in the address, empty, failed and past-the-last list states); the rows USR-13.1 |
| user-administration | AC-24 | dropped: loading, retry and confirmation states of the user page; kept as statements in USR-14, USR-15, USR-16 and the history USR-11 |
| user-administration | AC-25 | USR-17.1 (the permissions refresh after saving one's own roles is stated in USR-17) |
| user-administration | AC-26 | USR-15.1, USR-17.2 |
| user-administration | AC-27 | USR-12 (Profile in the account menu), USR-18.2 |
| user-administration | AC-28 | not moved: the manual check of a disabled user is covered by IDN-3.3 and IDN-10.3 |
| invitations | AC-22 | USR-11.2 (the history sentence of an acceptance, including the reactivation); USR-10.3 (the entry in the history) |
| invitations | AC-1 | INV-2.1, INV-9.1 (the rest of the criterion after the audit part AUD-7); the link only in three responses: INV-9 |
| invitations | AC-2 | INV-2.2, INV-2.3; a role outside the set: PLT-2 |
| invitations | AC-3 | INV-3.1, INV-3.2, INV-3.3 |
| invitations | AC-4 | INV-5.1, INV-5.2 (the rest after the paging part PLT-3) |
| invitations | AC-5 | INV-1.1, INV-1.2; sending an email stays with invitation-emails |
| invitations | AC-6 | INV-6.1, INV-6.2, INV-10.2 (the rest after the audit part AUD-7) |
| invitations | AC-7 | INV-7.1, INV-7.2, INV-7.3 (the rest after the audit part AUD-7) |
| invitations | AC-8 | INV-10.1 |
| invitations | AC-9 | INV-10.2 (the exception AC-14 is INV-14.1; an invitation accepted by someone else is one of the invalid links) |
| invitations | AC-10 | INV-11.1, INV-11.2 |
| invitations | AC-11 | INV-12.1, INV-12.2, INV-12.3 |
| invitations | AC-12 | INV-13.1 |
| invitations | AC-13 | INV-13.2 |
| invitations | AC-14 | INV-14.1 |
| invitations | AC-15 | INV-15.1, INV-3.4 |
| invitations | AC-16 | the history shows it: USR-10.3 (the entry itself: AUD-7, AUD-4.1) |
| invitations | AC-17 | INV-11.3 (401), INV-11.1 (a caller that is not invited); the gate exemption is IDN-3; INV-11.2 (an unknown stored role on acceptance), INV-5.1 (on reads) |
| invitations | AC-18 | INV-16.1 |
| invitations | AC-19 | INV-19.1, INV-19.2 (a server error shown at its field and the general message: statement of INV-19) |
| invitations | AC-20 | INV-16.2, INV-17.1, INV-18.1; the list states are PLT-9; the Send email action and the email line stay with invitation-emails; disabled actions while one runs, accessible names and the input limit: dropped as UI detail |
| invitations | AC-21 | INV-20.1 to INV-20.4 (focus on the heading, progress and per-state rendering: dropped as UI detail) |
| invitations | AC-23 | INV-21.1 (manual) |
| invitation-links | AC-1 | INV-8.1 |
| invitation-links | AC-2 | INV-8.2 |
| invitation-links | AC-3 | INV-1.1; disabled and not invited callers: IDN-3 |
| invitation-links | AC-4 | INV-9.1 |
| invitation-links | AC-5 | not moved: "unchanged behavior" of invitations AC-8 to AC-15, which is INV-10 to INV-15; a malformed token rejected without a query describes how |
| invitation-links | AC-6 | not moved: a one-off backfill of test data by a migration; no behavior remains |
| invitation-links | AC-7 | INV-9 (the token is not written to a log: a statement, checked against the code, no scenario); the rest is AUD-5, AUD-4 |
| invitation-links | AC-8 | INV-16.2, INV-17.1, INV-17.2 (manual) |
| invitation-links | AC-9 | INV-19.1 |
| invitation-links | AC-10 | INV-17.2, INV-21.1 (manual) |
| invitation-emails | AC-2 | IEM-3.1, IEM-3.2, IEM-5.1 (a refused creation requests no email; the audit part is AUD-7) |
| invitation-emails | AC-3 | IEM-3.1, IEM-3.2, IEM-3.3 |
| invitation-emails | AC-4 | IEM-4.1, IEM-4.2 (the entry is AUD-7) |
| invitation-emails | AC-5 | IEM-4.3, IEM-4.4 |
| invitation-emails | AC-6 | IEM-5.1 (403 before 400 is PERM-D1) |
| invitation-emails | AC-7 | IEM-2.1, IEM-2.2 |
| invitation-emails | AC-1 | IEM-1.1, IEM-1.2 (the flag true is PLT-4.1; a sender address with a display name is stated in IEM-1, no scenario) |
| invitation-emails | AC-8 | IEM-6.1, IEM-6.2 |
| invitation-emails | AC-9 | IEM-6 (statement: a rolled-back request sends nothing; no scenario) |
| invitation-emails | AC-10 | IEM-6.1, IEM-6.3; the accepted race is a known limitation |
| invitation-emails | AC-11 | IEM-6.4; the failure reason without the token is IEM-8 |
| invitation-emails | AC-12 | IEM-6.5; replay without a mail server IEM-6.7; the twice-sent case is a known limitation |
| invitation-emails | AC-13 | IEM-6.6 |
| invitation-emails | AC-14 | IEM-7.1 (exact subject and date format dropped: wording) |
| invitation-emails | AC-15 | IEM-8.1 (logs; the audit part is AUD-5); the mail debug setting is not moved: it describes how |
| invitation-emails | AC-16 | IEM-9.1 |
| invitation-emails | AC-17 | IEM-10.1, IEM-11.1 (the unavailable-server message is in the IEM-11 statement) |
| invitation-emails | AC-18 | IEM-12.1, IEM-12.2, IEM-13.1 |
| invitation-emails | AC-19 | IEM-7.2 (manual: the appearance in a real client); the rest is a journey covered by the automated scenarios |
