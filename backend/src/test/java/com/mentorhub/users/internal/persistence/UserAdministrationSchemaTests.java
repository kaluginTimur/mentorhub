package com.mentorhub.users.internal.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.TestcontainersConfiguration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class UserAdministrationSchemaTests {

    @Autowired
    JdbcTemplate jdbc;

    // A rejected statement aborts the test transaction, so each test has at most one.

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "ACTIVE, null, null",
                "DISABLED, GRADUATED, null",
                "DISABLED, SECURITY, Shared the password",
                "DISABLED, OTHER, Moved abroad"
            })
    void acceptsConsistentStatusReasonAndComment(String status, @Nullable String reason, @Nullable String comment) {
        assertThatCode(() -> insertUser(status, reason, comment)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "DISABLED, null, null", // a disabled user needs a reason
                "DISABLED, null, Comment",
                "ACTIVE, GRADUATED, null", // an active user has no reason or comment
                "ACTIVE, null, Comment",
                "DISABLED, OTHER, null", // OTHER needs a comment
                "DISABLED, RETIRED, null" // not a DisableReason
            })
    void rejectsInconsistentStatusReasonAndComment(String status, @Nullable String reason, @Nullable String comment) {
        assertThatThrownBy(() -> insertUser(status, reason, comment))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertUser(String status, @Nullable String reason, @Nullable String comment) {
        return jdbc.queryForObject(
                "insert into users (email, status, disabled_reason, disabled_comment) values (?, ?, ?, ?) returning id",
                UUID.class,
                UUID.randomUUID() + "@example.com",
                status,
                reason,
                comment);
    }
}
