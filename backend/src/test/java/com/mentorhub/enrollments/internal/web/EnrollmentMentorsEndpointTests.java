package com.mentorhub.enrollments.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.mentorhub.enrollments.internal.TestEnrollments;
import com.mentorhub.support.EndpointTest;
import com.mentorhub.tracks.internal.TestTracks;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.web.Problems;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

/** The mentors of an enrollment through the real chain: tokens, the gate, permissions, services and the database. */
@EndpointTest
class EnrollmentMentorsEndpointTests {

    static final URI MENTOR_NOT_ELIGIBLE = URI.create("urn:mentorhub:problem:mentor-not-eligible");
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
    UUID admin;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        tracks = new TestTracks(jdbc);
        enrollments = new TestEnrollments(jdbc);
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "admin");
    }

    @Test
    @DisplayName("ENR-1.5 A coordinator assigns mentors only on a track they coordinate")
    void aCoordinatorAssignsMentorsOnlyOnATrackTheyCoordinate() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID other = tracks.active("SWE", "Software Engineering");
        UUID elsewhere = coordinator("elsa@x.test", "elsa");
        tracks.addCoordinator(other, elsewhere);
        UUID removed = coordinator("rita@x.test", "rita");
        tracks.addCoordinator(track, removed);
        jdbc.sql("delete from track_coordinators where user_id = :user")
                .param("user", removed)
                .update();
        UUID candidate = person("cand@x.test", "Cand", "Idate", UserStatus.ACTIVE, Role.MENTOR);
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        tracks.addMentor(track, candidate);
        tracks.addMentor(track, mentor);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        enrollments.addMentor(enrollment, mentor, admin);

        for (String caller : List.of("elsa", "rita")) {
            assertProblem(
                    send(HttpMethod.GET, candidatesPath(enrollment), "", caller),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
            assertProblem(
                    send(HttpMethod.PUT, mentorPath(enrollment, candidate), "", caller),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
            assertProblem(
                    send(HttpMethod.DELETE, mentorPath(enrollment, mentor), "", caller),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
        }

        assertThat(mentorIds(track)).containsExactly(mentor.toString());
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-1.6 An unknown enrollment is not found")
    void anUnknownEnrollmentIsNotFound() {
        UUID clara = coordinator("clara@x.test", "clara");
        tracks.addCoordinator(tracks.active("QA", "Quality Assurance"), clara);
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        UUID unknown = UUID.randomUUID();

        for (String caller : List.of("admin", "clara")) {
            assertProblem(
                    send(HttpMethod.GET, candidatesPath(unknown), "", caller),
                    HttpStatus.NOT_FOUND,
                    Problems.NOT_FOUND);
            assertProblem(
                    send(HttpMethod.PUT, mentorPath(unknown, mentor), "", caller),
                    HttpStatus.NOT_FOUND,
                    Problems.NOT_FOUND);
            assertProblem(
                    send(HttpMethod.DELETE, mentorPath(unknown, mentor), "", caller),
                    HttpStatus.NOT_FOUND,
                    Problems.NOT_FOUND);
        }
    }

    @Test
    @DisplayName("ENR-4.2 The mentors of an enrollment are listed with their marks")
    void theMentorsOfAnEnrollmentAreListedWithTheirMarks() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID active = person("alice@x.test", "Alice", "Active", UserStatus.ACTIVE, Role.MENTOR);
        UUID disabled = person("boris@x.test", "Boris", "Disabled", UserStatus.DISABLED, Role.MENTOR);
        UUID demoted = person("clara@x.test", "Clara", "Demoted", UserStatus.ACTIVE, Role.COORDINATOR);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        // Assigned in reverse order, so the order of the answer is the sorting's.
        enrollments.addMentor(enrollment, demoted, admin);
        enrollments.addMentor(enrollment, disabled, admin);
        enrollments.addMentor(enrollment, active, admin);

        MvcTestResult result = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollments", "", "admin");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].mentors[*].id")
                .asArray()
                .containsExactly(active.toString(), disabled.toString(), demoted.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].mentors[*].disabled")
                .asArray()
                .containsExactly(false, true, false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].mentors[*].mentorRole")
                .asArray()
                .containsExactly(true, true, false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].mentors[0].name")
                .isEqualTo("Alice Active");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].mentors[0].email")
                .isEqualTo("alice@x.test");
    }

    @Test
    @DisplayName("ENR-5.3 A student sees the mentors who may lead them")
    void aStudentSeesTheMentorsWhoMayLeadThem() {
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        users.link(student, "sam");
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID active = person("alice@x.test", "Alice", "Active", UserStatus.ACTIVE, Role.MENTOR);
        jdbc.sql("update users set phone = '+77011234567' where id = :id")
                .param("id", active)
                .update();
        UUID disabled = person("boris@x.test", "Boris", "Disabled", UserStatus.DISABLED, Role.MENTOR);
        UUID demoted = person("clara@x.test", "Clara", "Demoted", UserStatus.ACTIVE, Role.COORDINATOR);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        for (UUID mentor : List.of(active, disabled, demoted)) {
            enrollments.addMentor(enrollment, mentor, admin);
        }

        MvcTestResult result = send(HttpMethod.GET, "/api/v1/me/enrollments", "", "sam");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].mentors")
                .isEqualTo(List.of(Map.of("name", "Alice Active", "email", "alice@x.test", "phone", "+77011234567")));
    }

    @Test
    @DisplayName("ENR-6.2 Assigning and removing a mentor show in three histories")
    void assigningAndRemovingAMentorShowInThreeHistories() {
        UUID clara = coordinator("clara@x.test", "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        tracks.addMentor(track, mentor);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        assertThat(send(HttpMethod.PUT, mentorPath(enrollment, mentor), "", "clara"))
                .hasStatusOk();
        assertThat(send(HttpMethod.DELETE, mentorPath(enrollment, mentor), "", "clara"))
                .hasStatusOk();
        assertThat(send(
                        HttpMethod.PUT,
                        "/api/v1/tracks/" + track,
                        "{\"code\": \"QE\", \"name\": \"Quality Engineering\"}",
                        "admin"))
                .hasStatusOk();

        MvcTestResult onTrack = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/history", "", "admin");
        MvcTestResult onStudent = send(HttpMethod.GET, "/api/v1/users/" + student + "/history", "", "admin");
        MvcTestResult onMentor = send(HttpMethod.GET, "/api/v1/users/" + mentor + "/history", "", "admin");

        for (String action : List.of("TRACK_ENROLLMENT_MENTOR_ASSIGNED", "TRACK_ENROLLMENT_MENTOR_REMOVED")) {
            List<Map<String, Object>> entries = entries(onTrack, action);
            assertThat(entries).hasSize(1);
            assertThat(path(entries.get(0), "actor", "id")).isEqualTo(clara.toString());
            assertThat(path(entries.get(0), "user", "id")).isEqualTo(student.toString());
            assertThat(path(entries.get(0), "mentor", "id")).isEqualTo(mentor.toString());
        }
        for (String action : List.of("USER_MENTOR_ASSIGNED", "USER_MENTOR_REMOVED")) {
            List<Map<String, Object>> entries = entries(onStudent, action);
            assertThat(entries).hasSize(1);
            assertThat(path(entries.get(0), "actor", "id")).isEqualTo(clara.toString());
            assertThat(path(entries.get(0), "mentor", "id")).isEqualTo(mentor.toString());
            assertOldTrack(entries.get(0), track);
        }
        for (String action : List.of("USER_STUDENT_ASSIGNED", "USER_STUDENT_REMOVED")) {
            List<Map<String, Object>> entries = entries(onMentor, action);
            assertThat(entries).hasSize(1);
            assertThat(path(entries.get(0), "actor", "id")).isEqualTo(clara.toString());
            assertThat(path(entries.get(0), "user", "id")).isEqualTo(student.toString());
            assertOldTrack(entries.get(0), track);
        }
    }

    @Test
    @DisplayName("ENR-9.1 A coordinator assigns and removes the mentors of an enrollment on their track")
    void aCoordinatorAssignsAndRemovesTheMentorsOfAnEnrollmentOnTheirTrack() {
        UUID clara = coordinator("clara@x.test", "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        UUID former = person("fred@x.test", "Fred", "Former", UserStatus.DISABLED, Role.MENTOR);
        tracks.addMentor(track, mentor);
        tracks.addMentor(track, former);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        enrollments.addMentor(enrollment, former, admin);

        MvcTestResult assigned = send(HttpMethod.PUT, mentorPath(enrollment, mentor), "", "clara");
        MvcTestResult removed = send(HttpMethod.DELETE, mentorPath(enrollment, former), "", "clara");

        assertThat(assigned).hasStatusOk();
        assertThat(assigned).bodyJson().extractingPath("$.id").isEqualTo(enrollment.toString());
        assertThat(removed).hasStatusOk();
        assertThat(removed)
                .bodyJson()
                .extractingPath("$.mentors[*].id")
                .asArray()
                .containsExactly(mentor.toString());
        assertThat(mentorIds(track)).containsExactly(mentor.toString());
    }

    @Test
    @DisplayName("ENR-9.2 Only an active mentor of the track with the mentor role is assigned")
    void onlyAnActiveMentorOfTheTrackWithTheMentorRoleIsAssigned() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID disabled = person("dina@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.MENTOR);
        UUID withoutRole = person("cora@x.test", "Cora", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        UUID elsewhere = person("eve@x.test", "Eve", "Elsewhere", UserStatus.ACTIVE, Role.MENTOR);
        tracks.addMentor(track, disabled);
        tracks.addMentor(track, withoutRole);
        tracks.addMentor(tracks.active("SWE", "Software Engineering"), elsewhere);
        UUID enrollment = enrollments.enroll(student, track, admin, START);

        for (UUID user : List.of(disabled, withoutRole, elsewhere, UUID.randomUUID())) {
            assertProblem(
                    send(HttpMethod.PUT, mentorPath(enrollment, user), "", "admin"),
                    HttpStatus.CONFLICT,
                    MENTOR_NOT_ELIGIBLE);
        }

        assertThat(mentorIds(track)).isEmpty();
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-9.3 A repeated assignment or removal records nothing")
    void aRepeatedAssignmentOrRemovalRecordsNothing() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID active = person("alice@x.test", "Alice", "Active", UserStatus.ACTIVE, Role.MENTOR);
        UUID demoted = person("clara@x.test", "Clara", "Demoted", UserStatus.ACTIVE, Role.COORDINATOR);
        UUID bystander = person("bob@x.test", "Bob", "Bystander", UserStatus.ACTIVE, Role.MENTOR);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        enrollments.addMentor(enrollment, active, admin);
        enrollments.addMentor(enrollment, demoted, admin);

        assertThat(send(HttpMethod.PUT, mentorPath(enrollment, active), "", "admin"))
                .hasStatusOk();
        assertThat(send(HttpMethod.PUT, mentorPath(enrollment, demoted), "", "admin"))
                .hasStatusOk();
        assertThat(send(HttpMethod.DELETE, mentorPath(enrollment, bystander), "", "admin"))
                .hasStatusOk();
        assertThat(send(HttpMethod.DELETE, mentorPath(enrollment, UUID.randomUUID()), "", "admin"))
                .hasStatusOk();

        assertThat(mentorIds(track)).containsExactlyInAnyOrder(active.toString(), demoted.toString());
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-10.1 The candidates are the eligible mentors of the track not yet assigned")
    void theCandidatesAreTheEligibleMentorsOfTheTrackNotYetAssigned() {
        UUID clara = coordinator("clara@x.test", "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID assigned = person("alice@x.test", "Alice", "Assigned", UserStatus.ACTIVE, Role.MENTOR);
        UUID candidate = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        UUID disabled = person("dina@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.MENTOR);
        UUID withoutRole = person("cora@x.test", "Cora", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        person("eve@x.test", "Eve", "Elsewhere", UserStatus.ACTIVE, Role.MENTOR);
        for (UUID mentor : List.of(assigned, candidate, disabled, withoutRole)) {
            tracks.addMentor(track, mentor);
        }
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        enrollments.addMentor(enrollment, assigned, admin);

        MvcTestResult result = send(HttpMethod.GET, candidatesPath(enrollment), "", "clara");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(candidate.toString());
        assertThat(result).bodyJson().extractingPath("$.items[0].name").isEqualTo("Mia Mentor");
        assertThat(result).bodyJson().extractingPath("$.items[0].email").isEqualTo("mia@x.test");
        assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(1);
    }

    @Test
    @DisplayName("ENR-11.1 A mentor sees only the students they lead now")
    void aMentorSeesOnlyTheStudentsTheyLeadNow() {
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        users.link(mentor, "mia");
        UUID otherMentor = person("omar@x.test", "Omar", "Other", UserStatus.ACTIVE, Role.MENTOR);
        UUID qa = tracks.active("QA", "Quality Assurance");
        UUID swe = tracks.active("SWE", "Software Engineering");
        UUID alice = person("alice@x.test", "Alice", "Disabled", UserStatus.DISABLED, Role.STUDENT);
        UUID boris = person("boris@x.test", "Boris", "Demoted", UserStatus.ACTIVE, Role.COORDINATOR);
        UUID clara = person("clara@x.test", "Clara", "Active", UserStatus.ACTIVE, Role.STUDENT);
        UUID dora = person("dora@x.test", "Dora", "Dropped", UserStatus.ACTIVE, Role.STUDENT);
        UUID emil = person("emil@x.test", "Emil", "Else", UserStatus.ACTIVE, Role.STUDENT);
        // Enrolled in reverse order, so the order of the answer is the sorting's.
        UUID forClara = enrollments.enroll(clara, qa, admin, START);
        UUID forBoris = enrollments.enroll(boris, swe, admin, START);
        UUID forAlice = enrollments.enroll(alice, qa, admin, START);
        UUID forDora = enrollments.enroll(dora, swe, admin, START);
        UUID forEmil = enrollments.enroll(emil, qa, admin, START);
        for (UUID enrollment : List.of(forClara, forBoris, forAlice, forDora)) {
            enrollments.addMentor(enrollment, mentor, admin);
        }
        enrollments.removeMentor(forDora, mentor);
        enrollments.addMentor(forEmil, otherMentor, admin);

        MvcTestResult result = send(HttpMethod.GET, "/api/v1/me/students", "", "mia");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly(forAlice.toString(), forBoris.toString(), forClara.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].track.code")
                .asArray()
                .containsExactly("QA", "SWE", "QA");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].student.disabled")
                .asArray()
                .containsExactly(true, false, false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].student.studentRole")
                .asArray()
                .containsExactly(true, false, true);
        assertThat(result).bodyJson().extractingPath("$.items[0].student.name").isEqualTo("Alice Disabled");
        assertThat(result).bodyJson().extractingPath("$.items[0].student.email").isEqualTo("alice@x.test");
        assertThat(result).bodyJson().extractingPath("$.items[0].startDate").isEqualTo("2026-09-01");
        assertThat(result).bodyJson().extractingPath("$.items[0].status").isEqualTo("ACTIVE");
        assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(3);
    }

    // The control shows the same user reads their students while holding the role.
    @Test
    @DisplayName("ENR-11.2 A mentor who lost the mentor role no longer sees their students")
    void aMentorWhoLostTheMentorRoleNoLongerSeesTheirStudents() {
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        users.link(mentor, "mia");
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID enrollment = enrollments.enroll(student, tracks.active("QA", "Quality Assurance"), admin, START);
        enrollments.addMentor(enrollment, mentor, admin);
        MvcTestResult control = send(HttpMethod.GET, "/api/v1/me/students", "", "mia");
        assertThat(control).hasStatusOk();
        assertThat(control).bodyJson().extractingPath("$.items").asArray().hasSize(1);
        jdbc.sql("delete from user_roles where user_id = :user")
                .param("user", mentor)
                .update();

        assertProblem(send(HttpMethod.GET, "/api/v1/me/students", "", "mia"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
    }

    private void assertOldTrack(Map<String, Object> entry, UUID track) {
        assertThat(path(entry, "track", "id")).isEqualTo(track.toString());
        assertThat(path(entry, "track", "code")).isEqualTo("QA");
        assertThat(path(entry, "track", "name")).isEqualTo("Quality Assurance");
    }

    private List<String> mentorIds(UUID track) {
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollments", "", "admin");
        assertThat(list).hasStatusOk();
        return JsonPath.read(
                new String(list.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                "$.items[0].mentors[*].id");
    }

    private static String candidatesPath(UUID enrollment) {
        return "/api/v1/enrollments/" + enrollment + "/mentor-candidates";
    }

    private static String mentorPath(UUID enrollment, UUID user) {
        return "/api/v1/enrollments/" + enrollment + "/mentors/" + user;
    }

    private UUID coordinator(String email, String subject) {
        UUID id = person(email, "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(id, subject);
        return id;
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

    private static List<Map<String, Object>> entries(MvcTestResult history, String action) {
        return JsonPath.read(
                new String(history.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                "$.items[?(@.action == '%s')]".formatted(action));
    }

    @SuppressWarnings("unchecked")
    private static Object path(Map<String, Object> entry, String object, String field) {
        Map<String, Object> inner = (Map<String, Object>) entry.get(object);
        return inner == null ? null : inner.get(field);
    }
}
