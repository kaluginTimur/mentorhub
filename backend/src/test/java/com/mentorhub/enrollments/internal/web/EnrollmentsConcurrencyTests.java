package com.mentorhub.enrollments.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.mentorhub.support.EndpointTest;
import com.mentorhub.tracks.internal.TestTracks;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestUsers;
import java.nio.charset.StandardCharsets;
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

/** Parallel requests through the real chain: never a server error, never a student on an archived track. */
@EndpointTest
class EnrollmentsConcurrencyTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    TestUsers users;
    TestTracks tracks;
    UUID student;
    UUID first;
    UUID second;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        tracks = new TestTracks(jdbc);
        users.link(users.create("anna@x.test", UserStatus.ACTIVE, Role.ADMIN), "anna");
        users.link(users.create("boris@x.test", UserStatus.ACTIVE, Role.ADMIN), "boris");
        student = users.create("sam@x.test", UserStatus.ACTIVE, Role.STUDENT);
        first = tracks.active("QA", "Quality Assurance");
        second = tracks.active("SWE", "Software Engineering");
    }

    @RepeatedTest(5)
    @DisplayName("ENR-2.6 Parallel enrollments of one student enroll them once")
    void parallelEnrollmentsOfOneStudentEnrollThemOnce() throws Exception {
        List<MvcTestResult> results = inParallel(() -> enroll(first, "anna"), () -> enroll(second, "boris"));

        List<Integer> statuses =
                results.stream().map(r -> r.getResponse().getStatus()).sorted().toList();
        assertThat(statuses).containsExactly(201, 409);
        MvcTestResult refused = results.stream()
                .filter(r -> r.getResponse().getStatus() == 409)
                .findFirst()
                .orElseThrow();
        assertThat(refused)
                .bodyJson()
                .extractingPath("$.type")
                .isEqualTo("urn:mentorhub:problem:student-already-enrolled");
        int enrolled =
                count("/api/v1/tracks/" + first + "/enrollments") + count("/api/v1/tracks/" + second + "/enrollments");
        assertThat(enrolled).isEqualTo(1);
        assertThat(actions("/api/v1/users/" + student + "/history")).containsExactly("USER_ENROLLED");
        List<String> onTracks = new ArrayList<>(actions("/api/v1/tracks/" + first + "/history"));
        onTracks.addAll(actions("/api/v1/tracks/" + second + "/history"));
        assertThat(onTracks).containsExactly("TRACK_STUDENT_ENROLLED");
    }

    @RepeatedTest(5)
    @DisplayName("TRK-5.6 An enrollment at the moment of archiving never leaves a student on an archived track")
    void anEnrollmentAtTheMomentOfArchivingNeverLeavesAStudentOnAnArchivedTrack() throws Exception {
        List<MvcTestResult> results = inParallel(
                () -> enroll(first, "anna"),
                () -> send(HttpMethod.POST, "/api/v1/tracks/" + first + "/archive", "", "boris"));

        MvcTestResult enrollment = results.get(0);
        MvcTestResult archiving = results.get(1);
        int enrollmentStatus = enrollment.getResponse().getStatus();
        int archivingStatus = archiving.getResponse().getStatus();
        if (enrollmentStatus == 201) {
            assertThat(archivingStatus).isEqualTo(409);
            assertThat(archiving)
                    .bodyJson()
                    .extractingPath("$.type")
                    .isEqualTo("urn:mentorhub:problem:track-has-active-enrollments");
            assertThat(count("/api/v1/tracks/" + first + "/enrollments")).isEqualTo(1);
        } else {
            assertThat(enrollmentStatus).isEqualTo(409);
            assertThat(enrollment)
                    .bodyJson()
                    .extractingPath("$.type")
                    .isEqualTo("urn:mentorhub:problem:track-archived");
            assertThat(archivingStatus).isEqualTo(200);
            assertThat(count("/api/v1/tracks/" + first + "/enrollments")).isZero();
        }
    }

    private int count(String path) {
        MvcTestResult list = send(HttpMethod.GET, path, "", "anna");
        assertThat(list).hasStatusOk();
        return JsonPath.read(
                new String(list.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.items.length()");
    }

    private List<String> actions(String path) {
        MvcTestResult history = send(HttpMethod.GET, path, "", "anna");
        assertThat(history).hasStatusOk();
        return JsonPath.read(
                new String(history.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.items[*].action");
    }

    private MvcTestResult enroll(UUID track, String caller) {
        return send(
                HttpMethod.POST,
                "/api/v1/tracks/" + track + "/enrollments",
                "{\"studentId\": \"%s\", \"startDate\": \"2026-09-01\"}".formatted(student),
                caller);
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
