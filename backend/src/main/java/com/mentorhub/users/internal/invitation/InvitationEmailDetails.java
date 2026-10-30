package com.mentorhub.users.internal.invitation;

import com.mentorhub.users.internal.domain.InvitationEmailStatus;
import com.mentorhub.users.internal.service.HistoryActor;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * The latest email of an invitation as administrators see it (spec invitation-emails, AC-16); never the failure reason.
 *
 * @param finishedAt present once the status is not {@link InvitationEmailStatus#QUEUED}
 */
public record InvitationEmailDetails(
        InvitationEmailStatus status,
        Instant requestedAt,
        HistoryActor requestedBy,
        @Nullable Instant finishedAt) {}
