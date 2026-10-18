# User administration

Status: implemented
Stage: tracking core (users, roles, audit log)
Related: ADR 0001, ADR 0002, ADR 0003, ADR 0004, ADR 0005, ADR 0006, ADR 0007, `docs/features/identity-foundation.md`. Followed by `invitations` (invitations with a copyable link) and `invitation-emails` (Mailpit and invitation emails).

## 1. Requirements

### Goal

Administrators need to see who has access to MentorHub and control it without touching the database: enable and disable users, assign and remove roles, and correct profiles. Every such change is recorded in an audit log, so "who gave this person access, and when?" always has an answer. The audit module, the users list and the user page are the base the invitation features and the tracking core build on.

### Scope

- A users list for administrators with search, filters and pagination.
- A user page: profile, access status, roles and the history of changes to the user.
- Disabling a user with a required reason from a predefined list and an optional comment, and enabling them again.
- Assigning and removing roles.
- Editing a profile (first name, last name, optional phone): administrators edit anyone's, every active user edits their own.
- Protection against locking the installation out: an administrator cannot disable themselves or remove their own ADMIN role, and the last active administrator cannot be disabled or lose the role.
- The `audit` module: an append-only log of the changes this feature makes, plus the administrator bootstrap (ADR 0005).

### Roles and scenarios

#### Administrator

- S1. As an administrator, I see the list of users with their status and roles, search it by name or email, and filter it by status and role, so I can find a person quickly.
- S2. As an administrator, I open a user's page and see their profile, status (with the reason when disabled), roles and the history of changes, so I know who changed what and when.
- S3. As an administrator, I disable a user, pick a reason and optionally add a comment, so their access ends on their next request and other administrators know why.
- S4. As an administrator, I enable a disabled user, so they can use MentorHub again.
- S5. As an administrator, I assign and remove a user's roles, so their access matches their job. Removing every role leaves the user active with no permissions ("Your access is being set up").
- S6. As an administrator, I correct a user's first name, last name or phone.
- S7. As an administrator, I am stopped when a change would leave the installation without an active administrator or lock me out, so nobody needs the bootstrap to recover from a slip.

#### Any active user

- S8. As an active user, I open my profile from the account menu and change my first name, last name or phone. My email is shown but cannot be changed.

Disable reasons (`DisableReason`, an enum in code and in the contract, user decision):

| Code | Label | Typical case |
|---|---|---|
| `GRADUATED` | Graduated | The student finished the program |
| `EXPELLED` | Expelled | The student was removed from the program |
| `LEFT_PROGRAM` | Left the program | The student withdrew on their own |
| `STAFF_OFFBOARDED` | No longer on staff | A mentor, coordinator or other employee stopped working in the program or left the company |
| `SECURITY` | Security concern | Account compromise or misuse |
| `OTHER` | Other | Anything else; the comment is required |

The comment is optional (up to 500 characters) except for `OTHER`. The tracking core will use the same values when it disables a user after an enrollment ends.

### Acceptance criteria

Each criterion is marked **auto** (automated tests) or **manual** (checked by hand in the local environment).

Users list and page:

- AC-1 (S1, auto): Given users exist, when an administrator calls `GET /api/v1/users`, then the response is a page of users (id, email, first and last name, status, roles), 25 per page by default and at most 100, with the total count. Users are sorted by last name, then first name, then email, case-insensitive, with a missing name sorted after present ones in its own column, and by id last so pages are stable.
- AC-2 (S1, auto): Given the `q` parameter, then only users whose email, first name or last name contains it (case-insensitive, after trimming; `%`, `_` and `\` match themselves) are returned. Given `status`, only users with that status. Given `role`, only users with that role; `role=NONE` returns only users without any role the code knows (ADR 0004). Filters combine with AND.
- AC-3 (S1, S2, auto): Given an invalid parameter (`size` outside 1 to 100, a negative `page`, an unknown `status` or `role`, a `q` longer than 100 characters, or an `{id}` that is not a UUID) on any `/api/v1/users/**` operation, then the response is 400 `validation-failed` with an error naming the parameter. Values are never clamped.
- AC-4 (S2, auto): Given a user, when an administrator calls `GET /api/v1/users/{id}`, then the response contains the id, email, first and last name, phone, status, disable reason and comment (present only when disabled; the comment only when set), roles and the creation time. An unknown id responds 404 `not-found`.
- AC-5 (S1, S2, auto): Given a caller without `USERS_READ` (any role other than ADMIN, or no roles), when they call any `/api/v1/users/**` operation, then the response is 403 `forbidden`.

Disabling and enabling:

- AC-6 (S3, auto): Given an active user other than the caller, when an administrator disables them with a reason and an optional comment, then the user's status is `DISABLED` with that reason and comment (trimmed; blank is stored as `null`), an audit entry `USER_DISABLED` with the reason and comment is written, and the user's next request is refused with 403 `access-ended`.
- AC-7 (S3, auto): Given a disable request whose reason is missing or not one of the `DisableReason` values, whose comment is longer than 500 characters after trimming, or whose reason is `OTHER` with a missing or blank comment, then the response is 400 `validation-failed` with an error for `reason` or `comment`, and nothing changes.
- AC-8 (S4, auto): Given a disabled user, when an administrator enables them, then the status is `ACTIVE`, the reason and comment are cleared, and an audit entry `USER_ENABLED` is written. Their roles are unchanged.
- AC-9 (S3, S4, auto): Given a user already in the requested status, when they are disabled or enabled again, then the response is 200 with the current user, nothing changes, and no audit entry is written. Disabling an already disabled user does not replace the reason or comment.

Roles:

- AC-10 (S5, auto): Given a user, when an administrator sets their roles with `PUT /api/v1/users/{id}/roles`, then the stored roles equal the requested set, one audit entry `USER_ROLES_CHANGED` lists the added and removed roles, and the change takes effect on the user's next request. Setting the same roles writes no entry.
- AC-11 (S5, auto): Given an empty role set, then the user stays active with no roles and gets "Your access is being set up" on their next request.
- AC-12 (S5, auto): Given a user with a stored role the code does not know (ADR 0004), when their roles are set, then the unknown stored role is left untouched and is not reported in the response or the audit entry.

Lockout protection:

- AC-13 (S7, auto): Given an administrator, when they try to disable themselves, then the response is 409 `self-lockout` and nothing changes. The same holds when they set their own roles without ADMIN.
- AC-14 (S7, auto): Given exactly one active user with the ADMIN role, when anyone tries to disable that user or set their roles without ADMIN, then the response is 409 `last-admin` and nothing changes. (With AC-13 this case is only reachable through the API by the last administrator acting on themselves, which gets `self-lockout`; the rule still holds on its own in the service.)
- AC-15 (S7, auto): Given two active administrators A and B, then:
  - when each one disables the other in parallel, exactly one request succeeds, the other responds 409 `last-admin`, and one active administrator remains;
  - when one request disables B and another sets B's roles without ADMIN in parallel, both finish with 200 or 409 (never 500), and one active administrator remains.

Profile:

- AC-16 (S6, auto): Given a user, when an administrator saves their first name, last name and phone with `PUT /api/v1/users/{id}/profile`, then the values are stored and one audit entry `USER_PROFILE_UPDATED` lists each changed field with its old and new value. Saving unchanged values writes no entry.
- AC-17 (S8, auto): Given any active user, including one without roles, when they save their own profile with `PUT /api/v1/me/profile`, then the same rules as AC-16 apply with the user as the actor, and `GET /api/v1/me` returns the new values, including `phone`.
- AC-18 (S6, S8, auto): Given profile values, then:
  - first and last name are trimmed, a blank value is stored as `null`, and each is at most 100 characters after trimming;
  - the phone is optional; a blank value is stored as `null`; otherwise it is parsed with the default region from configuration (KZ), must be a valid number, and is stored in E.164 (for example `8 701 123 45 67` is stored as `+77011234567`);
  - an invalid value responds 400 `validation-failed` with an error per invalid field, and nothing is stored;
  - the email is not part of the request and never changes.

History and audit:

- AC-19 (S2, auto): Given audit entries about a user, when an administrator calls `GET /api/v1/users/{id}/history`, then the entries about that user are returned newest first, 25 per page, each with the time, the action, the actor (id and name, or none for the system) and the action's details (reason and comment, added and removed roles, changed fields with old and new values, or what the bootstrap did). An entry whose action the code does not know is left out, and a warning is logged once per unknown action.
- AC-20 (S2, auto): Given the administrator bootstrap (ADR 0005) changed something, then besides the existing warning, an audit entry `USER_BOOTSTRAPPED_ADMIN` without an actor is written about that user. When it changed nothing, no entry is written. When it enables a user who was disabled with a reason and comment, both are cleared.
- AC-21 (auto): Given an audited change whose transaction fails, then neither the change nor its audit entry is stored. Given any `UPDATE` or `DELETE` on `audit_log`, then the database rejects it.

UI:

- AC-22 (S1, auto): Given a user with `USERS_READ`, then the toolbar shows "Users". Given a user without it, the toolbar has no "Users" link, and opening `/users` or `/users/{id}` redirects to `/`. Given a user with `USERS_READ` but without `USERS_MANAGE`, then the user page shows every card read-only: no "Edit", no "Disable user" or "Enable user", and the roles as a list instead of checkboxes with "Save".
- AC-23 (S1, auto): Given the users list, then it shows the loading, empty (with and without filters, including "Clear filters") and error states from section 2, and search, filters and page are kept in the URL, so reload and back restore them.
- AC-24 (S2–S6, auto): Given the user page, then it shows the loading, not found (also for an id that is not a UUID) and error states. After a successful disable, enable, role save or profile save it shows the updated user, reloads the history and shows the confirming snackbar.
- AC-25 (S7, auto): Given an administrator on their own user page, then "Disable user" and the Administrator checkbox are disabled with their hints, and the hints are reachable by keyboard and screen readers. After they save their own profile or roles there, the toolbar name and the permissions the application uses are refreshed.
- AC-26 (S3, S6, S7, auto): Given the server responds 400 `validation-failed`, then each field error shows under its field in the disable dialog or the profile form. Given 409 `self-lockout` or `last-admin`, then the matching snackbar message shows and the form keeps its values. Given "Other" in the disable dialog, then "Disable" requires a comment before sending.
- AC-27 (S8, auto): Given an active user with roles, then the account menu has "Profile", which opens `/profile` with the profile form; after saving, the toolbar name updates.
- AC-28 (manual): Given the local environment, when the administrator disables `mentor@mentorhub.local` and that user then clicks anything, then they see "Your access has ended"; after enabling and changing roles the effect shows on the user's next action.

### Access matrix

| Action | admin | coordinator | mentor | student | observer | active, no roles |
|---|---|---|---|---|---|---|
| List users, open a user, read a user's history | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ |
| Disable, enable, set roles, edit another user's profile | ✓ (lockout rules) | ✗ | ✗ | ✗ | ✗ | ✗ |
| Read and edit own profile (`/api/v1/me`, `/api/v1/me/profile`) | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

- Users list and user operations require `USERS_READ` (reads) or `USERS_MANAGE` (changes). Both stay on ADMIN only (user decision); no new permission is added.
- An administrator's own page and roles go through the same operations as anyone else's, so the lockout rules (AC-13, AC-14) apply; an administrator may add other roles to themselves.
- Disabled and not invited callers are refused by the existing gate (identity foundation).

### Visibility

| Data | Who sees it |
|---|---|
| Any user's profile (email, names, phone), status, roles, creation time | Administrators |
| A user's disable reason and comment | Administrators only; the disabled user sees only "Your access has ended" (user decision) |
| A user's history (audit entries about them) | Administrators |
| Own profile, including phone | The user themself, through `GET /api/v1/me` |

The audit log is only read through per-subject views that apply the subject's own access rules. This feature has one: a user's history, behind `USERS_READ`. There is no global audit endpoint.

### Audit

The audit entry records the time, the actor (`users.id`, or none for the system), the action, the subject (type `USER` and its id) and the details.

| Action | Details |
|---|---|
| `USER_DISABLED` | reason, comment |
| `USER_ENABLED` | the reason and comment the user had been disabled with |
| `USER_ROLES_CHANGED` | added roles, removed roles |
| `USER_PROFILE_UPDATED` | changed fields (`firstName`, `lastName`, `phone`), each with old and new value |
| `USER_BOOTSTRAPPED_ADMIN` | what the bootstrap did: created the user, linked the identity, enabled the user, added ADMIN (each a boolean) |

The development user runner writes no audit entries.

### Out of scope

- Invitations and creating users (feature `invitations`); sending email and Mailpit (feature `invitation-emails`).
- Changing a user's email, and showing or unlinking a user's sign-in identities.
- Deleting users.
- A global audit log screen; audit retention.
- Optimistic locking of concurrent edits: when two administrators save the same user's profile or roles, the last save wins (each save is still audited with what it changed).
- Coordinator, mentor and observer access to user data, and automatic revocation when an enrollment ends (tracking core).
- A profile entry point on the "Your access is being set up" screen: a user without roles can change their profile through the API only.
- Photo, consent and other personal data rules (postponed).

## 2. UI

### Navigation

- The toolbar gets a "Users" link next to "MentorHub", shown only when the user has `USERS_READ`. It is marked as current on `/users` and `/users/{id}`.
- The account menu gets "Profile" above "Sign out".
- Routes that need a permission the user lacks redirect to `/` (AC-22); the backend enforces regardless.

### Users list

- Route: `/users`; roles: administrators.
- Purpose: find a user and see their access at a glance.
- Content and actions:
  - `h1` "Users";
  - a search field "Search by name or email" (applied 300 ms after typing stops), a status select (All, Active, Disabled) and a role select (Any role, each role, No roles);
  - a table with Name (the name rule from the shell, linking to the user page), Email, Roles (chips; "No roles" in secondary text when empty) and Status ("Active" or "Disabled");
  - a paginator (25 per page);
  - search, filters and page are kept in the URL query, so back and reload keep them.
- States:
  - loading: the table shows a progress bar, the previous rows stay until the new page arrives;
  - empty: "No users match the filters." with a "Clear filters" action (filters set) or "No users yet." (no filters);
  - error: the unavailable card with "Retry".
- Patterns: Angular Material table, form fields, select, chips and paginator.

```
+---------------------------------------------------------------+
| MentorHub  Users                            [Anna Petrova  v] |
+---------------------------------------------------------------+
| Users                                                         |
| [Search by name or email      ] [Status: All v] [Role: Any v] |
| Name            Email                 Roles          Status   |
| Anna Petrova    anna@example.com      ADMIN          Active   |
| Ivan Sidorov    ivan@example.com      MENTOR         Active   |
| maria@ex.com    maria@ex.com          No roles       Disabled |
|                                    25 per page  1-3 of 3 < >  |
+---------------------------------------------------------------+
```

### User page

- Route: `/users/{id}`; roles: administrators.
- Purpose: see and change one user's access and profile, and see what was changed.
- Content and actions:
  - a back link "Users" to the list with its previous filters;
  - `h1` with the user's name and a status chip;
  - **Profile** card: email (read-only), first name, last name, phone, and "Created <date>". "Edit" (with `USERS_MANAGE`) turns the card into the profile form (see below) with "Save" and "Cancel";
  - **Access** card (the actions need `USERS_MANAGE`):
    - active: "Active" and the action "Disable user", which opens the disable dialog;
    - disabled: "Disabled" with the reason label and the comment, and the action "Enable user", which enables after a confirmation dialog "Enable <name>? They will be able to sign in again.";
  - **Roles** card (without `USERS_MANAGE`, a read-only list of role names): a checkbox per role with its name (Administrator, Coordinator, Mentor, Student, Observer), "Save" (enabled when the selection changed) and "Reset". When no role is selected, a hint "Without roles the user can sign in but has no access to anything.";
  - **History** card: entries newest first, each with the time, the actor's name (or "System") and a sentence, and "Show more" while more pages exist:
    - "Disabled: Graduated — <comment>" (without " — <comment>" when there is none), "Enabled",
    - "Roles changed: added Mentor; removed Student",
    - "Profile updated: phone +77011234567 → +77017654321, last name (empty) → Petrova",
    - "Granted administrator access by bootstrap (created the user, linked the sign-in identity, enabled the user)": the parentheses list only what the bootstrap did and are left out when it did none of these; the sentence starts with just "Bootstrap" when it did not add ADMIN.
- Own page: "Disable user" is disabled with the hint "You can't disable yourself."; the Administrator checkbox is disabled with the hint "You can't remove your own administrator role."
- Disable dialog: title "Disable <name>?", text "They lose access on their next action.", a required "Reason" select with the labels from the reason table, a "Comment" textarea (counter, 500 characters; labelled "Comment (required)" and required when "Other" is selected, otherwise optional), "Cancel" and "Disable". "Disable" shows field errors from the server under their fields.
- After a successful change the page shows the updated user, the history reloads, and a snackbar confirms ("User disabled", "User enabled", "Roles saved", "Profile saved"). On the administrator's own page, saving the profile or roles also reloads `GET /api/v1/me`, so the toolbar name and permissions are current.
- 409 responses show their message in a snackbar and leave the form as it was: `self-lockout` "You can't lock yourself out.", `last-admin` "MentorHub must keep at least one active administrator."
- States:
  - loading: the loading state until the user loads; History has its own progress bar;
  - not found: "User not found" with a link back to the list, also when the id in the address is not a UUID (the API answers 400);
  - error: the unavailable card with "Retry".
- Patterns: Material cards, dialog, checkboxes, snackbar.

```
+---------------------------------------------------------------+
| MentorHub  Users                            [Anna Petrova  v] |
+---------------------------------------------------------------+
| < Users                                                       |
| Ivan Sidorov  (Active)                                        |
| +-- Profile -------------------------------------- [Edit] --+ |
| | Email       ivan@example.com                              | |
| | First name  Ivan          Last name  Sidorov              | |
| | Phone       +77011234567  Created    12 Oct 2026          | |
| +-----------------------------------------------------------+ |
| +-- Access --------------------------+ +-- Roles ---------+ | |
| | Active            [Disable user]   | | [ ] Administrator| | |
| +------------------------------------+ | [ ] Coordinator  | | |
|                                        | [x] Mentor       | | |
|                                        | [ ] Student      | | |
|                                        | [ ] Observer     | | |
|                                        |   [Reset] [Save] | | |
|                                        +------------------+ | |
| +-- History ------------------------------------------------+ |
| | 17 Oct 2026 10:12  Anna Petrova  Roles changed: added     | |
| |                                  Mentor; removed Student  | |
| | 12 Oct 2026 09:00  System        Granted administrator... | |
| |                                          [Show more]      | |
| +-----------------------------------------------------------+ |
+---------------------------------------------------------------+
```

### Profile form

Shared by the user page and the own profile page.

- Fields: "First name", "Last name", "Phone" (hint "For example +7 701 123 45 67"), all optional; the email is shown read-only above them.
- Client-side checks: at most 100 characters per name. The phone is validated by the server; its error ("Enter a valid phone number") shows under the field.
- After saving, the form shows the stored values, so a phone entered as `8 701 123 45 67` reads `+77011234567`.

### My profile

- Route: `/profile`; roles: any active user with roles.
- Purpose: let users keep their own name and phone current.
- Content: `h1` "Profile" and the profile form with "Save". After saving, the toolbar name updates. The page never shows the user's status, disable reason or roles.
- States: loading and error as the shell; no not-found state.

### Accessibility

- Each page has one `h1`; cards use `h2` headings.
- The users table has column headers; the name cell is a link whose text is the user's name.
- Status is shown as text ("Active", "Disabled"), never by color alone; role chips carry the role name as text.
- Dialogs move focus to their first field when opened, trap focus, close on Escape, and return focus to the button that opened them.
- The reason select and comment field have visible labels; the counter and field errors are linked to their field with `aria-describedby`, and an invalid field sets `aria-invalid`.
- Snackbar messages are announced (`politeness: polite`; the 409 messages `assertive`).
- A disabled button or checkbox with a hint stays reachable: it uses `aria-disabled` with a focusable control (Material `disabledInteractive`), and the hint is linked with `aria-describedby`.
- The list's loading progress bar has the label "Loading users"; the container sets `aria-busy` while loading.

## 3. Design

### Modules

- `com.mentorhub.audit` (new):
  - public API: `AuditLog.record(AuditEntry)`, which joins the caller's transaction (`Propagation.MANDATORY`), and a query `AuditLog.findBySubject(subjectType, subjectId, page, size)`;
  - `AuditEntry`: actor id (nullable), action, subject type and id, details as a JSON-serializable map. The action is a `String` constant owned by the calling module, so `audit` depends on no other module;
  - internal `persistence` with a `JdbcClient` repository (ADR 0007). No endpoints: each module that shows history serves it under its own access rules.
- `com.mentorhub.users` gains:
  - `service`: `UserAdministrationService` (list, get, disable, enable, set roles, update profile, history) and `ProfileService` (own profile), both writing audit entries in the same transaction; the lockout rules; phone normalization;
  - `web`: one contract tag `Users`, so `UsersController` implements the generated `UsersApi`: the administration operations, `getMe` (formerly `MeController`) and `updateMyProfile`; `UsersMapper` maps by hand (convention per tag `Foo`: `FooApi`, `FooController`, `FooMapper`);
  - `persistence`: `users` stays on `JdbcClient` (ADR 0007);
  - the bootstrap writes `USER_BOOTSTRAPPED_ADMIN`;
  - `users` → `audit` is the only new module dependency.
- `com.mentorhub.web` gains two generic exceptions next to `NotFoundException`, so it never depends on a feature module: `ConflictException(type, detail)` (409 with the given problem type) and `ValidationException(errors)` (400 `validation-failed` with field errors). `ProblemDetailsAdvice` renders them, and bean validation and parameter conversion failures also map to `validation-failed`. The `users` module throws them with its own types `self-lockout` and `last-admin`.
- Frontend: `frontend/src/app/features/users/` (list, user page, disable dialog, role editor, history), `frontend/src/app/features/profile/` (my profile), the shared profile form in `frontend/src/app/shared/profile-form/`, and a permission guard in `core/auth/`.

### Data model

Migration `V3` (`V2__identity.sql` exists):

- `users`:
  - `phone text null`;
  - `disabled_reason text null`, check against the `DisableReason` values (text with a check constraint, like `status`; adding a value is a code change plus a migration of the constraint);
  - `disabled_comment text null`;
  - the phone format and the comment length are not checked in the database; the service enforces them (user decision; likewise `audit_log` has no checks on `action`, `subject_type` or the shape of `details`);
  - check `users_disabled_reason_ck`: `(status = 'DISABLED') = (disabled_reason is not null)`, and `disabled_comment is null or status = 'DISABLED'`, and `disabled_reason <> 'OTHER' or disabled_comment is not null`. Rows already `DISABLED` get `OTHER` with the comment `Disabled before reasons were recorded` in the same migration.
- `audit_log`:
  - `id uuid` PK default `gen_random_uuid()`;
  - `occurred_at timestamptz not null default now()`;
  - `actor_user_id uuid null` FK → `users` on delete restrict (null for the system);
  - `action text not null`, `subject_type text not null`, `subject_id uuid not null`;
  - `details jsonb not null default '{}'`;
  - index `audit_log_subject_idx` on (`subject_type`, `subject_id`, `occurred_at desc`, `id desc`).

  - a trigger `audit_log_append_only` (`before update or delete`, for each statement) raises an exception, so the log is append-only in the database, not only by convention (AC-21). `truncate` is not covered; it is not part of any application path.

  `audit_log` has no FK on `subject_id`, because subjects live in different modules' tables. The FK on `actor_user_id` is intentional although `audit` has no code dependency on `users`: actors are always users, users are never deleted, and the FK keeps every actor resolvable. Cross-module foreign keys are allowed; `subject_id` has none because it is polymorphic (ADR 0006).
- Every write of `users.status` sets the reason and comment with it, so the check constraint always holds. `UserRepository.setStatus` is replaced by `enable(userId)` (status `ACTIVE`, reason and comment cleared) and `disable(userId, reason, comment)`:
  - the bootstrap uses `enable`, so recovering a disabled administrator clears the reason (AC-20);
  - the development user runner uses `enable` for active users and `disable` for disabled ones, with the reason and comment from `mentorhub.development-users.users[]` (`disabled@mentorhub.local`: `OTHER`, `Development user`). It may also set phones from there. A development user an administrator disabled locally is enabled again, with its reason cleared, on the next start.

### API

| Method and path | Purpose | Permission | Errors |
|---|---|---|---|
| `GET /api/v1/users?q&status&role&page&size` | Page of users (AC-1 to AC-3) | `USERS_READ` | 400, 403 |
| `GET /api/v1/users/{id}` | One user (AC-4) | `USERS_READ` | 400, 403, 404 |
| `GET /api/v1/users/{id}/history?page&size` | The user's audit entries (AC-19) | `USERS_READ` | 400, 403, 404 |
| `POST /api/v1/users/{id}/disable` | Disable with `{reason, comment}` (AC-6, AC-7, AC-9) | `USERS_MANAGE` | 400, 403, 404, 409 |
| `POST /api/v1/users/{id}/enable` | Enable (AC-8, AC-9) | `USERS_MANAGE` | 400, 403, 404 |
| `PUT /api/v1/users/{id}/roles` | Set roles `{roles: Role[]}` (AC-10 to AC-12) | `USERS_MANAGE` | 400, 403, 404, 409 |
| `PUT /api/v1/users/{id}/profile` | Set `{firstName, lastName, phone}` (AC-16, AC-18) | `USERS_MANAGE` | 400, 403, 404 |
| `PUT /api/v1/me/profile` | Set own profile, same body (AC-17, AC-18) | none (own data) | 400 |

Every changing operation returns the updated `UserDetails`; `PUT /api/v1/me/profile` returns `Me`.

Schemas:

- `UserSummary`: `id`, `email`, `firstName`, `lastName` (nullable), `status` (`UserStatus`: `ACTIVE`, `DISABLED`), `roles`.
- `UserDetails`: `UserSummary` plus `phone` (nullable), `disabledReason` (`DisableReason`) and `disabledComment` (optional: absent from the JSON, not `null`, unless disabled and set), `createdAt`.
- `UserPage`: `items` (`UserSummary[]`), `page`, `size`, `totalItems`. Pages are zero-based; `size` is 1 to 100.
- `RoleFilter`: `Role` values plus `NONE`.
- `DisableReason`: string enum, equal to the code enum (see the reason table in section 1).
- `DisableRequest`: `reason` (required), `comment` (optional).
- `ProfileUpdate`: `firstName`, `lastName`, `phone`, all required and nullable, so the client always sends the whole profile.
- `MeUser` gains `phone` (required, nullable).
- `HistoryEntry`: `id`, `occurredAt`, `action` (`HistoryAction` enum: the five actions), `actor` (`{id, name}` or null), and optional detail properties: `reason` (`DisableReason`), `comment`, `rolesAdded`, `rolesRemoved`, `changes` (`[{field, oldValue, newValue}]`), `bootstrap` (`{userCreated, identityLinked, enabled, adminAdded}`); absent detail properties are left out of the JSON. `HistoryPage` as `UserPage`.
  - The contract's `HistoryAction` lists the actions the `users` history can show. A module that writes a new action about a user (for example the `invitations` feature) extends this enum in the same change. Entries with an action the code does not know are skipped (AC-19), as ADR 0004 does for unknown roles.
  - Known limitation (user decision): unknown actions are filtered after paging, so such a page can hold fewer than `size` items while `totalItems` counts every entry. It happens only when the log holds actions the running code does not know, for example after rolling back a release that wrote a new action.
- Optional properties (`disabledReason`, `disabledComment`, the `HistoryEntry` details) are left out with a `@JacksonMixin` and `@JsonInclude(NON_NULL)` per DTO, as `MeJsonMixin` does for `Me` (see the Jackson pitfall in `docs/development/backend.md`); required nullable properties stay explicit `null`.
- The contract has no `maxLength` on names, `q`, or the comment: the service trims first and then checks the length, so surrounding whitespace never causes a rejection (AC-7, AC-18).
- New problem types:
  - `urn:mentorhub:problem:validation-failed` (400) with an `errors` extension: `[{field, message}]`; used for bean validation and service validation, including malformed query parameters;
  - `urn:mentorhub:problem:self-lockout` and `urn:mentorhub:problem:last-admin` (409).

### Authorization

- Every `/api/v1/users/**` operation carries `@RequirePermission`, on the controller and on the service method; `PUT /api/v1/me/profile` carries the "no permission required" marker and only ever changes `CurrentUser.id()`, never an id from the request.
- User ids from paths are resolved in the service; an unknown id is `NotFoundException`. No other resource-level relation applies: administrators see all users.
- Lockout rules in `UserAdministrationService`, checked inside the changing transaction:
  - self: the target is the caller and the change disables them or removes ADMIN → `self-lockout`;
  - last admin: the change disables an active ADMIN or removes ADMIN from an active user, and no other active ADMIN would remain → `last-admin`;
  - only active administrators count: a disabled user's roles can be changed, including removing ADMIN, so roles can be prepared before the user is enabled (user decision);
  - to make AC-15 hold, a change that can remove an active administrator takes a transaction-scoped advisory lock (`pg_advisory_xact_lock` with a fixed key) and then counts the other active administrators.
- Lock order, always the same to rule out deadlocks (AC-15):
  1. the administrator lock: always for disable; for set roles when the requested set lacks ADMIN, which is known from the request before anything is read;
  2. the user row (`select ... for no key update`), then the current state is read, so the role diff and the audit entry match what is stored.

  Enable, profile updates and set roles that keep ADMIN take only the row lock. The row lock is `for no key update`, not `for update`: the audit entry's `actor_user_id` foreign key takes `for key share` on the caller's row, which `for update` blocks, so two administrators changing each other would deadlock.
- The ADR 0005 bootstrap runs its existing unlocked check for an active administrator first; only when none exists does it take the administrator lock and check again. Requests of an existing administrator are therefore never serialized, and a bootstrap racing a lockout change cannot see a stale count. (The bootstrap only adds an administrator, so the lock is for the re-check, not for its own safety.)
- Frontend permission checks (convenience only; the backend enforces every rule):
  - the permissions come from `GET /api/v1/me`, which the application already reloads on every navigation, so a change of roles shows on the user's next navigation;
  - one check in `CurrentUserService` answers "does the current user have permission X", typed with the generated `Permission`; templates and guards use only that check, never role names;
  - an element that needs a permission is not rendered without it (the "Users" link needs `USERS_READ`; "Edit", "Disable user", "Enable user" and the role editor need `USERS_MANAGE`);
  - a route that needs a permission declares it through a guard factory in `core/auth/`; without it the route does not match and the user is redirected to `/`, so its lazy chunk is not loaded;
  - between a permission being removed and the next navigation, a still-visible control gets 403 `forbidden` from the API and the existing snackbar; the next navigation hides it.
- History reads require `USERS_READ` and return only entries with subject type `USER` and the given id. Actor names come from `users` inside the `users` module.
- Search: `q` is trimmed, `%`, `_` and `\` are escaped, and the query uses `ilike ... escape '\'` on email, first and last name (AC-2). `role` filters by roles the code knows: `NONE` means no `user_roles` row with a known role.

### Events

None. Audit entries are written synchronously in the changing transaction (AC-21), not through events, so a change cannot be stored without its entry.

### Configuration

| Property | Meaning |
|---|---|
| `mentorhub.users.phone-default-region` | Region used to parse phones written without `+` (default `KZ`) |

### Dependencies

- Backend: `com.googlecode.libphonenumber:libphonenumber` for parsing and validating phones (strict E.164, user decision).

### Tests

- Negative authorization for every `/api/v1/users/**` operation: no roles, each non-admin role, disabled caller (gate).
- AC-15 runs parallel requests against Testcontainers Postgres, including the disable plus set-roles pair for the lock order, and two administrators changing each other (profile with profile, set roles keeping ADMIN), which must both succeed.
- A bootstrap test re-enables a user who was disabled with a reason and comment (AC-20).
- A migration test checks that `UPDATE` and `DELETE` on `audit_log` fail (AC-21).
- An `audit` module test checks that `record` outside a transaction fails, and `ModularityTests` keeps `audit` free of other module dependencies.
- Frontend unit tests for every state of the list and the user page, the disable dialog validation, the role editor's own-admin rule, the 409 messages, the permission guard and the accessibility attributes from section 2.

### Documentation updates

- `docs/development/backend.md`: the `audit` module and how to record an entry; pagination parameters and the page schema; the `validation-failed` problem and its `errors`; the advisory lock for lockout rules and `for no key update` row locks.
- `docs/development/frontend.md`: the permission check and guard (the algorithm above); the list query in the URL.
- `docs/ui/guidelines.md`: toolbar navigation, list page (filters, table, paginator, empty state), detail page with cards, confirmation and reason dialogs, 409 snackbar.
- `docs/architecture/invariants.md`: already updated with the audit rules of ADR 0006 when it was accepted; no change needed.
- `README.md`: the user administration screen in the walkthrough of the development users.

### Decisions

- Three features instead of one: user administration with audit, invitations with a copyable link, invitation emails with Mailpit (user decision).
- Users are managed by administrators only (user decision).
- Profiles are edited by administrators and by the user; email is not editable (user decision).
- Self-lockout and last-admin protection; the bootstrap stays the last resort (user decision).
- History per user, no global audit screen (user decision).
- `USERS_READ` shows everything on the users list and the user page, status and roles included; only the actions need `USERS_MANAGE` (user decision). Today both permissions are on ADMIN only.
- The disable reason is required, chosen from a predefined list, with an optional comment that `OTHER` requires; both are visible to administrators only (user decision).
- Staff who stop working in the program and staff who leave the company share `STAFF_OFFBOARDED`. A separate value (for example for deprovisioning through a corporate identity provider) is deferred to the corporate SSO ADR; adding one is a code change and a constraint migration.
- Phones are strict E.164, normalized with libphonenumber and a configurable default region (user decision).
- The audit design (synchronous in the changing transaction, actions as strings owned by the calling module, append-only enforced by a trigger, no global read endpoint): ADR 0006.
- Persistence is chosen per module; `users` and `audit` use `JdbcClient` (user decision, ADR 0007).
- Pagination and the `validation-failed` problem are new API conventions; they are recorded in the contract and `docs/development/backend.md` rather than an ADR.

## 4. Open questions

None blocking.
