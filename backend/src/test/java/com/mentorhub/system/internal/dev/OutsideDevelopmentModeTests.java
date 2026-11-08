package com.mentorhub.system.internal.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.MentorHubApplication;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestUsers;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Real starts of the application without the dev profile; each test has a database of its own. */
class OutsideDevelopmentModeTests {

    // keep in sync with infra/compose.yaml
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

    @BeforeAll
    static void start() {
        postgres.start();
    }

    @AfterAll
    static void stop() {
        postgres.stop();
    }

    @Test
    @DisplayName("DEV-1.1 Development users exist only in development mode")
    void aStartWithoutDevelopmentModeCreatesNoDevelopmentUser() {
        String database = newDatabase("no_development_users");

        try (ConfigurableApplicationContext application = run(database)) {
            JdbcClient jdbc = application.getBean(JdbcClient.class);

            assertThat(jdbc.sql("select subject from user_identities where subject in (:subjects)")
                            .param("subjects", Personas.ALL_SUBJECTS)
                            .query(String.class)
                            .list())
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("DEV-2.2 Data outside development mode is never replaced")
    void aStartWithoutDevelopmentModeKeepsTheExistingData() {
        String database = newDatabase("existing_data");
        Map<String, List<String>> existing;
        try (ConfigurableApplicationContext first = run(database)) {
            JdbcClient jdbc = first.getBean(JdbcClient.class);
            TestUsers users = new TestUsers(jdbc);
            UUID mentor = users.create("existing@example.test", UserStatus.ACTIVE, Role.MENTOR);
            // An identity the sample center would give to a persona.
            jdbc.sql("insert into user_identities (user_id, issuer, subject) values (:user, :issuer, :subject)")
                    .param("user", mentor)
                    .param("issuer", Personas.LOCAL_ISSUER)
                    .param("subject", Personas.USERS.getFirst().subject())
                    .update();
            jdbc.sql("insert into audit_log (action, subject_type, subject_id) values ('USER_CREATED', 'USER', :id)")
                    .param("id", mentor)
                    .update();
            existing = DatabaseSnapshot.complete(jdbc);
        }

        try (ConfigurableApplicationContext restarted = run(database)) {
            assertThat(DatabaseSnapshot.complete(restarted.getBean(JdbcClient.class)))
                    .isEqualTo(existing);
        }
    }

    // Arguments, not builder properties: those are defaults, which application.yaml would override.
    private static ConfigurableApplicationContext run(String database) {
        return new SpringApplicationBuilder(MentorHubApplication.class)
                .run(
                        "--spring.docker.compose.enabled=false",
                        "--spring.datasource.url=" + url(database),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--server.port=0");
    }

    private static String newDatabase(String name) {
        try (Connection connection =
                DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.createStatement().execute("create database " + name);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return name;
    }

    private static String url(String database) {
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + database;
    }
}
