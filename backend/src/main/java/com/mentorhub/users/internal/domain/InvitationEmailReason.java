package com.mentorhub.users.internal.domain;

/**
 * Why an invitation email was requested. Must equal the {@code reason} check of {@code invitation_emails}; also the
 * {@code reason} detail of the {@code INVITATION_EMAIL_REQUESTED} audit entry.
 */
public enum InvitationEmailReason {
    /** With a new invitation. */
    CREATED,
    /** With a new link. */
    LINK_REISSUED,
    /** The current link, sent again. */
    RESENT
}
