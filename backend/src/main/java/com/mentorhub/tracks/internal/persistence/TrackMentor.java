package com.mentorhub.tracks.internal.persistence;

import java.util.UUID;

/** A row of {@code track_mentors}. */
public record TrackMentor(UUID trackId, UUID userId) {}
