package com.mentorhub.users.internal.domain;

/**
 * The audit actions the {@code users} module writes about subject type {@link #SUBJECT_TYPE} (spec invitations,
 * Audit; spec invitation-emails, Audit). They have no read view yet.
 */
public enum InvitationAuditAction {
    /** Details: {@code email}, {@code roles}. */
    INVITATION_CREATED,
    /** Details: {@code expiresAt}, the new expiry. */
    INVITATION_LINK_REISSUED,
    /** No details. */
    INVITATION_REVOKED,
    /** Details: {@code reason}, an {@link InvitationEmailReason} (spec invitation-emails, Audit). */
    INVITATION_EMAIL_REQUESTED;

    public static final String SUBJECT_TYPE = "INVITATION";
}
