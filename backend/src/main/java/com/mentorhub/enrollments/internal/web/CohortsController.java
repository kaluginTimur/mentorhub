package com.mentorhub.enrollments.internal.web;

import com.mentorhub.api.CohortsApi;
import com.mentorhub.api.model.Cohort;
import com.mentorhub.api.model.CohortCandidatePage;
import com.mentorhub.api.model.CohortCreate;
import com.mentorhub.api.model.CohortEnrollmentsAddition;
import com.mentorhub.api.model.CohortList;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The {@code Cohorts} tag. Parameter names equal the request parameter names, because method validation reports
 * violations by Java parameter name. The changes and the candidates admit both permissions; the service checks the
 * relation that {@code ENROLLMENTS_MANAGE_COORDINATED} needs.
 */
// TODO(cohorts T4): every operation is a stub until the implementation lands.
@RestController
class CohortsController implements CohortsApi {

    @Override
    @RequirePermission(Permission.ENROLLMENTS_READ)
    public CohortList listTrackCohorts(UUID id) {
        throw notImplemented();
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public Cohort createCohort(UUID id, CohortCreate cohortCreate) {
        throw notImplemented();
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public void deleteCohort(UUID id, UUID cohortId) {
        throw notImplemented();
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public Cohort addCohortEnrollments(UUID id, UUID cohortId, CohortEnrollmentsAddition cohortEnrollmentsAddition) {
        throw notImplemented();
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public void removeCohortEnrollment(UUID id, UUID cohortId, UUID enrollmentId) {
        throw notImplemented();
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public CohortCandidatePage listNewCohortCandidates(
            UUID id, @Nullable String q, Boolean withoutCohort, Integer page, Integer size) {
        throw notImplemented();
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public CohortCandidatePage listCohortCandidates(
            UUID id, UUID cohortId, @Nullable String q, Boolean withoutCohort, Integer page, Integer size) {
        throw notImplemented();
    }

    private static ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
