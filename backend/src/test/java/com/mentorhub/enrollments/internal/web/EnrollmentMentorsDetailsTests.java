package com.mentorhub.enrollments.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.enrollments.internal.TestEnrollments;
import com.mentorhub.support.EndpointTest;
import com.mentorhub.tracks.internal.TestTracks;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.web.Problems;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Details of the mentors of an enrollment that no scenario fixes: order, ties, paging, search, order of checks. */
@EndpointTest
class EnrollmentMentorsDetailsTests {

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
    UUID track;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        tracks = new TestTracks(jdbc);
        enrollments = new TestEnrollments(jdbc);
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "admin");
        track = tracks.active("QA", "Quality Assurance");
    }

    @Test
    void candidatesAreSortedByNameIgnoringCaseThenEmailAndPaged() {
        UUID student = person("sam@x.test", "Sam", "Student", Role.STUDENT);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        UUID bella = person("bella@x.test", "Bella", "Aa", Role.MENTOR);
        UUID second = person("a2@x.test", "Anna", "Zed", Role.MENTOR);
        UUID first = person("a1@x.test", "anna", "zed", Role.MENTOR);

        String path = "/api/v1/enrollments/" + enrollment + "/mentor-candidates";
        MvcTestResult page0 = get(path + "?size=2&page=0", "admin");
        MvcTestResult page1 = get(path + "?size=2&page=1", "admin");

        assertThat(page0)
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly(first.toString(), second.toString());
        assertThat(page1).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(bella.toString());
        assertThat(page1).bodyJson().extractingPath("$.totalItems").isEqualTo(3);
    }

    @Test
    void candidateSearchIsCaseInsensitiveOnEmailAndTakesWildcardsLiterally() {
        UUID student = person("sam@x.test", "Sam", "Student", Role.STUDENT);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        UUID bella = person("bella@x.test", "Bella", "Mentor", Role.MENTOR);
        person("other@x.test", "Other", "Mentor", Role.MENTOR);
        String path = "/api/v1/enrollments/" + enrollment + "/mentor-candidates";

        assertThat(get(path + "?q=BELLA@", "admin"))
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly(bella.toString());
        assertThat(get(path + "?q=%25", "admin"))
                .bodyJson()
                .extractingPath("$.totalItems")
                .isEqualTo(0);
        assertThat(get(path + "?q=_ella", "admin"))
                .bodyJson()
                .extractingPath("$.totalItems")
                .isEqualTo(0);
    }

    @Test
    void candidatesChecksRunInTheOrderOfTheContract() {
        UUID student = person("sam@x.test", "Sam", "Student", Role.STUDENT);
        users.link(student, "sam");
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        UUID elsewhere = person("elsa@x.test", "Elsa", "Else", Role.COORDINATOR);
        users.link(elsewhere, "elsa");
        tracks.addCoordinator(tracks.active("SWE", "Software"), elsewhere);
        String bad = "/mentor-candidates?size=0";

        assertProblem(get("/api/v1/enrollments/" + enrollment + bad, "sam"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
        assertThat(get("/api/v1/enrollments/" + enrollment + bad, "admin")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/v1/enrollments/" + UUID.randomUUID() + bad, "admin"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(get("/api/v1/enrollments/" + enrollment + bad, "elsa")).hasStatus(HttpStatus.BAD_REQUEST);
        assertProblem(
                get("/api/v1/enrollments/" + enrollment + "/mentor-candidates", "elsa"),
                HttpStatus.FORBIDDEN,
                Problems.FORBIDDEN);
    }

    @Test
    void myStudentsAreSortedByNameIgnoringCaseThenEmailAndPaged() {
        UUID mentor = person("mia@x.test", "Mia", "Mentor", Role.MENTOR);
        users.link(mentor, "mia");
        UUID s2 = person("s2@x.test", "Sam", "Same", Role.STUDENT);
        UUID s1 = person("s1@x.test", "sam", "same", Role.STUDENT);
        UUID early = person("zz@x.test", "adam", "Early", Role.STUDENT);
        UUID other = tracks.active("SWE", "Software");
        UUID e2 = enrollments.enroll(s2, track, admin, START);
        UUID e1 = enrollments.enroll(s1, other, admin, START);
        UUID e0 = enrollments.enroll(early, track, admin, START);
        for (UUID e : List.of(e2, e1, e0)) {
            enrollments.addMentor(e, mentor, admin);
        }

        MvcTestResult page0 = get("/api/v1/me/students?size=2&page=0", "mia");
        MvcTestResult page1 = get("/api/v1/me/students?size=2&page=1", "mia");

        assertThat(page0)
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly(e0.toString(), e1.toString());
        assertThat(page1).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(e2.toString());
        assertThat(page1).bodyJson().extractingPath("$.totalItems").isEqualTo(3);
    }

    @Test
    void aMentorWithNoStudentsGetsAnEmptyPageAndACoordinatorGetsNothing() {
        UUID mentor = person("mia@x.test", "Mia", "Mentor", Role.MENTOR);
        users.link(mentor, "mia");
        UUID coordinator = person("clara@x.test", "Clara", "Coord", Role.COORDINATOR);
        users.link(coordinator, "clara");

        MvcTestResult empty = get("/api/v1/me/students", "mia");

        assertThat(empty).hasStatusOk();
        assertThat(empty).bodyJson().extractingPath("$.totalItems").isEqualTo(0);
        assertProblem(get("/api/v1/me/students", "clara"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
    }

    @Test
    void mentorsOfAnEnrollmentAreSortedByNameIgnoringCaseThenEmail() {
        UUID student = person("sam@x.test", "Sam", "Student", Role.STUDENT);
        users.link(student, "sam");
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        UUID zoe = person("zoe@x.test", "Zoe", "Z", Role.MENTOR);
        UUID b2 = person("b2@x.test", "bob", "b", Role.MENTOR);
        UUID b1 = person("b1@x.test", "Bob", "B", Role.MENTOR);
        UUID amy = person("amy@x.test", "Amy", "A", Role.MENTOR);
        for (UUID m : List.of(zoe, b2, b1, amy)) {
            enrollments.addMentor(enrollment, m, admin);
        }

        assertThat(get("/api/v1/tracks/" + track + "/enrollments", "admin"))
                .bodyJson()
                .extractingPath("$.items[0].mentors[*].id")
                .asArray()
                .containsExactly(amy.toString(), b1.toString(), b2.toString(), zoe.toString());
        assertThat(get("/api/v1/me/enrollments", "sam"))
                .bodyJson()
                .extractingPath("$.items[0].mentors[*].email")
                .asArray()
                .containsExactly("amy@x.test", "b1@x.test", "b2@x.test", "zoe@x.test");
    }

    @Test
    void aMentorIsNotAbleToAssignAnyoneByTheMentorRoleAlone() {
        UUID student = person("sam@x.test", "Sam", "Student", Role.STUDENT);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        UUID mentor = person("mia@x.test", "Mia", "Mentor", Role.MENTOR);
        users.link(mentor, "mia");
        enrollments.addMentor(enrollment, mentor, admin);

        assertProblem(
                send(HttpMethod.PUT, "/api/v1/enrollments/" + enrollment + "/mentors/" + mentor, "mia"),
                HttpStatus.FORBIDDEN,
                Problems.FORBIDDEN);
        assertProblem(
                send(HttpMethod.DELETE, "/api/v1/enrollments/" + enrollment + "/mentors/" + mentor, "mia"),
                HttpStatus.FORBIDDEN,
                Problems.FORBIDDEN);
    }

    @Test
    void parallelRemovalsOfOneMentorRecordOneEntryAndNeverFail() throws Exception {
        UUID second = person("boris@x.test", "Boris", "Admin", Role.ADMIN);
        users.link(second, "boris");
        UUID student = person("sam@x.test", "Sam", "Student", Role.STUDENT);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        UUID mentor = person("mia@x.test", "Mia", "Mentor", Role.MENTOR);
        enrollments.addMentor(enrollment, mentor, admin);
        String path = "/api/v1/enrollments/" + enrollment + "/mentors/" + mentor;

        CountDownLatch go = new CountDownLatch(1);
        List<Future<MvcTestResult>> futures;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<MvcTestResult> a = () -> {
                go.await();
                return send(HttpMethod.DELETE, path, "admin");
            };
            Callable<MvcTestResult> b = () -> {
                go.await();
                return send(HttpMethod.DELETE, path, "boris");
            };
            futures = List.of(pool.submit(a), pool.submit(b));
            go.countDown();
            for (Future<MvcTestResult> f : futures) {
                assertThat(f.get().getResponse().getStatus()).isEqualTo(200);
            }
        }

        assertThat(get("/api/v1/tracks/" + track + "/enrollments", "admin"))
                .bodyJson()
                .extractingPath("$.items[0].mentors")
                .asArray()
                .isEmpty();
        assertThat(get("/api/v1/users/" + mentor + "/history", "admin"))
                .bodyJson()
                .extractingPath("$.items[?(@.action == 'USER_STUDENT_REMOVED')]")
                .asArray()
                .hasSize(1);
    }

    private UUID person(String email, String first, String last, Role role) {
        UUID id = users.create(email, UserStatus.ACTIVE, role);
        users.rename(id, first, last);
        return id;
    }

    private MvcTestResult get(String uri, String subject) {
        return send(HttpMethod.GET, uri, subject);
    }

    private MvcTestResult send(HttpMethod method, String uri, String subject) {
        return mvc.method(method)
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, issuer.token(subject).bearer())
                .exchange();
    }
}
