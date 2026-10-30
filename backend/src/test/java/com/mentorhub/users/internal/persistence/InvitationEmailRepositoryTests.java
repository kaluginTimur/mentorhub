package com.mentorhub.users.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.internal.domain.InvitationEmailFailure;
import com.mentorhub.users.internal.domain.InvitationEmailReason;
import com.mentorhub.users.internal.domain.InvitationEmailStatus;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** Spec invitation-emails, Data model; the request rows behind AC-2 to AC-5, AC-8 to AC-11 and AC-16. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class InvitationEmailRepositoryTests {

    @Autowired
    InvitationEmailRepository emails;

    @Autowired
    InvitationRepository invitations;

    @Autowired
    JdbcClient jdbc;

    UUID admin;
    UUID invitation;

    @BeforeEach
    void createInvitation() {
        admin = jdbc.sql("insert into users (email, status) values (:email, 'ACTIVE') returning id")
                .param("email", UUID.randomUUID() + "@example.com")
                .query(UUID.class)
                .single();
        invitation = newInvitation();
    }

    @Test
    void storesAQueuedRequest() {
        UUID id = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);

        StoredInvitationEmail queued = emails.findQueued(invitation).orElseThrow();
        assertThat(queued.id()).isEqualTo(id);
        assertThat(queued.invitationId()).isEqualTo(invitation);
        assertThat(queued.reason()).isEqualTo(InvitationEmailReason.CREATED);
        assertThat(queued.status()).isEqualTo(InvitationEmailStatus.QUEUED);
        assertThat(queued.requestedBy()).isEqualTo(admin);
        assertThat(queued.requestedAt()).isNotNull();
        assertThat(queued.finishedAt()).isNull();
    }

    // AC-5
    @Test
    void aSecondQueuedRequestIsADuplicateKey() {
        emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);

        assertThatThrownBy(() -> emails.insertQueued(invitation, InvitationEmailReason.RESENT, admin))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void anInvitationWithoutAQueuedRequestHasNone() {
        UUID id = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);
        emails.markSent(id);

        assertThat(emails.findQueued(invitation)).isEmpty();
        assertThat(emails.findQueued(newInvitation())).isEmpty();
    }

    // AC-8
    @Test
    void marksSentWithTheTime() {
        UUID id = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);

        assertThat(emails.markSent(id)).isTrue();

        StoredInvitationEmail sent = emails.lock(id).orElseThrow();
        assertThat(sent.status()).isEqualTo(InvitationEmailStatus.SENT);
        assertThat(sent.finishedAt()).isNotNull();
        assertThat(failure(id)).isNull();
    }

    // AC-10
    @Test
    void marksSkipped() {
        UUID id = emails.insertQueued(invitation, InvitationEmailReason.RESENT, admin);

        assertThat(emails.markSkipped(id)).isTrue();

        assertThat(emails.lock(id).orElseThrow().status()).isEqualTo(InvitationEmailStatus.SKIPPED);
    }

    // AC-11
    @Test
    void marksFailedWithTheReason() {
        UUID id = emails.insertQueued(invitation, InvitationEmailReason.RESENT, admin);

        assertThat(emails.markFailed(id, InvitationEmailFailure.of("550 mailbox unavailable")))
                .isTrue();

        StoredInvitationEmail failed = emails.lock(id).orElseThrow();
        assertThat(failed.status()).isEqualTo(InvitationEmailStatus.FAILED);
        assertThat(failed.finishedAt()).isNotNull();
        assertThat(failure(id)).isEqualTo("550 mailbox unavailable");
    }

    // AC-12: a request that already has an outcome keeps it.
    @Test
    void onlyAQueuedRequestChanges() {
        UUID id = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);
        emails.markSent(id);

        assertThat(emails.markSent(id)).isFalse();
        assertThat(emails.markSkipped(id)).isFalse();
        assertThat(emails.markFailed(id, InvitationEmailFailure.of("refused"))).isFalse();
        assertThat(emails.lock(id).orElseThrow().status()).isEqualTo(InvitationEmailStatus.SENT);
        assertThat(failure(id)).isNull();
    }

    @Test
    void anUnknownRequestIsNeitherFoundNorChanged() {
        UUID unknown = UUID.randomUUID();

        assertThat(emails.lock(unknown)).isEmpty();
        assertThat(emails.markSent(unknown)).isFalse();
    }

    @Test
    void anInvitationCanBeQueuedAgainOnceTheRequestHasAnOutcome() {
        UUID first = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);
        emails.markFailed(first, InvitationEmailFailure.of("refused"));

        UUID second = emails.insertQueued(invitation, InvitationEmailReason.RESENT, admin);

        assertThat(emails.findQueued(invitation).orElseThrow().id()).isEqualTo(second);
    }

    // AC-16
    @Test
    void findsTheLatestRequestOfEachInvitation() {
        UUID sent = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);
        emails.markSent(sent);
        jdbc.sql("update invitation_emails set requested_at = now() - interval '1 hour' where id = :id")
                .param("id", sent)
                .update();
        UUID queued = emails.insertQueued(invitation, InvitationEmailReason.RESENT, admin);
        UUID other = newInvitation();
        UUID otherRequest = emails.insertQueued(other, InvitationEmailReason.CREATED, admin);
        UUID withoutEmail = newInvitation();

        Map<UUID, StoredInvitationEmail> latest = emails.findLatest(List.of(invitation, other, withoutEmail));

        assertThat(latest).containsOnlyKeys(invitation, other);
        assertThat(latest.get(invitation).id()).isEqualTo(queued);
        assertThat(latest.get(other).id()).isEqualTo(otherRequest);
    }

    @Test
    void requestsAtTheSameTimeAreOrderedByIdDescending() {
        // One transaction: now() is the same for both.
        UUID first = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);
        emails.markSent(first);
        UUID second = emails.insertQueued(invitation, InvitationEmailReason.RESENT, admin);
        emails.markSent(second);

        // PostgreSQL orders uuids by unsigned bytes, unlike UUID.compareTo.
        UUID expected = jdbc.sql("select id from invitation_emails where invitation_id = :id order by id desc limit 1")
                .param("id", invitation)
                .query(UUID.class)
                .single();
        assertThat(expected).isIn(first, second);
        assertThat(emails.findLatest(List.of(invitation)).get(invitation).id()).isEqualTo(expected);
    }

    @Test
    void noInvitationsHaveNoLatestRequests() {
        assertThat(emails.findLatest(List.of())).isEmpty();
    }

    private UUID newInvitation() {
        return invitations
                .insertIfNoOpenInvitation(
                        UUID.randomUUID() + "@example.com", token(), admin, Duration.ofDays(7), List.of())
                .orElseThrow();
    }

    private @Nullable String failure(UUID id) {
        return jdbc.sql("select failure from invitation_emails where id = :id")
                .param("id", id)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    private static String token() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
