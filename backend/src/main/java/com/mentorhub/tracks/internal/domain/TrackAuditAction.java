package com.mentorhub.tracks.internal.domain;

/**
 * The audit actions the {@code tracks} module writes about subject type {@link #SUBJECT_TYPE}. Must equal the
 * {@code TrackHistoryAction} enum of the API contract.
 */
public enum TrackAuditAction {
    TRACK_CREATED(TrackChange.Created.class),
    TRACK_UPDATED(TrackChange.Updated.class),
    TRACK_ARCHIVED(TrackChange.Archived.class),
    TRACK_RESTORED(TrackChange.Restored.class),
    TRACK_COORDINATOR_ADDED(TrackChange.CoordinatorAdded.class),
    TRACK_COORDINATOR_REMOVED(TrackChange.CoordinatorRemoved.class),
    TRACK_MENTOR_ADDED(TrackChange.MentorAdded.class),
    TRACK_MENTOR_REMOVED(TrackChange.MentorRemoved.class),
    TRACK_STUDENT_ENROLLED(TrackChange.StudentEnrolled.class),
    TRACK_ENROLLMENT_MENTOR_ASSIGNED(TrackChange.EnrollmentMentorAssigned.class),
    TRACK_ENROLLMENT_MENTOR_REMOVED(TrackChange.EnrollmentMentorRemoved.class),
    TRACK_COHORT_CREATED(TrackChange.CohortCreated.class),
    TRACK_COHORT_DELETED(TrackChange.CohortDeleted.class),
    TRACK_ENROLLMENT_COHORT_CHANGED(TrackChange.EnrollmentCohortChanged.class);

    public static final String SUBJECT_TYPE = "TRACK";

    private final Class<? extends TrackChange> detailsType;

    TrackAuditAction(Class<? extends TrackChange> detailsType) {
        this.detailsType = detailsType;
    }

    public Class<? extends TrackChange> detailsType() {
        return detailsType;
    }
}
