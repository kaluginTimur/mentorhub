package com.mentorhub.tracks.internal.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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

    /** @param userId {@code users.id} of the named mentor */
    record MentorAdded(UUID userId) implements TrackChange {
        public MentorAdded {
            Objects.requireNonNull(userId, "userId");
        }

        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_MENTOR_ADDED;
        }
    }

    /** @param userId {@code users.id} of the removed mentor */
    record MentorRemoved(UUID userId) implements TrackChange {
        public MentorRemoved {
            Objects.requireNonNull(userId, "userId");
        }

        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_MENTOR_REMOVED;
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

    /**
     * @param userId {@code users.id} of the student of the enrollment
     * @param mentorId {@code users.id} of the mentor
     */
    record EnrollmentMentorAssigned(UUID userId, UUID mentorId) implements TrackChange {
        public EnrollmentMentorAssigned {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(mentorId, "mentorId");
        }

        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_ENROLLMENT_MENTOR_ASSIGNED;
        }
    }

    /**
     * @param userId {@code users.id} of the student of the enrollment
     * @param mentorId {@code users.id} of the mentor
     */
    record EnrollmentMentorRemoved(UUID userId, UUID mentorId) implements TrackChange {
        public EnrollmentMentorRemoved {
            Objects.requireNonNull(userId, "userId");
            Objects.requireNonNull(mentorId, "mentorId");
        }

        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_ENROLLMENT_MENTOR_REMOVED;
        }
    }

    /** A cohort of the track as an entry names it: its id, and its name at the moment of the change. */
    record CohortRef(UUID id, String name) {
        public CohortRef {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(name, "name");
        }
    }

    record CohortCreated(CohortRef cohort, LocalDate startDate) implements TrackChange {
        public CohortCreated {
            Objects.requireNonNull(cohort, "cohort");
            Objects.requireNonNull(startDate, "startDate");
        }

        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_COHORT_CREATED;
        }
    }

    record CohortDeleted(CohortRef cohort, LocalDate startDate) implements TrackChange {
        public CohortDeleted {
            Objects.requireNonNull(cohort, "cohort");
            Objects.requireNonNull(startDate, "startDate");
        }

        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_COHORT_DELETED;
        }
    }

    /**
     * @param userId {@code users.id} of the student of the enrollment
     * @param from the cohort the enrollment left; {@code null} for none
     * @param to the cohort the enrollment joined; {@code null} for none
     */
    record EnrollmentCohortChanged(
            UUID userId, @Nullable CohortRef from, @Nullable CohortRef to) implements TrackChange {
        public EnrollmentCohortChanged {
            Objects.requireNonNull(userId, "userId");
        }

        @Override
        public TrackAuditAction action() {
            return TrackAuditAction.TRACK_ENROLLMENT_COHORT_CHANGED;
        }
    }
}
