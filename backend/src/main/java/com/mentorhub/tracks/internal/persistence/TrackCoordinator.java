package com.mentorhub.tracks.internal.persistence;

import java.util.UUID;

/** A row of {@code track_coordinators}. */
public record TrackCoordinator(UUID trackId, UUID userId) {}
