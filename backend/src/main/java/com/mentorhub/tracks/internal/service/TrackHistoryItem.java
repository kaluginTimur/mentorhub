package com.mentorhub.tracks.internal.service;

import com.mentorhub.tracks.internal.domain.TrackChange;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One entry of a track's history.
 *
 * @param actor {@code null} for a change made by the system
 * @param user for a coordinator change, the user named or removed, and for an enrollment or a change of its mentors
 *     the student; {@code null} for other changes or when that user cannot be resolved
 * @param mentor for a change of the mentors of an enrollment, the mentor; {@code null} for other changes or when that
 *     user cannot be resolved
 */
public record TrackHistoryItem(
        UUID id,
        Instant occurredAt,
        @Nullable NamedUser actor,
        TrackChange change,
        @Nullable NamedUser user,
        @Nullable NamedUser mentor) {}
