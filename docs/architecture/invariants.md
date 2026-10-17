# Domain and security invariants

Rules every feature must keep. Specs refine them; they never relax them without a new ADR.

## Product scope

MentorHub is the internal place for the training center's mentoring process: cohorts, enrollments, mentor notes and feedback, assignments and their review, meetings. It is not a general-purpose LMS.

Planned order: tracking core (users, roles, cohorts, enrollments, notes and feedback with visibility, audit log) → generic assignments (task bank, assignment, submission, manual review) → SQL assignments with automatic checking → meeting scheduling through external calendars and Webex.

## Domain model (planned)

- Track → Cohort → Enrollment (student, cohort, mentors, status).
- TaskTemplate (versioned bank entry: type, settings, tags, difficulty, author) → Assignment (pins a template version, assignee, deadline) → Submission (every attempt is kept) → Review (grade, feedback).
- Note (author, subject, visibility), Meeting, AuditLog.
- Task types share one abstraction: settings schema + executor + checker. Many checks compare a solution with a catalog of planted defects.

## Identity (ADR 0003)

- The application stores no passwords and does not implement sign-in. The backend is an OAuth2 resource server; trusted issuers are configuration.
- Domain data references `users.id`, never the token `sub`. External identities live in `user_identities(user_id, issuer, subject)`.
- Registration is by invitation only. The exceptions are the administrator bootstrap (ADR 0005) and development users, which exist only in the development profile.
- Only active users pass the gate: the identity in a validated token must be linked to the user (linking happens only through an invitation or ADR 0005, both with a verified email), and a disabled user is refused on the next request.

## Authorization

- Permissions and roles are defined in code; role assignments are stored in the application database, not in the identity provider (ADR 0004). Roles: admin, coordinator, mentor, student, observer. A user can have several roles or none.
- Authorization checks use permissions, never role names. Roles and groups in the token are never used for authorization.
- Roles are global. Which students a user can access depends on domain relations that change over time: a graduate can become a mentor, mentors are reassigned. Checks evaluate current relations.
- Every endpoint that reads or changes a student's data checks, at the resource level, that the caller is the student, a mentor assigned to that student's enrollment, a coordinator of the track, or an admin. A role check alone is not enough; IDs from the request are never trusted.
- A single resource the caller may not access responds 404, exactly like a missing one; lists leave inaccessible items out. 403 is reserved for a missing permission for the action as a whole.
- Observer rights are not defined yet; the tracking core spec defines them.
- Every feature has negative authorization tests (another mentor's student, another cohort, the wrong role).

## Note visibility

- A note has a visibility of PRIVATE, MENTORS or STUDENT. The exact audience of each level is defined in the tracking core spec.
- Visibility is enforced on every read path: single reads, lists, search, counters, exports, audit views, events and DTO mappings. Neither a note's body nor its existence may leak.

## Audit

- State changes that a spec marks as audited write an AuditLog entry.
