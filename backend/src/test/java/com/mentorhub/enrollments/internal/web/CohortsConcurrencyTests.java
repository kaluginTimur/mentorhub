package com.mentorhub.enrollments.internal.web;

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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Parallel changes of cohorts through the real chain: never a server error, never an enrollment in a lost cohort. */
@EndpointTest
class CohortsConcurrencyTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    TestUsers users;
    UUID student;
    UUID track;
    UUID enrollment;
    UUID firstCohort;
    UUID secondCohort;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        UUID anna = users.create("anna@x.test", UserStatus.ACTIVE, Role.ADMIN);
        users.link(anna, "anna");
        users.link(users.create("boris@x.test", UserStatus.ACTIVE, Role.ADMIN), "boris");
        student = users.create("sam@x.test", UserStatus.ACTIVE, Role.STUDENT);
        track = new TestTracks(jdbc).active("QA", "Quality Assurance");
        enrollment = new TestEnrollments(jdbc).enroll(student, track, anna, LocalDate.of(2026, 9, 1));
        TestCohorts cohorts = new TestCohorts(jdbc);
        firstCohort = cohorts.create(track, "First", LocalDate.of(2026, 9, 1));
        secondCohort = cohorts.create(track, "Second", LocalDate.of(2026, 9, 1));
    }

    @RepeatedTest(5)
    @DisplayName("COH-3.3 Parallel additions of one enrollment to two cohorts leave it in one")
    void parallelAdditionsOfOneEnrollmentToTwoCohortsLeaveItInOne() throws Exception {
        List<MvcTestResult> results = inParallel(() -> add(firstCohort, "anna"), () -> add(secondCohort, "boris"));

        results.forEach(result -> assertThat(result.getResponse().getStatus()).isLessThan(500));
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts", "", "anna");
        assertThat(list).hasStatusOk();
        assertThat(JsonPath.<List<Integer>>read(body(list), "$.items[*].enrollmentCount"))
                .containsExactlyInAnyOrder(0, 1);
        String holder = JsonPath.<List<String>>read(body(list), "$.items[?(@.enrollmentCount == 1)].id")
                .get(0);
        MvcTestResult history = send(HttpMethod.GET, "/api/v1/users/" + student + "/history", "", "anna");
        assertThat(history).hasStatusOk();
        List<Map<String, Object>> changes =
                JsonPath.read(body(history), "$.items[?(@.action == 'USER_COHORT_CHANGED')]");
        assertThat(changes).hasSize(2);
        // Newest first: the newest change ends in the cohort that holds the enrollment, the oldest starts from none,
        // and the newest starts where the oldest ended.
        Map<String, Object> newest = change(changes.get(0));
        Map<String, Object> oldest = change(changes.get(1));
        assertThat(oldest.get("from")).isNull();
        assertThat(id(newest.get("to"))).isEqualTo(holder);
        assertThat(id(newest.get("from"))).isEqualTo(id(oldest.get("to")));
    }

    @RepeatedTest(5)
    @DisplayName("COH-4.2 A deletion at the moment of an addition never leaves an enrollment in a deleted cohort")
    void aDeletionAtTheMomentOfAnAdditionNeverLeavesAnEnrollmentInADeletedCohort() throws Exception {
        List<MvcTestResult> results = inParallel(
                () -> send(HttpMethod.DELETE, "/api/v1/tracks/" + track + "/cohorts/" + firstCohort, "", "anna"),
                () -> add(firstCohort, "boris"));

        MvcTestResult deletion = results.get(0);
        MvcTestResult addition = results.get(1);
        assertThat(deletion.getResponse().getStatus()).isLessThan(500);
        assertThat(addition.getResponse().getStatus()).isLessThan(500);
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts", "", "anna");
        if (deletion.getResponse().getStatus() == 204) {
            assertThat(addition.getResponse().getStatus()).isEqualTo(404);
            assertThat(JsonPath.<List<String>>read(body(list), "$.items[*].id"))
                    .containsExactly(secondCohort.toString());
        } else {
            assertThat(deletion.getResponse().getStatus()).isEqualTo(409);
            assertThat(deletion)
                    .bodyJson()
                    .extractingPath("$.type")
                    .isEqualTo("urn:mentorhub:problem:cohort-not-empty");
            assertThat(addition.getResponse().getStatus()).isEqualTo(200);
            assertThat(JsonPath.<List<Integer>>read(
                            body(list), "$.items[?(@.id == '" + firstCohort + "')].enrollmentCount"))
                    .containsExactly(1);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> change(Map<String, Object> entry) {
        return (Map<String, Object>) entry.get("cohortChange");
    }

    @SuppressWarnings("unchecked")
    private static Object id(Object reference) {
        return reference == null ? null : ((Map<String, Object>) reference).get("id");
    }

    private MvcTestResult add(UUID cohort, String caller) {
        return send(
                HttpMethod.POST,
                "/api/v1/tracks/" + track + "/cohorts/" + cohort + "/enrollments",
                "{\"enrollmentIds\": [\"%s\"]}".formatted(enrollment),
                caller);
    }

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
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

    @SafeVarargs
    private static List<MvcTestResult> inParallel(Callable<MvcTestResult>... requests) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcTestResult>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(requests.length)) {
            for (Callable<MvcTestResult> request : requests) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return request.call();
                }));
            }
            start.countDown();
            List<MvcTestResult> results = new ArrayList<>();
            for (Future<MvcTestResult> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }
}
