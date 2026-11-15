package com.mentorhub.tracks;

import java.util.UUID;

/**
 * Tells archiving whether a track has an active enrollment (TRK-5). Declared here and implemented by the module that
 * owns enrollments, so that {@code tracks} does not depend on it.
 */
public interface ActiveEnrollments {

    /** Whether the track has an active enrollment, in the caller's transaction. */
    boolean existOnTrack(UUID trackId);
}
