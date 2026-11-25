package com.mentorhub.enrollments.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.mentorhub.enrollments.internal.TestCohorts;
import com.mentorhub.enrollments.internal.TestEnrollments;
import com.mentorhub.support.EndpointTest;
import com.mentorhub.tracks.internal.TestTracks;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestUsers;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * What the mentors of a cohort lead, as the enrollments, the student, the mentor and the track see it, through the real
 * chain.
 */
@EndpointTest
class CohortMentorsLeadingTests {

    static final URI MENTOR_LEADS_COHORTS = URI.create("urn:mentorhub:problem:mentor-leads-cohorts");
    static final URI MENTOR_LEADS_ENROLLMENTS = URI.create("urn:mentorhub:problem:mentor-leads-enrollments");
    static final LocalDate START = LocalDate.of(2026, 9, 1);

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    TestUsers users;
    TestTracks tracks;
    TestEnrollments enrollments;
    TestCohorts cohorts;
    UUID admin;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        tracks = new TestTracks(jdbc);
        enrollments = new TestEnrollments(jdbc);
        cohorts = new TestCohorts(jdbc);
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "admin");
    }

    @Test
    @DisplayName("ENR-4.4 The mentors of a cohort are listed with each of its enrollments")
    void theMentorsOfACohortAreListedWithEachOfItsEnrollments() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID first = person("alice@x.test", "Alice", "First", UserStatus.ACTIVE, Role.MENTOR);
        UUID second = person("boris@x.test", "Boris", "Second", UserStatus.ACTIVE, Role.COORDINATOR);
        UUID own = person("cora@x.test", "Cora", "Own", UserStatus.ACTIVE, Role.MENTOR);
        for (UUID mentor : List.of(first, second, own)) {
            tracks.addMentor(track, mentor);
        }
        cohorts.addMentor(cohort, first, admin);
        cohorts.addMentor(cohort, second, admin);
        UUID e1 = enrollments.enroll(student("s1@x.test", "One"), track, admin, START);
        UUID e2 = enrollments.enroll(student("s2@x.test", "Two"), track, admin, START);
        UUID e3 = enrollments.enroll(student("s3@x.test", "Three"), track, admin, START);
        cohorts.put(e1, cohort);
        cohorts.put(e2, cohort);
        enrollments.addMentor(e1, first, admin);
        enrollments.addMentor(e2, own, admin);

        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollments", "", "admin");

        assertThat(list).hasStatusOk();
        assertThat(read(list, mentors(e1) + "[*].id")).containsExactly(first.toString(), second.toString());
        assertThat(read(list, mentors(e1) + "[?(@.throughCohort == true)].id")).containsExactly(second.toString());
        assertThat(read(list, mentors(e2) + "[*].id"))
                .containsExactly(first.toString(), second.toString(), own.toString());
        assertThat(read(list, mentors(e2) + "[?(@.throughCohort == true)].id"))
                .containsExactly(first.toString(), second.toString());
        assertThat(read(list, mentors(e1) + "[?(@.mentorRole == false)].id")).containsExactly(second.toString());
        assertThat(read(list, mentors(e2) + "[?(@.mentorRole == false)].id")).containsExactly(second.toString());
        assertThat(read(list, mentors(e3) + "[*].id")).isEmpty();
    }

    @Test
    @DisplayName("ENR-5.4 A student sees the mentors of their cohort")
    void aStudentSeesTheMentorsOfTheirCohort() {
        UUID sam = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        users.link(sam, "sam");
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID otherCohort = cohorts.create(track, "Autumn", START);
        UUID first = person("alice@x.test", "Alice", "First", UserStatus.ACTIVE, Role.MENTOR);
        UUID second = person("boris@x.test", "Boris", "Second", UserStatus.ACTIVE, Role.MENTOR);
        UUID disabled = person("cora@x.test", "Cora", "Disabled", UserStatus.DISABLED, Role.MENTOR);
        UUID elsewhere = person("dan@x.test", "Dan", "Elsewhere", UserStatus.ACTIVE, Role.MENTOR);
        UUID enrollment = enrollments.enroll(sam, track, admin, START);
        cohorts.put(enrollment, cohort);
        cohorts.addMentor(cohort, first, admin);
        cohorts.addMentor(cohort, second, admin);
        cohorts.addMentor(cohort, disabled, admin);
        cohorts.addMentor(otherCohort, elsewhere, admin);
        enrollments.addMentor(enrollment, first, admin);

        MvcTestResult result = send(HttpMethod.GET, "/api/v1/me/enrollments", "", "sam");

        assertThat(result).hasStatusOk();
        assertThat(read(result, "$.items[0].mentors[*].email")).containsExactly("alice@x.test", "boris@x.test");
    }

    @Test
    @DisplayName("ENR-9.5 A mentor of the cohort is assigned to its enrollment directly")
    void aMentorOfTheCohortIsAssignedToItsEnrollmentDirectly() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID student = student("s1@x.test", "One");
        UUID first = person("alice@x.test", "Alice", "First", UserStatus.ACTIVE, Role.MENTOR);
        UUID second = person("boris@x.test", "Boris", "Second", UserStatus.ACTIVE, Role.MENTOR);
        for (UUID mentor : List.of(first, second)) {
            tracks.addMentor(track, mentor);
            cohorts.addMentor(cohort, mentor, admin);
        }
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        cohorts.put(enrollment, cohort);

        MvcTestResult assigned = send(HttpMethod.PUT, mentorPath(enrollment, first), "", "admin");
        MvcTestResult removed = send(HttpMethod.DELETE, mentorPath(enrollment, second), "", "admin");

        assertThat(assigned).hasStatusOk();
        assertThat(removed).hasStatusOk();
        MvcTestResult onTrack = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/history", "", "admin");
        assertThat(read(onTrack, "$.items[*].action")).containsExactly("TRACK_ENROLLMENT_MENTOR_ASSIGNED");
        MvcTestResult onStudent = send(HttpMethod.GET, "/api/v1/users/" + student + "/history", "", "admin");
        assertThat(read(onStudent, "$.items[*].action")).containsExactly("USER_MENTOR_ASSIGNED");
        MvcTestResult onFirst = send(HttpMethod.GET, "/api/v1/users/" + first + "/history", "", "admin");
        assertThat(read(onFirst, "$.items[*].action")).containsExactly("USER_STUDENT_ASSIGNED");
        MvcTestResult onSecond = send(HttpMethod.GET, "/api/v1/users/" + second + "/history", "", "admin");
        assertThat(read(onSecond, "$.items[*].action")).isEmpty();
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollments", "", "admin");
        assertThat(read(list, mentors(enrollment) + "[*].id")).containsExactly(first.toString(), second.toString());
        assertThat(read(list, mentors(enrollment) + "[?(@.throughCohort == true)].id"))
                .containsExactly(second.toString());
    }

    @Test
    @DisplayName("ENR-10.2 The candidates have the students and the cohorts they lead on the track")
    void theCandidatesHaveTheStudentsAndTheCohortsTheyLeadOnTheTrack() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID elsewhere = tracks.active("SWE", "Software Engineering");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID foreignCohort = cohorts.create(elsewhere, "Foreign", START);
        UUID busy = person("alice@x.test", "Alice", "Busy", UserStatus.ACTIVE, Role.MENTOR);
        UUID idle = person("boris@x.test", "Boris", "Idle", UserStatus.ACTIVE, Role.MENTOR);
        for (UUID mentor : List.of(busy, idle)) {
            tracks.addMentor(track, mentor);
        }
        tracks.addMentor(elsewhere, busy);
        UUID target = enrollments.enroll(student("s0@x.test", "Zero"), track, admin, START);
        UUID inCohort = enrollments.enroll(student("s1@x.test", "One"), track, admin, START);
        UUID free = enrollments.enroll(student("s2@x.test", "Two"), track, admin, START);
        UUID foreign = enrollments.enroll(student("s3@x.test", "Three"), elsewhere, admin, START);
        cohorts.put(inCohort, cohort);
        cohorts.addMentor(cohort, busy, admin);
        enrollments.addMentor(free, busy, admin);
        enrollments.addMentor(foreign, busy, admin);
        cohorts.addMentor(foreignCohort, busy, admin);

        MvcTestResult result =
                send(HttpMethod.GET, "/api/v1/enrollments/" + target + "/mentor-candidates", "", "admin");

        assertThat(result).hasStatusOk();
        assertThat(read(result, "$.items[*].id")).containsExactly(busy.toString(), idle.toString());
        assertThat(read(result, "$.items[*].students")).containsExactly(2, 0);
        assertThat(read(result, "$.items[*].cohorts")).containsExactly(1, 0);
    }

    @Test
    @DisplayName("ENR-11.3 A mentor of a cohort sees its students with their cohort")
    void aMentorOfACohortSeesItsStudentsWithTheirCohort() {
        UUID mia = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        users.link(mia, "mia");
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID other = cohorts.create(track, "Autumn", START);
        UUID removedFrom = cohorts.create(track, "Winter", START);
        UUID e0 = enrollments.enroll(namedStudent("a@x.test", "Alice"), track, admin, START);
        UUID e1 = enrollments.enroll(namedStudent("b@x.test", "Boris"), track, admin, START);
        UUID e2 = enrollments.enroll(namedStudent("c@x.test", "Cora"), track, admin, START);
        UUID e3 = enrollments.enroll(namedStudent("d@x.test", "Dan"), track, admin, START);
        UUID e4 = enrollments.enroll(namedStudent("e@x.test", "Eve"), track, admin, START);
        UUID e5 = enrollments.enroll(namedStudent("f@x.test", "Fay"), track, admin, START);
        UUID e6 = enrollments.enroll(namedStudent("g@x.test", "Gus"), track, admin, START);
        cohorts.put(e1, cohort);
        cohorts.put(e2, cohort);
        cohorts.put(e3, cohort);
        cohorts.put(e6, removedFrom);
        cohorts.addMentor(cohort, mia, admin);
        enrollments.addMentor(e1, mia, admin);
        enrollments.addMentor(e0, mia, admin);
        // After the assignment: a student enrolled into the cohort, an enrollment added, one moved away.
        cohorts.put(e4, cohort);
        cohorts.put(e5, cohort);
        cohorts.put(e3, other);

        MvcTestResult result = send(HttpMethod.GET, "/api/v1/me/students", "", "mia");

        assertThat(result).hasStatusOk();
        assertThat(read(result, "$.items[*].id"))
                .containsExactly(e0.toString(), e1.toString(), e2.toString(), e4.toString(), e5.toString());
        assertThat(read(result, "$.items[?(@.id == '" + e0 + "')].cohort")).isEqualTo(Collections.singletonList(null));
        for (UUID led : List.of(e1, e2, e4, e5)) {
            assertThat(read(result, "$.items[?(@.id == '" + led + "')].cohort.name"))
                    .containsExactly("Spring");
        }
        assertThat(read(result, "$.items[?(@.throughCohort == true)].id"))
                .containsExactly(e2.toString(), e4.toString(), e5.toString());
    }

    @Test
    @DisplayName("ENR-13.8 A mentor of a cohort is not removed from the track")
    void aMentorOfACohortIsNotRemovedFromTheTrack() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID empty = cohorts.create(track, "Empty", START);
        UUID full = cohorts.create(track, "Full", START);
        UUID ofCohortOnly = person("alice@x.test", "Alice", "Cohort", UserStatus.ACTIVE, Role.MENTOR);
        UUID ofBoth = person("boris@x.test", "Boris", "Both", UserStatus.ACTIVE, Role.MENTOR);
        tracks.addMentor(track, ofCohortOnly);
        tracks.addMentor(track, ofBoth);
        cohorts.addMentor(empty, ofCohortOnly, admin);
        cohorts.addMentor(full, ofBoth, admin);
        UUID enrollment = enrollments.enroll(student("s1@x.test", "One"), track, admin, START);
        cohorts.put(enrollment, full);
        enrollments.addMentor(enrollment, ofBoth, admin);

        MvcTestResult first =
                send(HttpMethod.DELETE, "/api/v1/tracks/" + track + "/mentors/" + ofCohortOnly, "", "admin");
        MvcTestResult second = send(HttpMethod.DELETE, "/api/v1/tracks/" + track + "/mentors/" + ofBoth, "", "admin");

        assertProblem(first, HttpStatus.CONFLICT, MENTOR_LEADS_COHORTS);
        assertProblem(second, HttpStatus.CONFLICT, MENTOR_LEADS_ENROLLMENTS);
        MvcTestResult view = send(HttpMethod.GET, "/api/v1/tracks/" + track, "", "admin");
        assertThat(read(view, "$.mentors[*].id")).containsExactly(ofCohortOnly.toString(), ofBoth.toString());
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts", "", "admin");
        assertThat(read(list, "$.items[?(@.id == '" + empty + "')].mentors[*].id"))
                .containsExactly(ofCohortOnly.toString());
        assertThat(read(list, "$.items[?(@.id == '" + full + "')].mentors[*].id"))
                .containsExactly(ofBoth.toString());
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-15.4 The students of a mentor of a track count those of their cohorts once")
    void theStudentsOfAMentorOfATrackCountThoseOfTheirCohortsOnce() {
        UUID clara = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(clara, "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        tracks.addMentor(track, mentor);
        UUID e0 = enrollments.enroll(namedStudent("a@x.test", "Alice"), track, admin, START);
        UUID e1 = enrollments.enroll(namedStudent("b@x.test", "Boris"), track, admin, START);
        UUID e2 = enrollments.enroll(namedStudent("c@x.test", "Cora"), track, admin, START);
        enrollments.enroll(namedStudent("d@x.test", "Dan"), track, admin, START);
        cohorts.put(e1, cohort);
        cohorts.put(e2, cohort);
        cohorts.addMentor(cohort, mentor, admin);
        enrollments.addMentor(e1, mentor, admin);
        enrollments.addMentor(e0, mentor, admin);

        MvcTestResult view = send(HttpMethod.GET, "/api/v1/tracks/" + track, "", "clara");
        MvcTestResult list =
                send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollments?mentor=" + mentor, "", "clara");

        assertThat(view).hasStatusOk();
        assertThat(read(view, "$.mentors[*].students")).containsExactly(3);
        assertThat(list).hasStatusOk();
        assertThat(read(list, "$.items[*].id")).containsExactly(e0.toString(), e1.toString(), e2.toString());
        assertThat(list).bodyJson().extractingPath("$.totalItems").isEqualTo(3);
    }

    private static String mentors(UUID enrollment) {
        return "$.items[?(@.id == '" + enrollment + "')].mentors";
    }

    private static String mentorPath(UUID enrollment, UUID user) {
        return "/api/v1/enrollments/" + enrollment + "/mentors/" + user;
    }

    private UUID student(String email, String lastName) {
        return person(email, "Sam", lastName, UserStatus.ACTIVE, Role.STUDENT);
    }

    private UUID namedStudent(String email, String firstName) {
        return person(email, firstName, "Student", UserStatus.ACTIVE, Role.STUDENT);
    }

    private UUID person(String email, String firstName, String lastName, UserStatus status, Role... roles) {
        UUID id = users.create(email, status, roles);
        users.rename(id, firstName, lastName);
        return id;
    }

    private MvcTestResult send(HttpMethod method, String uri, String body, String subject) {
        MockMvcRequestBuilder request = mvc.method(method)
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, issuer.token(subject).bearer());
        if (!body.isEmpty()) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    private static <T> List<T> read(MvcTestResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
    }
}
