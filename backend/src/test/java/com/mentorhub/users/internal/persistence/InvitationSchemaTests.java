package com.mentorhub.users.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.TestcontainersConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class InvitationSchemaTests {

    @Autowired
    JdbcTemplate jdbc;

    UUID admin;

    @BeforeEach
    void createAdmin() {
        admin = jdbc.queryForObject(
                "insert into users (email, status) values (?, 'ACTIVE') returning id",
                UUID.class,
                UUID.randomUUID() + "@example.com");
    }

    // A rejected statement aborts the test transaction, so each test has at most one.

    @ParameterizedTest
    @CsvSource({
        "false, false, false, false", // open
        "true, true, false, false", // accepted
        "false, false, true, true" // revoked
    })
    void acceptsConsistentClosingColumns(
            boolean acceptedAt, boolean acceptedUser, boolean revokedAt, boolean revokedBy) {
        assertThatCode(() -> insert(email(), token(), acceptedAt, acceptedUser, revokedAt, revokedBy))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
        "true, false, false, false", // accepted without the user
        "false, true, false, false", // a user without the acceptance time
        "false, false, true, false", // revoked without the administrator
        "false, false, false, true", // an administrator without the revocation time
        "true, true, true, true" // both accepted and revoked
    })
    void rejectsInconsistentClosingColumns(
            boolean acceptedAt, boolean acceptedUser, boolean revokedAt, boolean revokedBy) {
        assertThatThrownBy(() -> insert(email(), token(), acceptedAt, acceptedUser, revokedAt, revokedBy))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789", // a SHA-256 hash in hex
                "Xf3kq9WvT0bD2mJ7sL1pR8yZ4cN6hA5eG0uI3oK9wQ", // 42 characters
                "Xf3kq9WvT0bD2mJ7sL1pR8yZ4cN6hA5eG0uI3oK9wQeA", // 44 characters
                "Xf3kq9WvT0bD2mJ7sL1pR8yZ4cN6hA5eG0uI3oK9w+/", // base64, not base64url
                "Xf3kq9WvT0bD2mJ7sL1pR8yZ4cN6hA5eG0uI3oK9wQ=", // padding
                ""
            })
    void rejectsTokenThatIsNotBase64Url43(String token) {
        assertThatThrownBy(() -> insertOpen(email(), token)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsEmptyEmail() {
        assertThatThrownBy(() -> insertOpen("", token())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsDuplicateToken() {
        String token = token();
        insert(email(), token, true, true, false, false);

        assertThatThrownBy(() -> insertOpen(email(), token)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void rejectsSecondOpenInvitationForEmailIgnoringCase() {
        insertOpen("Ivan@Example.com", token());

        assertThatThrownBy(() -> insertOpen("ivan@example.COM", token())).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void openInvitationCountsWhenExpired() {
        jdbc.update(
                "insert into invitations (email, token, created_by, expires_at) values (?, ?, ?, ?)",
                "ivan@example.com",
                token(),
                admin,
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)));

        assertThatThrownBy(() -> insertOpen("ivan@example.com", token())).isInstanceOf(DuplicateKeyException.class);
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, true"})
    void acceptsNewOpenInvitationWhenEarlierOneIsClosed(boolean accepted, boolean revoked) {
        insert("ivan@example.com", token(), accepted, accepted, revoked, revoked);

        assertThatCode(() -> insertOpen("ivan@example.com", token())).doesNotThrowAnyException();
    }

    @Test
    void storesKnownRoles() {
        UUID invitation = insertOpen(email(), token());
        jdbc.update(
                "insert into invitation_roles (invitation_id, role) values (?, 'MENTOR'), (?, 'STUDENT')",
                invitation,
                invitation);

        assertThat(jdbc.queryForList(
                        "select role from invitation_roles where invitation_id = ? order by role",
                        String.class,
                        invitation))
                .containsExactly("MENTOR", "STUDENT");
    }

    @Test
    void rejectsRoleMissingFromRolesTable() {
        UUID invitation = insertOpen(email(), token());

        assertThatThrownBy(() -> jdbc.update(
                        "insert into invitation_roles (invitation_id, role) values (?, 'JANITOR')", invitation))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertOpen(String email, String token) {
        return insert(email, token, false, false, false, false);
    }

    private UUID insert(
            String email,
            String token,
            boolean acceptedAt,
            boolean acceptedUser,
            boolean revokedAt,
            boolean revokedBy) {
        return jdbc.queryForObject(
                "insert into invitations (email, token, created_by, expires_at, accepted_at, accepted_user_id,"
                        + " revoked_at, revoked_by) values (?, ?, ?, now() + interval '7 days', ?, ?, ?, ?)"
                        + " returning id",
                UUID.class,
                email,
                token,
                admin,
                timeIf(acceptedAt),
                userIf(acceptedUser),
                timeIf(revokedAt),
                userIf(revokedBy));
    }

    private @Nullable Timestamp timeIf(boolean set) {
        return set ? Timestamp.from(Instant.now()) : null;
    }

    private @Nullable UUID userIf(boolean set) {
        return set ? admin : null;
    }

    private static String email() {
        return UUID.randomUUID() + "@example.com";
    }

    private static String token() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
