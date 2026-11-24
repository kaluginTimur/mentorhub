package com.mentorhub.enrollments.internal.service;

import com.mentorhub.users.DirectoryUser;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An active enrollment that may be added to a cohort.
 *
 * @param cohort the cohort the enrollment is in now; {@code null} for none
 */
public record CohortCandidateItem(
        UUID enrollmentId, DirectoryUser student, @Nullable NamedCohort cohort) {}
