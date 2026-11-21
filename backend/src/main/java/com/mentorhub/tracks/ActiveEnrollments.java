package com.mentorhub.tracks;

import java.util.UUID;

/**
 * Tells archiving whether a track has an active enrollment (TRK-5), and the removal of a mentor of a track whether they
 * lead an active enrollment on it (ENR-13.4). Declared here and implemented by the module that
 * owns enrollments, so that {@code tracks} does not depend on it.
 */
public interface ActiveEnrollments {

    /** Whether the track has an active enrollment, in the caller's transaction. */
    boolean existOnTrack(UUID trackId);

    /** Whether the user is a mentor of an active enrollment on the track, in the caller's transaction. */
    boolean ledOnTrack(UUID trackId, UUID mentorId);
}
