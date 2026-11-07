# Roadmap

What is planned, and nothing else. An implemented item is deleted: its behavior moves to the requirements and its architectural choices to the decisions.

An item is an intention, not a settled solution: it names a goal, and any way of reaching it in its line is a first guess, written before anyone worked the item out. Only the product decisions marked "(user)" below are the owner's, and they are preliminary too. When an agent sees a better way to reach an item's goal, it proposes it.

## Stages

- **Pilot release:** the first production, on rented hosting, used by a few curators, without corporate infrastructure (no SSO, no corporate email). Scope and hosting are not chosen yet.
- **Corporate integration:** considered only if the pilot succeeds.

## Now

- **Workflow rework:** spec-driven documents and agent process (`docs/workflow-rework.md`).

## Next

In this order:

1. **Unreadable audit details:** a user's history answers 500 when an entry's details are valid JSON but not an object, though AUD-6.5 says such an entry is left out and the read does not fail. Found by the second test pass of the sample center; the application never writes such details, only direct SQL can. A test of AUD-6.5 with details that are not an object, then the fix.
2. **Tracking core:** tracks, enrollments, cohorts, notes and feedback. Touches authorization and audit.

## Later

In this order, after the tracking core:

1. **Generic assignments:** task bank, assignment, submission, manual review.
2. **SQL assignments:** automatic checking.
3. **Meeting scheduling:** through external calendars and Webex.

## Ideas

No commitment and no order.

- Corporate SSO auto-provisioning, with role mapping through Keycloak identity provider mappers. An exception to invitation-only registration; needs its own decision.
- Roles as data, editable in the UI (deferred in decision 0004).
- A global audit view for administrators, once there are rules for who may see which actions (deferred in decision 0005).
- End-to-end tests of scenarios on the real stack, with their own data rather than the sample center's.
- Every `dev` start now cleans the dev database, so a `dev` run no longer freezes a migration: reword that statement in `docs/development/backend.md` (How to run) and relax the hooks in `.claude/hooks/` that rely on it.

## Product decisions

Not implemented yet, and preliminary: each is discussed again when its feature is planned, and leaves with its implementation. "(user)" marks what the user said; the rest is inherited from earlier documents.

### Pilot

- Features do not depend on SMTP or corporate SSO being present; email is optional per deployment (user).

### Tracking core: access lifecycle

- When an enrollment ends, the student role is removed if no active enrollment is left, and a user left without roles is disabled (user).
- After graduation, a configurable read-only grace period gives access to own feedback and grades (user).
- Expulsion revokes access at once (user).
- Data is kept; only access is revoked (user).

### Tracking core: domain model

- Processes are mixed: SWE students are individual, with their own dates and 1–3 students per mentor; QA studies in classic groups with lectures (user).
- Enrollment is the center of the model, and Cohort an optional grouping.
- Observer rights are not defined yet; the tracking core defines them.

### Tracking core: notes

- A note has an author, a subject and a visibility of PRIVATE, MENTORS or STUDENT. The exact audience of each level is not defined yet.
- Visibility holds on every read path: single reads, lists, search, counters, exports, audit views, events and DTO mappings. Neither a note's body nor its existence may leak.

### Assignments

- TaskTemplate (versioned bank entry: type, settings, tags, difficulty, author) → Assignment (pins a template version, assignee, deadline) → Submission (every attempt is kept) → Review (grade, feedback).
- Task types share one abstraction: settings schema, executor and checker. Many checks compare a solution with a catalog of planted defects.
- The executor of student code is a separate process on an isolated host (decision 0001).
