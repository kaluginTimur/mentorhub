package com.mentorhub.audit.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.audit.AuditEntry;
import com.mentorhub.audit.AuditLog;
import com.mentorhub.audit.AuditPage;
import com.mentorhub.audit.RecordedAuditEntry;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuditLogServiceTests {

    @Autowired
    AuditLog auditLog;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcClient jdbc;

    UUID subject;

    @BeforeEach
    void newSubject() {
        subject = UUID.randomUUID();
    }

    @Test
    void recordFailsOutsideATransaction() {
        assertThatThrownBy(() -> auditLog.record(entry("TEST_CHANGED", Map.of())))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(count()).isZero();
    }

    // AC-21
    @Test
    void entryRollsBackWithItsTransaction() {
        transactions.executeWithoutResult(status -> {
            auditLog.record(entry("TEST_CHANGED", Map.of()));
            status.setRollbackOnly();
        });

        assertThat(count()).isZero();
    }

    @Test
    void storesAndReadsTheDetailsAsJson() {
        Map<String, @Nullable Object> details = new HashMap<>();
        details.put("reason", "GRADUATED");
        details.put("comment", null);
        details.put("roles", List.of("MENTOR", "STUDENT"));
        details.put("flags", Map.of("enabled", true));
        transactions.executeWithoutResult(status -> auditLog.record(entry("TEST_CHANGED", details)));

        AuditPage page = auditLog.findBySubject("TEST", subject, 0, 10);

        assertThat(page.totalItems()).isEqualTo(1);
        RecordedAuditEntry stored = page.items().getFirst();
        assertThat(stored.action()).isEqualTo("TEST_CHANGED");
        assertThat(stored.subjectType()).isEqualTo("TEST");
        assertThat(stored.subjectId()).isEqualTo(subject);
        assertThat(stored.actorUserId()).isNull();
        assertThat(stored.occurredAt()).isNotNull();
        assertThat(stored.details()).isEqualTo(details);
    }

    @Test
    void readsOnlyTheSubjectsEntriesNewestFirstInPages() {
        for (String action : List.of("FIRST", "SECOND", "THIRD")) {
            transactions.executeWithoutResult(status -> auditLog.record(entry(action, Map.of())));
        }
        transactions.executeWithoutResult(
                status -> auditLog.record(new AuditEntry(null, "OTHER_SUBJECT", "TEST", UUID.randomUUID(), Map.of())));

        AuditPage first = auditLog.findBySubject("TEST", subject, 0, 2);
        AuditPage second = auditLog.findBySubject("TEST", subject, 1, 2);

        assertThat(first.items()).extracting(RecordedAuditEntry::action).containsExactly("THIRD", "SECOND");
        assertThat(second.items()).extracting(RecordedAuditEntry::action).containsExactly("FIRST");
        assertThat(first.totalItems()).isEqualTo(3);
        assertThat(auditLog.findBySubject("OTHER", subject, 0, 2).items()).isEmpty();
    }

    private AuditEntry entry(String action, Map<String, ? extends @Nullable Object> details) {
        return new AuditEntry(null, action, "TEST", subject, details);
    }

    private int count() {
        return jdbc.sql("select count(*) from audit_log where subject_id = :subject")
                .param("subject", subject)
                .query(Integer.class)
                .single();
    }
}
