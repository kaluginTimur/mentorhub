# 0001. Modular monolith on Spring Modulith

Status: accepted, 2026-10-11

## Context

The platform covers several domains: cohorts and students, notes and feedback, assignments, solution checking, scheduling. There is one developer, working together with AI agents. Microservices would add operational complexity without benefit, while a monolith without explicit boundaries quickly turns into a tangle, especially when agents write the code.

## Decision

- A single Spring Boot application. Each domain is a Spring Modulith module, i.e. a direct subpackage of `com.mentorhub` (`com.mentorhub.cohorts`, `com.mentorhub.assignments`, ...).
- Modules communicate through public types in the module root package or through application events. Internal subpackages (`internal`) are not accessible to other modules.
- Boundaries are verified by `ModularityTests` (`ApplicationModules.verify()`), which runs as part of `./mvnw verify`.
- Exception: the future executor of student code (SQL/Java/Python). It is a security boundary, so it will be a separate process on an isolated host.

## Consequences

- Violating module boundaries breaks the build.
- A module can later be extracted into a service without rewriting the rest.
- The Modulith event publication registry (`spring-modulith-starter-jpa`) will be added when asynchronous events between modules appear.
