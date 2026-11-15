package com.mentorhub.tracks.internal.persistence;

import com.mentorhub.tracks.internal.domain.TrackStatus;
import java.util.UUID;

public record StoredTrack(UUID id, String code, String name, TrackStatus status) {}
