package com.mentorhub.enrollments.internal.web;

import com.mentorhub.api.model.Cohort;
import com.mentorhub.api.model.CohortCandidate;
import com.mentorhub.api.model.CohortCandidatePage;
import com.mentorhub.api.model.CohortReference;
import com.mentorhub.api.model.EnrolledStudent;
import com.mentorhub.api.model.EnrollmentCandidate;
import com.mentorhub.api.model.EnrollmentCandidatePage;
import com.mentorhub.api.model.EnrollmentContact;
import com.mentorhub.api.model.EnrollmentMentor;
import com.mentorhub.api.model.EnrollmentStatus;
import com.mentorhub.api.model.MentorAssignmentCandidate;
import com.mentorhub.api.model.MentorAssignmentCandidatePage;
import com.mentorhub.api.model.MentoredEnrollment;
import com.mentorhub.api.model.MentoredEnrollmentPage;
import com.mentorhub.api.model.MyEnrollment;
import com.mentorhub.api.model.MyEnrollmentList;
import com.mentorhub.api.model.TrackEnrollment;
import com.mentorhub.api.model.TrackEnrollmentPage;
import com.mentorhub.api.model.TrackReference;
import com.mentorhub.enrollments.internal.service.CohortCandidateItem;
import com.mentorhub.enrollments.internal.service.CohortItem;
import com.mentorhub.enrollments.internal.service.EnrollmentPage;
import com.mentorhub.enrollments.internal.service.MentoredEnrollmentItem;
import com.mentorhub.enrollments.internal.service.NamedCohort;
import com.mentorhub.enrollments.internal.service.OwnEnrollment;
import com.mentorhub.enrollments.internal.service.TrackEnrollmentItem;
import com.mentorhub.tracks.TrackSummary;
import com.mentorhub.users.DirectoryUser;
import com.mentorhub.users.Role;
import com.mentorhub.users.UserContact;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps the {@code enrollments} and cohorts service types to the generated contract types; enums map by constant name. */
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
                cohortReference(item.cohort()),
                item.mentors().stream()
                        .map(mentor -> new EnrollmentMentor(
                                mentor.id(),
                                mentor.name(),
                                mentor.email(),
                                mentor.disabled(),
                                mentor.roles().contains(Role.MENTOR),
                                // Placeholder until the mentors of a cohort are implemented (cohort-mentors task 3).
                                false))
                        .toList());
    }

    static MentorAssignmentCandidatePage mentorCandidatePage(EnrollmentPage<DirectoryUser> page) {
        return new MentorAssignmentCandidatePage(
                page.items().stream()
                        // Placeholder counts until the candidates' load is implemented (cohort-mentors task 3).
                        .map(user -> new MentorAssignmentCandidate(user.id(), user.name(), user.email(), 0, 0))
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
                                EnrollmentStatus.valueOf(item.status().name()),
                                // Placeholders until the mentors of a cohort are implemented (cohort-mentors task 3).
                                null,
                                false))
                        .toList(),
                page.page(),
                page.size(),
                page.totalItems());
    }

    static Cohort cohort(CohortItem item) {
        // Placeholder mentors until the mentors of a cohort are implemented (cohort-mentors task 3).
        return new Cohort(item.id(), item.name(), item.startDate(), item.enrollmentCount(), List.of());
    }

    static CohortCandidatePage cohortCandidatePage(EnrollmentPage<CohortCandidateItem> page) {
        return new CohortCandidatePage(
                page.items().stream()
                        .map(item -> new CohortCandidate(
                                item.enrollmentId(), enrolledStudent(item.student()), cohortReference(item.cohort())))
                        .toList(),
                page.page(),
                page.size(),
                page.totalItems());
    }

    private static @Nullable CohortReference cohortReference(@Nullable NamedCohort cohort) {
        return cohort == null ? null : new CohortReference(cohort.id(), cohort.name());
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
