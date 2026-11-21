package com.mentorhub.tracks.internal.web;

import com.mentorhub.api.TracksApi;
import com.mentorhub.api.model.CoordinatorCandidateList;
import com.mentorhub.api.model.MentorCandidatePage;
import com.mentorhub.api.model.Track;
import com.mentorhub.api.model.TrackCreate;
import com.mentorhub.api.model.TrackHistoryPage;
import com.mentorhub.api.model.TrackList;
import com.mentorhub.api.model.TrackStatusFilter;
import com.mentorhub.api.model.TrackUpdate;
import com.mentorhub.tracks.internal.service.TrackService;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The {@code Tracks} tag. Parameter names equal the request parameter names, because method validation reports
 * violations by Java parameter name.
 */
@RestController
class TracksController implements TracksApi {

    private final TrackService tracks;

    TracksController(TrackService tracks) {
        this.tracks = tracks;
    }

    @Override
    @RequirePermission(Permission.TRACKS_READ)
    public TrackList listTracks(TrackStatusFilter status) {
        return TracksMapper.trackList(tracks.list(TracksMapper.status(status)));
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public Track createTrack(TrackCreate trackCreate) {
        return TracksMapper.track(tracks.create(trackCreate.getCode(), trackCreate.getName()));
    }

    @Override
    @RequirePermission(Permission.TRACKS_READ)
    public Track getTrack(UUID id) {
        return TracksMapper.track(tracks.get(id));
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public Track updateTrack(UUID id, TrackUpdate trackUpdate) {
        return TracksMapper.track(tracks.update(id, trackUpdate.getCode(), trackUpdate.getName()));
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public Track archiveTrack(UUID id) {
        return TracksMapper.track(tracks.archive(id));
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public Track restoreTrack(UUID id) {
        return TracksMapper.track(tracks.restore(id));
    }

    @Override
    @RequirePermission(Permission.TRACKS_READ)
    public TrackHistoryPage getTrackHistory(UUID id, Integer page, Integer size) {
        return TracksMapper.historyPage(tracks.history(id, page, size));
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public CoordinatorCandidateList listTrackCoordinatorCandidates(UUID id) {
        return TracksMapper.candidates(tracks.candidates(id));
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public Track addTrackCoordinator(UUID id, UUID userId) {
        return TracksMapper.track(tracks.addCoordinator(id, userId));
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public Track removeTrackCoordinator(UUID id, UUID userId) {
        return TracksMapper.track(tracks.removeCoordinator(id, userId));
    }

    // Stubs until T3 of track-mentors implements the mentors of a track.
    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public MentorCandidatePage listTrackMentorCandidates(UUID id, @Nullable String q, Integer page, Integer size) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public Track addTrackMentor(UUID id, UUID userId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public Track removeTrackMentor(UUID id, UUID userId) {
        throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
