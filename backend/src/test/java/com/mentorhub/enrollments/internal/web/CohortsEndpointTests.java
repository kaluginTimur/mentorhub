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
 * The cohorts of a track through the real chain: signed tokens, the gate, permissions expanded from stored roles, the
 * services and the database. Users, tracks, enrollments and cohorts a scenario starts from are set up in the database;
 * what a request changes is read back through the API.
 */
@EndpointTest
class CohortsEndpointTests {

    static final URI TRACK_ARCHIVED = URI.create("urn:mentorhub:problem:track-archived");
    static final URI ENROLLMENT_NOT_ELIGIBLE = URI.create("urn:mentorhub:problem:enrollment-not-eligible");
    static final URI COHORT_NOT_EMPTY = URI.create("urn:mentorhub:problem:cohort-not-empty");

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
    @DisplayName("COH-1.1 Reading the cohorts of a track needs ENROLLMENTS_READ")
    void readingTheCohortsOfATrackNeedsEnrollmentsRead() {
        UUID mentor = person("mentor@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        users.link(mentor, "mentor");
        UUID student = person("student@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        users.link(student, "student");
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        cohorts.put(enrollment, cohort);
        enrollments.addMentor(enrollment, mentor, admin);
        String path = "/api/v1/tracks/" + track + "/cohorts";

        assertProblem(send(HttpMethod.GET, path, "", "mentor"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
        assertProblem(send(HttpMethod.GET, path, "", "student"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);

        MvcTestResult control = send(HttpMethod.GET, path, "", "admin");
        assertThat(control).hasStatusOk();
        assertThat(ids(control, "$.items[*].id")).containsExactly(cohort.toString());
    }

    @Test
    @DisplayName("COH-1.2 A coordinator changes the cohorts only of a track they coordinate")
    void aCoordinatorChangesTheCohortsOnlyOfATrackTheyCoordinate() {
        UUID elsewhere = person("eva@x.test", "Eva", "Elsewhere", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(elsewhere, "eva");
        UUID removed = person("rita@x.test", "Rita", "Removed", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(removed, "rita");
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID other = tracks.active("SWE", "Software Engineering");
        tracks.addCoordinator(other, elsewhere);
        tracks.addCoordinator(track, removed);
        jdbc.sql("delete from track_coordinators where track_id = :track and user_id = :user")
                .param("track", track)
                .param("user", removed)
                .update();
        UUID empty = cohorts.create(track, "Empty", START);
        UUID full = cohorts.create(track, "Full", START);
        UUID inCohort = enrollments.enroll(
                person("a@x.test", "Anna", "Inside", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        cohorts.put(inCohort, full);
        UUID free = enrollments.enroll(
                person("b@x.test", "Boris", "Free", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        String base = "/api/v1/tracks/" + track;

        for (String caller : List.of("eva", "rita")) {
            assertProblem(
                    send(HttpMethod.POST, base + "/cohorts", newCohort("Autumn", List.of()), caller),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
            assertProblem(
                    send(HttpMethod.GET, base + "/cohort-candidates", "", caller),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
            assertProblem(
                    send(HttpMethod.GET, base + "/cohorts/" + empty + "/candidates", "", caller),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
            assertProblem(
                    send(HttpMethod.POST, base + "/cohorts/" + empty + "/enrollments", addition(free), caller),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
            assertProblem(
                    send(HttpMethod.DELETE, base + "/cohorts/" + full + "/enrollments/" + inCohort, "", caller),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
            assertProblem(
                    send(HttpMethod.DELETE, base + "/cohorts/" + empty, "", caller),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
            MvcTestResult view = send(HttpMethod.GET, base, "", caller);
            assertThat(view).hasStatusOk();
            assertThat(view).bodyJson().extractingPath("$.canEnroll").isEqualTo(false);
        }

        MvcTestResult list = send(HttpMethod.GET, base + "/cohorts", "", "admin");
        assertThat(list).hasStatusOk();
        assertThat(ids(list, "$.items[*].id")).containsExactlyInAnyOrder(empty.toString(), full.toString());
        assertThat(ids(list, "$.items[?(@.id == '" + full + "')].enrollmentCount"))
                .containsExactly(1);
        assertThat(ids(list, "$.items[?(@.id == '" + empty + "')].enrollmentCount"))
                .containsExactly(0);
        assertThat(cohortOf(track, free)).isNull();
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("COH-1.3 An unknown track or cohort is not found")
    void anUnknownTrackOrCohortIsNotFound() {
        UUID clara = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(clara, "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID other = tracks.active("SWE", "Software Engineering");
        tracks.addCoordinator(track, clara);
        cohorts.create(track, "Spring", START);
        UUID foreign = cohorts.create(other, "Foreign", START);
        UUID unknownTrack = UUID.randomUUID();
        UUID unknownCohort = UUID.randomUUID();
        UUID someEnrollment = UUID.randomUUID();

        for (String caller : List.of("admin", "clara")) {
            String missing = "/api/v1/tracks/" + unknownTrack;
            assertNotFound(send(HttpMethod.GET, missing + "/cohorts", "", caller));
            assertNotFound(send(HttpMethod.POST, missing + "/cohorts", newCohort("Autumn", List.of()), caller));
            assertNotFound(send(HttpMethod.GET, missing + "/cohort-candidates", "", caller));

            for (UUID cohort : List.of(unknownCohort, foreign)) {
                String path = "/api/v1/tracks/" + track + "/cohorts/" + cohort;
                assertNotFound(send(HttpMethod.GET, path + "/candidates", "", caller));
                assertNotFound(send(HttpMethod.POST, path + "/enrollments", addition(someEnrollment), caller));
                assertNotFound(send(HttpMethod.DELETE, path + "/enrollments/" + someEnrollment, "", caller));
                assertNotFound(send(HttpMethod.DELETE, path, "", caller));
            }
        }
    }

    @Test
    @DisplayName("COH-2.1 A coordinator creates a cohort with its enrollments")
    void aCoordinatorCreatesACohortWithItsEnrollments() {
        UUID clara = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(clara, "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID existing = cohorts.create(track, "Autumn 2025", LocalDate.of(2025, 9, 1));
        UUID first = enrollments.enroll(
                person("a@x.test", "Anna", "First", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        UUID second = enrollments.enroll(
                person("b@x.test", "Boris", "Second", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        UUID third = enrollments.enroll(
                person("c@x.test", "Cleo", "Third", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        cohorts.put(third, existing);

        MvcTestResult created = send(
                HttpMethod.POST,
                "/api/v1/tracks/" + track + "/cohorts",
                newCohort(" Spring 2026 ", List.of(first, third)),
                "clara");

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.name").isEqualTo("Spring 2026");
        assertThat(created).bodyJson().extractingPath("$.startDate").isEqualTo("2026-03-01");
        assertThat(created).bodyJson().extractingPath("$.enrollmentCount").isEqualTo(2);
        String id = JsonPath.read(body(created), "$.id");
        MvcTestResult narrowed =
                send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollments?cohortId=" + id, "", "clara");
        assertThat(narrowed).hasStatusOk();
        assertThat(ids(narrowed, "$.items[*].id")).containsExactlyInAnyOrder(first.toString(), third.toString());
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts", "", "clara");
        assertThat(ids(list, "$.items[?(@.id == '" + existing + "')].enrollmentCount"))
                .containsExactly(0);
        assertThat(cohortOf(track, second)).isNull();
    }

    @Test
    @DisplayName("COH-2.2 An invalid cohort is rejected")
    void anInvalidCohortIsRejected() {
        UUID track = tracks.active("QA", "Quality Assurance");
        String path = "/api/v1/tracks/" + track + "/cohorts";

        MvcTestResult blank =
                send(HttpMethod.POST, path, "{\"name\": \"   \", \"startDate\": \"2026-03-01\"}", "admin");
        MvcTestResult withoutDate = send(HttpMethod.POST, path, "{\"name\": \"Spring\"}", "admin");

        assertValidationProblem(blank, "name");
        assertValidationProblem(withoutDate, "startDate");
        MvcTestResult list = send(HttpMethod.GET, path, "", "admin");
        assertThat(list).hasStatusOk();
        assertThat(ids(list, "$.items[*].id")).isEmpty();
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("COH-2.3 An enrollment from outside the track refuses the whole request")
    void anEnrollmentFromOutsideTheTrackRefusesTheWholeRequest() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID other = tracks.active("SWE", "Software Engineering");
        UUID empty = cohorts.create(track, "Empty", START);
        UUID own = enrollments.enroll(
                person("a@x.test", "Anna", "Own", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        UUID foreign = enrollments.enroll(
                person("b@x.test", "Boris", "Foreign", UserStatus.ACTIVE, Role.STUDENT), other, admin, START);
        String base = "/api/v1/tracks/" + track;

        for (UUID bad : List.of(foreign, UUID.randomUUID())) {
            assertProblem(
                    send(HttpMethod.POST, base + "/cohorts", newCohort("Spring", List.of(own, bad)), "admin"),
                    HttpStatus.CONFLICT,
                    ENROLLMENT_NOT_ELIGIBLE);
            assertProblem(
                    send(HttpMethod.POST, base + "/cohorts/" + empty + "/enrollments", addition(own, bad), "admin"),
                    HttpStatus.CONFLICT,
                    ENROLLMENT_NOT_ELIGIBLE);
        }

        MvcTestResult list = send(HttpMethod.GET, base + "/cohorts", "", "admin");
        assertThat(ids(list, "$.items[*].id")).containsExactly(empty.toString());
        assertThat(cohortOf(track, own)).isNull();
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("COH-2.4 The cohorts of an archived track cannot be changed")
    void theCohortsOfAnArchivedTrackCannotBeChanged() {
        UUID track = tracks.archived("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        String base = "/api/v1/tracks/" + track;
        UUID someEnrollment = UUID.randomUUID();

        assertProblem(
                send(HttpMethod.POST, base + "/cohorts", newCohort("Autumn", List.of()), "admin"),
                HttpStatus.CONFLICT,
                TRACK_ARCHIVED);
        assertProblem(
                send(HttpMethod.GET, base + "/cohort-candidates", "", "admin"), HttpStatus.CONFLICT, TRACK_ARCHIVED);
        assertProblem(
                send(HttpMethod.GET, base + "/cohorts/" + cohort + "/candidates", "", "admin"),
                HttpStatus.CONFLICT,
                TRACK_ARCHIVED);
        assertProblem(
                send(HttpMethod.POST, base + "/cohorts/" + cohort + "/enrollments", addition(someEnrollment), "admin"),
                HttpStatus.CONFLICT,
                TRACK_ARCHIVED);
        assertProblem(
                send(HttpMethod.DELETE, base + "/cohorts/" + cohort + "/enrollments/" + someEnrollment, "", "admin"),
                HttpStatus.CONFLICT,
                TRACK_ARCHIVED);
        assertProblem(
                send(HttpMethod.DELETE, base + "/cohorts/" + cohort, "", "admin"), HttpStatus.CONFLICT, TRACK_ARCHIVED);

        MvcTestResult list = send(HttpMethod.GET, base + "/cohorts", "", "admin");
        assertThat(list).hasStatusOk();
        assertThat(ids(list, "$.items[*].id")).containsExactly(cohort.toString());
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("COH-3.1 Adding moves an enrollment from another cohort and skips one already in the cohort")
    void addingMovesAnEnrollmentFromAnotherCohortAndSkipsOneAlreadyInTheCohort() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID firstCohort = cohorts.create(track, "First", START);
        UUID secondCohort = cohorts.create(track, "Second", START);
        UUID s1 = person("a@x.test", "Anna", "One", UserStatus.ACTIVE, Role.STUDENT);
        UUID s2 = person("b@x.test", "Boris", "Two", UserStatus.ACTIVE, Role.STUDENT);
        UUID s3 = person("c@x.test", "Cleo", "Three", UserStatus.ACTIVE, Role.STUDENT);
        UUID e1 = enrollments.enroll(s1, track, admin, START);
        UUID e2 = enrollments.enroll(s2, track, admin, START);
        UUID e3 = enrollments.enroll(s3, track, admin, START);
        cohorts.put(e1, firstCohort);
        cohorts.put(e2, secondCohort);

        MvcTestResult added = send(
                HttpMethod.POST,
                "/api/v1/tracks/" + track + "/cohorts/" + firstCohort + "/enrollments",
                addition(e1, e2, e3),
                "admin");

        assertThat(added).hasStatusOk();
        assertThat(added).bodyJson().extractingPath("$.enrollmentCount").isEqualTo(3);
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts", "", "admin");
        assertThat(ids(list, "$.items[?(@.id == '" + firstCohort + "')].enrollmentCount"))
                .containsExactly(3);
        assertThat(ids(list, "$.items[?(@.id == '" + secondCohort + "')].enrollmentCount"))
                .containsExactly(0);
        MvcTestResult trackHistory = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/history", "", "admin");
        assertThat(ids(trackHistory, "$.items[?(@.action == 'TRACK_ENROLLMENT_COHORT_CHANGED')].user.id"))
                .containsExactlyInAnyOrder(s2.toString(), s3.toString());
        assertThat(userActions(s1)).isEmpty();
        assertThat(userActions(s2)).containsExactly("USER_COHORT_CHANGED");
        assertThat(userActions(s3)).containsExactly("USER_COHORT_CHANGED");
    }

    @Test
    @DisplayName("COH-3.2 Taking an enrollment out leaves it in no cohort")
    void takingAnEnrollmentOutLeavesItInNoCohort() {
        UUID clara = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(clara, "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID s1 = person("a@x.test", "Anna", "One", UserStatus.ACTIVE, Role.STUDENT);
        UUID s2 = person("b@x.test", "Boris", "Two", UserStatus.ACTIVE, Role.STUDENT);
        UUID s3 = person("c@x.test", "Cleo", "Three", UserStatus.ACTIVE, Role.STUDENT);
        UUID e1 = enrollments.enroll(s1, track, admin, START);
        UUID e2 = enrollments.enroll(s2, track, admin, START);
        UUID e3 = enrollments.enroll(s3, track, admin, START);
        cohorts.put(e1, cohort);
        cohorts.put(e2, cohort);
        String base = "/api/v1/tracks/" + track + "/cohorts/" + cohort + "/enrollments/";

        MvcTestResult first = send(HttpMethod.DELETE, base + e1, "", "clara");
        MvcTestResult notInCohort = send(HttpMethod.DELETE, base + e3, "", "clara");
        MvcTestResult unknown = send(HttpMethod.DELETE, base + UUID.randomUUID(), "", "clara");

        assertThat(first).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(notInCohort).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(unknown).hasStatus(HttpStatus.NO_CONTENT);
        MvcTestResult narrowed =
                send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollments?cohortId=" + cohort, "", "clara");
        assertThat(ids(narrowed, "$.items[*].id")).containsExactly(e2.toString());
        assertThat(cohortOf(track, e1)).isNull();
        MvcTestResult trackHistory = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/history", "", "admin");
        assertThat(ids(trackHistory, "$.items[*].action")).containsExactly("TRACK_ENROLLMENT_COHORT_CHANGED");
        assertThat(ids(trackHistory, "$.items[*].user.id")).containsExactly(s1.toString());
        assertThat(userActions(s1)).containsExactly("USER_COHORT_CHANGED");
        assertThat(userActions(s2)).isEmpty();
        assertThat(userActions(s3)).isEmpty();
    }

    @Test
    @DisplayName("COH-4.1 Only an empty cohort is deleted")
    void onlyAnEmptyCohortIsDeleted() {
        UUID clara = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(clara, "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID empty = cohorts.create(track, "Empty", START);
        UUID full = cohorts.create(track, "Full", START);
        UUID enrollment = enrollments.enroll(
                person("a@x.test", "Anna", "One", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        cohorts.put(enrollment, full);
        String base = "/api/v1/tracks/" + track + "/cohorts/";

        MvcTestResult deleted = send(HttpMethod.DELETE, base + empty, "", "clara");
        MvcTestResult refused = send(HttpMethod.DELETE, base + full, "", "clara");

        assertThat(deleted).hasStatus(HttpStatus.NO_CONTENT);
        assertProblem(refused, HttpStatus.CONFLICT, COHORT_NOT_EMPTY);
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts", "", "clara");
        assertThat(ids(list, "$.items[*].id")).containsExactly(full.toString());
        MvcTestResult history = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/history", "", "admin");
        assertThat(ids(history, "$.items[*].action")).containsExactly("TRACK_COHORT_DELETED");
    }

    @Test
    @DisplayName("COH-5.1 The cohorts are listed newest first with their numbers of students")
    void theCohortsAreListedNewestFirstWithTheirNumbersOfStudents() {
        UUID elsewhere = person("eva@x.test", "Eva", "Elsewhere", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(elsewhere, "eva");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(tracks.active("SWE", "Software Engineering"), elsewhere);
        UUID oldest = cohorts.create(track, "Oldest", LocalDate.of(2026, 3, 1));
        UUID b = cohorts.create(track, "B", LocalDate.of(2026, 9, 1));
        UUID a = cohorts.create(track, "A", LocalDate.of(2026, 9, 1));
        UUID active = enrollments.enroll(
                person("a@x.test", "Anna", "Active", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        UUID disabled = enrollments.enroll(
                person("d@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.STUDENT), track, admin, START);
        UUID inB = enrollments.enroll(
                person("c@x.test", "Cleo", "Bee", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        cohorts.put(active, oldest);
        cohorts.put(disabled, oldest);
        cohorts.put(inB, b);

        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts", "", "eva");

        assertThat(list).hasStatusOk();
        assertThat(ids(list, "$.items[*].name")).containsExactly("A", "B", "Oldest");
        assertThat(ids(list, "$.items[*].id")).containsExactly(a.toString(), b.toString(), oldest.toString());
        assertThat(ids(list, "$.items[*].enrollmentCount")).containsExactly(0, 1, 2);
    }

    @Test
    @DisplayName("COH-6.1 The candidates are the enrollments of the track outside the cohort")
    void theCandidatesAreTheEnrollmentsOfTheTrackOutsideTheCohort() {
        UUID clara = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(clara, "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID other = tracks.active("SWE", "Software Engineering");
        tracks.addCoordinator(track, clara);
        UUID firstCohort = cohorts.create(track, "First", START);
        UUID secondCohort = cohorts.create(track, "Second", START);
        UUID e1 = enrollments.enroll(
                person("a@x.test", "Anna", "Alpha", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        UUID e2 = enrollments.enroll(
                person("b@x.test", "Boris", "Bravo", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        UUID e3 = enrollments.enroll(
                person("c@x.test", "Cleo", "Charlie", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        enrollments.enroll(person("d@x.test", "Dan", "Delta", UserStatus.ACTIVE, Role.STUDENT), other, admin, START);
        cohorts.put(e1, firstCohort);
        cohorts.put(e2, secondCohort);
        String base = "/api/v1/tracks/" + track;

        MvcTestResult forFirst = send(HttpMethod.GET, base + "/cohorts/" + firstCohort + "/candidates", "", "clara");
        MvcTestResult withoutCohort =
                send(HttpMethod.GET, base + "/cohorts/" + firstCohort + "/candidates?withoutCohort=true", "", "clara");
        MvcTestResult forNew = send(HttpMethod.GET, base + "/cohort-candidates", "", "clara");

        assertThat(forFirst).hasStatusOk();
        assertThat(ids(forFirst, "$.items[*].id")).containsExactly(e2.toString(), e3.toString());
        assertThat(forFirst).bodyJson().extractingPath("$.items[0].cohort.id").isEqualTo(secondCohort.toString());
        assertThat(forFirst).bodyJson().extractingPath("$.items[0].cohort.name").isEqualTo("Second");
        assertThat(forFirst).bodyJson().extractingPath("$.items[1].cohort").isNull();
        assertThat(forFirst)
                .bodyJson()
                .extractingPath("$.items[0].student.name")
                .isEqualTo("Boris Bravo");
        assertThat(withoutCohort).hasStatusOk();
        assertThat(ids(withoutCohort, "$.items[*].id")).containsExactly(e3.toString());
        assertThat(forNew).hasStatusOk();
        assertThat(ids(forNew, "$.items[*].id")).containsExactly(e1.toString(), e2.toString(), e3.toString());
    }

    @Test
    @DisplayName("COH-6.2 The candidates are searched by name or email")
    void theCandidatesAreSearchedByNameOrEmail() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID first = enrollments.enroll(
                person("first@x.test", "Alice", "Smith", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        UUID second = enrollments.enroll(
                person("zorro@x.test", "Boris", "Jones", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        enrollments.enroll(
                person("third@x.test", "Clara", "Nolan", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        String path = "/api/v1/tracks/" + track + "/cohorts/" + cohort + "/candidates?q=";

        MvcTestResult byName = send(HttpMethod.GET, path + "lice", "", "admin");
        MvcTestResult byEmail = send(HttpMethod.GET, path + "ZORRO", "", "admin");

        assertThat(byName).hasStatusOk();
        assertThat(ids(byName, "$.items[*].id")).containsExactly(first.toString());
        assertThat(byEmail).hasStatusOk();
        assertThat(ids(byEmail, "$.items[*].id")).containsExactly(second.toString());
    }

    @Test
    @DisplayName("COH-7.1 Cohorts and their enrollments show in the track's and the student's history")
    void cohortsAndTheirEnrollmentsShowInTheTracksAndTheStudentsHistory() {
        UUID clara = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(clara, "clara");
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        String base = "/api/v1/tracks/" + track;
        MvcTestResult first =
                send(HttpMethod.POST, base + "/cohorts", newCohort("First", List.of(enrollment)), "clara");
        assertThat(first).hasStatus(HttpStatus.CREATED);
        String firstId = JsonPath.read(body(first), "$.id");
        MvcTestResult second =
                send(HttpMethod.POST, base + "/cohorts", newCohort("Second", List.of(enrollment)), "clara");
        assertThat(second).hasStatus(HttpStatus.CREATED);
        String secondId = JsonPath.read(body(second), "$.id");
        assertThat(send(HttpMethod.DELETE, base + "/cohorts/" + secondId + "/enrollments/" + enrollment, "", "clara"))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(send(HttpMethod.DELETE, base + "/cohorts/" + firstId, "", "clara"))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(send(HttpMethod.PUT, base, "{\"code\": \"QE\", \"name\": \"Quality Engineering\"}", "admin"))
                .hasStatusOk();

        MvcTestResult trackHistory = send(HttpMethod.GET, base + "/history", "", "admin");
        MvcTestResult userHistory = send(HttpMethod.GET, "/api/v1/users/" + student + "/history", "", "admin");

        assertThat(trackHistory).hasStatusOk();
        List<Map<String, Object>> created = entries(trackHistory, "TRACK_COHORT_CREATED");
        assertThat(created).hasSize(2);
        // Newest first: the second cohort, then the first.
        assertThat(inner(created.get(0), "cohort", "name")).isEqualTo("Second");
        assertThat(inner(created.get(1), "cohort", "name")).isEqualTo("First");
        assertThat(created).allSatisfy(entry -> {
            assertThat(entry).containsEntry("startDate", "2026-03-01");
            assertThat(inner(entry, "actor", "id")).isEqualTo(clara.toString());
        });
        List<Map<String, Object>> deleted = entries(trackHistory, "TRACK_COHORT_DELETED");
        assertThat(deleted).hasSize(1);
        assertThat(inner(deleted.get(0), "cohort", "name")).isEqualTo("First");
        assertThat(inner(deleted.get(0), "actor", "id")).isEqualTo(clara.toString());
        List<Map<String, Object>> changed = entries(trackHistory, "TRACK_ENROLLMENT_COHORT_CHANGED");
        assertThat(changed).hasSize(3);
        assertThat(changed).allSatisfy(entry -> {
            assertThat(inner(entry, "actor", "id")).isEqualTo(clara.toString());
            assertThat(inner(entry, "user", "id")).isEqualTo(student.toString());
        });
        assertChange(changed.get(0), "Second", null);
        assertChange(changed.get(1), "First", "Second");
        assertChange(changed.get(2), null, "First");

        assertThat(userHistory).hasStatusOk();
        List<Map<String, Object>> onUser = entries(userHistory, "USER_COHORT_CHANGED");
        assertThat(onUser).hasSize(3);
        assertChange(onUser.get(0), "Second", null);
        assertChange(onUser.get(1), "First", "Second");
        assertChange(onUser.get(2), null, "First");
        assertThat(onUser).allSatisfy(entry -> {
            assertThat(inner(entry, "actor", "id")).isEqualTo(clara.toString());
            assertThat(inner(entry, "track", "id")).isEqualTo(track.toString());
            assertThat(inner(entry, "track", "code")).isEqualTo("QA");
            assertThat(inner(entry, "track", "name")).isEqualTo("Quality Assurance");
        });
    }

    private static void assertChange(Map<String, Object> entry, String from, String to) {
        assertThat(inner(entry, "cohortChange", "from") == null ? null : innerName(entry, "from"))
                .isEqualTo(from);
        assertThat(inner(entry, "cohortChange", "to") == null ? null : innerName(entry, "to"))
                .isEqualTo(to);
    }

    @SuppressWarnings("unchecked")
    private static String innerName(Map<String, Object> entry, String side) {
        Map<String, Object> change = (Map<String, Object>) entry.get("cohortChange");
        return (String) ((Map<String, Object>) change.get(side)).get("name");
    }

    private UUID person(String email, String firstName, String lastName, UserStatus status, Role... roles) {
        UUID id = users.create(email, status, roles);
        users.rename(id, firstName, lastName);
        return id;
    }

    private static String newCohort(String name, List<UUID> enrollmentIds) {
        return "{\"name\": \"%s\", \"startDate\": \"2026-03-01\", \"enrollmentIds\": %s}"
                .formatted(name, idArray(enrollmentIds));
    }

    private static String addition(UUID... enrollmentIds) {
        return "{\"enrollmentIds\": %s}".formatted(idArray(List.of(enrollmentIds)));
    }

    private static String idArray(List<UUID> ids) {
        return ids.stream().map(id -> "\"" + id + "\"").toList().toString();
    }

    /** The id of the cohort the enrollment is in on the track, or null, read through the API. */
    private String cohortOf(UUID track, UUID enrollment) {
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollments?size=100", "", "admin");
        assertThat(list).hasStatusOk();
        List<Object> found = JsonPath.read(body(list), "$.items[?(@.id == '" + enrollment + "')].cohort.id");
        return found.isEmpty() ? null : (String) found.get(0);
    }

    private List<String> userActions(UUID user) {
        MvcTestResult history = send(HttpMethod.GET, "/api/v1/users/" + user + "/history", "", "admin");
        assertThat(history).hasStatusOk();
        return JsonPath.read(body(history), "$.items[*].action");
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

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    private static <T> List<T> ids(MvcTestResult result, String path) {
        return JsonPath.read(body(result), path);
    }

    private static List<Map<String, Object>> entries(MvcTestResult history, String action) {
        return JsonPath.read(body(history), "$.items[?(@.action == '%s')]".formatted(action));
    }

    @SuppressWarnings("unchecked")
    private static Object inner(Map<String, Object> entry, String object, String field) {
        Map<String, Object> inner = (Map<String, Object>) entry.get(object);
        return inner == null ? null : inner.get(field);
    }

    private static void assertNotFound(MvcTestResult result) {
        assertProblem(result, HttpStatus.NOT_FOUND, Problems.NOT_FOUND);
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
