package com.mentorhub.tracks.internal.service;

import com.mentorhub.tracks.TrackEnrollmentHistory;
import com.mentorhub.tracks.internal.domain.TrackChange;
import com.mentorhub.tracks.internal.persistence.TrackAuditTrail;
import com.mentorhub.users.HistoryCohort;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class TrackEnrollmentHistoryService implements TrackEnrollmentHistory {

    private final TrackAuditTrail auditTrail;

    TrackEnrollmentHistoryService(TrackAuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordStudentEnrolled(
            UUID actorUserId, UUID trackId, UUID studentId, LocalDate startDate, @Nullable HistoryCohort cohort) {
        auditTrail.record(
                actorUserId, trackId, new TrackChange.StudentEnrolled(studentId, startDate, cohortRef(cohort)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMentorAssigned(UUID actorUserId, UUID trackId, UUID studentId, UUID mentorId) {
        auditTrail.record(actorUserId, trackId, new TrackChange.EnrollmentMentorAssigned(studentId, mentorId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMentorRemoved(UUID actorUserId, UUID trackId, UUID studentId, UUID mentorId) {
        auditTrail.record(actorUserId, trackId, new TrackChange.EnrollmentMentorRemoved(studentId, mentorId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCohortCreated(UUID actorUserId, UUID trackId, HistoryCohort cohort, LocalDate startDate) {
        auditTrail.record(actorUserId, trackId, new TrackChange.CohortCreated(ref(cohort), startDate));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCohortDeleted(UUID actorUserId, UUID trackId, HistoryCohort cohort, LocalDate startDate) {
        auditTrail.record(actorUserId, trackId, new TrackChange.CohortDeleted(ref(cohort), startDate));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEnrollmentCohortChanged(
            UUID actorUserId, UUID trackId, UUID studentId, @Nullable HistoryCohort from, @Nullable HistoryCohort to) {
        auditTrail.record(
                actorUserId,
                trackId,
                new TrackChange.EnrollmentCohortChanged(studentId, cohortRef(from), cohortRef(to)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCohortMentorAssigned(UUID actorUserId, UUID trackId, HistoryCohort cohort, UUID mentorId) {
        auditTrail.record(actorUserId, trackId, new TrackChange.CohortMentorAssigned(ref(cohort), mentorId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCohortMentorRemoved(UUID actorUserId, UUID trackId, HistoryCohort cohort, UUID mentorId) {
        auditTrail.record(actorUserId, trackId, new TrackChange.CohortMentorRemoved(ref(cohort), mentorId));
    }

    private static TrackChange.CohortRef ref(HistoryCohort cohort) {
        return new TrackChange.CohortRef(cohort.id(), cohort.name());
    }

    private static TrackChange.@Nullable CohortRef cohortRef(@Nullable HistoryCohort cohort) {
        return cohort == null ? null : ref(cohort);
    }
}
