# Architecture decisions

Living decisions: each describes the current choice and is edited in place. A reversed choice becomes one line in Rejected alternatives, with its date and reason. A decision is recorded when it is cross-cutting, hard to reverse, and had real alternatives.

Read this index and open a decision when the task touches it.

| Decision | Summary |
|---|---|
| [0001](0001-modular-monolith.md) | One Spring Boot application of Spring Modulith modules with verified boundaries; the student code executor is a separate process |
| [0002](0002-contract-first-api.md) | `api/openapi.yaml` is the source of truth; backend interfaces and the frontend client are generated from it |
| [0003](0003-external-identity-provider.md) | Authentication through Keycloak; no passwords in the application; domain data references `users.id`; registration by invitation |
| [0004](0004-permissions-in-code-role-assignments-in-database.md) | Permissions and roles are enums in code, role assignments are data, checks use permissions only, and relations decide access to a resource |
| [0006](0006-audit-log.md) | Audit entries are written in the changing transaction to an append-only table and read only through per-subject views |
| [0008](0008-asynchronous-events.md) | Asynchronous work goes through the Spring Modulith event publication registry with JDBC, for a single application instance |
