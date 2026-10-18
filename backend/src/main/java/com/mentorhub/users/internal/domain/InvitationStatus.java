package com.mentorhub.users.internal.domain;

/**
 * The derived status of an invitation; never stored. Must equal the {@code InvitationStatus} enum of the API contract.
 * Derived in this order: {@link #ACCEPTED} when accepted, {@link #REVOKED} when revoked, {@link #EXPIRED} when the
 * expiry is not after the database's {@code now()}, else {@link #PENDING}.
 */
public enum InvitationStatus {
    PENDING,
    EXPIRED,
    ACCEPTED,
    REVOKED;

    /** Pending and expired invitations are open: they can be revoked or get a new link. */
    public boolean isOpen() {
        return this == PENDING || this == EXPIRED;
    }
}
