package com.mentorhub.users;

import java.util.UUID;

/**
 * Records in a user's history that they were named or removed as a mentor of a track. The {@code users} module owns
 * these entries and shows them in the user's history; the module that changes the assignment calls it.
 */
public interface TrackMentorHistory {

    /**
     * Records that the user was named a mentor of the track, in the caller's transaction.
     *
     * @param actorUserId {@code users.id} of who made the change
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordAdded(UUID actorUserId, UUID userId, HistoryTrack track);

    /**
     * Records that the user was removed as a mentor of the track, in the caller's transaction.
     *
     * @param actorUserId {@code users.id} of who made the change
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordRemoved(UUID actorUserId, UUID userId, HistoryTrack track);
}
