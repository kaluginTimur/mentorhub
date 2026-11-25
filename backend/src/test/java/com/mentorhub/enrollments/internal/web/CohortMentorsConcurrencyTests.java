package com.mentorhub.enrollments.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.mentorhub.enrollments.internal.TestCohorts;
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

/** Parallel changes of the mentors of a cohort through the real chain: never a server error, never a stray mentor. */
@EndpointTest
class CohortMentorsConcurrencyTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    TestUsers users;
    TestTracks tracks;
    UUID track;
    UUID cohort;
    UUID first;
    UUID second;
    String cohortPath;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        users.link(users.create("anna@x.test", UserStatus.ACTIVE, Role.ADMIN), "anna");
        users.link(users.create("boris@x.test", UserStatus.ACTIVE, Role.ADMIN), "boris");
        tracks = new TestTracks(jdbc);
        track = tracks.active("QA", "Quality Assurance");
        cohort = new TestCohorts(jdbc).create(track, "Spring", LocalDate.of(2026, 9, 1));
        first = users.create("mia@x.test", UserStatus.ACTIVE, Role.MENTOR);
        second = users.create("max@x.test", UserStatus.ACTIVE, Role.MENTOR);
        tracks.addMentor(track, first);
        tracks.addMentor(track, second);
        cohortPath = "/api/v1/tracks/" + track + "/cohorts/" + cohort;
    }

    @RepeatedTest(5)
    @DisplayName("COH-9.4 Parallel assignments of one mentor assign them once")
    void parallelAssignmentsOfOneMentorAssignThemOnce() throws Exception {
        List<MvcTestResult> results = inParallel(
                () -> send(HttpMethod.POST, cohortPath + "/mentors", assignment(first), "anna"),
                () -> send(HttpMethod.POST, cohortPath + "/mentors", assignment(first, second), "boris"));

        for (MvcTestResult result : results) {
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
        }
        assertThat(cohortMentors()).containsExactlyInAnyOrder(first.toString(), second.toString());
        assertThat(actions("/api/v1/tracks/" + track + "/history"))
                .containsExactly("TRACK_COHORT_MENTOR_ASSIGNED", "TRACK_COHORT_MENTOR_ASSIGNED");
        assertThat(actions("/api/v1/users/" + first + "/history")).containsExactly("USER_COHORT_MENTOR_ASSIGNED");
        assertThat(actions("/api/v1/users/" + second + "/history")).containsExactly("USER_COHORT_MENTOR_ASSIGNED");
    }

    @RepeatedTest(10)
    @DisplayName("COH-4.4 A deletion at the moment of an assignment of a mentor never leaves a mentor of a deleted"
            + " cohort")
    void aDeletionAtTheMomentOfAnAssignmentNeverLeavesAMentorOfADeletedCohort() throws Exception {
        List<MvcTestResult> results = inParallel(
                () -> send(HttpMethod.DELETE, cohortPath, "", "anna"),
                () -> send(HttpMethod.POST, cohortPath + "/mentors", assignment(first), "boris"));

        MvcTestResult deletion = results.get(0);
        MvcTestResult assignment = results.get(1);
        assertThat(deletion.getResponse().getStatus()).isEqualTo(204);
        assertThat(assignment.getResponse().getStatus()).isIn(200, 404);
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts", "", "anna");
        assertThat(JsonPath.<List<String>>read(body(list), "$.items[*].id")).isEmpty();
        List<String> onTrack = actions("/api/v1/tracks/" + track + "/history");
        List<String> onMentor = actions("/api/v1/users/" + first + "/history");
        if (assignment.getResponse().getStatus() == 200) {
            assertThat(onTrack.stream().filter("TRACK_COHORT_MENTOR_ASSIGNED"::equals))
                    .hasSize(1);
            assertThat(onTrack.stream().filter("TRACK_COHORT_MENTOR_REMOVED"::equals))
                    .hasSize(1);
            assertThat(onMentor).containsExactlyInAnyOrder("USER_COHORT_MENTOR_ASSIGNED", "USER_COHORT_MENTOR_REMOVED");
        } else {
            assertThat(onTrack).noneMatch(action -> action.contains("COHORT_MENTOR"));
            assertThat(onMentor).isEmpty();
        }
    }

    @RepeatedTest(10)
    @DisplayName("ENR-13.9 A removal from the track at the moment of an assignment to a cohort never leaves a cohort"
            + " with a mentor from outside the track")
    void aRemovalAtTheMomentOfAnAssignmentNeverLeavesACohortWithAMentorFromOutsideTheTrack() throws Exception {
        List<MvcTestResult> results = inParallel(
                () -> send(HttpMethod.DELETE, "/api/v1/tracks/" + track + "/mentors/" + first, "", "anna"),
                () -> send(HttpMethod.POST, cohortPath + "/mentors", assignment(first), "boris"));

        List<Integer> statuses =
                results.stream().map(r -> r.getResponse().getStatus()).sorted().toList();
        assertThat(statuses).containsExactly(200, 409);
        MvcTestResult refused = results.stream()
                .filter(r -> r.getResponse().getStatus() == 409)
                .findFirst()
                .orElseThrow();
        assertThat(refused)
                .bodyJson()
                .extractingPath("$.type")
                .asString()
                .isIn("urn:mentorhub:problem:mentor-leads-cohorts", "urn:mentorhub:problem:mentor-not-eligible");
        MvcTestResult view = send(HttpMethod.GET, "/api/v1/tracks/" + track, "", "anna");
        List<String> onTrack = JsonPath.read(body(view), "$.mentors[*].id");
        List<String> ofCohort = cohortMentors();
        assertThat(ofCohort.contains(first.toString())).isEqualTo(onTrack.contains(first.toString()));
    }

    private List<String> cohortMentors() {
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/cohorts", "", "anna");
        assertThat(list).hasStatusOk();
        return JsonPath.read(body(list), "$.items[?(@.id == '" + cohort + "')].mentors[*].id");
    }

    private List<String> actions(String path) {
        MvcTestResult history = send(HttpMethod.GET, path, "", "anna");
        assertThat(history).hasStatusOk();
        return JsonPath.read(body(history), "$.items[*].action");
    }

    private static String assignment(UUID... userIds) {
        return "{\"userIds\": %s}"
                .formatted(java.util.Arrays.stream(userIds)
                        .map(id -> "\"" + id + "\"")
                        .toList());
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
