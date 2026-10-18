package com.mentorhub.audit;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An entry to record.
 *
 * @param actorUserId {@code users.id} of who made the change, {@code null} for a change made by the system
 * @param action owned by the writing module, for example {@code USER_DISABLED}
 * @param subjectType owned by the writing module, for example {@code USER}
 * @param details JSON-serializable; never content with its own visibility rules, such as a note's body
 */
public record AuditEntry(
        @Nullable UUID actorUserId,
        String action,
        String subjectType,
        UUID subjectId,
        Map<String, ? extends @Nullable Object> details) {

    public AuditEntry {
        if (action.isBlank() || subjectType.isBlank()) {
            throw new IllegalArgumentException("An audit entry needs an action and a subject type");
        }
        details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }
}
