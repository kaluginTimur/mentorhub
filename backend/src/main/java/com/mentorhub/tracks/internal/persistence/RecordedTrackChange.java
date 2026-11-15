package com.mentorhub.tracks.internal.persistence;

import com.mentorhub.tracks.internal.domain.TrackChange;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A stored audit entry about a track; {@code actorUserId} is {@code null} for a change made by the system. */
public record RecordedTrackChange(
        UUID id, Instant occurredAt, @Nullable UUID actorUserId, TrackChange change) {}
