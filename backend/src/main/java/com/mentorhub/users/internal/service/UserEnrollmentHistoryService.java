package com.mentorhub.users.internal.service;

import com.mentorhub.users.HistoryCohort;
import com.mentorhub.users.HistoryTrack;
import com.mentorhub.users.UserEnrollmentHistory;
import com.mentorhub.users.internal.domain.UserChange;
import com.mentorhub.users.internal.persistence.UserAuditTrail;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserEnrollmentHistoryService implements UserEnrollmentHistory {

    private final UserAuditTrail auditTrail;

    UserEnrollmentHistoryService(UserAuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordEnrolled(
            UUID actorUserId, UUID userId, HistoryTrack track, LocalDate startDate, @Nullable HistoryCohort cohort) {
        auditTrail.record(actorUserId, userId, new UserChange.Enrolled(track, startDate, cohortRef(cohort)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCohortChanged(
            UUID actorUserId,
            UUID userId,
            HistoryTrack track,
            @Nullable HistoryCohort from,
            @Nullable HistoryCohort to) {
        auditTrail.record(actorUserId, userId, new UserChange.CohortChanged(track, cohortRef(from), cohortRef(to)));
    }

    private static UserChange.@Nullable CohortRef cohortRef(@Nullable HistoryCohort cohort) {
        return cohort == null ? null : new UserChange.CohortRef(cohort.id(), cohort.name());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMentorAssigned(UUID actorUserId, UUID studentId, UUID mentorId, HistoryTrack track) {
        auditTrail.record(actorUserId, studentId, new UserChange.MentorAssigned(mentorId, track));
        auditTrail.record(actorUserId, mentorId, new UserChange.StudentAssigned(studentId, track));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordMentorRemoved(UUID actorUserId, UUID studentId, UUID mentorId, HistoryTrack track) {
        auditTrail.record(actorUserId, studentId, new UserChange.MentorRemoved(mentorId, track));
        auditTrail.record(actorUserId, mentorId, new UserChange.StudentRemoved(studentId, track));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCohortMentorAssigned(UUID actorUserId, UUID mentorId, HistoryCohort cohort, HistoryTrack track) {
        auditTrail.record(
                actorUserId,
                mentorId,
                new UserChange.CohortMentorAssigned(new UserChange.CohortRef(cohort.id(), cohort.name()), track));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCohortMentorRemoved(UUID actorUserId, UUID mentorId, HistoryCohort cohort, HistoryTrack track) {
        auditTrail.record(
                actorUserId,
                mentorId,
                new UserChange.CohortMentorRemoved(new UserChange.CohortRef(cohort.id(), cohort.name()), track));
    }
}
