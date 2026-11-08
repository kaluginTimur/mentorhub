# Backend development

Java 25, Spring Boot 4.1, Spring Modulith 2.1, PostgreSQL 18, Flyway, Maven (wrapper `./mvnw`), springdoc, Spotless (palantir-java-format), Testcontainers 2, JSpecify, libphonenumber. Commands run from `backend/`; paths in this document are relative to the repository root.

## Modules (decision 0001)

- A module is a direct subpackage of `com.mentorhub` (e.g. `com.mentorhub.cohorts`). Its public API is the types in its root package; the implementation lives in `<module>.internal`. Other modules use only its public types and its events; `ModularityTests` checks the boundaries.
- Internal code is split into layer subpackages: `domain` (domain types and rules), `persistence` (repositories and their row types), `service`, `web` (controllers), plus module-specific technical ones such as `security`. A type is `public` only when another subpackage uses it; controllers are `public` only if the split requires it. Dependencies between the subpackages stay acyclic. Tests sit in the package of the class they test.
- `com.mentorhub.api` is the generated contract, an open module. No hand-written code there except `package-info.java`.
- Every package has a `package-info.java` with `@NullMarked` (JSpecify), internal subpackages included: `@NullMarked` does not apply to subpackages. Mark nullable values with `@org.jspecify.annotations.Nullable`.
- Events between and inside modules go through the Spring Modulith event publication registry (JDBC, decision 0006): `ApplicationEventPublisher` inside the changing transaction, `@ApplicationModuleListener` after the commit on the async executor. A listener that throws leaves its publication incomplete, and incomplete publications are published again on the next start, or earlier if the module resubmits them itself; so a listener must tolerate a second delivery of the same event. A listener that handles a business failure itself (by recording it) completes. The registry's tables are created by Modulith, not by Flyway. Delivery assumes a single application instance.

## API (decision 0002)

- `openapi-generator-maven-plugin` generates the `*Api` interfaces and DTOs (`com.mentorhub.api.model`) from `api/openapi.yaml` into `backend/target/generated-sources/openapi` (configured in `backend/pom.xml`).
- A controller is a package-private `@RestController` class that `implements` the generated `*Api`. Per tag `Foo` there is the generated `FooApi`, the controller `FooController` and the mapper `FooMapper` (contract DTOs to and from service types), both in the module's `web` package.
- Generated `*Api` interfaces are `@Validated`, so `@Min`/`@Max` on parameters are checked by a method validation proxy, which reports violations by the controller's Java parameter name. Keep controller parameter names equal to the request parameter names.
- A text the service trims (names, comments, search) has no `maxLength` in the contract: the service trims first and then checks the length, so surrounding whitespace never causes a rejection.
- Nullability in generated DTOs: an optional property is a `@Nullable` field (`org.springframework.lang.Nullable`) outside the required-args constructor. A required nullable property (`type: [string, 'null']`) is a field without `@NotNull` and without `@Nullable`, and it is a parameter of the required-args constructor, so pass `null` explicitly.
- Because the generator skips default methods (`skipDefaultInterface`), a contract change can break compilation of existing code: a new operation in a tag that already has a controller needs an implementation, and a new required property changes the generated DTO constructor. Keep the build green with the smallest temporary change (a stub throwing `new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED)`, or a placeholder value) until the real implementation lands.

## Security (decision 0003, decision 0004)

- Every `/api/v1/**` controller method carries `@RequirePermission(Permission.X)` or `@NoPermissionRequired` (both in `com.mentorhub.users`). Services do not repeat it: the controller is the one place where a permission is checked. Permission names never appear in strings; checks never use role names. `PermissionDeclarationTests` fails the build on an operation with neither or both.
- The gate refuses callers that are not invited or disabled with 403 before any controller runs, except for the few operations `SecurityConfiguration` exempts, which need only a valid token. Services behind the gate therefore only see active users: get the caller with `CurrentUserProvider.currentUser()` (`users.id` and permissions), never from the token. Services behind an exemption read the caller in any access status, with the token's identity, through `CurrentCaller`.
- Resource-level checks are explicit service code, evaluated against current domain relations; IDs from the request are never trusted. A single resource the caller may not access throws `com.mentorhub.web.NotFoundException`, exactly like a missing one; lists leave inaccessible items out. A missing permission is Spring Security's `AccessDeniedException`.
- A new public endpoint is listed in `SecurityConfiguration` and declares `security: []` in the contract. The public chain has no bearer token processing, so a stale or invalid token cannot fail a public endpoint. Its path matchers are GET-only; a public endpoint with another method needs its own matcher in `SecurityConfiguration.publicPaths`. Test a public endpoint with an invalid bearer token.
- Secret tokens in links (invitations) travel only in request and response bodies, never in an API path or query, so they stay out of server access logs. Audit details never contain them, reads that do not need a token leave it out, and a response that returns one sets `Cache-Control: no-store`. Do not enable bind-parameter logging (`org.springframework.jdbc` at TRACE, PostgreSQL `log_statement`), which would write tokens to the logs.

## Configuration and profiles

- A required setting has no default: without it startup fails, naming the property. `application-dev.yaml` and the test `backend/src/test/resources/config/application.yaml` set every required one.
- `backend/src/main/resources/application.yaml` holds the production defaults and exposes nothing beyond health, without details: the API docs, Swagger UI and `/openapi.yaml` are opened only in `dev`.
- Optional infrastructure stays out of `/actuator/health`: `management.health.mail.enabled: false` in `application.yaml` turns off Spring Boot's mail health indicator, so an unreachable mail server never makes the application unhealthy.
- Email is optional (module `mail`, infrastructure without domain knowledge). It is enabled when `spring.mail.host` is set, and `mentorhub.mail.from` is then required. Without a host `MailDelivery.isEnabled()` is false, and a feature that sends email must work without it (IEM-1). The caller decides what to send and calls `MailDelivery.send` outside database transactions. Never enable `spring.mail.properties.mail.debug`: JavaMailSender then logs whole messages, links with secret tokens included; treat it like the bind-parameter logging warning in Security.
- `application.yaml` never activates `dev`; only `./mvnw spring-boot:run` (through `backend/pom.xml`) and `TestMentorHubApplication` do.
- In `dev`, `DevelopmentUserRunner` (`backend/src/main/java/com/mentorhub/users/internal/dev/`) creates or updates the development users of `mentorhub.development-users` on every start: identity link, status (a disabled user with its configured `disabled-reason` and `disabled-comment`) and exactly the configured roles. It writes no audit entries. They match the realm users of `infra/keycloak/mentorhub-realm.json` by fixed ids. Development users are never a Flyway migration.

## Errors

- Errors are RFC 9457 Problem Details. The shared problem types are in `backend/src/main/java/com/mentorhub/web/Problems.java`; a feature module keeps its own types in its own class (as `LockoutProblems` in `users`). Every problem passes through `Problems.withDefaultType`, which writes `about:blank` for the rest (see Pitfalls).
- A validation failure is `validation-failed` with field errors (`ValidationProblem` in the contract), each named as in the request. Services throw `com.mentorhub.web.ValidationException`; bean validation, parameters that do not convert and body properties Jackson cannot bind map to the same shape, with bean validation wording. A body that is not JSON keeps Spring's generic 400.
- A conflict is a `com.mentorhub.web.ConflictException` with its problem type, and with extension members when the client needs them. In the contract, a problem with extension members gets its own schema, an `allOf` over `Problem`, like `ValidationProblem`.
- A missing resource is the plain `com.mentorhub.web.NotFoundException`, without a detail for the client. A typed 404 with a detail is only for one answer that covers several reasons, as for an invalid invitation link.
- Modules throw these exceptions and declare no exception handlers: `ProblemDetailsAdvice` in `com.mentorhub.web` renders them, and errors from servlet filters and the container are rendered as Problem Details too, without exception text.

## Code

- Comment sparingly: only non-obvious infrastructure or algorithmic points. No comments that restate the code.
- Internal types are `record`s; constructor injection with `final` fields; no Lombok.
- `spring.jpa.open-in-view=false`: load the data you need in the service.
- Persistence is chosen per module: `JdbcClient` with records or Spring Data JPA, one approach per module, chosen when the module is first planned and recorded here. `users` and `audit` use `JdbcClient`, because their logic depends on explicit locking, escaped search and an append-only table, where JPA would mostly be native queries.
- Persistence types stay internal to their module: no JPA associations or shared entities across module boundaries, other modules are referenced by id, and a JPA module keeps entities out of its public API.
- Each table is written through its owning module's approach only: JDBC writes to a table that a persistence context has loaded leave stale entities and bypass `@Version` checks.
- JPA defers writes until flush, and Hibernate does not flush before `JdbcClient` queries. A JPA module flushes (`EntityManager.flush()`) before calling code that reads its changed tables with SQL, and before returning from a service whose constraint violations must map to a specific problem, so the error surfaces in the service and not at commit.
- `JdbcClient` and JPA share one transaction manager, so both work in one `@Transactional` call, including `AuditLog.record`.

## Database

- Migrations: `backend/src/main/resources/db/migration/V<n>__<description>.sql`. The schema is created by migrations only (no `ddl-auto`).
- A migration that is not in main yet belongs to its change: edit it as the change goes, and keep the change's schema in one migration where it fits. Every `dev` start rebuilds the dev database from the migrations, and tests start from an empty Testcontainers database, so neither holds an old version.
- A migration in main is never edited, renamed or deleted without the project owner's word: a database outside the repository may have applied it, and Flyway validates a CRC32 checksum over the file's lines, so even a comment change breaks startup there. Change the schema with a new migration instead. A project hook blocks edits to migrations in main.
- Parallel branches can pick the same version number, and Flyway rejects duplicate versions. Before merging, rename your migration to the next free number after those in main.
- Primary keys are `uuid` (`gen_random_uuid()`), timestamps are `timestamptz`.
- Advisory locks (`pg_advisory_xact_lock`) use a fixed `long` key per purpose; a new lock picks a key no other lock uses (the administrator lock of the lockout rules is `ADMINISTRATORS_LOCK` in `UserRepository`). Lock order is the advisory lock first, then row locks. The class that takes a lock says in its comment which lock and in which order.
- Parallel creates are decided by a unique index: insert with `on conflict do nothing`, then read again.
- The database's `now()` is the one clock for expiry, status and audit. A status derived from timestamps (an invitation's) is not stored, and its list filters on the same expression.
- Row locks on a table that foreign keys reference use `for no key update`, unless a key column changes: `for update` conflicts with the `for key share` that every referencing insert takes, so two transactions that each lock a row the other's insert references deadlock (`UserRepository.lockUser` and the `audit_log` actor).

## Audit (decision 0005)

- Record an audited change with `com.mentorhub.audit.AuditLog.record` inside the changing `@Transactional` method; it fails without a transaction. The actor is `users.id`, or `null` for the system.
- The action and subject type are string constants owned by the writing module (`UserAuditAction`, subject type `USER`). Details are a JSON-serializable map and never copy content with its own visibility rules.
- There is no global read: the subject's module serves a per-subject history under its own access rules (`AuditLog.findBySubject`) and skips actions it does not know.
- `audit_log` is append-only (a trigger rejects `UPDATE` and `DELETE`). Tests clean it with `truncate` (`TestUsers.deleteAll`).

## Tests

- Every feature has negative authorization tests (another mentor's student, another cohort, the wrong role).
- Levels: the `@WebMvcTest` slice owns each operation's permission, request binding, validation before the service and the JSON of each response type; service tests own the business rules; `@SpringBootTest` endpoint tests own what crosses layers (what a later request sees, the history, that a change refused before the service, by the gate, a permission or binding, stored nothing) and authentication through real tokens. A refusal by the service is its test's, which runs against the same database. A slice test that stubs the service to return or throw re-tests only the mapper and `ProblemDetailsAdvice`: one per response type is enough.
- Rules of the platform are tested once, not again for each controller: the mechanism against `PermissionProbeController` (`WebSliceSecurityTests`) and through real tokens (`AuthenticationChainTests`); that every operation is behind authentication and the gate, unless the contract makes it public or a requirement exempts it, in `OperationGateTests`, which also checks that the served operations are the contract's; that every UUID path id converts the same way, in `PathIdConversionTests`; that every operation declares its permission, in `PermissionDeclarationTests`.
- Integration: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` (real PostgreSQL 18, needs Docker).
- Web layer: `@WebMvcTest(Controller.class)` + `@Import(WebSliceSecurity.class)` + `MockMvcTester`. The import adds the application's filter chains, gate and method security. Authenticate requests with `TestAuthentications`; it and the other shared test support are in `backend/src/test/java/com/mentorhub/users/internal/security/`.
- Authentication through real tokens: `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})`, with tokens signed by `TestIssuer`, which the application trusts through `jwk-set-uri`. Use this for the gate, the bootstrap and permissions.
- Tests get the required security settings from `backend/src/test/resources/config/application.yaml`. It is loaded in addition to the main `application.yaml` (`classpath:/config/` is a default config location); a test `application.yaml` at the classpath root would replace the main one instead.
- Every test configuration of its own (properties, `@DynamicPropertySource`, `@MockitoSpyBean`, `@Import`) starts another Spring context, and restarts and extra containers cost more still. Reuse the contexts that exist, and control timing inside them (holding a send, for example) rather than adding one.
- No fixed waits: wait for an observable end state, such as an empty `event_publication` or a recorded status.
- A schema test checks what the code relies on and no other test would notice: that a check constraint or a reference table accepts every value of its Java enum and every length the code writes, and a constraint the code depends on (a unique index that settles parallel requests, a default an insert leaves out, an append-only trigger) where no scenario test covers it. A constraint that only repeats a check the service makes gets no test.
- Each place a secret could leak (a log, a stored failure reason, audit details, an event) has one test that the secret is absent from it. No test asserts the wording of a log line.
- An endpoint test reads a result through the API where the API shows it, not with SQL. A journey test without an id asserts only what crosses layers.
- A test of an asynchronous listener clears `event_publication` before it starts, so that a publication left by another test does not run in its context, and waits until the table is empty afterwards: completed publications are deleted, so an empty table means every listener has finished.

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

Jackson pitfall: a global `spring.jackson.default-property-inclusion: non_null` also drops required nullable properties such as `MeUser.firstName`, which the contract sends as `null`. To leave an optional property out of one DTO, add a `@JacksonMixin` with `@JsonInclude(NON_NULL)` for that type, as `MeJsonMixin` does for `Me`. When the DTO also has required nullable properties, annotate the optional properties' getters in the mixin instead of the class. Optional array properties of generated DTOs start as `[]`, not `null`; set the unused ones to `null` before serializing.

Events pitfall: `@EnableAsync` on `MentorHubApplication` is what makes `@ApplicationModuleListener` asynchronous. Without it the listener silently runs in the request thread after the commit, and a response waits for the mail server. Keep it, and `@EnableScheduling` for the resubmission job.

Security pitfalls:

- `SecurityMockMvcRequestPostProcessors.jwt()` bypasses the application's converter: no gate, no bootstrap, no permissions from the database. Do not use it; use `TestAuthentications` or `TestIssuer`.
- MockMvc does not perform the servlet container's error dispatch: an exception from a filter reaches the test as an exception instead of the `ProblemErrorController` response. Test filter-level failures with a `RANDOM_PORT` server, as `IssuerFailureTests` does.
- `SecurityConfiguration` uses `@EnableMethodSecurity(proxyTargetClass = true)`. A JDK interface proxy of a controller that implements a generated `*Api` hides `@RestController` from handler mapping, so a `@WebMvcTest` slice answers 404 on every endpoint (the application itself gets class proxies from Boot's AOP auto-configuration). Keep the flag.
- A catch-all `@ExceptionHandler(Exception.class)` turns method security's `AccessDeniedException` into a 500. `ProblemDetailsAdvice` rethrows security exceptions so Spring Security answers them.

When unsure whether a class, annotation or method exists, check the real artifacts (see [Checking an API](#checking-an-api)) instead of relying on examples. Implementation agents ask the `api-lookup` subagent, which follows that section.

## Commands

| Task | Command |
|---|---|
| Build, tests, formatting, module checks | `./mvnw verify` |
| Format | `./mvnw spotless:apply` |
| Regenerate API interfaces | `./mvnw -q generate-sources` |
| Run on :8080 in the `dev` profile (starts Postgres, Keycloak and Mailpit from `infra/compose.yaml`) | `./mvnw spring-boot:run` |
| Run in the `dev` profile with Postgres from Testcontainers; no Keycloak (start it with `docker compose -f infra/compose.yaml up -d keycloak` to sign in) and no Mailpit, so emails end failed | `TestMentorHubApplication` in `backend/src/test` |

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

The `api-lookup` subagent follows this section. Implementation agents ask it and never inspect a dependency themselves: it answers from the exact versions on a small model, and the lookup stays out of their context. To verify a class, annotation or method against the exact versions the project compiles with:

```bash
cd backend && ./mvnw -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

`backend/target/classpath.txt` lists the jar paths, colon-separated. Use only these jars, not other versions in `~/.m2`. Then, with absolute jar paths:

```bash
unzip -l <jar> | grep '<SimpleName>.class'
javap -cp <jar> <fully.qualified.Name>
```

Run these commands in exactly this form (no `cd` into `~/.m2`, nothing appended); other forms do not match the allow rules.
