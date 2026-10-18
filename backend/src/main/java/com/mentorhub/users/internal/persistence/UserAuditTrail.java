package com.mentorhub.users.internal.persistence;

import com.mentorhub.audit.AuditEntry;
import com.mentorhub.audit.AuditLog;
import com.mentorhub.audit.AuditPage;
import com.mentorhub.audit.RecordedAuditEntry;
import com.mentorhub.users.internal.domain.UserAuditAction;
import com.mentorhub.users.internal.domain.UserChange;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * The audit entries about users (subject type {@link UserAuditAction#SUBJECT_TYPE}). Writing joins the caller's
 * transaction. Reading skips entries whose action this version does not know, with one warning per action, as
 * {@code StoredRoles} does for unknown roles (ADR 0004, ADR 0006).
 */
@Component
public class UserAuditTrail {

    private static final Logger log = LoggerFactory.getLogger(UserAuditTrail.class);
    private static final TypeReference<Map<String, @Nullable Object>> DETAILS = new TypeReference<>() {};
    private static final Map<String, UserAuditAction> ACTIONS_BY_NAME = Arrays.stream(UserAuditAction.values())
            .collect(Collectors.toUnmodifiableMap(UserAuditAction::name, Function.identity()));

    private final AuditLog auditLog;
    private final JsonMapper jsonMapper;
    private final Set<String> reportedUnknownActions = ConcurrentHashMap.newKeySet();

    UserAuditTrail(AuditLog auditLog, JsonMapper jsonMapper) {
        this.auditLog = auditLog;
        this.jsonMapper = jsonMapper;
    }

    /** @param actorUserId {@code null} for a change made by the system */
    public void record(@Nullable UUID actorUserId, UUID userId, UserChange change) {
        auditLog.record(new AuditEntry(
                actorUserId,
                change.action().name(),
                UserAuditAction.SUBJECT_TYPE,
                userId,
                jsonMapper.convertValue(change, DETAILS)));
    }

    public UserChangePage find(UUID userId, int page, int size) {
        AuditPage entries = auditLog.findBySubject(UserAuditAction.SUBJECT_TYPE, userId, page, size);
        List<RecordedUserChange> items = entries.items().stream()
                .map(this::read)
                .filter(Objects::nonNull)
                .toList();
        return new UserChangePage(items, entries.totalItems());
    }

    private @Nullable RecordedUserChange read(RecordedAuditEntry entry) {
        UserAuditAction action = ACTIONS_BY_NAME.get(entry.action());
        if (action == null) {
            if (reportedUnknownActions.add(entry.action())) {
                log.warn("Skipping audit action '{}' unknown to this version of the application", entry.action());
            }
            return null;
        }
        try {
            UserChange change = jsonMapper.convertValue(entry.details(), action.detailsType());
            return new RecordedUserChange(entry.id(), entry.occurredAt(), entry.actorUserId(), change);
        } catch (IllegalArgumentException | JacksonException exception) {
            log.warn("Skipping audit entry {}: its details do not match action {}", entry.id(), action, exception);
            return null;
        }
    }
}
