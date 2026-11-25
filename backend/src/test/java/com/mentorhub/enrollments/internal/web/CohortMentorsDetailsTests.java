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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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

/** Details of the mentors of a cohort that no scenario of its own covers. */
@EndpointTest
class CohortMentorsDetailsTests {

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
    void aMalformedAssignmentIsRejectedAndStoresNothing() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID mentor = mentor("alice@x.test", "Alice");
        tracks.addMentor(track, mentor);
        String path = base(track, cohort) + "/mentors";

        for (String body : List.of(
                "{}", "{\"userIds\": null}", "{\"userIds\": [null]}", "{\"userIds\": [\"" + mentor + "\", null]}")) {
            assertProblem(
                    send(HttpMethod.POST, path, body, "admin"), HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        }
        assertThat(send(HttpMethod.POST, path, "{\"userIds\": [\"nope\"]}", "admin"))
                .hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(mentorsOf(cohort, track)).isEmpty();
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    void theStudentOfTheCohortAndTheCallerMayBeAssigned() {
        UUID mia = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR, Role.STUDENT);
        users.link(mia, "mia");
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        tracks.addMentor(track, mia);
        cohorts.put(enrollments.enroll(mia, track, admin, START), cohort);

        MvcTestResult assigned = send(HttpMethod.POST, base(track, cohort) + "/mentors", assignment(mia), "admin");

        assertThat(assigned).hasStatusOk();
        assertThat(mentorsOf(cohort, track)).containsExactly(mia.toString());
    }

    @Test
    void theCandidatesAreSearchedPagedAndOrderedWithWildcardsAsText() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID percent = mentor("100%@x.test", "Zed");
        UUID carol = mentor("carol@x.test", "carol");
        UUID bob = mentor("bob@x.test", "Bob");
        UUID dan = mentor("dan@x.test", "Dan");
        for (UUID mentor : List.of(percent, carol, bob, dan)) {
            tracks.addMentor(track, mentor);
        }
        String path = base(track, cohort) + "/mentor-candidates";

        MvcTestResult percentSearch = mvc.get()
                .uri(path + "?q={q}", "%")
                .header(HttpHeaders.AUTHORIZATION, issuer.token("admin").bearer())
                .exchange();
        MvcTestResult underscore = send(HttpMethod.GET, path + "?q=_", "", "admin");
        MvcTestResult byEmail = send(HttpMethod.GET, path + "?q=CAROL@", "", "admin");
        MvcTestResult firstPage = send(HttpMethod.GET, path + "?size=3", "", "admin");
        MvcTestResult secondPage = send(HttpMethod.GET, path + "?size=3&page=1", "", "admin");

        assertThat(read(percentSearch, "$.items[*].id")).containsExactly(percent.toString());
        assertThat(read(underscore, "$.items[*].id")).isEmpty();
        assertThat(read(byEmail, "$.items[*].id")).containsExactly(carol.toString());
        assertThat(read(firstPage, "$.items[*].id")).containsExactly(bob.toString(), carol.toString(), dan.toString());
        assertThat(firstPage).bodyJson().extractingPath("$.totalItems").isEqualTo(4);
        assertThat(read(secondPage, "$.items[*].id")).containsExactly(percent.toString());
    }

    @Test
    void theCohortsOfAnArchivedTrackStillShowTheirMentorsAndTheEnrollmentsTheirMark() {
        UUID track = tracks.archived("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID mentor = mentor("alice@x.test", "Alice");
        tracks.addMentor(track, mentor);
        cohorts.addMentor(cohort, mentor, admin);
        UUID enrollment = enrollments.enroll(student("s1@x.test", "One"), track, admin, START);
        cohorts.put(enrollment, cohort);

        assertThat(mentorsOf(cohort, track)).containsExactly(mentor.toString());
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/enrollments", "", "admin");
        assertThat(read(list, "$.items[0].mentors[?(@.throughCohort == true)].id"))
                .containsExactly(mentor.toString());
    }

    @Test
    void aMentorRemovedFromTheirLastCohortCanThenLeaveTheTrack() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID mentor = mentor("alice@x.test", "Alice");
        tracks.addMentor(track, mentor);
        cohorts.addMentor(cohort, mentor, admin);

        assertThat(send(HttpMethod.DELETE, base(track, cohort) + "/mentors/" + mentor, "", "admin"))
                .hasStatusOk();

        assertThat(send(HttpMethod.DELETE, "/api/v1/tracks/" + track + "/mentors/" + mentor, "", "admin"))
                .hasStatusOk();
    }

    @Test
    void deletingACohortFreesItsMentorsFromTheTrack() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID mentor = mentor("alice@x.test", "Alice");
        tracks.addMentor(track, mentor);
        cohorts.addMentor(cohort, mentor, admin);

        assertThat(send(HttpMethod.DELETE, base(track, cohort), "", "admin")).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(send(HttpMethod.DELETE, "/api/v1/tracks/" + track + "/mentors/" + mentor, "", "admin"))
                .hasStatusOk();
    }

    @Test
    void theNarrowingByMentorAndByCohortCombineThroughTheCohort() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID spring = cohorts.create(track, "Spring", START);
        UUID autumn = cohorts.create(track, "Autumn", START);
        UUID mentor = mentor("alice@x.test", "Alice");
        tracks.addMentor(track, mentor);
        cohorts.addMentor(spring, mentor, admin);
        UUID inSpring = enrollments.enroll(student("s1@x.test", "One"), track, admin, START);
        UUID inAutumn = enrollments.enroll(student("s2@x.test", "Two"), track, admin, START);
        cohorts.put(inSpring, spring);
        cohorts.put(inAutumn, autumn);
        String list = "/api/v1/tracks/" + track + "/enrollments?mentor=" + mentor;

        assertThat(read(send(HttpMethod.GET, list + "&cohortId=" + spring, "", "admin"), "$.items[*].id"))
                .containsExactly(inSpring.toString());
        assertThat(read(send(HttpMethod.GET, list + "&cohortId=" + autumn, "", "admin"), "$.items[*].id"))
                .isEmpty();
    }

    @Test
    void aMentorLeadingByManyPathsIsCountedOncePerStudentAndPaged() {
        UUID mia = mentor("mia@x.test", "Mia");
        users.link(mia, "mia");
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID spring = cohorts.create(track, "Spring", START);
        UUID autumn = cohorts.create(track, "Autumn", START);
        tracks.addMentor(track, mia);
        cohorts.addMentor(spring, mia, admin);
        cohorts.addMentor(autumn, mia, admin);
        UUID e1 = enrollments.enroll(student("s1@x.test", "One"), track, admin, START);
        UUID e2 = enrollments.enroll(student("s2@x.test", "Two"), track, admin, START);
        UUID e3 = enrollments.enroll(student("s3@x.test", "Three"), track, admin, START);
        cohorts.put(e1, spring);
        cohorts.put(e2, autumn);
        cohorts.put(e3, spring);
        enrollments.addMentor(e1, mia, admin);
        enrollments.addMentor(e2, mia, admin);

        MvcTestResult first = send(HttpMethod.GET, "/api/v1/me/students?size=2", "", "mia");
        MvcTestResult second = send(HttpMethod.GET, "/api/v1/me/students?size=2&page=1", "", "mia");

        assertThat(first).bodyJson().extractingPath("$.totalItems").isEqualTo(3);
        assertThat(read(first, "$.items[*].id")).containsExactly(e1.toString(), e3.toString());
        assertThat(read(second, "$.items[*].id")).containsExactly(e2.toString());
    }

    private String base(UUID track, UUID cohort) {
        return "/api/v1/tracks/" + track + "/cohorts/" + cohort;
    }

    private List<String> mentorsOf(UUID cohort, UUID track) {
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts", "", "admin");
        assertThat(list).hasStatusOk();
        return read(list, "$.items[?(@.id == '" + cohort + "')].mentors[*].id");
    }

    private UUID student(String email, String lastName) {
        return person(email, "Sam", lastName, UserStatus.ACTIVE, Role.STUDENT);
    }

    private UUID mentor(String email, String firstName) {
        return person(email, firstName, "Mentor", UserStatus.ACTIVE, Role.MENTOR);
    }

    private UUID person(String email, String firstName, String lastName, UserStatus status, Role... roles) {
        UUID id = users.create(email, status, roles);
        users.rename(id, firstName, lastName);
        return id;
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

    private static <T> List<T> read(MvcTestResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
    }
}
