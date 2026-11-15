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

/**
 * Enrollments through the real chain: signed tokens, the gate, permissions expanded from stored roles, the services and
 * the database. Users, tracks and (where a scenario starts from one) enrollments are set up in the database; what a
 * request changes is read back through the API.
 */
@EndpointTest
class EnrollmentsEndpointTests {

    static final URI TRACK_ARCHIVED = URI.create("urn:mentorhub:problem:track-archived");
    static final URI STUDENT_NOT_ELIGIBLE = URI.create("urn:mentorhub:problem:student-not-eligible");
    static final URI STUDENT_ALREADY_ENROLLED = URI.create("urn:mentorhub:problem:student-already-enrolled");

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

    // The 403 of a missing permission is what the stubs answer too, so the control at the end shows that the track's
    // enrollments are readable by a caller who holds the permission.
    @Test
    @DisplayName("ENR-1.1 Reading the enrollments of a track needs ENROLLMENTS_READ")
    void readingTheEnrollmentsOfATrackNeedsEnrollmentsRead() {
        UUID mentor = person("mentor@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        users.link(mentor, "mentor");
        UUID student = person("student@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        users.link(student, "student");
        UUID track = tracks.active("QA", "Quality Assurance");
        enrollments.enroll(student, track, admin, START);
        String path = "/api/v1/tracks/" + track + "/enrollments";

        assertProblem(send(HttpMethod.GET, path, "", "mentor"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
        assertProblem(send(HttpMethod.GET, path, "", "student"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);

        MvcTestResult control = send(HttpMethod.GET, path, "", "admin");
        assertThat(control).hasStatusOk();
        assertThat(control)
                .bodyJson()
                .extractingPath("$.items[*].student.id")
                .asArray()
                .containsExactly(student.toString());
    }

    // The coordinator of the first track is refused on every operation of ENROLLMENTS_MANAGE by the stubs too, so the
    // controls (the coordinator's own track tells them they may enroll, and lists its candidates) keep the test honest.
    @Test
    @DisplayName("ENR-1.2 A coordinator enrolls only on a track they coordinate")
    void aCoordinatorEnrollsOnlyOnATrackTheyCoordinate() {
        UUID coordinator = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(coordinator, "clara");
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID first = tracks.active("QA", "Quality Assurance");
        UUID second = tracks.active("SWE", "Software Engineering");
        tracks.addCoordinator(first, coordinator);

        assertThat(send(HttpMethod.GET, "/api/v1/tracks/" + first, "", "clara"))
                .bodyJson()
                .extractingPath("$.canEnroll")
                .isEqualTo(true);
        assertThat(send(HttpMethod.GET, "/api/v1/tracks/" + first + "/enrollment-candidates", "", "clara"))
                .hasStatusOk();

        assertProblem(
                send(HttpMethod.GET, "/api/v1/tracks/" + second + "/enrollment-candidates", "", "clara"),
                HttpStatus.FORBIDDEN,
                Problems.FORBIDDEN);
        assertProblem(enroll(second, student, START, "clara"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);

        MvcTestResult view = send(HttpMethod.GET, "/api/v1/tracks/" + second, "", "clara");
        assertThat(view).hasStatusOk();
        assertThat(view).bodyJson().extractingPath("$.canEnroll").isEqualTo(false);
        assertNoEnrollments(second);
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-1.3 A coordinator removed from a track no longer enrolls on it")
    void aCoordinatorRemovedFromATrackNoLongerEnrollsOnIt() {
        UUID coordinator = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(coordinator, "clara");
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, coordinator);
        String candidates = "/api/v1/tracks/" + track + "/enrollment-candidates";
        assertThat(send(HttpMethod.GET, candidates, "", "clara")).hasStatusOk();
        jdbc.sql("delete from track_coordinators where track_id = :track and user_id = :user")
                .param("track", track)
                .param("user", coordinator)
                .update();

        assertProblem(send(HttpMethod.GET, candidates, "", "clara"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
        assertProblem(enroll(track, student, START, "clara"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);

        assertNoEnrollments(track);
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-1.4 An unknown track is not found")
    void anUnknownTrackIsNotFound() {
        UUID coordinator = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(coordinator, "clara");
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        tracks.addCoordinator(tracks.active("QA", "Quality Assurance"), coordinator);
        UUID unknown = UUID.randomUUID();
        String path = "/api/v1/tracks/" + unknown;

        for (String caller : List.of("admin", "clara")) {
            assertProblem(
                    send(HttpMethod.GET, path + "/enrollments", "", caller), HttpStatus.NOT_FOUND, Problems.NOT_FOUND);
            assertProblem(
                    send(HttpMethod.GET, path + "/enrollment-candidates", "", caller),
                    HttpStatus.NOT_FOUND,
                    Problems.NOT_FOUND);
            assertProblem(enroll(unknown, student, START, caller), HttpStatus.NOT_FOUND, Problems.NOT_FOUND);
        }
    }

    @Test
    @DisplayName("ENR-2.1 A coordinator enrolls a student on their track")
    void aCoordinatorEnrollsAStudentOnTheirTrack() {
        UUID coordinator = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(coordinator, "clara");
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, coordinator);

        MvcTestResult result = enroll(track, student, START, "clara");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.id").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.student.id").isEqualTo(student.toString());
        assertThat(result).bodyJson().extractingPath("$.student.name").isEqualTo("Sam Student");
        assertThat(result).bodyJson().extractingPath("$.student.email").isEqualTo("sam@x.test");
        assertThat(result).bodyJson().extractingPath("$.startDate").isEqualTo("2026-09-01");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollments", "", "clara");
        assertThat(list).hasStatusOk();
        assertThat(list)
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly(jsonString(result, "$.id"));
    }

    @Test
    @DisplayName("ENR-2.2 An invalid enrollment is rejected")
    void anInvalidEnrollmentIsRejected() {
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID track = tracks.active("QA", "Quality Assurance");
        String path = "/api/v1/tracks/" + track + "/enrollments";

        MvcTestResult withoutStudent = send(HttpMethod.POST, path, "{\"startDate\": \"2026-09-01\"}", "admin");
        MvcTestResult withoutDate = send(HttpMethod.POST, path, "{\"studentId\": \"%s\"}".formatted(student), "admin");

        assertValidationProblem(withoutStudent, "studentId");
        assertValidationProblem(withoutDate, "startDate");
        assertNoEnrollments(track);
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-2.3 Only an active user with the student role is enrolled")
    void onlyAnActiveUserWithTheStudentRoleIsEnrolled() {
        UUID disabled = person("dina@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.STUDENT);
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        UUID track = tracks.active("QA", "Quality Assurance");

        for (UUID user : List.of(disabled, mentor, UUID.randomUUID())) {
            assertProblem(enroll(track, user, START, "admin"), HttpStatus.CONFLICT, STUDENT_NOT_ELIGIBLE);
        }

        assertNoEnrollments(track);
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-2.4 A student with an active enrollment is not enrolled again")
    void aStudentWithAnActiveEnrollmentIsNotEnrolledAgain() {
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID first = tracks.active("QA", "Quality Assurance");
        UUID second = tracks.active("SWE", "Software Engineering");
        enrollments.enroll(student, first, admin, START);

        assertProblem(enroll(second, student, START, "admin"), HttpStatus.CONFLICT, STUDENT_ALREADY_ENROLLED);
        assertProblem(enroll(first, student, START, "admin"), HttpStatus.CONFLICT, STUDENT_ALREADY_ENROLLED);

        MvcTestResult onFirst = send(HttpMethod.GET, "/api/v1/tracks/" + first + "/enrollments", "", "admin");
        assertThat(onFirst).hasStatusOk();
        assertThat(onFirst)
                .bodyJson()
                .extractingPath("$.items[*].student.id")
                .asArray()
                .containsExactly(student.toString());
        assertNoEnrollments(second);
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-2.5 Nobody is enrolled on an archived track")
    void nobodyIsEnrolledOnAnArchivedTrack() {
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID track = tracks.archived("QA", "Quality Assurance");

        assertProblem(
                send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollment-candidates", "", "admin"),
                HttpStatus.CONFLICT,
                TRACK_ARCHIVED);
        assertProblem(enroll(track, student, START, "admin"), HttpStatus.CONFLICT, TRACK_ARCHIVED);

        assertNoEnrollments(track);
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-3.1 The candidates are the eligible students not enrolled")
    void theCandidatesAreTheEligibleStudentsNotEnrolled() {
        UUID coordinator = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(coordinator, "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID other = tracks.active("SWE", "Software Engineering");
        tracks.addCoordinator(track, coordinator);
        UUID enrolled = person("enrolled@x.test", "Edna", "Enrolled", UserStatus.ACTIVE, Role.STUDENT);
        enrollments.enroll(enrolled, other, admin, START);
        UUID candidate = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        person("dina@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.STUDENT);
        person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);

        MvcTestResult result = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollment-candidates", "", "clara");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(candidate.toString());
        assertThat(result).bodyJson().extractingPath("$.items[0].name").isEqualTo("Sam Student");
        assertThat(result).bodyJson().extractingPath("$.items[0].email").isEqualTo("sam@x.test");
        assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(1);
    }

    @Test
    @DisplayName("ENR-3.2 The candidates are searched by name or email")
    void theCandidatesAreSearchedByNameOrEmail() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID first = person("first@x.test", "Alice", "Smith", UserStatus.ACTIVE, Role.STUDENT);
        UUID second = person("zorro@x.test", "Boris", "Jones", UserStatus.ACTIVE, Role.STUDENT);
        person("third@x.test", "Clara", "Nolan", UserStatus.ACTIVE, Role.STUDENT);
        String path = "/api/v1/tracks/" + track + "/enrollment-candidates?q=";

        MvcTestResult byName = send(HttpMethod.GET, path + "lice", "", "admin");
        MvcTestResult byEmail = send(HttpMethod.GET, path + "ZORRO", "", "admin");

        assertThat(byName).hasStatusOk();
        assertThat(byName).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(first.toString());
        assertThat(byEmail).hasStatusOk();
        assertThat(byEmail).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(second.toString());
    }

    @Test
    @DisplayName("ENR-4.1 A coordinator of another track sees the enrollments with the marks")
    void aCoordinatorOfAnotherTrackSeesTheEnrollmentsWithTheMarks() {
        UUID coordinator = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(coordinator, "clara");
        tracks.addCoordinator(tracks.active("SWE", "Software Engineering"), coordinator);
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID active = person("alice@x.test", "Alice", "Active", UserStatus.ACTIVE, Role.STUDENT);
        UUID disabled = person("boris@x.test", "Boris", "Disabled", UserStatus.DISABLED, Role.STUDENT);
        UUID withoutRole = person("clarice@x.test", "Clarice", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        // Enrolled in reverse order, so the order of the answer is the sorting's.
        enrollments.enroll(withoutRole, track, admin, START);
        enrollments.enroll(disabled, track, admin, START);
        enrollments.enroll(active, track, admin, START);

        MvcTestResult result = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollments", "", "clara");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].student.id")
                .asArray()
                .containsExactly(active.toString(), disabled.toString(), withoutRole.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].student.disabled")
                .asArray()
                .containsExactly(false, true, false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[*].student.studentRole")
                .asArray()
                .containsExactly(true, true, false);
        assertThat(result).bodyJson().extractingPath("$.items[0].student.name").isEqualTo("Alice Active");
        assertThat(result).bodyJson().extractingPath("$.items[0].student.email").isEqualTo("alice@x.test");
        assertThat(result).bodyJson().extractingPath("$.items[0].startDate").isEqualTo("2026-09-01");
        assertThat(result).bodyJson().extractingPath("$.items[0].status").isEqualTo("ACTIVE");
        assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(3);
    }

    @Test
    @DisplayName("ENR-5.1 A student sees their own enrollment and the track's coordinators")
    void aStudentSeesTheirOwnEnrollmentAndTheTracksCoordinators() {
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        users.link(student, "sam");
        UUID other = person("olga@x.test", "Olga", "Other", UserStatus.ACTIVE, Role.STUDENT);
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID elsewhere = tracks.active("SWE", "Software Engineering");
        UUID active = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        jdbc.sql("update users set phone = '+77011234567' where id = :id")
                .param("id", active)
                .update();
        UUID disabled = person("dina@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.COORDINATOR);
        UUID demoted = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        for (UUID coordinator : List.of(active, disabled, demoted)) {
            tracks.addCoordinator(track, coordinator);
        }
        enrollments.enroll(student, track, admin, START);
        enrollments.enroll(other, elsewhere, admin, START);

        MvcTestResult result = send(HttpMethod.GET, "/api/v1/me/enrollments", "", "sam");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items").asArray().hasSize(1);
        assertThat(result).bodyJson().extractingPath("$.items[0].track.id").isEqualTo(track.toString());
        assertThat(result).bodyJson().extractingPath("$.items[0].track.code").isEqualTo("QA");
        assertThat(result).bodyJson().extractingPath("$.items[0].track.name").isEqualTo("Quality Assurance");
        assertThat(result).bodyJson().extractingPath("$.items[0].startDate").isEqualTo("2026-09-01");
        assertThat(result).bodyJson().extractingPath("$.items[0].status").isEqualTo("ACTIVE");
        assertThat(result)
                .bodyJson()
                .extractingPath("$.items[0].coordinators")
                .isEqualTo(
                        List.of(Map.of("name", "Clara Coordinator", "email", "clara@x.test", "phone", "+77011234567")));
    }

    @Test
    @DisplayName("ENR-5.2 A user without an enrollment gets an empty answer")
    void aUserWithoutAnEnrollmentGetsAnEmptyAnswer() {
        UUID nobody = person("nobody@x.test", "Nina", "Nobody", UserStatus.ACTIVE);
        users.link(nobody, "nobody");
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        enrollments.enroll(student, tracks.active("QA", "Quality Assurance"), admin, START);

        MvcTestResult result = send(HttpMethod.GET, "/api/v1/me/enrollments", "", "nobody");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items").asArray().isEmpty();
    }

    @Test
    @DisplayName("ENR-6.1 An enrollment shows in the track's and the student's history")
    void anEnrollmentShowsInTheTracksAndTheStudentsHistory() {
        UUID coordinator = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(coordinator, "clara");
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, coordinator);
        assertThat(enroll(track, student, START, "clara")).hasStatus(HttpStatus.CREATED);
        assertThat(send(
                        HttpMethod.PUT,
                        "/api/v1/tracks/" + track,
                        "{\"code\": \"QE\", \"name\": \"Quality Engineering\"}",
                        "admin"))
                .hasStatusOk();

        MvcTestResult trackHistory = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/history", "", "admin");
        MvcTestResult userHistory = send(HttpMethod.GET, "/api/v1/users/" + student + "/history", "", "admin");

        assertThat(trackHistory).hasStatusOk();
        List<Map<String, Object>> onTrack = entries(trackHistory, "TRACK_STUDENT_ENROLLED");
        assertThat(onTrack).hasSize(1);
        assertThat(path(onTrack.get(0), "actor", "id")).isEqualTo(coordinator.toString());
        assertThat(path(onTrack.get(0), "user", "id")).isEqualTo(student.toString());
        assertThat(onTrack.get(0)).containsEntry("startDate", "2026-09-01");
        assertThat(userHistory).hasStatusOk();
        List<Map<String, Object>> onUser = entries(userHistory, "USER_ENROLLED");
        assertThat(onUser).hasSize(1);
        assertThat(path(onUser.get(0), "actor", "id")).isEqualTo(coordinator.toString());
        assertThat(onUser.get(0)).containsEntry("startDate", "2026-09-01");
        assertThat(path(onUser.get(0), "track", "id")).isEqualTo(track.toString());
        assertThat(path(onUser.get(0), "track", "code")).isEqualTo("QA");
        assertThat(path(onUser.get(0), "track", "name")).isEqualTo("Quality Assurance");
    }

    private UUID person(String email, String firstName, String lastName, UserStatus status, Role... roles) {
        UUID id = users.create(email, status, roles);
        users.rename(id, firstName, lastName);
        return id;
    }

    private MvcTestResult enroll(UUID track, UUID student, LocalDate startDate, String caller) {
        return send(
                HttpMethod.POST,
                "/api/v1/tracks/" + track + "/enrollments",
                "{\"studentId\": \"%s\", \"startDate\": \"%s\"}".formatted(student, startDate),
                caller);
    }

    private void assertNoEnrollments(UUID track) {
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollments", "", "admin");
        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$.items").asArray().isEmpty();
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

    private static String jsonString(MvcTestResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
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

    private static void assertValidationProblem(MvcTestResult result, String field) {
        assertProblem(result, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .contains(field);
    }
}
