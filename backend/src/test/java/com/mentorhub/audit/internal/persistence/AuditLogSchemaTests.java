package com.mentorhub.audit.internal.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class AuditLogSchemaTests {

    @Autowired
    JdbcTemplate jdbc;

    @ParameterizedTest(name = "AUD-3.1 An entry cannot be updated or deleted: {0}")
    @ValueSource(
            strings = {"update audit_log set action = 'CHANGED' where id = ?", "delete from audit_log where id = ?"})
    @DisplayName("AUD-3.1 An entry cannot be updated or deleted")
    void rejectsUpdateAndDelete(String statement) {
        UUID id = insertEntry();

        assertThatThrownBy(() -> jdbc.update(statement, id))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
    }

    private UUID insertEntry() {
        return jdbc.queryForObject(
                "insert into audit_log (action, subject_type, subject_id, details)"
                        + " values ('USER_DISABLED', 'USER', ?, '{}'::jsonb) returning id",
                UUID.class,
                UUID.randomUUID());
    }
}
