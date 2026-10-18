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
    USER_BOOTSTRAPPED_ADMIN(UserChange.BootstrappedAdmin.class);

    public static final String SUBJECT_TYPE = "USER";

    private final Class<? extends UserChange> detailsType;

    UserAuditAction(Class<? extends UserChange> detailsType) {
        this.detailsType = detailsType;
    }

    public Class<? extends UserChange> detailsType() {
        return detailsType;
    }
}
