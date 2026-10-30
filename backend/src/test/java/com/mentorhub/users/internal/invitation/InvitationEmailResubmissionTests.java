package com.mentorhub.users.internal.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.mail.MailDelivery;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Spec invitation-emails, AC-13 and AC-12 ("never sent again"): the stale resubmission picks up a publication whose
 * send is still in progress (a backlog makes it look stale), and that must not send the email a second time. It
 * resubmits only invitation email publications.
 */
@SpringBootTest(properties = {"spring.mail.host=localhost", "mentorhub.mail.from=no-reply@mentorhub.test"})
@Import({TestcontainersConfiguration.class, InvitationEmailResubmissionTests.FailingListener.class})
class InvitationEmailResubmissionTests {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    InvitationAdministrationService service;

    @Autowired
    InvitationEmailResubmission resubmission;

    @Autowired
    ApplicationEventPublisher events;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    FailingListener otherListener;

    @MockitoSpyBean
    MailDelivery mail;

    TestUsers users;
    UUID admin;
    CountDownLatch release;
    AtomicInteger sends;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        jdbc.sql("delete from event_publication").update();
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        release = new CountDownLatch(1);
        sends = new AtomicInteger();
        doAnswer(invocation -> {
                    sends.incrementAndGet();
                    release.await();
                    return null;
                })
                .when(mail)
                .send(any());
    }

    @AfterEach
    void awaitIdle() {
        release.countDown();
        await(() -> users.count("event_publication") == 0);
    }

    @Test
    void aSendStillInProgressIsNotStartedAgain() {
        create("a@x.test");
        await(() -> sends.get() == 1);
        jdbc.sql("update event_publication set publication_date = publication_date - interval '6 minutes'")
                .update();

        resubmission.resubmitStalePublications();
        sleep(Duration.ofSeconds(1));
        assertThat(sends).hasValue(1);

        release.countDown();
        await(() -> users.count("event_publication") == 0);
        assertThat(jdbc.sql("select status from invitation_emails")
                        .query(String.class)
                        .single())
                .isEqualTo("SENT");
        assertThat(sends).hasValue(1);
    }

    @Test
    void otherEventsAreNotResubmitted() {
        transactions.executeWithoutResult(status -> events.publishEvent(new OtherEvent()));
        await(() -> otherListener.calls() == 1
                && jdbc.sql("select status from event_publication")
                        .query(String.class)
                        .list()
                        .equals(List.of("FAILED")));
        jdbc.sql("update event_publication set publication_date = publication_date - interval '6 minutes'")
                .update();

        resubmission.resubmitStalePublications();
        sleep(Duration.ofSeconds(1));
        assertThat(otherListener.calls()).isEqualTo(1);

        jdbc.sql("delete from event_publication").update();
    }

    private void create(String email) {
        SecurityContextHolder.getContext()
                .setAuthentication(TestAuthentications.activeUserAuthentication(admin, Permission.USERS_INVITE));
        try {
            service.create(email, Set.of(), true);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static void await(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
            sleep(Duration.ofMillis(50));
        }
        assertThat(condition.getAsBoolean()).isTrue();
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    record OtherEvent() {}

    static class FailingListener {

        private final AtomicInteger calls = new AtomicInteger();

        int calls() {
            return calls.get();
        }

        @ApplicationModuleListener
        void on(OtherEvent event) {
            calls.incrementAndGet();
            throw new IllegalStateException("unexpected failure");
        }
    }
}
