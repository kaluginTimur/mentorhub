package com.mentorhub.enrollments.internal.service;

import com.mentorhub.enrollments.internal.domain.EnrollmentStatus;
import com.mentorhub.users.DirectoryUser;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An enrollment as the enrollments of its track show it, with the student and the mentors as they are now.
 *
 * @param mentors every mentor, in any status and with any roles, sorted by name, then email, then id
 */
public record TrackEnrollmentItem(
        UUID id, DirectoryUser student, LocalDate startDate, EnrollmentStatus status, List<DirectoryUser> mentors) {

    public TrackEnrollmentItem {
        mentors = List.copyOf(mentors);
    }
}
