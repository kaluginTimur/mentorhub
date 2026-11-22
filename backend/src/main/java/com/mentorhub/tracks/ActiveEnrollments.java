package com.mentorhub.tracks;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Tells archiving whether a track has an active enrollment (TRK-5), the removal of a mentor of a track whether they
 * lead an active enrollment on it (ENR-13.4), and a track how many students each of its mentors leads (ENR-15).
 * Declared here and implemented by the module that owns enrollments, so that {@code tracks} does not depend on it.
 */
public interface ActiveEnrollments {

    /** Whether the track has an active enrollment, in the caller's transaction. */
    boolean existOnTrack(UUID trackId);

    /** Whether the user is a mentor of an active enrollment on the track, in the caller's transaction. */
    boolean ledOnTrack(UUID trackId, UUID mentorId);

    /**
     * The number of active enrollments each user is a mentor of, per track (ENR-15), in the caller's transaction; a
     * user who leads none on a track is left out of its map, and a track without any is left out.
     */
    Map<UUID, Map<UUID, Integer>> countLed(Collection<UUID> trackIds);
}
