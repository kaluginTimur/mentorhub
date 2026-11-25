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

/** The mentors of a cohort through the real chain: tokens, the gate, permissions, services and the database. */
@EndpointTest
class CohortMentorsEndpointTests {

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
    @DisplayName("COH-4.3 A cohort with mentors and no enrollment is deleted with its mentors")
    void aCohortWithMentorsAndNoEnrollmentIsDeletedWithItsMentors() {
        UUID clara = coordinator("clara@x.test", "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID first = mentor("alice@x.test", "Alice", "First");
        UUID second = mentor("boris@x.test", "Boris", "Second");
        tracks.addMentor(track, first);
        tracks.addMentor(track, second);
        cohorts.addMentor(cohort, first, admin);
        cohorts.addMentor(cohort, second, admin);

        MvcTestResult deleted = send(HttpMethod.DELETE, "/api/v1/tracks/" + track + "/cohorts/" + cohort, "", "clara");

        assertThat(deleted).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(ids(send(HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts", "", "clara"), "$.items[*].id"))
                .isEmpty();
        MvcTestResult onTrack = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/history", "", "admin");
        assertThat(entries(onTrack, "TRACK_COHORT_DELETED")).hasSize(1);
        List<Map<String, Object>> removed = entries(onTrack, "TRACK_COHORT_MENTOR_REMOVED");
        assertThat(removed).hasSize(2);
        assertThat(removed)
                .extracting(entry -> inner(entry, "mentor", "id"))
                .containsExactlyInAnyOrder(first.toString(), second.toString());
        assertThat(removed).allSatisfy(entry -> {
            assertThat(inner(entry, "actor", "id")).isEqualTo(clara.toString());
            assertThat(inner(entry, "cohort", "name")).isEqualTo("Spring");
        });
        for (UUID mentor : List.of(first, second)) {
            MvcTestResult onMentor = send(HttpMethod.GET, "/api/v1/users/" + mentor + "/history", "", "admin");
            assertThat(entries(onMentor, "USER_COHORT_MENTOR_REMOVED")).hasSize(1);
        }
    }

    @Test
    @DisplayName("COH-5.2 The mentors of a cohort are listed with their marks")
    void theMentorsOfACohortAreListedWithTheirMarks() {
        UUID elsewhere = person("eva@x.test", "Eva", "Elsewhere", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(elsewhere, "eva");
        tracks.addCoordinator(tracks.active("SWE", "Software Engineering"), elsewhere);
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID active = person("alice@x.test", "Alice", "Active", UserStatus.ACTIVE, Role.MENTOR);
        UUID disabled = person("boris@x.test", "Boris", "Disabled", UserStatus.DISABLED, Role.MENTOR);
        UUID demoted = person("cora@x.test", "Cora", "Demoted", UserStatus.ACTIVE, Role.COORDINATOR);
        // Assigned in reverse order, so the order of the answer is the sorting's.
        for (UUID mentor : List.of(demoted, disabled, active)) {
            tracks.addMentor(track, mentor);
            cohorts.addMentor(cohort, mentor, admin);
        }

        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts", "", "eva");

        assertThat(list).hasStatusOk();
        assertThat(ids(list, "$.items[0].mentors[*].id"))
                .containsExactly(active.toString(), disabled.toString(), demoted.toString());
        assertThat(ids(list, "$.items[0].mentors[?(@.disabled == true)].id")).containsExactly(disabled.toString());
        assertThat(ids(list, "$.items[0].mentors[?(@.mentorRole == false)].id")).containsExactly(demoted.toString());
        assertThat(list).bodyJson().extractingPath("$.items[0].mentors[0].name").isEqualTo("Alice Active");
        assertThat(list)
                .bodyJson()
                .extractingPath("$.items[0].mentors[0].email")
                .isEqualTo("alice@x.test");
    }

    @Test
    @DisplayName("COH-7.2 Assigning and removing a mentor of a cohort show in two histories")
    void assigningAndRemovingAMentorOfACohortShowInTwoHistories() {
        UUID clara = coordinator("clara@x.test", "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        cohorts.put(enrollments.enroll(student, track, admin, START), cohort);
        UUID mentor = mentor("mia@x.test", "Mia", "Mentor");
        tracks.addMentor(track, mentor);
        String base = "/api/v1/tracks/" + track + "/cohorts/" + cohort + "/mentors";
        assertThat(send(HttpMethod.POST, base, assignment(mentor), "clara")).hasStatusOk();
        assertThat(send(HttpMethod.DELETE, base + "/" + mentor, "", "clara")).hasStatusOk();
        assertThat(send(
                        HttpMethod.PUT,
                        "/api/v1/tracks/" + track,
                        "{\"code\": \"QE\", \"name\": \"Quality Engineering\"}",
                        "admin"))
                .hasStatusOk();

        MvcTestResult onTrack = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/history", "", "admin");
        MvcTestResult onMentor = send(HttpMethod.GET, "/api/v1/users/" + mentor + "/history", "", "admin");
        MvcTestResult onStudent = send(HttpMethod.GET, "/api/v1/users/" + student + "/history", "", "admin");

        for (String action : List.of("TRACK_COHORT_MENTOR_ASSIGNED", "TRACK_COHORT_MENTOR_REMOVED")) {
            List<Map<String, Object>> found = entries(onTrack, action);
            assertThat(found).hasSize(1);
            assertThat(inner(found.get(0), "actor", "id")).isEqualTo(clara.toString());
            assertThat(inner(found.get(0), "cohort", "name")).isEqualTo("Spring");
            assertThat(inner(found.get(0), "mentor", "id")).isEqualTo(mentor.toString());
        }
        for (String action : List.of("USER_COHORT_MENTOR_ASSIGNED", "USER_COHORT_MENTOR_REMOVED")) {
            List<Map<String, Object>> found = entries(onMentor, action);
            assertThat(found).hasSize(1);
            assertThat(inner(found.get(0), "actor", "id")).isEqualTo(clara.toString());
            assertThat(inner(found.get(0), "cohort", "name")).isEqualTo("Spring");
            assertThat(inner(found.get(0), "track", "id")).isEqualTo(track.toString());
            assertThat(inner(found.get(0), "track", "code")).isEqualTo("QA");
            assertThat(inner(found.get(0), "track", "name")).isEqualTo("Quality Assurance");
        }
        assertThat(ids(onStudent, "$.items[*].action")).isEmpty();
    }

    @Test
    @DisplayName("COH-9.1 A coordinator assigns and removes the mentors of a cohort on their track")
    void aCoordinatorAssignsAndRemovesTheMentorsOfACohortOnTheirTrack() {
        UUID clara = coordinator("clara@x.test", "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID first = mentor("alice@x.test", "Alice", "First");
        UUID second = mentor("boris@x.test", "Boris", "Second");
        UUID former = person("fred@x.test", "Fred", "Former", UserStatus.DISABLED, Role.MENTOR);
        for (UUID mentor : List.of(first, second, former)) {
            tracks.addMentor(track, mentor);
        }
        cohorts.addMentor(cohort, former, admin);
        String base = "/api/v1/tracks/" + track + "/cohorts/" + cohort + "/mentors";

        MvcTestResult assigned = send(HttpMethod.POST, base, assignment(first, second), "clara");
        MvcTestResult removed = send(HttpMethod.DELETE, base + "/" + former, "", "clara");

        assertThat(assigned).hasStatusOk();
        assertThat(removed).hasStatusOk();
        assertThat(ids(removed, "$.mentors[*].id")).containsExactly(first.toString(), second.toString());
        assertThat(cohortMentors(track, cohort)).containsExactly(first.toString(), second.toString());
    }

    @Test
    @DisplayName("COH-9.2 Only an active mentor of the track with the mentor role is assigned")
    void onlyAnActiveMentorOfTheTrackWithTheMentorRoleIsAssigned() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID disabled = person("dina@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.MENTOR);
        UUID withoutRole = person("cora@x.test", "Cora", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        UUID elsewhere = mentor("eve@x.test", "Eve", "Elsewhere");
        tracks.addMentor(track, disabled);
        tracks.addMentor(track, withoutRole);
        tracks.addMentor(tracks.active("SWE", "Software Engineering"), elsewhere);

        for (UUID user : List.of(disabled, withoutRole, elsewhere, UUID.randomUUID())) {
            assertProblem(
                    send(
                            HttpMethod.POST,
                            "/api/v1/tracks/" + track + "/cohorts/" + cohort + "/mentors",
                            assignment(user),
                            "admin"),
                    HttpStatus.CONFLICT,
                    MENTOR_NOT_ELIGIBLE);
        }

        assertThat(cohortMentors(track, cohort)).isEmpty();
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("COH-9.3 A repeated assignment or removal records nothing")
    void aRepeatedAssignmentOrRemovalRecordsNothing() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID active = mentor("alice@x.test", "Alice", "Active");
        UUID demoted = person("clara@x.test", "Clara", "Demoted", UserStatus.ACTIVE, Role.COORDINATOR);
        UUID bystander = mentor("bob@x.test", "Bob", "Bystander");
        UUID fresh = mentor("nina@x.test", "Nina", "New");
        for (UUID mentor : List.of(active, demoted, bystander, fresh)) {
            tracks.addMentor(track, mentor);
        }
        cohorts.addMentor(cohort, active, admin);
        cohorts.addMentor(cohort, demoted, admin);
        String base = "/api/v1/tracks/" + track + "/cohorts/" + cohort + "/mentors";

        assertThat(send(HttpMethod.POST, base, assignment(active), "admin")).hasStatusOk();
        assertThat(send(HttpMethod.POST, base, assignment(demoted), "admin")).hasStatusOk();
        assertThat(send(HttpMethod.DELETE, base + "/" + bystander, "", "admin")).hasStatusOk();
        assertThat(send(HttpMethod.DELETE, base + "/" + UUID.randomUUID(), "", "admin"))
                .hasStatusOk();
        assertThat(users.count("audit_log")).isZero();
        assertThat(send(HttpMethod.POST, base, assignment(active, fresh, fresh), "admin"))
                .hasStatusOk();

        assertThat(cohortMentors(track, cohort))
                .containsExactlyInAnyOrder(active.toString(), demoted.toString(), fresh.toString());
        MvcTestResult onTrack = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/history", "", "admin");
        assertThat(ids(onTrack, "$.items[*].action")).containsExactly("TRACK_COHORT_MENTOR_ASSIGNED");
        assertThat(entries(onTrack, "TRACK_COHORT_MENTOR_ASSIGNED").get(0))
                .satisfies(entry -> assertThat(inner(entry, "mentor", "id")).isEqualTo(fresh.toString()));
        MvcTestResult onFresh = send(HttpMethod.GET, "/api/v1/users/" + fresh + "/history", "", "admin");
        assertThat(ids(onFresh, "$.items[*].action")).containsExactly("USER_COHORT_MENTOR_ASSIGNED");
        MvcTestResult onActive = send(HttpMethod.GET, "/api/v1/users/" + active + "/history", "", "admin");
        assertThat(ids(onActive, "$.items[*].action")).isEmpty();
    }

    @Test
    @DisplayName("COH-9.5 Assigning several mentors is all or nothing")
    void assigningSeveralMentorsIsAllOrNothing() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID eligible = mentor("alice@x.test", "Alice", "Eligible");
        UUID outside = mentor("omar@x.test", "Omar", "Outside");
        tracks.addMentor(track, eligible);
        String base = "/api/v1/tracks/" + track + "/cohorts/" + cohort + "/mentors";

        MvcTestResult mixed = send(HttpMethod.POST, base, assignment(eligible, outside), "admin");
        MvcTestResult none = send(HttpMethod.POST, base, assignment(), "admin");

        assertProblem(mixed, HttpStatus.CONFLICT, MENTOR_NOT_ELIGIBLE);
        assertProblem(none, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(cohortMentors(track, cohort)).isEmpty();
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("COH-10.1 The candidates are the eligible mentors of the track not yet assigned")
    void theCandidatesAreTheEligibleMentorsOfTheTrackNotYetAssigned() {
        UUID clara = coordinator("clara@x.test", "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID assigned = mentor("zoe@x.test", "Zoe", "Assigned");
        UUID other = mentor("alice@x.test", "Alice", "Other");
        UUID ofEnrollment = mentor("boris@x.test", "Boris", "Enrollment");
        UUID disabled = person("dina@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.MENTOR);
        UUID withoutRole = person("cora@x.test", "Cora", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        mentor("eve@x.test", "Eve", "Elsewhere");
        for (UUID mentor : List.of(assigned, other, ofEnrollment, disabled, withoutRole)) {
            tracks.addMentor(track, mentor);
        }
        cohorts.addMentor(cohort, assigned, admin);
        UUID enrollment = enrollments.enroll(
                person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT), track, admin, START);
        cohorts.put(enrollment, cohort);
        enrollments.addMentor(enrollment, ofEnrollment, admin);

        MvcTestResult result = send(
                HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts/" + cohort + "/mentor-candidates", "", "clara");

        assertThat(result).hasStatusOk();
        assertThat(ids(result, "$.items[*].id")).containsExactly(other.toString(), ofEnrollment.toString());
        assertThat(result).bodyJson().extractingPath("$.items[0].name").isEqualTo("Alice Other");
        assertThat(result).bodyJson().extractingPath("$.items[0].email").isEqualTo("alice@x.test");
        assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(2);
    }

    @Test
    @DisplayName("COH-10.2 The candidates have the students and the cohorts they lead on the track")
    void theCandidatesHaveTheStudentsAndTheCohortsTheyLeadOnTheTrack() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID elsewhere = tracks.active("SWE", "Software Engineering");
        UUID target = cohorts.create(track, "Target", START);
        UUID busyCohort = cohorts.create(track, "Busy", START);
        UUID foreignCohort = cohorts.create(elsewhere, "Foreign", START);
        UUID busy = mentor("alice@x.test", "Alice", "Busy");
        UUID idle = mentor("boris@x.test", "Boris", "Idle");
        for (UUID mentor : List.of(busy, idle)) {
            tracks.addMentor(track, mentor);
        }
        tracks.addMentor(elsewhere, busy);
        UUID first = enrollments.enroll(student("s1@x.test", "One"), track, admin, START);
        UUID second = enrollments.enroll(student("s2@x.test", "Two"), track, admin, START);
        UUID free = enrollments.enroll(student("s3@x.test", "Three"), track, admin, START);
        cohorts.put(first, busyCohort);
        cohorts.put(second, busyCohort);
        cohorts.addMentor(busyCohort, busy, admin);
        enrollments.addMentor(first, busy, admin);
        enrollments.addMentor(free, busy, admin);
        UUID foreign = enrollments.enroll(student("s4@x.test", "Four"), elsewhere, admin, START);
        enrollments.addMentor(foreign, busy, admin);
        cohorts.addMentor(foreignCohort, busy, admin);

        MvcTestResult result = send(
                HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts/" + target + "/mentor-candidates", "", "admin");

        assertThat(result).hasStatusOk();
        assertThat(ids(result, "$.items[*].id")).containsExactly(busy.toString(), idle.toString());
        assertThat(ids(result, "$.items[*].students")).containsExactly(3, 0);
        assertThat(ids(result, "$.items[*].cohorts")).containsExactly(1, 0);
    }

    private UUID student(String email, String lastName) {
        return person(email, "Sam", lastName, UserStatus.ACTIVE, Role.STUDENT);
    }

    private UUID mentor(String email, String firstName, String lastName) {
        return person(email, firstName, lastName, UserStatus.ACTIVE, Role.MENTOR);
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

    private List<String> cohortMentors(UUID track, UUID cohort) {
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts", "", "admin");
        assertThat(list).hasStatusOk();
        return ids(list, "$.items[?(@.id == '" + cohort + "')].mentors[*].id");
    }

    private static String assignment(UUID... userIds) {
        return "{\"userIds\": %s}"
                .formatted(java.util.Arrays.stream(userIds)
                        .map(id -> "\"" + id + "\"")
                        .toList());
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

    private static <T> List<T> ids(MvcTestResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
    }

    private static List<Map<String, Object>> entries(MvcTestResult history, String action) {
        return ids(history, "$.items[?(@.action == '%s')]".formatted(action));
    }

    @SuppressWarnings("unchecked")
    private static Object inner(Map<String, Object> entry, String object, String field) {
        Map<String, Object> inner = (Map<String, Object>) entry.get(object);
        return inner == null ? null : inner.get(field);
    }
}
