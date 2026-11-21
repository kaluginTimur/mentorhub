package com.mentorhub.users.internal.service;

import com.mentorhub.users.HistoryTrack;
import com.mentorhub.users.TrackMentorHistory;
import com.mentorhub.users.internal.domain.UserChange;
import com.mentorhub.users.internal.persistence.UserAuditTrail;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class TrackMentorHistoryService implements TrackMentorHistory {

    private final UserAuditTrail auditTrail;

    TrackMentorHistoryService(UserAuditTrail auditTrail) {
        this.auditTrail = auditTrail;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAdded(UUID actorUserId, UUID userId, HistoryTrack track) {
        auditTrail.record(actorUserId, userId, new UserChange.TrackMentorAdded(track));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordRemoved(UUID actorUserId, UUID userId, HistoryTrack track) {
        auditTrail.record(actorUserId, userId, new UserChange.TrackMentorRemoved(track));
    }
}
