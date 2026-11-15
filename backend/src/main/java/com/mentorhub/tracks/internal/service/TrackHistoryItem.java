package com.mentorhub.tracks.internal.service;

import com.mentorhub.tracks.internal.domain.TrackChange;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One entry of a track's history.
 *
 * @param actor {@code null} for a change made by the system
 * @param user for a coordinator change, the user named or removed; {@code null} for other changes or when that user
 *     cannot be resolved
 */
public record TrackHistoryItem(
        UUID id,
        Instant occurredAt,
        @Nullable NamedUser actor,
        TrackChange change,
        @Nullable NamedUser user) {}
