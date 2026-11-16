package com.mentorhub.enrollments.internal.service;

import com.mentorhub.enrollments.internal.persistence.EnrollmentRepository;
import com.mentorhub.tracks.ActiveEnrollments;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Answers archiving inside its transaction, after it has locked the track's row (TRK-5.6). */
@Service
class ActiveEnrollmentsService implements ActiveEnrollments {

    private final EnrollmentRepository enrollments;

    ActiveEnrollmentsService(EnrollmentRepository enrollments) {
        this.enrollments = enrollments;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean existOnTrack(UUID trackId) {
        return enrollments.existActiveOnTrack(trackId);
    }
}
