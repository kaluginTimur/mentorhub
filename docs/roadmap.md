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

1. **A path for every change:** every change goes through the path of `docs/process.md` that fits it, without the owner having to know where to start, and the agents' skills lead each path to the pull request, a change with no behavior included. Today there are two entries, `/explore` and `/spec`, and it is not always clear which comes first; `/spec` starts a feature branch and a plan for an item that touches no capability; nothing leads a change with no behavior from the brief to the pull request; the `/pr` template has no place for the owner's decisions and the answers for a guide passage; and a change started in an ordinary session, without a skill, skips the process unnoticed. Product decisions: a mandatory first step of every change chooses its path from the goal and the chosen approach, not from the roadmap item, whose line is a first guess. The goal-and-approach step of Specify moves into it, and the owner confirms the goal, the approach and the path in one answer, a gate. The step does not drag: it proposes the approach and the path with a recommendation, and a choice that needs more (alternatives weighed at length, research, an experiment) goes to Explore, which keeps notes. So the step writes no handover, and what the owner confirmed goes into the first document of the chosen path: the plan, the brief, the exploration notes. A change is anything that would be committed, an exploration included, so nothing is changed on the default branch: the step creates the branch, and the branch's prefix names the path. The agent can start the skill of this step itself, and, with the owner's agreement, the skills of Explore, Specify and a change with no behavior; a hook refuses an edit of a committed file on the default branch; and a skill that finds its change taking another path stops and returns to this step, as when a change with no behavior turns out to change what a user sees. Touches no capability.
2. **Tracking core:** tracks, enrollments, cohorts, notes and feedback. Touches authorization and audit.
3. **Guides audit:** after the tracking core, one pass over `docs/development/` and `docs/ui/guidelines.md` that asks of every passage why it is there, who needs it and whether its source already holds it, as the **Guides** section of `docs/README.md` says. Additions are gated, but old passages drift as the code changes. Once done, the item is replaced by the next pass, after another two or three features.
4. **Vulnerable dependencies detected automatically:** known vulnerabilities in the backend's and the frontend's dependencies, transitive ones included, are reported without anyone having to look for them. Out of scope: deciding how fast a finding is fixed. Touches no capability. The way is not chosen yet; IDEA's inspections found CVEs rated up to 9.8 (Tomcat, Jackson) in November 2026.

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

Not implemented yet, and preliminary: each is discussed again when its feature is planned, and leaves with its implementation.

### Pilot

- Features do not depend on SMTP or corporate SSO being present; email is optional per deployment.

### Tracking core: access lifecycle

- When an enrollment ends, the student role is removed if no active enrollment is left, and a user left without roles is disabled.
- After graduation, a configurable read-only grace period gives access to own feedback and grades.
- Expulsion revokes access at once.
- Data is kept; only access is revoked.
- Disabling a user after an enrollment ends uses the disable reasons user administration already has.

### Tracking core: domain model

- Processes are mixed: SWE students are individual, with their own dates and 1–3 students per mentor; QA studies in classic groups with lectures.
- Enrollment is the center of the model, and Cohort an optional grouping.
- A student's data is read and changed only by the student, a mentor assigned to that student's enrollment, a coordinator of the track, or an administrator. A role alone is not enough.
- Observer rights are not defined yet; the tracking core defines them.

### Tracking core: notes

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
