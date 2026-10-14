# Backend development

Java 25, Spring Boot 4.1, Spring Modulith 2.1, PostgreSQL 18, Flyway, Maven (wrapper `./mvnw`), springdoc, Spotless (palantir-java-format), Testcontainers 2, JSpecify. Commands run from `backend/`; paths in this document are relative to the repository root.

## Modules (ADR 0001)

- A module is a direct subpackage of `com.mentorhub` (e.g. `com.mentorhub.cohorts`). Its public API is the types in its root package; the implementation lives in `<module>.internal`.
- Other modules use only public types or events (`ApplicationEventPublisher` + `@ApplicationModuleListener`).
- `com.mentorhub.api` is the generated contract, an open module. No hand-written code there except `package-info.java`.
- Every module has a `package-info.java` with `@NullMarked` (JSpecify). Mark nullable values with `@org.jspecify.annotations.Nullable`.
- `ModularityTests` verifies the boundaries and must stay green.

## API (ADR 0002)

- `openapi-generator-maven-plugin` generates the `*Api` interfaces and DTOs (`com.mentorhub.api.model`) from `api/openapi.yaml` into `backend/target/generated-sources/openapi`, with `interfaceOnly`, `useTags` and `skipDefaultInterface`.
- A controller is a package-private `@RestController` class that `implements` the generated `*Api`.
- Nullability in generated DTOs: an optional property is a `@Nullable` field (`org.springframework.lang.Nullable`) outside the required-args constructor. A required nullable property (`type: [string, 'null']`) is a field without `@NotNull` and without `@Nullable`, and it is a parameter of the required-args constructor, so pass `null` explicitly.
- Because of `skipDefaultInterface`, a contract change can break compilation of existing code: a new operation in a tag that already has a controller needs an implementation, and a new required property changes the generated DTO constructor. Keep the build green with the smallest temporary change (a stub throwing `new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED)`, or a placeholder value) until the real implementation lands.

## Code

- Internal types are `record`s. No Lombok.
- Constructor injection, `final` fields.
- `spring.jpa.open-in-view=false`: load the data you need in the service.
- Formatting: palantir-java-format via Spotless (`./mvnw spotless:apply`). `verify` fails on unformatted code.

## Database

- Migrations: `backend/src/main/resources/db/migration/V<n>__<description>.sql`. The schema is created by migrations only (no `ddl-auto`).
- Never edit, rename or delete an applied migration; add a new one. Flyway validates a CRC32 checksum over the file's lines, so even a comment change breaks startup on every existing database. Tests start from an empty Testcontainers database and do not catch this; only a persistent database does. A project hook blocks edits to committed migrations and to migrations recorded in the dev database.
- Running the application (`./mvnw spring-boot:run` or `TestMentorHubApplication`) applies pending migrations to the dev database in `infra/compose.yaml`, which freezes them. Do not run it with a migration that is still under review.
- Parallel branches can pick the same version number for a new migration, and Flyway rejects duplicate versions. Before merging, rename your own **unapplied** migration to the next free number; never rename an applied one.
- Primary keys are `uuid` (`gen_random_uuid()`), timestamps are `timestamptz`.

## Tests

- Integration: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` (real PostgreSQL 18, needs Docker).
- Web layer: `@WebMvcTest(Controller.class)` + `MockMvcTester`.
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

Jackson 2 (`com.fasterxml.jackson.databind` 2.x) is on the classpath transitively (springdoc → swagger-core), so Jackson 2 imports compile and run with a mapper the application does not configure. Always use the Jackson 3 types.

When unsure whether a class, annotation or method exists, check the real artifacts (see [Checking an API](#checking-an-api)) instead of relying on examples.

## Commands

| Task | Command |
|---|---|
| Build, tests, formatting, module checks | `./mvnw verify` |
| Format | `./mvnw spotless:apply` |
| Regenerate API interfaces | `./mvnw -q generate-sources` |
| Run on :8080 (starts Postgres from `infra/compose.yaml`) | `./mvnw spring-boot:run` |
| Run with Postgres from Testcontainers | `TestMentorHubApplication` in `backend/src/test` |

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
