package com.mentorhub.tracks.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.mentorhub.support.EndpointTest;
import com.mentorhub.tracks.internal.TestTracks;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestUsers;
import java.net.URI;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Parallel requests on one track through the real chain: never a server error, never a change on an archived track. */
@EndpointTest
class TracksConcurrencyTests {

    static final URI TRACK_ARCHIVED = URI.create("urn:mentorhub:problem:track-archived");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    TestUsers users;
    TestTracks tracks;
    UUID coordinator;
    UUID track;
    String path;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        tracks = new TestTracks(jdbc);
        users.link(users.create("anna@x.test", UserStatus.ACTIVE, Role.ADMIN), "anna");
        users.link(users.create("boris@x.test", UserStatus.ACTIVE, Role.ADMIN), "boris");
        coordinator = users.create("clara@x.test", UserStatus.ACTIVE, Role.COORDINATOR);
        track = tracks.active("QA", "Quality Assurance");
        path = "/api/v1/tracks/" + track;
    }

    // Repeated because a race shows only in some runs.
    @RepeatedTest(5)
    @DisplayName("TRK-5.4 A change at the moment of archiving is never stored on an archived track (name)")
    void renameAtTheMomentOfArchivingIsNeverStoredOnAnArchivedTrack() throws Exception {
        List<MvcTestResult> results = inParallel(
                () -> send(HttpMethod.POST, path + "/archive", "", "anna"),
                () -> send(HttpMethod.PUT, path, "{\"code\": \"QA\", \"name\": \"Quality Engineering\"}", "boris"));

        assertThat(results.get(0)).hasStatusOk();
        MvcTestResult change = results.get(1);
        MvcTestResult after = send(HttpMethod.GET, path, "", "anna");
        assertThat(after).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
        if (change.getResponse().getStatus() == HttpStatus.OK.value()) {
            assertThat(after).bodyJson().extractingPath("$.name").isEqualTo("Quality Engineering");
            assertThat(trackActions()).contains("TRACK_UPDATED");
        } else {
            assertProblem(change, HttpStatus.CONFLICT, TRACK_ARCHIVED);
            assertThat(after).bodyJson().extractingPath("$.name").isEqualTo("Quality Assurance");
            assertThat(trackActions()).doesNotContain("TRACK_UPDATED");
        }
    }

    @RepeatedTest(5)
    @DisplayName("TRK-5.4 A change at the moment of archiving is never stored on an archived track (coordinator)")
    void namingAtTheMomentOfArchivingIsNeverStoredOnAnArchivedTrack() throws Exception {
        List<MvcTestResult> results = inParallel(
                () -> send(HttpMethod.POST, path + "/archive", "", "anna"),
                () -> send(HttpMethod.PUT, path + "/coordinators/" + coordinator, "", "boris"));

        assertThat(results.get(0)).hasStatusOk();
        MvcTestResult change = results.get(1);
        MvcTestResult after = send(HttpMethod.GET, path, "", "anna");
        assertThat(after).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
        if (change.getResponse().getStatus() == HttpStatus.OK.value()) {
            assertThat(after)
                    .bodyJson()
                    .extractingPath("$.coordinators[*].id")
                    .asArray()
                    .containsExactly(coordinator.toString());
            assertThat(trackActions()).contains("TRACK_COORDINATOR_ADDED");
        } else {
            assertProblem(change, HttpStatus.CONFLICT, TRACK_ARCHIVED);
            assertThat(after)
                    .bodyJson()
                    .extractingPath("$.coordinators")
                    .asArray()
                    .isEmpty();
            assertThat(trackActions()).doesNotContain("TRACK_COORDINATOR_ADDED");
        }
    }

    @RepeatedTest(5)
    @DisplayName("TRK-6.4 Parallel namings of one coordinator name them once")
    void parallelNamingsOfOneCoordinatorNameThemOnce() throws Exception {
        String naming = path + "/coordinators/" + coordinator;

        List<MvcTestResult> results = inParallel(
                () -> send(HttpMethod.PUT, naming, "", "anna"), () -> send(HttpMethod.PUT, naming, "", "boris"));

        results.forEach(result -> assertThat(result).hasStatusOk());
        MvcTestResult after = send(HttpMethod.GET, path, "", "anna");
        assertThat(after)
                .bodyJson()
                .extractingPath("$.coordinators[*].id")
                .asArray()
                .containsExactly(coordinator.toString());
        assertThat(trackActions()).containsExactly("TRACK_COORDINATOR_ADDED");
        MvcTestResult userHistory = send(HttpMethod.GET, "/api/v1/users/" + coordinator + "/history", "", "anna");
        assertThat(userHistory)
                .bodyJson()
                .extractingPath("$.items[*].action")
                .asArray()
                .containsExactly("USER_TRACK_COORDINATOR_ADDED");
    }

    private List<String> trackActions() {
        MvcTestResult history = send(HttpMethod.GET, path + "/history", "", "anna");
        assertThat(history).hasStatusOk();
        return JsonPath.read(
                new String(history.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.items[*].action");
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
