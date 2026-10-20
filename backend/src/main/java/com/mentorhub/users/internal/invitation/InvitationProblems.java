package com.mentorhub.users.internal.invitation;

import java.net.URI;

/** The problem types of invitations (spec invitations, API). */
public final class InvitationProblems {

    /** 404: the same for a token that is unknown, malformed, replaced, or of an expired, revoked or accepted one. */
    public static final URI INVITATION_INVALID = URI.create("urn:mentorhub:problem:invitation-invalid");
    /** 409 with extension {@code userId}. */
    public static final URI USER_ACTIVE = URI.create("urn:mentorhub:problem:user-active");
    /** 409 with extension {@code invitationId}. */
    public static final URI INVITATION_EXISTS = URI.create("urn:mentorhub:problem:invitation-exists");

    public static final URI INVITATION_CLOSED = URI.create("urn:mentorhub:problem:invitation-closed");
    /** 409: the link of an expired invitation is not returned (spec invitation-links, AC-2). */
    public static final URI INVITATION_EXPIRED = URI.create("urn:mentorhub:problem:invitation-expired");

    public static final URI INVITATION_EMAIL_MISMATCH = URI.create("urn:mentorhub:problem:invitation-email-mismatch");
    public static final URI IDENTITY_LINKED_ELSEWHERE = URI.create("urn:mentorhub:problem:identity-linked-elsewhere");

    private InvitationProblems() {}
}
