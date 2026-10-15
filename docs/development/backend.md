# Backend development

Java 25, Spring Boot 4.1, Spring Modulith 2.1, PostgreSQL 18, Flyway, Maven (wrapper `./mvnw`), springdoc, Spotless (palantir-java-format), Testcontainers 2, JSpecify. Commands run from `backend/`; paths in this document are relative to the repository root.

## Modules (ADR 0001)

- A module is a direct subpackage of `com.mentorhub` (e.g. `com.mentorhub.cohorts`). Its public API is the types in its root package; the implementation lives in `<module>.internal`.
- Internal code is split into layer subpackages: `domain` (domain types and rules), `persistence` (repositories and their row types), `service`, `web` (controllers), plus module-specific technical ones such as `security`. A type is `public` only when another subpackage uses it; controllers are `public` only if the split requires it. Dependencies between the subpackages stay acyclic. Tests sit in the package of the class they test.
- Other modules use only public types or events (`ApplicationEventPublisher` + `@ApplicationModuleListener`).
- `com.mentorhub.api` is the generated contract, an open module. No hand-written code there except `package-info.java`.
- Every package has a `package-info.java` with `@NullMarked` (JSpecify), internal subpackages included: `@NullMarked` does not apply to subpackages. Mark nullable values with `@org.jspecify.annotations.Nullable`.
- `ModularityTests` verifies the boundaries and must stay green.

## API (ADR 0002)

- `openapi-generator-maven-plugin` generates the `*Api` interfaces and DTOs (`com.mentorhub.api.model`) from `api/openapi.yaml` into `backend/target/generated-sources/openapi`, with `interfaceOnly`, `useTags` and `skipDefaultInterface`.
- A controller is a package-private `@RestController` class that `implements` the generated `*Api`.
- Nullability in generated DTOs: an optional property is a `@Nullable` field (`org.springframework.lang.Nullable`) outside the required-args constructor. A required nullable property (`type: [string, 'null']`) is a field without `@NotNull` and without `@Nullable`, and it is a parameter of the required-args constructor, so pass `null` explicitly.
- Because of `skipDefaultInterface`, a contract change can break compilation of existing code: a new operation in a tag that already has a controller needs an implementation, and a new required property changes the generated DTO constructor. Keep the build green with the smallest temporary change (a stub throwing `new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED)`, or a placeholder value) until the real implementation lands.

## Security (ADR 0003, ADR 0004)

- `com.mentorhub.users` authenticates every request: the token's `(iss, sub)` is resolved to `users.id`, stored roles are expanded into permissions. Configuration and the filter chains are in `backend/src/main/java/com/mentorhub/users/internal/security/SecurityConfiguration.java`.
- Every `/api/v1/**` controller method carries `@RequirePermission(Permission.X)` or `@NoPermissionRequired` (both in `com.mentorhub.users`). Permission names never appear in strings; checks never use role names. `PermissionDeclarationTests` fails the build on an operation with neither or both.
- The gate refuses callers that are not invited or disabled with 403 before any controller runs, except `GET /api/v1/me`. Services therefore only see active users: get the caller with `CurrentUserProvider.currentUser()` (`users.id` and permissions), never from the token.
- Resource-level checks are explicit service code. A single resource the caller may not access throws `com.mentorhub.web.NotFoundException`, exactly like a missing one; lists leave inaccessible items out. A missing permission is Spring Security's `AccessDeniedException`.
- A new public endpoint is listed in `SecurityConfiguration` and declares `security: []` in the contract.

## Configuration and profiles

- Startup fails, naming the property, without `mentorhub.security.trusted-issuers` (owned by `users`) or `mentorhub.security.client.issuer-uri` and `client-id` (owned by `system`).
- `backend/src/main/resources/application.yaml` holds the production defaults: actuator exposes only health, without details; springdoc's API docs and Swagger UI are disabled; security denies `/openapi.yaml`.
- The `dev` profile (`backend/src/main/resources/application-dev.yaml`) serves Swagger UI, the API docs, `/openapi.yaml` and the actuator `info` and `modulith` endpoints, without sign-in. It trusts the local Keycloak (`http://localhost:8180/realms/mentorhub`) and sets the bootstrap email to `admin@mentorhub.local`.
- Only `./mvnw spring-boot:run` (the `spring-boot-maven-plugin` configuration) and `TestMentorHubApplication` activate `dev`; `application.yaml` never does.
- In `dev`, `DevelopmentUserRunner` (`backend/src/main/java/com/mentorhub/users/internal/dev/`) creates or updates the development users of `mentorhub.development-users` on every start: identity link, status and exactly the configured roles. They match the realm users of `infra/keycloak/mentorhub-realm.json` by fixed ids. Development users are never a Flyway migration.

## Errors

- Errors are RFC 9457 Problem Details (`application/problem+json`). MentorHub types are in `backend/src/main/java/com/mentorhub/web/Problems.java`: `unauthorized` (401), `not-invited`, `access-ended`, `forbidden` (403) and `not-found` (404). Other errors use `about:blank`, written explicitly: the contract makes `type` required, but Spring 7 leaves it `null` and omits it from the JSON, so every problem passes through `Problems.withDefaultType`.
- `ProblemDetailsAdvice` in `com.mentorhub.web` renders Spring MVC exceptions, `NotFoundException` and unexpected exceptions; the security entry point and access-denied handler write the 401 and 403 problems through `ProblemResponseWriter`.

## Code

- Internal types are `record`s. No Lombok.
- Constructor injection, `final` fields.
- `spring.jpa.open-in-view=false`: load the data you need in the service.
- Formatting: palantir-java-format via Spotless (`./mvnw spotless:apply`). `verify` fails on unformatted code.

## Database

- Migrations: `backend/src/main/resources/db/migration/V<n>__<description>.sql`. The schema is created by migrations only (no `ddl-auto`).
- Never edit, rename or delete an applied migration; add a new one. Flyway validates a CRC32 checksum over the file's lines, so even a comment change breaks startup on every existing database. Tests start from an empty Testcontainers database and do not catch this; only a persistent database does. A project hook blocks edits to committed migrations and to migrations recorded in the dev database.
- Running the application with `./mvnw spring-boot:run` applies pending migrations to the dev database in `infra/compose.yaml`, which freezes them. Do not run it with a migration that is still under review. `TestMentorHubApplication` uses a throwaway Testcontainers database instead.
- Parallel branches can pick the same version number for a new migration, and Flyway rejects duplicate versions. Before merging, rename your own **unapplied** migration to the next free number; never rename an applied one.
- Primary keys are `uuid` (`gen_random_uuid()`), timestamps are `timestamptz`.

## Tests

- Integration: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` (real PostgreSQL 18, needs Docker).
- Web layer: `@WebMvcTest(Controller.class)` + `@Import(WebSliceSecurity.class)` + `MockMvcTester`. The import adds the application's filter chains, gate and method security. Authenticate requests with `TestAuthentications.activeUser(userId, permissions...)`, `notInvited()` or `disabledUser(userId)` (in `backend/src/test/java/com/mentorhub/users/internal/security/`, with the other shared test support: `TestIssuer`, `TestIssuerConfiguration`, `TestUsers`, `ProblemAssertions`).
- Authentication through real tokens: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})`, with tokens signed by `TestIssuer`, which the application trusts through `jwk-set-uri`. Use this for the gate, the bootstrap and permissions.
- Tests get the required security settings from `backend/src/test/resources/config/application.yaml`. It is loaded in addition to the main `application.yaml` (`classpath:/config/` is a default config location); a test `application.yaml` at the classpath root would replace the main one instead.
- Per module: `@ApplicationModuleTest`.

## Pitfalls: Spring Boot 4 is not Boot 3

Models and older examples tend to produce Boot 3 code. Most of it fails to compile; Jackson does not.

| Use (Boot 4) | Not (Boot 3) |
|---|---|
| `spring-boot-starter-webmvc` and paired test starters (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, ...) | `spring-boot-starter-web` |
| `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` | `org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest` |
| `org.springframework.test.context.bean.override.mockito.MockitoBean` | `@MockBean` (removed) |
| Jackson 3: `tools.jackson.*`; annotations stay in `com.fasterxml.jackson.annotation` | `com.fasterxml.jackson.databind.*` |
| `org.testcontainers.postgresql.PostgreSQLContainer`, artifacts `testcontainers-*` | `org.testcontainers.containers.PostgreSQLContainer` |
| `spring-boot-starter-security-oauth2-resource-server` and its paired `-test` starter | `spring-boot-starter-oauth2-resource-server` (no paired test starter) |
| `ProblemDetail.getType()` is `null` by default and omitted from the JSON; `Problems.withDefaultType` sets `about:blank` | Assuming `about:blank` is serialized |

Jackson 2 (`com.fasterxml.jackson.databind` 2.x) is on the classpath transitively (springdoc → swagger-core), so Jackson 2 imports compile and run with a mapper the application does not configure. Always use the Jackson 3 types.

Jackson pitfall: a global `spring.jackson.default-property-inclusion: non_null` also drops required nullable properties such as `MeUser.firstName`, which the contract sends as `null`. To leave an optional property out of one DTO, add a `@JacksonMixin` with `@JsonInclude(NON_NULL)` for that type, as `MeJsonMixin` does for `Me`.

Security pitfalls:

- `SecurityMockMvcRequestPostProcessors.jwt()` bypasses the application's converter: no gate, no bootstrap, no permissions from the database. Do not use it; use `TestAuthentications` or `TestIssuer`.
- A catch-all `@ExceptionHandler(Exception.class)` turns method security's `AccessDeniedException` into a 500. `ProblemDetailsAdvice` rethrows security exceptions so Spring Security answers them; no other module declares a catch-all handler.

When unsure whether a class, annotation or method exists, check the real artifacts (see [Checking an API](#checking-an-api)) instead of relying on examples.

## Commands

| Task | Command |
|---|---|
| Build, tests, formatting, module checks | `./mvnw verify` |
| Format | `./mvnw spotless:apply` |
| Regenerate API interfaces | `./mvnw -q generate-sources` |
| Run on :8080 in the `dev` profile (starts Postgres and Keycloak from `infra/compose.yaml`) | `./mvnw spring-boot:run` |
| Run in the `dev` profile with Postgres from Testcontainers; no Keycloak (start it with `docker compose -f infra/compose.yaml up -d keycloak` to sign in) | `TestMentorHubApplication` in `backend/src/test` |

## Verification

The definition of done for backend work. Run exactly this form; it matches the allow rules in `.claude/settings.json`, so agents can run it without prompts (change both together):

```bash
cd backend && ./mvnw -B -q verify -Dmaven.test.redirectTestOutputToFile=true
```

On success it prints only the OpenAPI Generator banner and a JVM warning. On failure:

- test failures: `backend/target/surefire-reports/*.txt`; the console output of each test class is in `backend/target/surefire-reports/*-output.txt`;
- compilation errors: the `[ERROR] /path/File.java:[line,col]` lines;
- formatting: Spotless lists the unformatted files; fix with `./mvnw spotless:apply`;
- module boundaries: `ModularityTests` names the violating dependency;
- Docker unavailable: an environment problem, not a code failure.

## Checking an API

To verify a class, annotation or method against the exact versions the project compiles with:

```bash
cd backend && ./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

`backend/target/classpath.txt` lists the jar paths, colon-separated. Use only these jars, not other versions in `~/.m2`. Then, with absolute jar paths:

```bash
unzip -l <jar> | grep '<SimpleName>.class'
javap -cp <jar> <fully.qualified.Name>
```

Run these commands in exactly this form (no `cd` into `~/.m2`, nothing appended); other forms do not match the allow rules.
