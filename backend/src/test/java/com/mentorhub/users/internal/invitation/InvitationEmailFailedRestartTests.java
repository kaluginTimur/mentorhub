package com.mentorhub.users.internal.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.MentorHubApplication;
import com.mentorhub.support.Mailpit;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.TestUsers;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** An email that failed is not sent by the next start. Two application runs share one database. */
class InvitationEmailFailedRestartTests {

    // keep in sync with infra/compose.yaml
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    static final Mailpit mailpit = new Mailpit();

    @BeforeAll
    static void start() {
        postgres.start();
        mailpit.start();
    }

    @AfterAll
    static void stop() {
        mailpit.stop();
        postgres.stop();
    }

    @Test
    @DisplayName("IEM-6.8 A failed email is not sent again after a restart")
    void aFailedEmailIsNotSentAfterARestart() throws InterruptedException {
        try (ConfigurableApplicationContext failing = run("127.0.0.1", 1)) {
            JdbcClient jdbc = failing.getBean(JdbcClient.class);
            UUID admin = new TestUsers(jdbc).create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
            SecurityContextHolder.getContext()
                    .setAuthentication(TestAuthentications.activeUserAuthentication(admin, Permission.USERS_INVITE));
            try {
                failing.getBean(InvitationAdministrationService.class).create("a@x.test", Set.of(), true);
            } finally {
                SecurityContextHolder.clearContext();
            }
            await(() -> statuses(jdbc).equals(List.of("FAILED")) && publications(jdbc) == 0);
        }

        try (ConfigurableApplicationContext restarted = run(mailpit.host(), mailpit.smtpPort())) {
            JdbcClient jdbc = restarted.getBean(JdbcClient.class);
            Thread.sleep(2000);

            assertThat(statuses(jdbc)).containsExactly("FAILED");
            assertThat(mailpit.awaitMessages(0)).isEmpty();
        }
    }

    // Arguments, not builder properties: those are defaults, which application.yaml would override.
    private static ConfigurableApplicationContext run(String mailHost, int mailPort) {
        return new SpringApplicationBuilder(MentorHubApplication.class)
                .run(
                        "--spring.docker.compose.enabled=false",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--spring.mail.host=" + mailHost,
                        "--spring.mail.port=" + mailPort,
                        "--mentorhub.mail.from=no-reply@mentorhub.test",
                        "--server.port=0");
    }

    private static List<String> statuses(JdbcClient jdbc) {
        return jdbc.sql("select status from invitation_emails order by requested_at, id")
                .query(String.class)
                .list();
    }

    private static int publications(JdbcClient jdbc) {
        return jdbc.sql("select count(*) from event_publication where completion_date is null")
                .query(Integer.class)
                .single();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("condition not reached within 15 seconds");
            }
            Thread.sleep(50);
        }
    }
}
