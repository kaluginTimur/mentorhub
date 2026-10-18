# 0007. Persistence approach per module

Status: accepted, 2026-10-18

## Context

The `users` module (identity foundation) and the `audit` module (user administration) access the database with Spring's `JdbcClient` and map rows to records. No decision recorded this; `spring-boot-starter-data-jpa` is on the classpath but unused.

These modules are a good fit for plain SQL: their logic depends on explicit locking (`select … for update`, advisory locks), search with escaped `ilike` and a fixed sort order, and an append-only table with `jsonb` details. With JPA most of it would be native queries. The tracking core (cohorts, students, notes, assignments) will bring relations and plain create, read and update flows, where an ORM can remove boilerplate.

## Decision

- **Each module chooses its persistence approach:** `JdbcClient` with records, or Spring Data JPA. The choice is made in the module's first spec (Design section) for what fits that module best, and the module uses one approach.
- **`users` and `audit` use `JdbcClient`.**
- Whatever the approach:
  - the schema comes from Flyway migrations only (no `ddl-auto`);
  - persistence types stay internal to their module; no JPA associations or shared entities across module boundaries (ADR 0001), other modules are referenced by id;
  - services load what they need inside the transaction (`spring.jpa.open-in-view=false`).

Alternatives considered:

- **`JdbcClient` everywhere.** One style across the codebase, but relation-heavy modules would hand-write mapping that JPA provides.
- **JPA everywhere.** Uniform, but the lock-heavy and append-only code in `users` and `audit` would be rewritten into native queries with no behaviour change.

## Consequences

- Two persistence styles can coexist; a reader checks the module's spec to know which one applies.
- A module that adopts JPA owns its entity mapping and must keep entities out of its public API.
- Changing a module's approach later is a refactoring of that module only.
- One transaction manager serves both approaches: with the JPA starter present, Spring Boot configures `JpaTransactionManager`, which binds the same connection for `JdbcClient`. JPA and JDBC work in one `@Transactional` call, including `AuditLog.record`, commits or rolls back together.
- Each table is written through one approach only, its owning module's. JDBC writes to a table that a persistence context has loaded leave stale entities and bypass `@Version` checks.
- JPA defers writes until flush, and Hibernate does not flush before `JdbcClient` queries. A JPA module flushes (`EntityManager.flush()`) before calling code that reads its changed tables with SQL, and before returning from a service whose constraint violations must map to a specific problem, so the error surfaces in the service and not at commit.
