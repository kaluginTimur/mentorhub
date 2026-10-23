package com.mentorhub.users.internal.persistence;

import com.mentorhub.users.internal.domain.InvitationEmailReason;
import com.mentorhub.users.internal.domain.InvitationEmailStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An invitation email request. The failure reason is left out: no read path shows it (spec invitation-emails,
 * Visibility).
 *
 * @param finishedAt present exactly when the status is not {@link InvitationEmailStatus#QUEUED}
 */
public record StoredInvitationEmail(
        UUID id,
        UUID invitationId,
        InvitationEmailReason reason,
        InvitationEmailStatus status,
        Instant requestedAt,
        UUID requestedBy,
        @Nullable Instant finishedAt) {}
