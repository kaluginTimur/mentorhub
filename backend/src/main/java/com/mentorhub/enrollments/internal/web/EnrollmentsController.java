package com.mentorhub.enrollments.internal.web;

import com.mentorhub.api.EnrollmentsApi;
import com.mentorhub.api.model.EnrollmentCandidatePage;
import com.mentorhub.api.model.EnrollmentCreate;
import com.mentorhub.api.model.MentorCandidatePage;
import com.mentorhub.api.model.MentoredEnrollmentPage;
import com.mentorhub.api.model.MyEnrollmentList;
import com.mentorhub.api.model.TrackEnrollment;
import com.mentorhub.api.model.TrackEnrollmentPage;
import com.mentorhub.enrollments.internal.service.EnrollmentService;
import com.mentorhub.users.NoPermissionRequired;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code Enrollments} tag. Parameter names equal the request parameter names, because method validation reports
 * violations by Java parameter name. Enrolling and the candidates admit both permissions; the service checks the
 * relation that {@code ENROLLMENTS_MANAGE_COORDINATED} needs; so do the mentors of an enrollment.
 */
@RestController
class EnrollmentsController implements EnrollmentsApi {

    private final EnrollmentService enrollments;

    EnrollmentsController(EnrollmentService enrollments) {
        this.enrollments = enrollments;
    }

    @Override
    @RequirePermission(Permission.ENROLLMENTS_READ)
    public TrackEnrollmentPage listTrackEnrollments(
            UUID id, @Nullable UUID mentor, @Nullable UUID cohortId, Integer page, Integer size) {
        return EnrollmentsMapper.trackEnrollmentPage(enrollments.listOfTrack(id, mentor, cohortId, page, size));
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public TrackEnrollment enrollStudent(UUID id, EnrollmentCreate enrollmentCreate) {
        return EnrollmentsMapper.trackEnrollment(enrollments.enroll(
                id, enrollmentCreate.getStudentId(), enrollmentCreate.getStartDate(), enrollmentCreate.getCohortId()));
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public EnrollmentCandidatePage listEnrollmentCandidates(UUID id, @Nullable String q, Integer page, Integer size) {
        return EnrollmentsMapper.candidatePage(enrollments.candidates(id, q, page, size));
    }

    @Override
    @NoPermissionRequired
    public MyEnrollmentList listMyEnrollments() {
        return EnrollmentsMapper.myEnrollments(enrollments.mine());
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public MentorCandidatePage listMentorCandidates(UUID id, @Nullable String q, Integer page, Integer size) {
        return EnrollmentsMapper.mentorCandidatePage(enrollments.mentorCandidates(id, q, page, size));
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public TrackEnrollment assignEnrollmentMentor(UUID id, UUID userId) {
        return EnrollmentsMapper.trackEnrollment(enrollments.assignMentor(id, userId));
    }

    @Override
    @RequirePermission({Permission.ENROLLMENTS_MANAGE, Permission.ENROLLMENTS_MANAGE_COORDINATED})
    public TrackEnrollment removeEnrollmentMentor(UUID id, UUID userId) {
        return EnrollmentsMapper.trackEnrollment(enrollments.removeMentor(id, userId));
    }

    @Override
    @RequirePermission(Permission.ENROLLMENTS_READ_MENTORED)
    public MentoredEnrollmentPage listMyStudents(Integer page, Integer size) {
        return EnrollmentsMapper.mentoredEnrollmentPage(enrollments.mentored(page, size));
    }
}
