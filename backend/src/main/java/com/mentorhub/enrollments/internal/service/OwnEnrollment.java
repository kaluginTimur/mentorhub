package com.mentorhub.enrollments.internal.service;

import com.mentorhub.enrollments.internal.domain.EnrollmentStatus;
import com.mentorhub.tracks.TrackSummary;
import com.mentorhub.users.UserContact;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One of the caller's own enrollments.
 *
 * @param coordinators the coordinators of the track who are active and hold the coordinator role, sorted by name
 */
public record OwnEnrollment(
        UUID id, TrackSummary track, LocalDate startDate, EnrollmentStatus status, List<UserContact> coordinators) {

    public OwnEnrollment {
        coordinators = List.copyOf(coordinators);
    }
}
