package com.mentorhub.users.internal.service;

import com.mentorhub.users.internal.domain.UserChange;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One entry of a user's history. In a {@link UserChange.RolesChanged} or {@link UserChange.InvitationAccepted}
 * only role names the code knows remain, so each maps to a {@code Role}.
 *
 * @param actor {@code null} for a change made by the system
 * @param invitedBy for a {@link UserChange.InvitationAccepted}, the administrator who created the invitation;
 *     {@code null} for other changes or when that user cannot be resolved
 * @param person for a mentor's assignment or removal, the mentor in the student's history and the student in the
 *     mentor's; {@code null} for other changes or when that user cannot be resolved
 */
public record UserHistoryEntry(
        UUID id,
        Instant occurredAt,
        @Nullable HistoryActor actor,
        UserChange change,
        @Nullable HistoryActor invitedBy,
        @Nullable HistoryActor person) {

    /** An entry of a change that names no other user than an inviter. */
    public UserHistoryEntry(
            UUID id,
            Instant occurredAt,
            @Nullable HistoryActor actor,
            UserChange change,
            @Nullable HistoryActor invitedBy) {
        this(id, occurredAt, actor, change, invitedBy, null);
    }

    /** An entry of a change that names no inviter. */
    public UserHistoryEntry(UUID id, Instant occurredAt, @Nullable HistoryActor actor, UserChange change) {
        this(id, occurredAt, actor, change, null, null);
    }
}
