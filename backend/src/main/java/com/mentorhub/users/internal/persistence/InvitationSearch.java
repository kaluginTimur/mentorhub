package com.mentorhub.users.internal.persistence;

import com.mentorhub.users.internal.domain.InvitationStatus;
import org.jspecify.annotations.Nullable;

/**
 * Filters of the invitations list, combined with AND; {@code null} means no filter.
 *
 * @param text a trimmed, non-blank substring of the email, matched literally and ignoring case
 */
public record InvitationSearch(
        @Nullable String text, @Nullable InvitationStatus status) {}
