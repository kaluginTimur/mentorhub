# 0001. Modular monolith on Spring Modulith

## Context

The platform covers several domains: cohorts and students, notes and feedback, assignments, solution checking, scheduling. There is one developer, working together with AI agents.

## Decision

- A single Spring Boot application. Each domain is a Spring Modulith module, i.e. a direct subpackage of `com.mentorhub` (`com.mentorhub.cohorts`, `com.mentorhub.assignments`, ...).
- Modules communicate through public types in the module root package or through application events. Internal subpackages (`internal`) are not accessible to other modules.
- Boundaries are verified by `ModularityTests` (`ApplicationModules.verify()`), which runs as part of `./mvnw verify`.
- Exception: the future executor of student code (SQL/Java/Python). It is a security boundary, so it will be a separate process on an isolated host.

## Why

- Violating module boundaries breaks the build, so agents cannot quietly tangle the code.
- A module can later be extracted into a service without rewriting the rest.

## Rejected alternatives

- **Microservices.** Operational complexity without benefit for one developer.
- **A monolith without explicit boundaries.** It quickly turns into a tangle, especially when agents write the code.
