package com.mentorhub.users.internal.persistence;

import com.mentorhub.users.internal.domain.InvitationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A stored invitation with the names of its stored roles, including names the code may not know. The status is
 * derived by the query that read it, with the database's {@code now()}.
 */
public record StoredInvitation(
        UUID id,
        String email,
        InvitationStatus status,
        Instant createdAt,
        UUID createdBy,
        Instant expiresAt,
        @Nullable Instant acceptedAt,
        @Nullable UUID acceptedUserId,
        @Nullable Instant revokedAt,
        @Nullable UUID revokedBy,
        List<String> roleNames) {

    public StoredInvitation {
        roleNames = List.copyOf(roleNames);
    }

    StoredInvitation withRoleNames(List<String> names) {
        return new StoredInvitation(
                id,
                email,
                status,
                createdAt,
                createdBy,
                expiresAt,
                acceptedAt,
                acceptedUserId,
                revokedAt,
                revokedBy,
                names);
    }
}
