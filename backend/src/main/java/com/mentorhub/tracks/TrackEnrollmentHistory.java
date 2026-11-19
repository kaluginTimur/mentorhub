package com.mentorhub.tracks;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Records in a track's history that a student was enrolled on it, and that a mentor was assigned to or removed from an
 * enrollment on it. The {@code tracks} module owns these entries and shows them in the track's history; the module that
 * enrolls calls it.
 */
public interface TrackEnrollmentHistory {

    /**
     * Records that the student was enrolled on the track from the start date, in the caller's transaction.
     *
     * @param actorUserId {@code users.id} of who enrolled the student
     * @param studentId {@code users.id} of the student
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordStudentEnrolled(UUID actorUserId, UUID trackId, UUID studentId, LocalDate startDate);

    /**
     * Records that the mentor was assigned to the student's enrollment on the track, in the caller's transaction.
     *
     * @param actorUserId {@code users.id} of who assigned the mentor
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordMentorAssigned(UUID actorUserId, UUID trackId, UUID studentId, UUID mentorId);

    /**
     * Records that the mentor was removed from the student's enrollment on the track, in the caller's transaction.
     *
     * @param actorUserId {@code users.id} of who removed the mentor
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordMentorRemoved(UUID actorUserId, UUID trackId, UUID studentId, UUID mentorId);
}
