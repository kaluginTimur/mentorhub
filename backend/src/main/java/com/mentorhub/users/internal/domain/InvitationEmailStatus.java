package com.mentorhub.users.internal.domain;

/**
 * The state of an invitation email request (spec invitation-emails). Must equal the {@code InvitationEmailStatus} enum
 * of the API contract and the {@code status} check of {@code invitation_emails}. A request starts {@link #QUEUED} and
 * ends in exactly one of the others.
 */
public enum InvitationEmailStatus {
    QUEUED,
    SENT,
    FAILED,
    SKIPPED
}
