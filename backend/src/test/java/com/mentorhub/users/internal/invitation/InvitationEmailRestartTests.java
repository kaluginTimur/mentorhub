package com.mentorhub.users.internal.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.MentorHubApplication;
import com.mentorhub.support.Mailpit;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.TestUsers;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Spec invitation-emails, AC-12: requests committed by a run that stopped before sending them are sent once by the next
 * start, through the event publication registry. Two application runs share one database.
 */
class InvitationEmailRestartTests {

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
    void theNextStartSendsTheEmailsOfTheStoppedRun() {
        UUID inProgress;
        try (ConfigurableApplicationContext stopped = run(MentorHubApplication.class, LostAsyncTasks.class)) {
            JdbcClient jdbc = stopped.getBean(JdbcClient.class);
            UUID admin = new TestUsers(jdbc).create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
            SecurityContextHolder.getContext()
                    .setAuthentication(TestAuthentications.activeUserAuthentication(admin, Permission.USERS_INVITE));
            try {
                InvitationAdministrationService service = stopped.getBean(InvitationAdministrationService.class);
                service.create("a@x.test", Set.of(), true).invitation().id();
                inProgress =
                        service.create("b@x.test", Set.of(), true).invitation().id();
            } finally {
                SecurityContextHolder.clearContext();
            }
            // A run that stopped while sending leaves its publication in processing.
            jdbc.sql("""
                            update event_publication set status = 'PROCESSING'
                            where serialized_event like '%' || (
                                select id::text from invitation_emails where invitation_id = :id) || '%'
                            """).param("id", inProgress).update();
            assertThat(statuses(jdbc)).containsExactly("QUEUED", "QUEUED");
            assertThat(jdbc.sql("select count(*) from event_publication where completion_date is null")
                            .query(Integer.class)
                            .single())
                    .isEqualTo(2);
        }
        assertThat(storedStatuses()).containsExactly("QUEUED", "QUEUED");
        assertThat(mailpit.awaitMessages(0)).isEmpty();

        try (ConfigurableApplicationContext restarted = run(MentorHubApplication.class)) {
            JdbcClient jdbc = restarted.getBean(JdbcClient.class);
            Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
            while (!statuses(jdbc).equals(List.of("SENT", "SENT"))
                    && Instant.now().isBefore(deadline)) {
                sleep();
            }
            assertThat(statuses(jdbc)).containsExactly("SENT", "SENT");
            assertThat(mailpit.awaitMessages(2))
                    .extracting(message -> message.to().getFirst())
                    .containsExactlyInAnyOrder("a@x.test", "b@x.test");
            while (count(jdbc) > 0 && Instant.now().isBefore(deadline)) {
                sleep();
            }
            assertThat(count(jdbc)).isZero();
        }
    }

    // Arguments, not builder properties: those are defaults, which application.yaml would override.
    private static ConfigurableApplicationContext run(Class<?>... sources) {
        return new SpringApplicationBuilder(sources)
                .run(
                        "--spring.docker.compose.enabled=false",
                        "--spring.datasource.url=" + postgres.getJdbcUrl(),
                        "--spring.datasource.username=" + postgres.getUsername(),
                        "--spring.datasource.password=" + postgres.getPassword(),
                        "--spring.mail.host=" + mailpit.host(),
                        "--spring.mail.port=" + mailpit.smtpPort(),
                        "--mentorhub.mail.from=no-reply@mentorhub.test",
                        "--server.port=0");
    }

    private static List<String> statuses(JdbcClient jdbc) {
        return jdbc.sql("select status from invitation_emails order by requested_at, id")
                .query(String.class)
                .list();
    }

    /** Between the runs, without an application context. */
    private static List<String> storedStatuses() {
        try (Connection connection = DriverManager.getConnection(
                        postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                ResultSet rows = connection
                        .createStatement()
                        .executeQuery("select status from invitation_emails order by requested_at, id")) {
            List<String> statuses = new ArrayList<>();
            while (rows.next()) {
                statuses.add(rows.getString(1));
            }
            return statuses;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int count(JdbcClient jdbc) {
        return jdbc.sql("select count(*) from event_publication")
                .query(Integer.class)
                .single();
    }

    private static void sleep() {
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** The run stops before its async tasks start: they are dropped. */
    @Configuration(proxyBeanMethods = false)
    static class LostAsyncTasks implements AsyncConfigurer {

        @Override
        public Executor getAsyncExecutor() {
            return task -> {};
        }
    }
}
