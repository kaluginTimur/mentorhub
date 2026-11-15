package com.mentorhub.tracks.internal.service;

import com.mentorhub.tracks.TrackEnrollmentHistory;
import com.mentorhub.tracks.internal.domain.TrackChange;
import com.mentorhub.tracks.internal.persistence.TrackAuditTrail;
import java.time.LocalDate;
import java.util.UUID;
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
    public void recordStudentEnrolled(UUID actorUserId, UUID trackId, UUID studentId, LocalDate startDate) {
        auditTrail.record(actorUserId, trackId, new TrackChange.StudentEnrolled(studentId, startDate));
    }
}
