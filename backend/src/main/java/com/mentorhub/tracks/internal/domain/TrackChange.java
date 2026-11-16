package com.mentorhub.tracks.internal.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** An audited change to a track; its components are the details of the audit entry (AUD-7). */
public sealed interface TrackChange {

    TrackAuditAction action();

    record Created(String code, String name) implements TrackChange {
        public Created {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(name, "name");
        }

        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_CREATED;
        }
    }

    /** One entry per changed field. */
    record Updated(List<FieldChange> changes) implements TrackChange {
        public Updated {
            changes = List.copyOf(changes);
        }

        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_UPDATED;
        }
    }

    record FieldChange(TrackField field, String oldValue, String newValue) {
        public FieldChange {
            Objects.requireNonNull(field, "field");
            Objects.requireNonNull(oldValue, "oldValue");
            Objects.requireNonNull(newValue, "newValue");
        }
    }

    /** No details. */
    record Archived() implements TrackChange {
        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_ARCHIVED;
        }
    }

    /** No details. */
    record Restored() implements TrackChange {
        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_RESTORED;
        }
    }

    /** @param userId {@code users.id} of the named user */
    record CoordinatorAdded(UUID userId) implements TrackChange {
        public CoordinatorAdded {
            Objects.requireNonNull(userId, "userId");
        }

        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_COORDINATOR_ADDED;
        }
    }

    /** @param userId {@code users.id} of the removed user */
    record CoordinatorRemoved(UUID userId) implements TrackChange {
        public CoordinatorRemoved {
            Objects.requireNonNull(userId, "userId");
        }

        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_COORDINATOR_REMOVED;
        }
    }

    /** @param userId {@code users.id} of the enrolled student */
    record StudentEnrolled(UUID userId, LocalDate startDate) implements TrackChange {
        public StudentEnrolled {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(startDate, "startDate");
        }

        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_STUDENT_ENROLLED;
        }
    }
}
