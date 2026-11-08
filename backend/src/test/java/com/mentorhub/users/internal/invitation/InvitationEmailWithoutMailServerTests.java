package com.mentorhub.users.internal.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.InvitationEmailReason;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.persistence.InvitationEmailRepository;
import com.mentorhub.users.internal.persistence.InvitationRepository;
import com.mentorhub.users.internal.security.TestUsers;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The default test configuration has no mail server, so this class shares the default context. Sending is impossible
 * there: {@code MailDelivery.send} throws without a mail server, which would leave the publication incomplete and the
 * email queued.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InvitationEmailWithoutMailServerTests {

    @Autowired
    JdbcClient jdbc;

    @Autowired
    InvitationRepository invitations;

    @Autowired
    InvitationEmailRepository emails;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("IEM-6.7 An email replayed without a mail server fails")
    void aQueuedEmailReplayedWithoutAMailServerFails() throws InterruptedException {
        TestUsers users = new TestUsers(jdbc);
        users.deleteAll();
        jdbc.sql("delete from event_publication").update();
        UUID admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        UUID invitation = invitations
                .insertIfNoOpenInvitation("ivan@x.test", "a".repeat(43), admin, Duration.ofDays(7), List.of())
                .orElseThrow();
        UUID request = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(status -> publisher.publishEvent(new InvitationEmailRequested(request)));

        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (!"FAILED".equals(status(request)) || users.count("event_publication") > 0) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("The email was not recorded as failed within 10 seconds");
            }
            Thread.sleep(50);
        }
        assertThat(status(request)).isEqualTo("FAILED");
    }

    private String status(UUID request) {
        return jdbc.sql("select status from invitation_emails where id = :id")
                .param("id", request)
                .query(String.class)
                .single();
    }
}
