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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** V6 (spec invitation-emails, Data model). */
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

    @Test
    void rejectsAnUnknownReason() {
        assertThatThrownBy(() -> insert("REMINDER", "QUEUED", false, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @EnumSource(InvitationEmailStatus.class)
    void acceptsEveryStatusOfTheCode(InvitationEmailStatus status) {
        boolean finished = status != InvitationEmailStatus.QUEUED;
        String failure = status == InvitationEmailStatus.FAILED ? "refused" : null;

        assertThatCode(() -> insert("RESENT", status.name(), finished, failure)).doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUnknownStatus() {
        assertThatThrownBy(() -> insert("RESENT", "BOUNCED", true, null))
                .isInstanceOf(DataIntegrityViolationException.class);
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

    @ParameterizedTest
    @CsvSource({
        "QUEUED, true,", // queued with a finish time
        "SENT, false,", // finished without a finish time
        "SKIPPED, false,",
        "FAILED, true,", // failed without a reason
        "SENT, true, refused", // a reason without the failure
        "QUEUED, false, refused",
        "FAILED, true, ''" // an empty reason
    })
    void rejectsInconsistentOutcomeColumns(String status, boolean finished, @Nullable String failure) {
        assertThatThrownBy(() -> insert("RESENT", status, finished, failure))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void acceptsAFailureOf500Characters() {
        assertThatCode(() -> insert("RESENT", "FAILED", true, "é".repeat(500))).doesNotThrowAnyException();
    }

    @Test
    void rejectsAFailureOver500Characters() {
        assertThatThrownBy(() -> insert("RESENT", "FAILED", true, "x".repeat(501)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // AC-5
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

    @Test
    void rejectsAnUnknownInvitation() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into invitation_emails (invitation_id, reason, requested_by) values (?, 'RESENT', ?)",
                        UUID.randomUUID(),
                        admin))
                .isInstanceOf(DataIntegrityViolationException.class);
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
