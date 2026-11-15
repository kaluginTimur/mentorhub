package com.mentorhub.tracks.internal.web;

import com.mentorhub.api.TracksApi;
import com.mentorhub.api.model.CoordinatorCandidateList;
import com.mentorhub.api.model.Track;
import com.mentorhub.api.model.TrackCreate;
import com.mentorhub.api.model.TrackHistoryPage;
import com.mentorhub.api.model.TrackList;
import com.mentorhub.api.model.TrackStatusFilter;
import com.mentorhub.api.model.TrackUpdate;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The {@code Tracks} tag. A stub answering 501 until the module is implemented. Parameter names equal the request
 * parameter names, because method validation reports violations by Java parameter name.
 */
@RestController
class TracksController implements TracksApi {

    @Override
    @RequirePermission(Permission.TRACKS_READ)
    public TrackList listTracks(TrackStatusFilter status) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public Track createTrack(TrackCreate trackCreate) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(Permission.TRACKS_READ)
    public Track getTrack(UUID id) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public Track updateTrack(UUID id, TrackUpdate trackUpdate) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public Track archiveTrack(UUID id) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public Track restoreTrack(UUID id) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(Permission.TRACKS_READ)
    public TrackHistoryPage getTrackHistory(UUID id, Integer page, Integer size) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public CoordinatorCandidateList listTrackCoordinatorCandidates(UUID id) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public Track addTrackCoordinator(UUID id, UUID userId) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(Permission.TRACKS_MANAGE)
    public Track removeTrackCoordinator(UUID id, UUID userId) {
        throw notImplemented();
    }

    private static ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
