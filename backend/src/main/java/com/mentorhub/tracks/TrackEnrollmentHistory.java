package com.mentorhub.tracks;

import com.mentorhub.users.HistoryCohort;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Records in a track's history that a student was enrolled on it, that a mentor was assigned to or removed from an
 * enrollment on it, and the changes of its cohorts and of the cohorts of its enrollments. The {@code tracks} module owns these entries and shows them in the track's history; the module that
 * enrolls calls it.
 */
public interface TrackEnrollmentHistory {

    /**
     * Records that the student was enrolled on the track from the start date, in the caller's transaction.
     *
     * @param actorUserId {@code users.id} of who enrolled the student
     * @param studentId {@code users.id} of the student
     * @param cohort the cohort of the track the student was enrolled into; {@code null} for none
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordStudentEnrolled(
            UUID actorUserId, UUID trackId, UUID studentId, LocalDate startDate, @Nullable HistoryCohort cohort);

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

    /**
     * Records that the cohort was created on the track, in the caller's transaction.
     *
     * @param actorUserId {@code users.id} of who created the cohort
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordCohortCreated(UUID actorUserId, UUID trackId, HistoryCohort cohort, LocalDate startDate);

    /**
     * Records that the cohort was deleted from the track, in the caller's transaction.
     *
     * @param actorUserId {@code users.id} of who deleted the cohort
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordCohortDeleted(UUID actorUserId, UUID trackId, HistoryCohort cohort, LocalDate startDate);

    /**
     * Records that the student's enrollment on the track was put into a cohort, moved to another or taken out, in the
     * caller's transaction.
     *
     * @param actorUserId {@code users.id} of who made the change
     * @param from the cohort the enrollment left; {@code null} for none
     * @param to the cohort the enrollment joined; {@code null} for none
     * @throws org.springframework.transaction.IllegalTransactionStateException without an active transaction
     */
    void recordEnrollmentCohortChanged(
            UUID actorUserId, UUID trackId, UUID studentId, @Nullable HistoryCohort from, @Nullable HistoryCohort to);
}
