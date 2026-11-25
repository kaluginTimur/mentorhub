package com.mentorhub.enrollments.internal.service;

import com.mentorhub.enrollments.internal.domain.EnrollmentStatus;
import com.mentorhub.tracks.TrackSummary;
import com.mentorhub.users.DirectoryUser;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An enrollment the caller leads, with the student and the cohort as they are now.
 *
 * @param cohort {@code null} for none
 * @param throughCohort the caller leads it only as a mentor of its cohort, not of the enrollment itself
 */
public record MentoredEnrollmentItem(
        UUID id,
        DirectoryUser student,
        TrackSummary track,
        LocalDate startDate,
        EnrollmentStatus status,
        @Nullable NamedCohort cohort,
        boolean throughCohort) {}
