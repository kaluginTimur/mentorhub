package com.mentorhub.users;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Records in users' histories that they were enrolled on a track, and that a mentor was assigned to or removed from an
 * enrollment. The {@code users} module owns these entries and shows them in the user's history; the module that
 * enrolls calls it.
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

    /**
     * Records that the mentor was assigned to the student's enrollment on the track: in the student's history and in
     * the mentor's, in the caller's transaction.
     *
     * @param actorUserId {@code users.id} of who assigned the mentor
     * @param track as it is at the moment of the assignment
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordMentorAssigned(UUID actorUserId, UUID studentId, UUID mentorId, HistoryTrack track);

    /**
     * Records that the mentor was removed from the student's enrollment on the track: in the student's history and in
     * the mentor's, in the caller's transaction.
     *
     * @param actorUserId {@code users.id} of who removed the mentor
     * @param track as it is at the moment of the removal
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordMentorRemoved(UUID actorUserId, UUID studentId, UUID mentorId, HistoryTrack track);
}
