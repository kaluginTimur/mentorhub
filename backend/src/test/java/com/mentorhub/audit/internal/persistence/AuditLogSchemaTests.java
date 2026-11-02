package com.mentorhub.audit.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.TestcontainersConfiguration;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class AuditLogSchemaTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void storesAnEntryWithDefaults() {
        UUID id = insertEntry(null, "{\"reason\": \"GRADUATED\"}");

        assertThat(jdbc.queryForObject(
                        "select occurred_at is not null and actor_user_id is null and details ->> 'reason' = 'GRADUATED'"
                                + " from audit_log where id = ?",
                        Boolean.class,
                        id))
                .isTrue();
    }

    @Test
    void actorMustBeAUser() {
        assertThatThrownBy(() -> insertEntry(UUID.randomUUID(), "{}"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest(name = "AUD-3.1 An entry cannot be updated or deleted: {0}")
    @ValueSource(
            strings = {"update audit_log set action = 'CHANGED' where id = ?", "delete from audit_log where id = ?"})
    @DisplayName("AUD-3.1 An entry cannot be updated or deleted")
    void rejectsUpdateAndDelete(String statement) {
        UUID id = insertEntry(null, "{}");

        assertThatThrownBy(() -> jdbc.update(statement, id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private UUID insertEntry(@Nullable UUID actorUserId, String details) {
        return jdbc.queryForObject(
                "insert into audit_log (actor_user_id, action, subject_type, subject_id, details)"
                        + " values (?, 'USER_DISABLED', 'USER', ?, ?::jsonb) returning id",
                UUID.class,
                actorUserId,
                UUID.randomUUID(),
                details);
    }
}
