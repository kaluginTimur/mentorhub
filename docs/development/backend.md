# Backend development

Java 25, Spring Boot 4.1, Spring Modulith 2.1, PostgreSQL 18, Flyway, Maven (wrapper `./mvnw`), springdoc, Spotless (palantir-java-format), Testcontainers 2, JSpecify, libphonenumber (phone parsing and E.164 normalization). Commands run from `backend/`; paths in this document are relative to the repository root.

## Modules (ADR 0001)

- A module is a direct subpackage of `com.mentorhub` (e.g. `com.mentorhub.cohorts`). Its public API is the types in its root package; the implementation lives in `<module>.internal`.
- Internal code is split into layer subpackages: `domain` (domain types and rules), `persistence` (repositories and their row types), `service`, `web` (controllers), plus module-specific technical ones such as `security`. A type is `public` only when another subpackage uses it; controllers are `public` only if the split requires it. Dependencies between the subpackages stay acyclic. Tests sit in the package of the class they test.
- Other modules use only public types or events (`ApplicationEventPublisher` + `@ApplicationModuleListener`).
- `com.mentorhub.api` is the generated contract, an open module. No hand-written code there except `package-info.java`.
- Every package has a `package-info.java` with `@NullMarked` (JSpecify), internal subpackages included: `@NullMarked` does not apply to subpackages. Mark nullable values with `@org.jspecify.annotations.Nullable`.
- `ModularityTests` verifies the boundaries and must stay green.

## API (ADR 0002)

- `openapi-generator-maven-plugin` generates the `*Api` interfaces and DTOs (`com.mentorhub.api.model`) from `api/openapi.yaml` into `backend/target/generated-sources/openapi`, with `interfaceOnly`, `useTags` and `skipDefaultInterface`.
- A controller is a package-private `@RestController` class that `implements` the generated `*Api`. Per tag `Foo` there is the generated `FooApi`, the controller `FooController` and the mapper `FooMapper` (contract DTOs to and from service types), both in the module's `web` package (for example `UsersApi`, `UsersController`, `UsersMapper`).
- Generated `*Api` interfaces are `@Validated`, so `@Min`/`@Max` on parameters are checked by a method validation proxy, which reports violations by the controller's Java parameter name. Keep controller parameter names equal to the request parameter names.
- Pagination: query parameters `page` (zero-based, default 0) and `size` (1 to 100, default 25), never clamped; the response is a page object `{items, page, size, totalItems}` (`UserPage`, `HistoryPage`), where `size` is the requested size.
- Nullability in generated DTOs: an optional property is a `@Nullable` field (`org.springframework.lang.Nullable`) outside the required-args constructor. A required nullable property (`type: [string, 'null']`) is a field without `@NotNull` and without `@Nullable`, and it is a parameter of the required-args constructor, so pass `null` explicitly.
- Because of `skipDefaultInterface`, a contract change can break compilation of existing code: a new operation in a tag that already has a controller needs an implementation, and a new required property changes the generated DTO constructor. Keep the build green with the smallest temporary change (a stub throwing `new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED)`, or a placeholder value) until the real implementation lands.

## Security (ADR 0003, ADR 0004)

- `com.mentorhub.users` authenticates every request: the token's `(iss, sub)` is resolved to `users.id`, stored roles are expanded into permissions. The filter chains and the gate are in `backend/src/main/java/com/mentorhub/users/internal/security/SecurityConfiguration.java`; token validation per trusted issuer is in `backend/src/main/java/com/mentorhub/users/internal/security/JwtAuthenticationConfiguration.java` and `backend/src/main/java/com/mentorhub/users/internal/security/TrustedIssuers.java`. `JwtAuthenticationConfiguration` also wires the administrator bootstrap (ADR 0005), which writes its `USER_BOOTSTRAPPED_ADMIN` audit entry.
- Every `/api/v1/**` controller method carries `@RequirePermission(Permission.X)` or `@NoPermissionRequired` (both in `com.mentorhub.users`). Permission names never appear in strings; checks never use role names. `PermissionDeclarationTests` fails the build on an operation with neither or both.
- The gate refuses callers that are not invited or disabled with 403 before any controller runs, except `GET /api/v1/me` and `POST /api/v1/invitations/accept`, which need only a valid token. Services behind the gate therefore only see active users: get the caller with `CurrentUserProvider.currentUser()` (`users.id` and permissions), never from the token. Services behind the two exemptions read the caller in any access status through `CurrentCaller` (`backend/src/main/java/com/mentorhub/users/internal/security/CurrentCaller.java`): access status, `users.id` if linked, issuer, subject, email, `email_verified` and names.
- Resource-level checks are explicit service code. A single resource the caller may not access throws `com.mentorhub.web.NotFoundException`, exactly like a missing one; lists leave inaccessible items out. A missing permission is Spring Security's `AccessDeniedException`.
- A new public endpoint is listed in `SecurityConfiguration` and declares `security: []` in the contract. The public chain has no bearer token processing, so a stale or invalid token cannot fail a public endpoint. Its path matchers are GET-only; a public endpoint with another method needs its own matcher in `SecurityConfiguration.publicPaths`, as `POST /api/v1/invitations/preview` has. Test a public endpoint with an invalid bearer token.
- Secret link tokens (invitations): 32 bytes from `SecureRandom`, base64url without padding (`InvitationTokens`). The token is stored in plaintext in `invitations.token`, unique and with a format check constraint (`token ~ '^[A-Za-z0-9_-]{43}$'`), and lookups use the token itself. A token that does not have the generated format is rejected without a query. Only `InvitationRepository.findLink` reads a stored token back, for the link of a pending invitation; every other read leaves it out. Tokens travel only in request and response bodies, never in an API path or query, so they stay out of server access logs; audit details never contain them. A response that returns a token sets `Cache-Control: no-store`. Do not enable bind-parameter logging (`org.springframework.jdbc` at TRACE, PostgreSQL `log_statement`), which would write tokens to the logs.

## Configuration and profiles

- Startup fails, naming the property, without `mentorhub.security.trusted-issuers` or `mentorhub.app-url` (owned by `users`), or `mentorhub.security.client.issuer-uri` and `client-id` (owned by `system`). `mentorhub.app-url` is the frontend's public URL that invitation links point to (`InvitationProperties`); `application-dev.yaml` sets it to the frontend dev server and the test `backend/src/test/resources/config/application.yaml` to `https://app.test`.
- `mentorhub.invitations.link-lifetime` (owned by `users`, default `7d`) is how long an invitation link stays valid after it is created or issued again; startup fails unless it is positive.
- `backend/src/main/resources/application.yaml` holds the production defaults: actuator exposes only health, without details; springdoc's API docs and Swagger UI are disabled; security denies `/openapi.yaml`.
- The mail server is optional and stays out of `/actuator/health`: `management.health.mail.enabled: false` in `application.yaml` turns off Spring Boot's mail health indicator, so an unreachable mail server never makes the application unhealthy. A failed invitation email shows as `FAILED` in the invitations list and as a warning in the log instead. `DevelopmentProfileTests` checks that health stays up in the `dev` profile, whose Mailpit those tests do not start.
- The `dev` profile (`backend/src/main/resources/application-dev.yaml`) serves Swagger UI, the API docs, `/openapi.yaml` and the actuator `info` and `modulith` endpoints, without sign-in. It trusts the local Keycloak (`http://localhost:8180/realms/mentorhub`) and sets the bootstrap email to `admin@mentorhub.local`.
- Only `./mvnw spring-boot:run` and `TestMentorHubApplication` activate `dev`; `application.yaml` never does. For Maven it is the `spring-boot.run.profiles` property in `backend/pom.xml`, which covers `spring-boot:run` (and `start`, `test-run`) but not packaging or `process-aot`; `-Dspring-boot.run.profiles=...` overrides it.
- Each trusted issuer's OIDC discovery and JWK set requests use `mentorhub.security.issuer-connect-timeout` (default 2s) and `mentorhub.security.issuer-read-timeout` (default 5s). An unreachable or hung identity provider fails the request with a 500 Problem after them instead of holding the request thread.
- In `dev`, `DevelopmentUserRunner` (`backend/src/main/java/com/mentorhub/users/internal/dev/`) creates or updates the development users of `mentorhub.development-users` on every start: identity link, status (a disabled user with its configured `disabled-reason` and `disabled-comment`) and exactly the configured roles. It writes no audit entries. They match the realm users of `infra/keycloak/mentorhub-realm.json` by fixed ids. Development users are never a Flyway migration.

## Errors

- Errors are RFC 9457 Problem Details (`application/problem+json`). MentorHub types are in `backend/src/main/java/com/mentorhub/web/Problems.java`: `unauthorized` (401), `not-invited`, `access-ended`, `forbidden` (403), `not-found` (404) and `validation-failed` (400). A feature module owns its own types, such as `self-lockout` and `last-admin` (409) in `backend/src/main/java/com/mentorhub/users/internal/service/LockoutProblems.java` and the invitation types in `backend/src/main/java/com/mentorhub/users/internal/invitation/InvitationProblems.java`. Other errors use `about:blank`, written explicitly: the contract makes `type` required, but Spring 7 leaves it `null` and omits it from the JSON, so every problem passes through `Problems.withDefaultType`.
- `validation-failed` carries an `errors` member, `[{field, message}]` (`ValidationProblem` in the contract), where `field` is the query or path parameter name or the request body property name. Services throw `com.mentorhub.web.ValidationException` with `FieldViolation`s; bean validation (`@Valid` bodies, `@Min`/`@Max` parameters), parameters that do not convert (a non-UUID `{id}`, an unknown enum value) and body properties Jackson cannot bind (an unknown enum value) map to the same shape. Messages use bean validation wording (`must be between 1 and 100`); the frontend may show its own text per field. A body that is not JSON keeps Spring's generic 400 (`about:blank`).
- `com.mentorhub.web.ConflictException(type, detail)` responds 409 with the given problem type. `ConflictException(type, detail, extensions)` adds the map's entries as `ProblemDetail` properties, i.e. members of the problem next to the standard ones (for example `invitationId` of the conflicting invitation).
- `com.mentorhub.web.NotFoundException()` is the plain `not-found` 404 without a client-visible detail. `NotFoundException(URI type, String detail)` is a typed 404 whose detail is sent to the client, for one answer that covers several reasons (`invitation-invalid`: unknown, expired, revoked or used link).
- In the contract, a problem with extension members gets its own schema, an `allOf` over `Problem` plus the extension properties (`InvitationConflictProblem`), like `ValidationProblem`.
- `ProblemDetailsAdvice` in `com.mentorhub.web` renders Spring MVC exceptions, `NotFoundException`, `ValidationException`, `ConflictException`, bean validation failures and unexpected exceptions; the security entry point and access-denied handler write the 401 and 403 problems through `ProblemResponseWriter`.
- `ProblemErrorController` in `com.mentorhub.web` replaces Boot's `BasicErrorController`. Errors from servlet filters (for example the security filter chain when an issuer's keys cannot be loaded) and from the container go through the error dispatch and are rendered as Problem Details: `about:blank`, the original status, the original path as `instance`, no exception text.

## Code

- Internal types are `record`s. No Lombok.
- Constructor injection, `final` fields.
- `spring.jpa.open-in-view=false`: load the data you need in the service.
- Persistence is chosen per module (ADR 0007, `docs/adr/0007-persistence-approach-per-module.md`): `JdbcClient` with records or Spring Data JPA, one approach per module, stated in the module's first spec. `users` and `audit` use `JdbcClient`. Each table is written through its owning module's approach only; a JPA module flushes before SQL reads of its changed tables.
- Formatting: palantir-java-format via Spotless (`./mvnw spotless:apply`). `verify` fails on unformatted code.

## Database

- Migrations: `backend/src/main/resources/db/migration/V<n>__<description>.sql`. The schema is created by migrations only (no `ddl-auto`).
- Never edit, rename or delete an applied migration; add a new one. Flyway validates a CRC32 checksum over the file's lines, so even a comment change breaks startup on every existing database. Tests start from an empty Testcontainers database and do not catch this; only a persistent database does. A project hook blocks edits to committed migrations and to migrations recorded in the dev database.
- Running the application with `./mvnw spring-boot:run` applies pending migrations to the dev database in `infra/compose.yaml`, which freezes them. Do not run it with a migration that is still under review. `TestMentorHubApplication` uses a throwaway Testcontainers database instead.
- Parallel branches can pick the same version number for a new migration, and Flyway rejects duplicate versions. Before merging, rename your own **unapplied** migration to the next free number; never rename an applied one.
- Primary keys are `uuid` (`gen_random_uuid()`), timestamps are `timestamptz`.
- Advisory locks (`pg_advisory_xact_lock`) use a fixed `long` key per purpose. The administrator lock of the lockout rules is `ADMINISTRATORS_LOCK` in `UserRepository`; a new lock picks a different key. Lock order is the advisory lock first, then row locks.
- Row locks on a table that foreign keys reference use `for no key update`, unless a key column changes: `for update` conflicts with the `for key share` that every referencing insert takes, so two transactions that each lock a row the other's insert references deadlock (`UserRepository.lockUser` and the `audit_log` actor).

## Audit (ADR 0006)

- Record an audited change with `com.mentorhub.audit.AuditLog.record(new AuditEntry(actorUserId, action, subjectType, subjectId, details))` inside the changing `@Transactional` method; `record` joins the transaction (`Propagation.MANDATORY`) and fails without one. The actor is `users.id`, or `null` for the system.
- The action and subject type are string constants owned by the writing module (`UserAuditAction`, subject type `USER`). Details are a JSON-serializable map and never copy content with its own visibility rules.
- There is no global read: the subject's module serves a per-subject history under its own access rules (`AuditLog.findBySubject`) and skips actions it does not know.
- `audit_log` is append-only (a trigger rejects `UPDATE` and `DELETE`). Tests clean it with `truncate` (`TestUsers.deleteAll`).

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

Jackson pitfall: a global `spring.jackson.default-property-inclusion: non_null` also drops required nullable properties such as `MeUser.firstName`, which the contract sends as `null`. To leave an optional property out of one DTO, add a `@JacksonMixin` with `@JsonInclude(NON_NULL)` for that type, as `MeJsonMixin` does for `Me`. When the DTO also has required nullable properties, annotate the optional properties' getters in the mixin instead of the class (`UserDetailsJsonMixin`, `HistoryEntryJsonMixin`). Optional array properties of generated DTOs start as `[]`, not `null`; set the unused ones to `null` before serializing.

Security pitfalls:

- `SecurityMockMvcRequestPostProcessors.jwt()` bypasses the application's converter: no gate, no bootstrap, no permissions from the database. Do not use it; use `TestAuthentications` or `TestIssuer`.
- MockMvc does not perform the servlet container's error dispatch: an exception from a filter reaches the test as an exception instead of the `ProblemErrorController` response. Test filter-level failures with a `RANDOM_PORT` server, as `IssuerFailureTests` does.
- `SecurityConfiguration` uses `@EnableMethodSecurity(proxyTargetClass = true)`. A JDK interface proxy of a controller that implements a generated `*Api` hides `@RestController` from handler mapping, so a `@WebMvcTest` slice answers 404 on every endpoint (the application itself gets class proxies from Boot's AOP auto-configuration). Keep the flag.
- A catch-all `@ExceptionHandler(Exception.class)` turns method security's `AccessDeniedException` into a 500. `ProblemDetailsAdvice` rethrows security exceptions so Spring Security answers them; no other module declares a catch-all handler.

When unsure whether a class, annotation or method exists, check the real artifacts (see [Checking an API](#checking-an-api)) instead of relying on examples. Implementation agents ask the `api-lookup` subagent, which follows that section.

## Commands

| Task | Command |
|---|---|
| Build, tests, formatting, module checks | `./mvnw verify` |
| Format | `./mvnw spotless:apply` |
| Regenerate API interfaces | `./mvnw -q generate-sources` |
| Run on :8080 in the `dev` profile (starts Postgres, Keycloak and Mailpit from `infra/compose.yaml`) | `./mvnw spring-boot:run` |
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

The `api-lookup` subagent follows this section; implementation agents ask it instead of running these commands themselves. To verify a class, annotation or method against the exact versions the project compiles with:

```bash
cd backend && ./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

`backend/target/classpath.txt` lists the jar paths, colon-separated. Use only these jars, not other versions in `~/.m2`. Then, with absolute jar paths:

```bash
unzip -l <jar> | grep '<SimpleName>.class'
javap -cp <jar> <fully.qualified.Name>
```

Run these commands in exactly this form (no `cd` into `~/.m2`, nothing appended); other forms do not match the allow rules.
