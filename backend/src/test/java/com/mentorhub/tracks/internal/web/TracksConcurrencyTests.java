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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
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
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    TransactionTemplate transactions;

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
        coordinator = users.create("clara@x.test", UserStatus.ACTIVE, Role.COORDINATOR, Role.MENTOR);
        track = tracks.active("QA", "Quality Assurance");
        path = "/api/v1/tracks/" + track;
    }

    @Test
    @DisplayName("TRK-5.4 A change at the moment of archiving is never stored on an archived track (name)")
    void renameAtTheMomentOfArchivingIsNeverStoredOnAnArchivedTrack() throws Exception {
        MvcTestResult change = archivedFirst(
                () -> send(HttpMethod.PUT, path, "{\"code\": \"QA\", \"name\": \"Quality Engineering\"}", "boris"));

        assertProblem(change, HttpStatus.CONFLICT, TRACK_ARCHIVED);
        MvcTestResult after = send(HttpMethod.GET, path, "", "anna");
        assertThat(after).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
        assertThat(after).bodyJson().extractingPath("$.name").isEqualTo("Quality Assurance");
        assertThat(trackActions()).containsExactly("TRACK_ARCHIVED");
    }

    @Test
    @DisplayName("TRK-5.4 A change at the moment of archiving is never stored on an archived track (coordinator)")
    void namingAtTheMomentOfArchivingIsNeverStoredOnAnArchivedTrack() throws Exception {
        MvcTestResult change =
                archivedFirst(() -> send(HttpMethod.PUT, path + "/coordinators/" + coordinator, "", "boris"));

        assertProblem(change, HttpStatus.CONFLICT, TRACK_ARCHIVED);
        MvcTestResult after = send(HttpMethod.GET, path, "", "anna");
        assertThat(after).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
        assertThat(after).bodyJson().extractingPath("$.coordinators").asArray().isEmpty();
        assertThat(trackActions()).containsExactly("TRACK_ARCHIVED");
    }

    @Test
    @DisplayName("TRK-5.4 A change at the moment of archiving is never stored on an archived track (mentor)")
    void namingAMentorAtTheMomentOfArchivingIsNeverStoredOnAnArchivedTrack() throws Exception {
        MvcTestResult change = archivedFirst(() -> send(HttpMethod.PUT, path + "/mentors/" + coordinator, "", "boris"));

        assertProblem(change, HttpStatus.CONFLICT, TRACK_ARCHIVED);
        MvcTestResult after = send(HttpMethod.GET, path, "", "anna");
        assertThat(after).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
        assertThat(after).bodyJson().extractingPath("$.mentors").asArray().isEmpty();
        assertThat(trackActions()).containsExactly("TRACK_ARCHIVED");
    }

    @Test
    @DisplayName("TRK-5.4 A change at the moment of archiving is never stored on an archived track (cohort)")
    void creatingACohortAtTheMomentOfArchivingIsNeverStoredOnAnArchivedTrack() throws Exception {
        MvcTestResult change = archivedFirst(() -> send(
                HttpMethod.POST, path + "/cohorts", "{\"name\": \"Spring\", \"startDate\": \"2026-03-01\"}", "boris"));

        assertProblem(change, HttpStatus.CONFLICT, TRACK_ARCHIVED);
        MvcTestResult after = send(HttpMethod.GET, path + "/cohorts", "", "anna");
        assertThat(after).hasStatusOk();
        assertThat(after).bodyJson().extractingPath("$.items").asArray().isEmpty();
        assertThat(trackActions()).containsExactly("TRACK_ARCHIVED");
    }

    @RepeatedTest(5)
    @DisplayName("ENR-13.6 Parallel namings of one mentor name them once")
    void parallelNamingsOfOneMentorNameThemOnce() throws Exception {
        String naming = path + "/mentors/" + coordinator;

        List<MvcTestResult> results = inParallel(
                () -> send(HttpMethod.PUT, naming, "", "anna"), () -> send(HttpMethod.PUT, naming, "", "boris"));

        results.forEach(result -> assertThat(result).hasStatusOk());
        MvcTestResult after = send(HttpMethod.GET, path, "", "anna");
        assertThat(after)
                .bodyJson()
                .extractingPath("$.mentors[*].id")
                .asArray()
                .containsExactly(coordinator.toString());
        assertThat(trackActions()).containsExactly("TRACK_MENTOR_ADDED");
        MvcTestResult userHistory = send(HttpMethod.GET, "/api/v1/users/" + coordinator + "/history", "", "anna");
        assertThat(userHistory)
                .bodyJson()
                .extractingPath("$.items[*].action")
                .asArray()
                .containsExactly("USER_TRACK_MENTOR_ADDED");
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

    /**
     * Makes the moment of archiving deterministic: a transaction of the test holds the track's row, the archiving is
     * sent and waits for it, then the change is sent, and the row is released only once the change is seen waiting
     * too. PostgreSQL hands a row to its waiters in turn, so the archiving runs first and the change runs after its
     * commit. A change that does not wait, or that stores without seeing the archiving, fails the caller's assertions.
     * Only the sessions queued behind the holder are counted, so a lock wait elsewhere in the database does not count.
     */
    private MvcTestResult archivedFirst(Callable<MvcTestResult> change) throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger holderPid = new AtomicInteger();
        try (ExecutorService executor = Executors.newFixedThreadPool(3)) {
            Future<?> holder = executor.submit(() -> transactions.executeWithoutResult(status -> {
                jdbc.sql("select id from tracks where id = :id for no key update")
                        .param("id", track)
                        .query(UUID.class)
                        .single();
                holderPid.set(
                        jdbc.sql("select pg_backend_pid()").query(Integer.class).single());
                held.countDown();
                awaitQuietly(release);
            }));
            Future<MvcTestResult> archiving;
            Future<MvcTestResult> changing;
            try {
                assertThat(held.await(10, TimeUnit.SECONDS)).isTrue();
                archiving = executor.submit(() -> send(HttpMethod.POST, path + "/archive", "", "anna"));
                awaitWaitersBehind(holderPid.get(), 1, archiving);
                assertThat(archiving).as("the archiving waits for the held row").isNotDone();
                changing = executor.submit(change);
                awaitWaitersBehind(holderPid.get(), 2, changing);
                assertThat(changing)
                        .as("the change waits until the archiving is committed")
                        .isNotDone();
            } finally {
                release.countDown();
            }
            holder.get(10, TimeUnit.SECONDS);
            MvcTestResult archived = archiving.get(10, TimeUnit.SECONDS);
            assertThat(archived).hasStatusOk();
            assertThat(archived).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
            return changing.get(10, TimeUnit.SECONDS);
        }
    }

    /** Waits until this many sessions wait behind the holder's lock, or the request has already answered. */
    private void awaitWaitersBehind(int holderPid, int count, Future<MvcTestResult> request)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (!request.isDone()
                && waitersBehind(holderPid) < count
                && Instant.now().isBefore(deadline)) {
            Thread.sleep(10);
        }
    }

    /**
     * The sessions blocked by the holder, directly or through another waiter: the first waiter for a row lock waits on
     * the holder, and the ones after it queue for the row behind the first waiter.
     */
    private long waitersBehind(int holderPid) {
        return jdbc.sql("""
                        with recursive blocked(pid) as (
                            select pid from pg_stat_activity
                            where datname = current_database() and :holder = any(pg_blocking_pids(pid))
                          union
                            select a.pid from pg_stat_activity a
                            join blocked b on b.pid = any(pg_blocking_pids(a.pid))
                            where a.datname = current_database()
                        )
                        select count(*) from blocked
                        """).param("holder", holderPid).query(Long.class).single();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("The held row was never released");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
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
