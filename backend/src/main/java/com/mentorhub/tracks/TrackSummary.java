package com.mentorhub.tracks;

import java.util.UUID;

/** A track with its code and name as they are now. */
public record TrackSummary(UUID id, String code, String name, boolean archived) {}
