package com.mentorhub.users.internal.invitation;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.InvitationStatus;
import com.mentorhub.users.internal.service.HistoryActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An invitation as administrators see it (AC-4). The acceptance is present only when accepted, the revocation only
 * when revoked. Roles are the known ones, in enum order. Never contains the token.
 */
public record InvitationDetails(
        UUID id,
        String email,
        List<Role> roles,
        InvitationStatus status,
        Instant createdAt,
        Instant expiresAt,
        HistoryActor createdBy,
        @Nullable Instant acceptedAt,
        @Nullable HistoryActor acceptedUser,
        @Nullable Instant revokedAt,
        @Nullable HistoryActor revokedBy) {

    public InvitationDetails {
        roles = List.copyOf(roles);
    }
}
