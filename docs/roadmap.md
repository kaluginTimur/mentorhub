# Roadmap

What is planned, and nothing else, without dates. An implemented item is deleted: its behavior moves to the requirements and its architectural choices to the decisions.

An item is an intention, not a settled solution: it names a goal, and any way of reaching it in its line is a first guess, written before anyone worked the item out. Everything here was proposed or approved by the owner; an agent adds nothing without the owner's word. The product decisions are preliminary too. When an agent sees a better way to reach an item's goal, it proposes it.

## Stages

- **Pilot release:** the first production, on rented hosting, used by a few curators, without corporate infrastructure (no SSO, no corporate email). Scope and hosting are not chosen yet.
- **Corporate integration:** considered only if the pilot succeeds.

## Now

Nothing in progress.

## Next

In this order:

1. **Mentors of a track:** the mentors who teach on a track, and only they are assigned to its enrollments, since a mentor rarely teaches on more than one track. The track page shows its active mentors, each with the number of students they lead and, on request, those students, so that a coordinator follows each mentor's students without a page of the mentor. Who names them is not decided. Touches tracks, enrollments and audit.
2. **The enrollments list in full:** search, sorting and filters in the enrollments of a track. Touches enrollments.
3. **Cohorts:** a coordinator gathers the enrollments of one intake on a track and follows its progress as students finish one by one. Out of scope: mentors of a cohort, groups inside a cohort. A new capability; touches enrollments and audit.
4. **Mentors of a cohort:** mentors assigned to a cohort as a whole see all its students. Touches cohorts, enrollments, authorization and audit.
5. **End of an enrollment:** an enrollment ends with its outcome, and stays in the student's and the track's history. Decides whether a student reads their own enrollment by a permission of the student role, as a mentor reads their students, which depends on whether there is a grace period (Access lifecycle). An ended enrollment keeps its mentors, so that it stays known who led the student, but the relation gives the mentor no access to the student after the end. A mentor is not assigned to the enrollment of a student who is disabled or no longer holds the student role. Out of scope: the result of the job interview, what happens to the student's access. Touches enrollments and audit.
6. **Access lifecycle:** a student whose study has ended loses access as the product decisions below say. Out of scope: reading feedback and grades in the grace period, until those exist. Touches enrollments, authorization, user administration and audit.
7. **Guides audit:** after the tracking core (items 1 to 7), one pass over `docs/development/` and `docs/ui/guidelines.md` that asks of every passage why it is there, who needs it and whether its source already holds it, as the **Guides** section of `docs/README.md` says. Additions are gated, but old passages drift as the code changes. Once done, the item is replaced by the next pass, after another two or three features.
8. **Vulnerable dependencies detected automatically:** known vulnerabilities in the backend's and the frontend's dependencies, transitive ones included, are reported without anyone having to look for them. Out of scope: deciding how fast a finding is fixed. Touches no capability. The way is not chosen yet; IDEA's inspections found CVEs rated up to 9.8 (Tomcat, Jackson) in November 2026.

## Later

In this order, after the tracking core:

1. **Notes and feedback:** what mentors and coordinators record about a student, and what of it the student reads. The domain is not worked out yet, so the item starts with an exploration. Touches enrollments, authorization and audit.
2. **Generic assignments:** task bank, assignment, submission, manual review.
3. **SQL assignments:** automatic checking.
4. **Meeting scheduling:** through external calendars and Webex.

## Ideas

No commitment and no order.

- Corporate SSO auto-provisioning, with role mapping through Keycloak identity provider mappers. An exception to invitation-only registration; needs its own decision.
- Roles as data, editable in the UI (deferred in decision 0004).
- A global audit view for administrators, once there are rules for who may see which actions (deferred in decision 0005).
- End-to-end tests of scenarios on the real stack, with their own data rather than the sample center's.
- Every `dev` start now cleans the dev database, so a `dev` run no longer freezes a migration: reword that statement in `docs/development/backend.md` (How to run) and relax the hooks in `.claude/hooks/` that rely on it.
- Bulk enrollment: a coordinator enrolls several students on a track at once.
- My tracks for a coordinator: a "My track" pill, a filter of all, mine and not mine, own tracks first, a quick action on the home page, the list in the profile.
- A page size chosen by the user in every paged list, and the list scrolled into view on a change of page; `docs/ui/guidelines.md` says "a paginator without a page-size selector" today.
- Unknown properties of a request body: the contract's `additionalProperties: false` is not enforced, so a body with an unknown property is accepted (seen on `EnrollmentCreate`, probably every operation); to decide for all operations at once.

## Product decisions

Not implemented yet, and preliminary: each is discussed again when its feature is planned, and leaves with its implementation.

### Pilot

- Features do not depend on SMTP or corporate SSO being present; email is optional per deployment.

### Tracking core: access lifecycle

- When an enrollment ends, the student role is removed if no active enrollment is left, and a user left without roles is disabled.
- After graduation, a configurable read-only grace period gives access to the student's own enrollment, with the contacts of their mentors and coordinators in case the student needs to reach them. Own feedback and grades join it when those features exist.
- Expulsion revokes access at once.
- Data is kept; only access is revoked.
- Disabling a user after an enrollment ends uses the disable reasons user administration already has.

### Tracking core: domain model

- A track is a direction of study, such as SWE, QA or DevOps. A cohort is one intake on a track: people who start and study together. It is optional and is not divided into groups.
- The way of studying is not tied to a track. Today QA teaches a group by lectures, with two or more mentors who do not divide the students among themselves; SWE takes students once a year, sometimes adds a promising one later, and each mentor leads one or two students through an individual project, so they finish at different times. How DevOps teaches is not known.
- Enrollment is the center of the model: one student on one track, in a cohort or without one. A student has one active enrollment at a time, and may come back later for another, on the same track or a different one.
- A mentor is related to a student through the student's enrollment or through the enrollment's cohort; a track may use either or both.
- The coordinator of a track assigns its mentors and follows the progress of its students.
- A student's data is changed only by the student, a mentor related to that student's enrollment, a coordinator of the track, or an administrator; for a change a role alone is not enough. Coordinators and administrators read the students of every track by a permission (decision 0004).
- A student sees the contacts of their mentors and coordinators.
- The study ends when the mentor stops leading the student: at the defence on SWE, at the end of the lectures on QA. The end and the outcome belong to the enrollment, not to the cohort. What follows, the job interview and its result, is not stored.

### Notes and feedback

- A note has an author, a subject and a visibility of PRIVATE, MENTORS or STUDENT. The exact audience of each level is not defined yet.
- Visibility holds on every read path: single reads, lists, search, counters, exports, audit views, events and DTO mappings. Neither a note's body nor its existence may leak.

### Tracking core: checks of the workflow

The first real feature through the spec-anchored process; what it should show about the process itself:

- Context budget: the hook that stops an agent with PARTIAL has run only on short test tasks. Watch whether an agent stops, whether its handover in the plan's Partial work is complete, and whether the fresh agent goes on without asking what was done. The budget is 250k, and a fresh agent starts between tasks above 150k.
- Requirements: when the user often rejects items of the list of details, the requirements are too thin; a kind of scenario that catches nothing and gets in the way of changes can go.
- The second test pass is removed if it finds no real bug on the tracking core and the next feature.
- `test-engineer` runs on Opus, then its usage is checked. It keeps the 5-minute cache, unless the usage records show it rebuilding.
- The threshold of the cheap path (more than one or two requirements, the contract or a migration) is a draft until small changes happen.
- Nullability is checked by the build (NullAway): on the tracking core and the next feature, watch the reviewer's findings about nullability and how many iterations an agent spends to get the build green. A style mistake the reviewer catches in agents' code feature after feature gets a rule in the cheapest place that can check it: an Error Prone check, a test, PMD, a custom check; the guide only when nothing can check it.

### Assignments

- TaskTemplate (versioned bank entry: type, settings, tags, difficulty, author) → Assignment (pins a template version, assignee, deadline) → Submission (every attempt is kept) → Review (grade, feedback).
- Task types share one abstraction: settings schema, executor and checker. Many checks compare a solution with a catalog of planted defects.
- The executor of student code is a separate process on an isolated host (decision 0001).
