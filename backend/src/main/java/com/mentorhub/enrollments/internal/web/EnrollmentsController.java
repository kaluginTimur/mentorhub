package com.mentorhub.enrollments.internal.web;

import com.mentorhub.api.EnrollmentsApi;
import com.mentorhub.api.model.EnrollmentCandidatePage;
import com.mentorhub.api.model.EnrollmentCreate;
import com.mentorhub.api.model.MyEnrollmentList;
import com.mentorhub.api.model.TrackEnrollment;
import com.mentorhub.api.model.TrackEnrollmentPage;
import com.mentorhub.users.NoPermissionRequired;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The {@code Enrollments} tag. Parameter names equal the request parameter names, because method validation reports
 * violations by Java parameter name.
 */
// A stub answering 501 until enrolling is implemented. Enrolling and the candidates also allow
// ENROLLMENTS_MANAGE_COORDINATED with the relation; until @RequirePermission takes either permission they declare
// ENROLLMENTS_MANAGE.
@RestController
class EnrollmentsController implements EnrollmentsApi {

    @Override
    @RequirePermission(Permission.ENROLLMENTS_READ)
    public TrackEnrollmentPage listTrackEnrollments(UUID id, Integer page, Integer size) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(Permission.ENROLLMENTS_MANAGE)
    public TrackEnrollment enrollStudent(UUID id, EnrollmentCreate enrollmentCreate) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(Permission.ENROLLMENTS_MANAGE)
    public EnrollmentCandidatePage listEnrollmentCandidates(UUID id, @Nullable String q, Integer page, Integer size) {
        throw notImplemented();
    }

    @Override
    @NoPermissionRequired
    public MyEnrollmentList listMyEnrollments() {
        throw notImplemented();
    }

    private static ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
