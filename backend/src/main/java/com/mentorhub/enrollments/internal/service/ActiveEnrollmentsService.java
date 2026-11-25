package com.mentorhub.enrollments.internal.service;

import com.mentorhub.enrollments.internal.persistence.CohortRepository;
import com.mentorhub.enrollments.internal.persistence.EnrollmentRepository;
import com.mentorhub.tracks.ActiveEnrollments;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Answers archiving and the removal of a mentor of a track inside their transaction, after they have locked the track's
 * row (TRK-5.6, ENR-13.7, ENR-13.9), and counts the students of the mentors of tracks for a read of them (ENR-15).
 */
@Service
class ActiveEnrollmentsService implements ActiveEnrollments {

    private final EnrollmentRepository enrollments;
    private final CohortRepository cohorts;

    ActiveEnrollmentsService(EnrollmentRepository enrollments, CohortRepository cohorts) {
        this.enrollments = enrollments;
        this.cohorts = cohorts;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean existOnTrack(UUID trackId) {
        return enrollments.existActiveOnTrack(trackId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean ledOnTrack(UUID trackId, UUID mentorId) {
        return enrollments.existActiveLedOnTrack(trackId, mentorId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean leadsCohortOnTrack(UUID trackId, UUID mentorId) {
        return cohorts.isMentorOnTrack(trackId, mentorId);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<UUID, Map<UUID, Integer>> countLed(Collection<UUID> trackIds) {
        return enrollments.countActiveLed(trackIds);
    }
}
