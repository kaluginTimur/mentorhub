package com.mentorhub.users.internal.domain;

/**
 * The audit actions the {@code users} module writes about subject type {@link #SUBJECT_TYPE}. Must equal the
 * {@code HistoryAction} enum of the API contract.
 */
public enum UserAuditAction {
    USER_DISABLED(UserChange.Disabled.class),
    USER_ENABLED(UserChange.Enabled.class),
    USER_ROLES_CHANGED(UserChange.RolesChanged.class),
    USER_PROFILE_UPDATED(UserChange.ProfileUpdated.class),
    USER_BOOTSTRAPPED_ADMIN(UserChange.BootstrappedAdmin.class),
    USER_INVITATION_ACCEPTED(UserChange.InvitationAccepted.class),
    USER_TRACK_COORDINATOR_ADDED(UserChange.TrackCoordinatorAdded.class),
    USER_TRACK_COORDINATOR_REMOVED(UserChange.TrackCoordinatorRemoved.class),
    USER_TRACK_MENTOR_ADDED(UserChange.TrackMentorAdded.class),
    USER_TRACK_MENTOR_REMOVED(UserChange.TrackMentorRemoved.class),
    USER_ENROLLED(UserChange.Enrolled.class),
    USER_MENTOR_ASSIGNED(UserChange.MentorAssigned.class),
    USER_MENTOR_REMOVED(UserChange.MentorRemoved.class),
    USER_STUDENT_ASSIGNED(UserChange.StudentAssigned.class),
    USER_STUDENT_REMOVED(UserChange.StudentRemoved.class),
    USER_COHORT_CHANGED(UserChange.CohortChanged.class),
    USER_COHORT_MENTOR_ASSIGNED(UserChange.CohortMentorAssigned.class),
    USER_COHORT_MENTOR_REMOVED(UserChange.CohortMentorRemoved.class);

    public static final String SUBJECT_TYPE = "USER";

    private final Class<? extends UserChange> detailsType;

    UserAuditAction(Class<? extends UserChange> detailsType) {
        this.detailsType = detailsType;
    }

    public Class<? extends UserChange> detailsType() {
        return detailsType;
    }
}
