package com.mentorhub.users;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Records in users' histories that they were enrolled on a track, that a mentor was assigned to or removed from an
 * enrollment or a cohort, and that an enrollment changed its cohort. The {@code users} module owns these entries and shows them in the user's history; the module that
 * enrolls calls it.
 */
public interface UserEnrollmentHistory {

    /**
     * Records that the user was enrolled on the track from the start date, in the caller's transaction.
     *
     * @param actorUserId {@code users.id} of who enrolled the user
     * @param track as it is at the moment of the enrollment
     * @param cohort the cohort the user was enrolled into; {@code null} for none
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordEnrolled(
            UUID actorUserId, UUID userId, HistoryTrack track, LocalDate startDate, @Nullable HistoryCohort cohort);

    /**
     * Records that the user's enrollment on the track was put into a cohort, moved to another or taken out, in the
     * caller's transaction.
     *
     * @param actorUserId {@code users.id} of who made the change
     * @param track as it is at the moment of the change
     * @param from the cohort the enrollment left; {@code null} for none
     * @param to the cohort the enrollment joined; {@code null} for none
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordCohortChanged(
            UUID actorUserId,
            UUID userId,
            HistoryTrack track,
            @Nullable HistoryCohort from,
            @Nullable HistoryCohort to);

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

    /**
     * Records in the mentor's history that they were assigned as a mentor of the cohort of the track, in the caller's
     * transaction.
     *
     * @param actorUserId {@code users.id} of who assigned the mentor
     * @param cohort and {@code track} as they are at the moment of the assignment
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordCohortMentorAssigned(UUID actorUserId, UUID mentorId, HistoryCohort cohort, HistoryTrack track);

    /**
     * Records in the mentor's history that they were removed as a mentor of the cohort of the track, or that the cohort
     * was deleted, in the caller's transaction.
     *
     * @param actorUserId {@code users.id} of who removed the mentor or deleted the cohort
     * @param cohort and {@code track} as they are at the moment of the removal
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordCohortMentorRemoved(UUID actorUserId, UUID mentorId, HistoryCohort cohort, HistoryTrack track);
}
