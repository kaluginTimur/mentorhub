package com.mentorhub.enrollments.internal.persistence;

import com.mentorhub.enrollments.internal.domain.EnrollmentStatus;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** @param cohortId the cohort of the enrollment's track it is in; {@code null} for none */
public record StoredEnrollment(
        UUID id,
        UUID studentId,
        UUID trackId,
        LocalDate startDate,
        EnrollmentStatus status,
        @Nullable UUID cohortId) {}
