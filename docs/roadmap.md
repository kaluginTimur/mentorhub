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

1. **Tracking core:** tracks, enrollments, cohorts, notes and feedback. Touches authorization and audit.
2. **Agents look at screens:** agents open the running application in a browser, sign in as a persona of the sample center and look at a screen in each of its states, so that `frontend-dev` checks its screens are plausible before the owner looks, and the manual checks of `/feature` have a browser that is set up rather than improvised, in local and cloud sessions alike. Out of scope: failing a single endpoint to show a screen's first-load error state (DEV stays as it is). Touches the agents, `docs/process.md` and `README.md`, not the product.
3. **Guides audit:** after the tracking core, one pass over `docs/development/` and `docs/ui/guidelines.md` that asks of every passage why it is there, who needs it and whether its source already holds it, as the **Guides** section of `docs/README.md` says. Additions are gated, but old passages drift as the code changes. Once done, the item is replaced by the next pass, after another two or three features.

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
- Meaning lost in shortened or rewritten passages: find where trimming or rewriting the documents, agents and skills dropped something that still matters, and restore it where it belongs. Example: "a cloud session has no browser" in `/feature` lost "for them" and the pre-installed Playwright (commits 0c8e3e0, 9bcb62f). Not only `docs/development/` (the guides audit), but the requirements, `docs/process.md`, `.claude/agents` and `.claude/skills` too.
- Every `dev` start now cleans the dev database, so a `dev` run no longer freezes a migration: reword that statement in `docs/development/backend.md` (How to run) and relax the hooks in `.claude/hooks/` that rely on it.

## Product decisions

Not implemented yet, and preliminary: each is discussed again when its feature is planned, and leaves with its implementation.

### Pilot

- Features do not depend on SMTP or corporate SSO being present; email is optional per deployment.

### Agents look at screens

- The browser is driven through `playwright-cli` from the shell. Compared with Playwright MCP on the Users screen, the cost was about the same, and the CLI needs no MCP configuration for the session, subagents or the Haiku manual checks.
- If the stack is not running, the agent starts it and stops it when done; if it is running, the agent looks alongside the owner and never restarts or stops it, since a dev start replaces the data.
- Screenshots go to a git-ignored folder in the repository, so the owner opens them in the IDE.
- A screen's own error state is reached with the failure switch (DEV-3) on an open screen, followed by a request of the screen itself (a filter, a page, an action): a navigation with the switch on shows the full-page message (PLT-8.2), as a real outage would.
- In WSL, Chromium needs system packages installed once by a person with `sudo`; cloud sessions have Chromium pre-installed. Both Playwright tools default to the Chrome channel and need Chromium chosen explicitly.
- The phrase "a cloud session has no browser" in `/feature` is fixed: it is the user who has no browser for the app in a cloud session, not the agent.

### Tracking core: access lifecycle

- When an enrollment ends, the student role is removed if no active enrollment is left, and a user left without roles is disabled.
- After graduation, a configurable read-only grace period gives access to own feedback and grades.
- Expulsion revokes access at once.
- Data is kept; only access is revoked.

### Tracking core: domain model

- Processes are mixed: SWE students are individual, with their own dates and 1–3 students per mentor; QA studies in classic groups with lectures.
- Enrollment is the center of the model, and Cohort an optional grouping.
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

### Assignments

- TaskTemplate (versioned bank entry: type, settings, tags, difficulty, author) → Assignment (pins a template version, assignee, deadline) → Submission (every attempt is kept) → Review (grade, feedback).
- Task types share one abstraction: settings schema, executor and checker. Many checks compare a solution with a catalog of planted defects.
- The executor of student code is a separate process on an isolated host (decision 0001).
