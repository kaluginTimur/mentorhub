package com.mentorhub.enrollments.internal.service;

import com.mentorhub.enrollments.internal.domain.EnrollmentStatus;
import com.mentorhub.users.DirectoryUser;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An enrollment as the enrollments of its track show it, with the student, the cohort and the mentors as they are now.
 *
 * @param cohort {@code null} for none
 * @param mentors every mentor, in any status and with any roles, sorted by name, then email, then id
 */
public record TrackEnrollmentItem(
        UUID id,
        DirectoryUser student,
        LocalDate startDate,
        EnrollmentStatus status,
        @Nullable NamedCohort cohort,
        List<DirectoryUser> mentors) {

    public TrackEnrollmentItem {
        mentors = List.copyOf(mentors);
    }
}
