package com.mentorhub.tracks.internal.service;

import com.mentorhub.tracks.TrackDirectory;
import com.mentorhub.tracks.TrackSummary;
import com.mentorhub.tracks.internal.domain.TrackStatus;
import com.mentorhub.tracks.internal.persistence.StoredTrack;
import com.mentorhub.tracks.internal.persistence.TrackCoordinator;
import com.mentorhub.tracks.internal.persistence.TrackRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class TrackDirectoryService implements TrackDirectory {

    private final TrackRepository tracks;

    TrackDirectoryService(TrackRepository tracks) {
        this.tracks = tracks;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<TrackSummary> find(UUID trackId) {
        return tracks.find(trackId).map(TrackDirectoryService::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TrackSummary> findTracks(Collection<UUID> trackIds) {
        return tracks.findAll(trackIds).stream()
                .map(TrackDirectoryService::summary)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<TrackSummary> lockForEnrollment(UUID trackId) {
        return tracks.lock(trackId).map(TrackDirectoryService::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isCoordinator(UUID trackId, UUID userId) {
        return tracks.isCoordinator(trackId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> coordinatorIds(UUID trackId) {
        return tracks.findCoordinators(List.of(trackId)).stream()
                .map(TrackCoordinator::userId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static TrackSummary summary(StoredTrack track) {
        return new TrackSummary(track.id(), track.code(), track.name(), track.status() == TrackStatus.ARCHIVED);
    }
}
