# 0008. Asynchronous events through the Modulith event publication registry

Status: accepted, 2026-10-23

## Context

Invitation emails (`docs/features/invitation-emails.md`) are the first work MentorHub does after a request commits: a slow or unreachable mail server must never fail or delay the administrator's request, and a requested email must not be lost when the application stops before it is sent. ADR 0001 expected the Spring Modulith event publication registry for this, with the JPA starter. ADR 0007 has since made `JdbcClient` the persistence approach of `users` and `audit`.

## Decision

- **Events go through the Spring Modulith event publication registry with JDBC** (`spring-modulith-starter-jdbc`). A module publishes an event with `ApplicationEventPublisher` inside the changing transaction; the registry stores the publication in that transaction, and an `@ApplicationModuleListener` handles it after the commit on Boot's async executor (`@EnableAsync` on `MentorHubApplication`). One transaction manager serves the registry, `JdbcClient` and JPA.
- **Modulith creates the registry's tables** (`spring.modulith.events.jdbc.schema-initialization.enabled: true`), not Flyway: the schema belongs to the library and follows its version.
- **Completed publications are deleted** (`spring.modulith.events.completion-mode: delete`), so the table holds only outstanding work.
- **Incomplete publications are published again**: on startup (`spring.modulith.events.republish-outstanding-events-on-restart: true`) and, for invitation emails, every 5 minutes for those older than 5 minutes (`InvitationEmailResubmission`, `IncompleteEventPublications`). A listener therefore must be safe to run again: it handles business failures itself and completes (an invitation email records `FAILED`), and throws only on unexpected errors, which leave the publication incomplete.
- **A single application instance is assumed.** Republishing does not know about other instances, and the guard that keeps two deliveries of one invitation email apart is in memory. Running several instances needs a new decision.

Alternatives considered:

- **The JPA starter, as ADR 0001 expected.** It would bring JPA into modules that use `JdbcClient` (ADR 0007) for no benefit.
- **A hand-written outbox table and poller.** The registry already provides storing, dispatching after the commit and republishing.
- **Plain `@Async` or `@TransactionalEventListener` without the registry.** An event in flight would be lost on a stop or crash.
- **A message broker.** Operational cost out of proportion for one application instance.

## Consequences

- Asynchronous work survives a restart; delivery is at least once, so listeners must tolerate a repeat (an invitation email can be sent twice if the application stops after the mail server accepted it and before `SENT` is recorded, accepted in the spec).
- Without `@EnableAsync`, `@ApplicationModuleListener` silently runs in the request thread after the commit; `docs/development/backend.md` lists it as a pitfall.
- Mail-enabled tests clear `event_publication` before each test and wait for it to be empty afterwards (`docs/development/backend.md`, Tests).
- The registry's tables are not in the Flyway history, so they are not part of migration reviews.
