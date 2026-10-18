package com.mentorhub.users.internal.invitation;

import com.mentorhub.users.internal.domain.InvitationStatus;
import org.jspecify.annotations.Nullable;

/**
 * The invitations list request (AC-4); {@code null} filters do not filter.
 *
 * @param text searched in the email after trimming; blank means no search
 */
public record InvitationQuery(
        @Nullable String text, @Nullable InvitationStatus status, int page, int size) {}
