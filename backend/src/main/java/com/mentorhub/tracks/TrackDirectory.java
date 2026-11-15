package com.mentorhub.tracks;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Reads of tracks for other modules, so they never read the {@code tracks} tables. */
public interface TrackDirectory {

    Optional<TrackSummary> find(UUID trackId);

    /** The tracks with the given ids; ids no track has are left out. */
    List<TrackSummary> findTracks(Collection<UUID> trackIds);

    /**
     * Locks the track's row until the end of the caller's transaction, with the lock every change of the track and its
     * archiving take, so that enrolling on the track and archiving it run one after another (TRK-5.6). Take it before
     * any other row lock.
     *
     * @return empty for an id no track has
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    Optional<TrackSummary> lockForEnrollment(UUID trackId);

    /** Whether the user is a coordinator of the track now. */
    boolean isCoordinator(UUID trackId, UUID userId);

    /** The ids of the coordinators of the track, in any status and with any roles. */
    Set<UUID> coordinatorIds(UUID trackId);
}
