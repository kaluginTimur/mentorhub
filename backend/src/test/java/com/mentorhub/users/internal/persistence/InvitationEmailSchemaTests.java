package com.mentorhub.users.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.internal.domain.InvitationEmailReason;
import com.mentorhub.users.internal.domain.InvitationEmailStatus;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** The schema of invitation emails. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class InvitationEmailSchemaTests {

    @Autowired
    JdbcTemplate jdbc;

    UUID admin;
    UUID invitation;

    @BeforeEach
    void createInvitation() {
        admin = jdbc.queryForObject(
                "insert into users (email, status) values (?, 'ACTIVE') returning id",
                UUID.class,
                UUID.randomUUID() + "@example.com");
        invitation = jdbc.queryForObject(
                "insert into invitations (email, token, created_by, expires_at)"
                        + " values (?, ?, ?, now() + interval '7 days') returning id",
                UUID.class,
                UUID.randomUUID() + "@example.com",
                token(),
                admin);
    }

    // A rejected statement aborts the test transaction, so each test has at most one.

    @ParameterizedTest
    @EnumSource(InvitationEmailReason.class)
    void acceptsEveryReasonOfTheCode(InvitationEmailReason reason) {
        assertThatCode(() -> insert(reason.name(), "QUEUED", false, null)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @EnumSource(InvitationEmailStatus.class)
    void acceptsEveryStatusOfTheCode(InvitationEmailStatus status) {
        boolean finished = status != InvitationEmailStatus.QUEUED;
        String failure = status == InvitationEmailStatus.FAILED ? "refused" : null;

        assertThatCode(() -> insert("RESENT", status.name(), finished, failure)).doesNotThrowAnyException();
    }

    @Test
    void aNewRequestIsQueuedAtNow() {
        jdbc.update(
                "insert into invitation_emails (invitation_id, reason, requested_by) values (?, 'CREATED', ?)",
                invitation,
                admin);

        assertThat(jdbc.queryForObject(
                        "select status = 'QUEUED' and requested_at = now() and finished_at is null"
                                + " from invitation_emails where invitation_id = ?",
                        Boolean.class,
                        invitation))
                .isTrue();
    }

    @Test
    void acceptsAFailureOf500Characters() {
        assertThatCode(() -> insert("RESENT", "FAILED", true, "é".repeat(500))).doesNotThrowAnyException();
    }

    @Test
    void rejectsASecondQueuedRequestForAnInvitation() {
        insert("CREATED", "QUEUED", false, null);

        assertThatThrownBy(() -> insert("RESENT", "QUEUED", false, null)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void acceptsAQueuedRequestNextToFinishedOnes() {
        insert("CREATED", "SENT", true, null);
        insert("RESENT", "FAILED", true, "refused");

        assertThatCode(() -> insert("RESENT", "QUEUED", false, null)).doesNotThrowAnyException();
    }

    private void insert(String reason, String status, boolean finished, @Nullable String failure) {
        jdbc.update(
                "insert into invitation_emails (invitation_id, reason, requested_by, status, finished_at, failure)"
                        + " values (?, ?, ?, ?, case when ? then now() end, ?)",
                invitation,
                reason,
                admin,
                status,
                finished,
                failure);
    }

    private static String token() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
