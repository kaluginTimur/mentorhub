package com.mentorhub.users.internal.domain;

/**
 * Why a user was disabled. Must equal the {@code DisableReason} enum of the API contract and the
 * {@code users_disabled_reason_value_ck} constraint.
 */
public enum DisableReason {
    GRADUATED,
    EXPELLED,
    LEFT_PROGRAM,
    STAFF_OFFBOARDED,
    SECURITY,
    /** Requires a comment. */
    OTHER
}
