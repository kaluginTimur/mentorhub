package com.mentorhub.enrollments.internal.web;

import com.mentorhub.api.model.EnrolledStudent;
import com.mentorhub.api.model.EnrollmentCandidate;
import com.mentorhub.api.model.EnrollmentCandidatePage;
import com.mentorhub.api.model.EnrollmentContact;
import com.mentorhub.api.model.EnrollmentMentor;
import com.mentorhub.api.model.EnrollmentStatus;
import com.mentorhub.api.model.MentorCandidate;
import com.mentorhub.api.model.MentorCandidatePage;
import com.mentorhub.api.model.MentoredEnrollment;
import com.mentorhub.api.model.MentoredEnrollmentPage;
import com.mentorhub.api.model.MyEnrollment;
import com.mentorhub.api.model.MyEnrollmentList;
import com.mentorhub.api.model.TrackEnrollment;
import com.mentorhub.api.model.TrackEnrollmentPage;
import com.mentorhub.api.model.TrackReference;
import com.mentorhub.enrollments.internal.service.EnrollmentPage;
import com.mentorhub.enrollments.internal.service.MentoredEnrollmentItem;
import com.mentorhub.enrollments.internal.service.OwnEnrollment;
import com.mentorhub.enrollments.internal.service.TrackEnrollmentItem;
import com.mentorhub.tracks.TrackSummary;
import com.mentorhub.users.DirectoryUser;
import com.mentorhub.users.Role;
import com.mentorhub.users.UserContact;
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
        return new TrackEnrollment(
                item.id(),
                enrolledStudent(item.student()),
                item.startDate(),
                EnrollmentStatus.valueOf(item.status().name()),
                // TODO(cohorts T4): the enrollment's cohort.
                null,
                item.mentors().stream()
                        .map(mentor -> new EnrollmentMentor(
                                mentor.id(),
                                mentor.name(),
                                mentor.email(),
                                mentor.disabled(),
                                mentor.roles().contains(Role.MENTOR)))
                        .toList());
    }

    static MentorCandidatePage mentorCandidatePage(EnrollmentPage<DirectoryUser> page) {
        return new MentorCandidatePage(
                page.items().stream()
                        .map(user -> new MentorCandidate(user.id(), user.name(), user.email()))
                        .toList(),
                page.page(),
                page.size(),
                page.totalItems());
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
                        trackReference(enrollment.track()),
                        enrollment.startDate(),
                        EnrollmentStatus.valueOf(enrollment.status().name()),
                        contacts(enrollment.coordinators()),
                        contacts(enrollment.mentors())))
                .toList());
    }

    static MentoredEnrollmentPage mentoredEnrollmentPage(EnrollmentPage<MentoredEnrollmentItem> page) {
        return new MentoredEnrollmentPage(
                page.items().stream()
                        .map(item -> new MentoredEnrollment(
                                item.id(),
                                enrolledStudent(item.student()),
                                trackReference(item.track()),
                                item.startDate(),
                                EnrollmentStatus.valueOf(item.status().name())))
                        .toList(),
                page.page(),
                page.size(),
                page.totalItems());
    }

    private static EnrolledStudent enrolledStudent(DirectoryUser student) {
        return new EnrolledStudent(
                student.id(),
                student.name(),
                student.email(),
                student.disabled(),
                student.roles().contains(Role.STUDENT));
    }

    private static TrackReference trackReference(TrackSummary track) {
        return new TrackReference(track.id(), track.code(), track.name());
    }

    private static List<EnrollmentContact> contacts(List<UserContact> contacts) {
        return contacts.stream()
                .map(contact -> new EnrollmentContact(contact.name(), contact.email(), contact.phone()))
                .toList();
    }
}
