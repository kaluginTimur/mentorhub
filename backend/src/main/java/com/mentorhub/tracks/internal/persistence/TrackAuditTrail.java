package com.mentorhub.tracks.internal.persistence;

import com.mentorhub.audit.AuditEntry;
import com.mentorhub.audit.AuditLog;
import com.mentorhub.audit.AuditPage;
import com.mentorhub.audit.RecordedAuditEntry;
import com.mentorhub.tracks.internal.domain.TrackAuditAction;
import com.mentorhub.tracks.internal.domain.TrackChange;
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
 * The audit entries about tracks (subject type {@link TrackAuditAction#SUBJECT_TYPE}). Writing joins the caller's
 * transaction. Reading skips entries whose action this version does not know, with one warning per action (decision
 * 0005).
 */
@Component
public class TrackAuditTrail {

    private static final Logger log = LoggerFactory.getLogger(TrackAuditTrail.class);
    private static final TypeReference<Map<String, @Nullable Object>> DETAILS = new TypeReference<>() {};
    private static final Map<String, TrackAuditAction> ACTIONS_BY_NAME = Arrays.stream(TrackAuditAction.values())
            .collect(Collectors.toUnmodifiableMap(TrackAuditAction::name, Function.identity()));

    private final AuditLog auditLog;
    private final JsonMapper jsonMapper;
    private final Set<String> reportedUnknownActions = ConcurrentHashMap.newKeySet();

    TrackAuditTrail(AuditLog auditLog, JsonMapper jsonMapper) {
        this.auditLog = auditLog;
        this.jsonMapper = jsonMapper;
    }

    public void record(UUID actorUserId, UUID trackId, TrackChange change) {
        auditLog.record(new AuditEntry(
                actorUserId, change.action().name(), TrackAuditAction.SUBJECT_TYPE, trackId, details(change)));
    }

    public TrackChangePage find(UUID trackId, int page, int size) {
        AuditPage entries = auditLog.findBySubject(TrackAuditAction.SUBJECT_TYPE, trackId, page, size);
        List<RecordedTrackChange> items = entries.items().stream()
                .map(this::read)
                .filter(Objects::nonNull)
                .toList();
        return new TrackChangePage(items, entries.totalItems());
    }

    // Changes without details are not serialized: Jackson refuses a record without properties.
    private Map<String, @Nullable Object> details(TrackChange change) {
        return switch (change) {
            case TrackChange.Archived _, TrackChange.Restored _ -> Map.of();
            default -> jsonMapper.convertValue(change, DETAILS);
        };
    }

    private @Nullable RecordedTrackChange read(RecordedAuditEntry entry) {
        TrackAuditAction action = ACTIONS_BY_NAME.get(entry.action());
        if (action == null) {
            if (reportedUnknownActions.add(entry.action())) {
                log.warn("Skipping audit action '{}' unknown to this version of the application", entry.action());
            }
            return null;
        }
        try {
            TrackChange change = switch (action) {
                case TRACK_ARCHIVED -> new TrackChange.Archived();
                case TRACK_RESTORED -> new TrackChange.Restored();
                default -> jsonMapper.convertValue(entry.details(), action.detailsType());
            };
            return new RecordedTrackChange(entry.id(), entry.occurredAt(), entry.actorUserId(), change);
        } catch (IllegalArgumentException | JacksonException exception) {
            log.warn("Skipping audit entry {}: its details do not match action {}", entry.id(), action, exception);
            return null;
        }
    }
}
