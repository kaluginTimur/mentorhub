package com.mentorhub.audit.internal.service;

import com.mentorhub.audit.AuditEntry;
import com.mentorhub.audit.AuditLog;
import com.mentorhub.audit.AuditPage;
import com.mentorhub.audit.internal.persistence.AuditLogRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class AuditLogService implements AuditLog {

    private final AuditLogRepository repository;

    AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditEntry entry) {
        repository.insert(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public AuditPage findBySubject(String subjectType, UUID subjectId, int page, int size) {
        if (page < 0 || size < 1) {
            throw new IllegalArgumentException("page must be at least 0 and size at least 1");
        }
        return new AuditPage(
                repository.findBySubject(subjectType, subjectId, size, (long) page * size),
                repository.countBySubject(subjectType, subjectId));
    }
}
