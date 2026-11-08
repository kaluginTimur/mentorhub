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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
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
 * What the next start does with the emails of earlier runs, through the event publication registry. Three application
 * runs share one database: one whose mail server refuses connections, so its email fails; one that stops before its
 * async tasks run, so its emails stay queued; and the restart with a working mail server, which the tests observe.
 */
class InvitationEmailRestartTests {

    // keep in sync with infra/compose.yaml
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
    static final Mailpit mailpit = new Mailpit();

    static ConfigurableApplicationContext restarted;

    @BeforeAll
    static void runAndRestart() {
        postgres.start();
        mailpit.start();

        UUID admin;
        try (ConfigurableApplicationContext failing = run("127.0.0.1", 1)) {
            JdbcClient jdbc = failing.getBean(JdbcClient.class);
            admin = new TestUsers(jdbc).create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
            create(failing, admin, "failed@x.test");
            await(() -> "FAILED".equals(statuses(jdbc).get("failed@x.test")) && publications(jdbc) == 0);
        }

        try (ConfigurableApplicationContext stopped =
                run(mailpit.host(), mailpit.smtpPort(), MentorHubApplication.class, LostAsyncTasks.class)) {
            JdbcClient jdbc = stopped.getBean(JdbcClient.class);
            create(stopped, admin, "a@x.test");
            create(stopped, admin, "b@x.test");
            // A run that stopped while sending leaves its publication in processing.
            jdbc.sql("""
                            update event_publication set status = 'PROCESSING'
                            where serialized_event like '%' || (
                                select e.id::text from invitation_emails e join invitations i on i.id = e.invitation_id
                                where i.email = 'b@x.test') || '%'
                            """).update();
            assertThat(statuses(jdbc)).containsEntry("a@x.test", "QUEUED").containsEntry("b@x.test", "QUEUED");
            assertThat(publications(jdbc)).isEqualTo(2);
        }
        assertThat(mailpit.awaitMessages(0)).isEmpty();

        restarted = run(mailpit.host(), mailpit.smtpPort(), MentorHubApplication.class);
        JdbcClient jdbc = restarted.getBean(JdbcClient.class);
        // Every outstanding publication of the earlier runs has been processed.
        await(() -> count(jdbc) == 0);
    }

    @AfterAll
    static void stop() {
        if (restarted != null) {
            restarted.close();
        }
        mailpit.stop();
        postgres.stop();
    }

    @Test
    @DisplayName("IEM-6.5 An email left unsent by a stop is sent once after the next start")
    void theNextStartSendsTheEmailsOfTheStoppedRun() {
        JdbcClient jdbc = restarted.getBean(JdbcClient.class);

        assertThat(statuses(jdbc)).containsEntry("a@x.test", "SENT").containsEntry("b@x.test", "SENT");
        assertThat(mailpit.awaitMessages(2))
                .extracting(message -> message.to().getFirst())
                .containsExactlyInAnyOrder("a@x.test", "b@x.test");
    }

    @Test
    @DisplayName("IEM-6.8 A failed email is not sent again after a restart")
    void aFailedEmailIsNotSentAfterARestart() {
        JdbcClient jdbc = restarted.getBean(JdbcClient.class);

        assertThat(statuses(jdbc)).containsEntry("failed@x.test", "FAILED");
        assertThat(mailpit.awaitMessages(2))
                .extracting(message -> message.to().getFirst())
                .doesNotContain("failed@x.test");
    }

    private static void create(ConfigurableApplicationContext context, UUID admin, String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(TestAuthentications.activeUserAuthentication(admin, Permission.USERS_INVITE));
        try {
            context.getBean(InvitationAdministrationService.class).create(email, Set.of(), true);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    // Arguments, not builder properties: those are defaults, which application.yaml would override.
    private static ConfigurableApplicationContext run(String mailHost, int mailPort, Class<?>... sources) {
        return new SpringApplicationBuilder(sources.length == 0 ? new Class<?>[] {MentorHubApplication.class} : sources)
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

    /** The status of each invitation's email, by the invited address. */
    private static Map<String, String> statuses(JdbcClient jdbc) {
        return jdbc
                .sql("select i.email, e.status from invitation_emails e join invitations i on i.id = e.invitation_id")
                .query((rs, rowNum) -> Map.entry(rs.getString(1), rs.getString(2)))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static int publications(JdbcClient jdbc) {
        return jdbc.sql("select count(*) from event_publication where completion_date is null")
                .query(Integer.class)
                .single();
    }

    private static int count(JdbcClient jdbc) {
        return jdbc.sql("select count(*) from event_publication")
                .query(Integer.class)
                .single();
    }

    private static void await(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("condition not reached within 15 seconds");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
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
