# Backend

Java 25, Spring Boot 4.1, Spring Modulith 2.1, PostgreSQL 18, Flyway, Maven

## Modules (ADR 0001)

- A module is a direct subpackage of `com.mentorhub` (e.g. `com.mentorhub.cohorts`). The module's public API is the types in its root package; keep the implementation in `<module>.internal`.
- Other modules use only public types or events (`ApplicationEventPublisher` + `@ApplicationModuleListener`).
- `com.mentorhub.api` is the generated contract, an open module. Do not add hand-written code there except `package-info.java`.
- Every module has a `package-info.java` with `@NullMarked` (JSpecify). Mark nullable values with `@org.jspecify.annotations.Nullable`.
- `ModularityTests` must stay green.

## Spring Boot 4 differences from Boot 3

- Starters are modular: `spring-boot-starter-webmvc` (not `-web`), with paired test starters (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, ...).
- Test annotations moved: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` etc. Use `MockMvcTester` (AssertJ) for MockMvc.
- Jackson 3: packages are `tools.jackson.*`; annotations remain in `com.fasterxml.jackson.annotation`.
- Testcontainers 2: `org.testcontainers.postgresql.PostgreSQLContainer`, artifacts named `testcontainers-*`.
- When unsure about an API, check the classes in `~/.m2/repository` instead of relying on Boot 3 examples.

## Code

- API DTOs come from the generator (`com.mentorhub.api.model`). Make internal types `record`s. No Lombok.
- A controller is a package-private `@RestController` class that `implements` the generated `*Api`.
- Constructor injection, `final` fields.
- `spring.jpa.open-in-view=false`: load the data you need in the service.
- Formatting: palantir-java-format via Spotless (`./mvnw spotless:apply`). `verify` fails on unformatted code.

## Database

- Flyway migrations: `src/main/resources/db/migration/V<n>__<description>.sql`. Never edit applied migrations; add new ones.
- The schema is created by migrations only (no `ddl-auto`).
- Primary keys are `uuid` (`gen_random_uuid()`), timestamps are `timestamptz`.

## Tests

- Integration: `@SpringBootTest` + `@Import(TestcontainersConfiguration.class)` (real Postgres 18).
- Web layer: `@WebMvcTest(Controller.class)` + `MockMvcTester`.
- Per module: `@ApplicationModuleTest`.
