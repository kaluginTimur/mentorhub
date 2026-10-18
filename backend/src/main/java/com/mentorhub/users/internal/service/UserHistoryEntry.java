package com.mentorhub.users.internal.service;

import com.mentorhub.users.internal.domain.UserChange;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One entry of a user's history (AC-19). In a {@link UserChange.RolesChanged} only role names the code knows remain,
 * so each maps to a {@code Role}.
 *
 * @param actor {@code null} for a change made by the system
 */
public record UserHistoryEntry(
        UUID id, Instant occurredAt, @Nullable HistoryActor actor, UserChange change) {}
