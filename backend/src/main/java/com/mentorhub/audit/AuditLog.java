package com.mentorhub.audit;

import java.util.UUID;

/**
 * Writes and reads the audit log (ADR 0006). There is no global read: each module serves the entries about its
 * subjects under the subjects' own access rules.
 */
public interface AuditLog {

    /**
     * Stores the entry in the caller's transaction, so the change and its entry commit or roll back together.
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void record(AuditEntry entry);

    /** The subject's entries, newest first. {@code page} is zero-based. */
    AuditPage findBySubject(String subjectType, UUID subjectId, int page, int size);
}
