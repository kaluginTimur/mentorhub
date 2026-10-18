package com.mentorhub.users.internal.service;

import com.mentorhub.users.internal.domain.UserChange;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One entry of a user's history (AC-19). In a {@link UserChange.RolesChanged} or {@link UserChange.InvitationAccepted}
 * only role names the code knows remain, so each maps to a {@code Role}.
 *
 * @param actor {@code null} for a change made by the system
 * @param invitedBy for a {@link UserChange.InvitationAccepted}, the administrator who created the invitation;
 *     {@code null} for other changes or when that user cannot be resolved
 */
public record UserHistoryEntry(
        UUID id,
        Instant occurredAt,
        @Nullable HistoryActor actor,
        UserChange change,
        @Nullable HistoryActor invitedBy) {

    /** An entry of a change that names no inviter. */
    public UserHistoryEntry(UUID id, Instant occurredAt, @Nullable HistoryActor actor, UserChange change) {
        this(id, occurredAt, actor, change, null);
    }
}
