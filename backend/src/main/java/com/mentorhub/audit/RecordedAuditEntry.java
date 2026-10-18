package com.mentorhub.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A stored entry; {@code details} is the stored JSON object as maps, lists and scalars. */
public record RecordedAuditEntry(
        UUID id,
        Instant occurredAt,
        @Nullable UUID actorUserId,
        String action,
        String subjectType,
        UUID subjectId,
        Map<String, @Nullable Object> details) {}
