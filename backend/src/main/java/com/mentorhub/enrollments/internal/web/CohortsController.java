package com.mentorhub.enrollments.internal.web;

import com.mentorhub.api.CohortsApi;
import com.mentorhub.api.model.Cohort;
import com.mentorhub.api.model.CohortCandidatePage;
import com.mentorhub.api.model.CohortCreate;
import com.mentorhub.api.model.CohortEnrollmentsAddition;
import com.mentorhub.api.model.CohortList;
import com.mentorhub.enrollments.internal.service.CohortService;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code Cohorts} tag. Parameter names equal the request parameter names, because method validation reports
 * violations by Java parameter name. The changes and the candidates admit both permissions; the service checks the
 * relation that {@code ENROLLMENTS_MANAGE_COORDINATED} needs.
 */
@RestController
class CohortsController implements CohortsApi {

    private final CohortService cohorts;

    CohortsController(CohortService cohorts) {
        this.cohorts = cohorts;
    }

    @Override
    @RequirePermission(Permission.ENROLLMENTS_READ)
    public CohortList listTrackCohorts(UUID id) {
        return new CohortList(
                cohorts.list(id).stream().map(EnrollmentsMapper::cohort).toList());
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public Cohort createCohort(UUID id, CohortCreate cohortCreate) {
        List<UUID> enrollmentIds = Objects.requireNonNullElse(cohortCreate.getEnrollmentIds(), List.of());
        return EnrollmentsMapper.cohort(
                cohorts.create(id, cohortCreate.getName(), cohortCreate.getStartDate(), enrollmentIds));
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public void deleteCohort(UUID id, UUID cohortId) {
        cohorts.delete(id, cohortId);
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public Cohort addCohortEnrollments(UUID id, UUID cohortId, CohortEnrollmentsAddition cohortEnrollmentsAddition) {
        return EnrollmentsMapper.cohort(cohorts.add(id, cohortId, cohortEnrollmentsAddition.getEnrollmentIds()));
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public void removeCohortEnrollment(UUID id, UUID cohortId, UUID enrollmentId) {
        cohorts.remove(id, cohortId, enrollmentId);
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public CohortCandidatePage listNewCohortCandidates(
            UUID id, @Nullable String q, Boolean withoutCohort, Integer page, Integer size) {
        return EnrollmentsMapper.cohortCandidatePage(cohorts.candidates(id, null, q, withoutCohort, page, size));
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public CohortCandidatePage listCohortCandidates(
            UUID id, UUID cohortId, @Nullable String q, Boolean withoutCohort, Integer page, Integer size) {
        return EnrollmentsMapper.cohortCandidatePage(cohorts.candidates(id, cohortId, q, withoutCohort, page, size));
    }
}
