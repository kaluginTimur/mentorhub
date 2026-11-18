package com.mentorhub.enrollments.internal.web;

import com.mentorhub.api.model.EnrolledStudent;
import com.mentorhub.api.model.EnrollmentCandidate;
import com.mentorhub.api.model.EnrollmentCandidatePage;
import com.mentorhub.api.model.EnrollmentContact;
import com.mentorhub.api.model.EnrollmentStatus;
import com.mentorhub.api.model.MyEnrollment;
import com.mentorhub.api.model.MyEnrollmentList;
import com.mentorhub.api.model.TrackEnrollment;
import com.mentorhub.api.model.TrackEnrollmentPage;
import com.mentorhub.api.model.TrackReference;
import com.mentorhub.enrollments.internal.service.EnrollmentPage;
import com.mentorhub.enrollments.internal.service.OwnEnrollment;
import com.mentorhub.enrollments.internal.service.TrackEnrollmentItem;
import com.mentorhub.users.DirectoryUser;
import com.mentorhub.users.Role;
import java.util.List;

/** Maps the {@code enrollments} service types to the generated contract types; enums map by constant name. */
final class EnrollmentsMapper {

    private EnrollmentsMapper() {}

    static TrackEnrollmentPage trackEnrollmentPage(EnrollmentPage<TrackEnrollmentItem> page) {
        return new TrackEnrollmentPage(
                page.items().stream().map(EnrollmentsMapper::trackEnrollment).toList(),
                page.page(),
                page.size(),
                page.totalItems());
    }

    static TrackEnrollment trackEnrollment(TrackEnrollmentItem item) {
        DirectoryUser student = item.student();
        return new TrackEnrollment(
                item.id(),
                new EnrolledStudent(
                        student.id(),
                        student.name(),
                        student.email(),
                        student.disabled(),
                        student.roles().contains(Role.STUDENT)),
                item.startDate(),
                EnrollmentStatus.valueOf(item.status().name()),
                List.of());
    }

    static EnrollmentCandidatePage candidatePage(EnrollmentPage<DirectoryUser> page) {
        return new EnrollmentCandidatePage(
                page.items().stream()
                        .map(user -> new EnrollmentCandidate(user.id(), user.name(), user.email()))
                        .toList(),
                page.page(),
                page.size(),
                page.totalItems());
    }

    static MyEnrollmentList myEnrollments(List<OwnEnrollment> enrollments) {
        return new MyEnrollmentList(enrollments.stream()
                .map(enrollment -> new MyEnrollment(
                        enrollment.id(),
                        new TrackReference(
                                enrollment.track().id(),
                                enrollment.track().code(),
                                enrollment.track().name()),
                        enrollment.startDate(),
                        EnrollmentStatus.valueOf(enrollment.status().name()),
                        enrollment.coordinators().stream()
                                .map(contact -> new EnrollmentContact(contact.name(), contact.email(), contact.phone()))
                                .toList(),
                        List.of()))
                .toList());
    }
}
