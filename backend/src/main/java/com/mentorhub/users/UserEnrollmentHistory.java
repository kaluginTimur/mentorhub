package com.mentorhub.users;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Records in a user's history that they were enrolled on a track. The {@code users} module owns these entries and shows
 * them in the user's history; the module that enrolls calls it.
 */
public interface UserEnrollmentHistory {

    /**
     * Records that the user was enrolled on the track from the start date, in the caller's transaction.
     *
     * @param actorUserId {@code users.id} of who enrolled the user
     * @param track as it is at the moment of the enrollment
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordEnrolled(UUID actorUserId, UUID userId, HistoryTrack track, LocalDate startDate);
}
