# 0006. Asynchronous events through the Modulith event publication registry

## Context

Invitation emails are the first work MentorHub does after a request commits: a slow or unreachable mail server must never fail or delay the administrator's request, and a requested email must not be lost when the application stops before it is sent. The `users` and `audit` modules use `JdbcClient`.

## Decision

- **Events go through the Spring Modulith event publication registry with JDBC** (`spring-modulith-starter-jdbc`). A module publishes an event with `ApplicationEventPublisher` inside the changing transaction; the registry stores the publication in that transaction, and an `@ApplicationModuleListener` handles it after the commit on Boot's async executor (`@EnableAsync` on `MentorHubApplication`). One transaction manager serves the registry, `JdbcClient` and JPA.
- **Modulith creates the registry's tables** (`spring.modulith.events.jdbc.schema-initialization.enabled: true`), not Flyway: the schema belongs to the library and follows its version. So these tables are not in the Flyway history.
- **Completed publications are deleted** (`spring.modulith.events.completion-mode: delete`), so the table holds only outstanding work.
- **Incomplete publications are published again**: on startup (`spring.modulith.events.republish-outstanding-events-on-restart: true`) and, for invitation emails, every 5 minutes for those older than 5 minutes (`InvitationEmailResubmission`, `IncompleteEventPublications`). A listener therefore must be safe to run again: it handles business failures itself and completes (an invitation email records `FAILED`), and throws only on unexpected errors, which leave the publication incomplete.
- **A single application instance is assumed.** Republishing does not know about other instances, and the guard that keeps two deliveries of one invitation email apart is in memory. Running several instances needs a new decision.

## Why

- Asynchronous work survives a restart without a hand-written outbox.
- Cost: delivery is at least once, so listeners must tolerate a repeat. An invitation email can be sent twice if the application stops after the mail server accepted it and before `SENT` is recorded; this is accepted.

## Rejected alternatives

- **Sending the email synchronously right after the commit, without an outbox** (reversed 2026-10-20, user). A slow mail server would delay the response, and an email would be lost if the application stopped before sending it.
- **The JPA starter for the registry.** It would bring JPA into modules that use `JdbcClient` for no benefit.
- **A hand-written outbox table and poller.** The registry already provides storing, dispatching after the commit and republishing.
- **Plain `@Async` or `@TransactionalEventListener` without the registry.** An event in flight would be lost on a stop or crash.
- **A message broker.** Operational cost out of proportion for one application instance.
