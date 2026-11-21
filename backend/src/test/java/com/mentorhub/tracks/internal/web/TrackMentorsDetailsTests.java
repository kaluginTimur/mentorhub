package com.mentorhub.tracks.internal.web;

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

/** Details of the mentors of a track that no scenario fixes: candidates, order of checks, repeats, history. */
@EndpointTest
class TrackMentorsDetailsTests {

    static final URI TRACK_ARCHIVED = URI.create("urn:mentorhub:problem:track-archived");
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
        UUID bella = person("bella@x.test", "Bella", "Aa", Role.MENTOR);
        UUID second = person("a2@x.test", "Anna", "Zed", Role.MENTOR);
        UUID first = person("a1@x.test", "anna", "zed", Role.MENTOR);
        String path = candidates(track);

        MvcTestResult page0 = send(HttpMethod.GET, path + "?size=2&page=0", "admin");
        MvcTestResult page1 = send(HttpMethod.GET, path + "?size=2&page=1", "admin");
        MvcTestResult beyond = send(HttpMethod.GET, path + "?size=2&page=5", "admin");

        assertThat(page0)
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly(first.toString(), second.toString());
        assertThat(page1).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(bella.toString());
        assertThat(page1).bodyJson().extractingPath("$.totalItems").isEqualTo(3);
        assertThat(beyond).bodyJson().extractingPath("$.items").asArray().isEmpty();
        assertThat(beyond).bodyJson().extractingPath("$.totalItems").isEqualTo(3);
    }

    @Test
    void candidateSearchMatchesNameOrEmailCaseInsensitivelyAndTakesWildcardsLiterally() {
        UUID bella = person("bella@x.test", "Bella", "Mentor", Role.MENTOR);
        person("other@x.test", "Other", "Smith", Role.MENTOR);
        String path = candidates(track);

        for (String q : List.of("BELLA@", " bel ", "mentor")) {
            assertThat(send(HttpMethod.GET, path + "?q=" + q, "admin"))
                    .as(q)
                    .bodyJson()
                    .extractingPath("$.items[*].id")
                    .asArray()
                    .containsExactly(bella.toString());
        }
        assertThat(send(HttpMethod.GET, path + "?q=%", "admin"))
                .bodyJson()
                .extractingPath("$.totalItems")
                .isEqualTo(0);
        assertThat(send(HttpMethod.GET, path + "?q=_ella", "admin"))
                .bodyJson()
                .extractingPath("$.totalItems")
                .isEqualTo(0);
        assertThat(send(HttpMethod.GET, path + "?q=\\", "admin"))
                .bodyJson()
                .extractingPath("$.totalItems")
                .isEqualTo(0);
        assertThat(send(HttpMethod.GET, path + "?q= ", "admin"))
                .bodyJson()
                .extractingPath("$.totalItems")
                .isEqualTo(2);
    }

    @Test
    void aSearchLongerThanTheLimitAndABadPageAreRejected() {
        String path = candidates(track);

        assertThat(send(HttpMethod.GET, path + "?q=" + "a".repeat(101), "admin"))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.GET, path + "?q=" + "a".repeat(100), "admin"))
                .hasStatusOk();
        assertThat(send(HttpMethod.GET, path + "?size=0", "admin")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(send(HttpMethod.GET, path + "?page=-1", "admin")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aMentorOfAnotherTrackStaysACandidateAndANamedOneLeavesAndReturnsTheList() {
        UUID other = tracks.active("SWE", "Software");
        UUID mia = person("mia@x.test", "Mia", "Mentor", Role.MENTOR);
        tracks.addMentor(other, mia);

        assertThat(send(HttpMethod.GET, candidates(track), "admin"))
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly(mia.toString());
        assertThat(send(HttpMethod.PUT, mentorPath(track, mia), "admin")).hasStatusOk();
        assertThat(send(HttpMethod.GET, candidates(track), "admin"))
                .bodyJson()
                .extractingPath("$.totalItems")
                .isEqualTo(0);
        assertThat(send(HttpMethod.DELETE, mentorPath(track, mia), "admin")).hasStatusOk();
        assertThat(send(HttpMethod.GET, candidates(track), "admin"))
                .bodyJson()
                .extractingPath("$.totalItems")
                .isEqualTo(1);
        assertThat(mentorIds(other)).containsExactly(mia.toString());
    }

    @Test
    void checksRunInTheOrderOfTheContract() {
        UUID archived = tracks.archived("OLD", "Old");
        UUID elsa = person("elsa@x.test", "Elsa", "Else", Role.COORDINATOR);
        users.link(elsa, "elsa");
        tracks.addCoordinator(tracks.active("SWE", "Software"), elsa);
        UUID ghost = UUID.randomUUID();
        UUID unknownTrack = UUID.randomUUID();

        // the track, before the relation
        for (HttpMethod method : List.of(HttpMethod.PUT, HttpMethod.DELETE)) {
            assertProblem(
                    send(method, mentorPath(unknownTrack, ghost), "elsa"), HttpStatus.NOT_FOUND, Problems.NOT_FOUND);
        }
        assertProblem(
                send(HttpMethod.GET, "/api/v1/tracks/" + unknownTrack + "/mentor-candidates", "elsa"),
                HttpStatus.NOT_FOUND,
                Problems.NOT_FOUND);
        // then the relation, before the status
        assertProblem(
                send(HttpMethod.PUT, mentorPath(archived, ghost), "elsa"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
        assertProblem(
                send(HttpMethod.DELETE, mentorPath(archived, ghost), "elsa"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
        // then the status, before the eligibility
        assertProblem(send(HttpMethod.PUT, mentorPath(archived, ghost), "admin"), HttpStatus.CONFLICT, TRACK_ARCHIVED);
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    void aCoordinatorOrAStudentOfTheTrackAndTheCallerCanBeNamed() {
        UUID clara = person("clara@x.test", "Clara", "Coordinator", Role.COORDINATOR, Role.MENTOR);
        users.link(clara, "clara");
        tracks.addCoordinator(track, clara);
        UUID sam = person("sam@x.test", "Sam", "Student", Role.STUDENT, Role.MENTOR);
        enrollments.enroll(sam, track, admin, START);

        assertThat(send(HttpMethod.PUT, mentorPath(track, clara), "clara")).hasStatusOk();
        assertThat(send(HttpMethod.PUT, mentorPath(track, sam), "clara")).hasStatusOk();

        assertThat(mentorIds(track)).containsExactlyInAnyOrder(clara.toString(), sam.toString());
    }

    @Test
    void aDisabledMentorWhoLeadsAnEnrollmentIsNotRemovedButOneWhoLeadsOnAnotherTrackIs() {
        UUID other = tracks.active("SWE", "Software");
        UUID disabled = person("dina@x.test", "Dina", "Disabled", Role.MENTOR);
        UUID elsewhere = person("eli@x.test", "Eli", "Elsewhere", Role.MENTOR);
        UUID s1 = person("s1@x.test", "S", "One", Role.STUDENT);
        UUID s2 = person("s2@x.test", "S", "Two", Role.STUDENT);
        UUID e1 = enrollments.enroll(s1, track, admin, START);
        UUID e2 = enrollments.enroll(s2, other, admin, START);
        tracks.addMentor(track, disabled);
        tracks.addMentor(track, elsewhere);
        enrollments.addMentor(e1, disabled, admin);
        enrollments.addMentor(e2, elsewhere, admin);
        disable(disabled);

        assertProblem(
                send(HttpMethod.DELETE, mentorPath(track, disabled), "admin"),
                HttpStatus.CONFLICT,
                URI.create("urn:mentorhub:problem:mentor-leads-enrollments"));
        assertThat(send(HttpMethod.DELETE, mentorPath(track, elsewhere), "admin"))
                .hasStatusOk();

        assertThat(mentorIds(track)).containsExactly(disabled.toString());
    }

    @Test
    void aMentorCanBeRemovedAfterTheirEnrollmentsLoseThem() {
        UUID mia = person("mia@x.test", "Mia", "Mentor", Role.MENTOR);
        UUID student = person("sam@x.test", "Sam", "Student", Role.STUDENT);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        tracks.addMentor(track, mia);
        enrollments.addMentor(enrollment, mia, admin);

        assertThat(send(HttpMethod.DELETE, "/api/v1/enrollments/" + enrollment + "/mentors/" + mia, "admin"))
                .hasStatusOk();
        assertThat(send(HttpMethod.DELETE, mentorPath(track, mia), "admin")).hasStatusOk();

        assertThat(mentorIds(track)).isEmpty();
    }

    @Test
    void anEnrollmentTakesOnlyAMentorOfItsTrack() {
        UUID other = tracks.active("SWE", "Software");
        UUID student = person("sam@x.test", "Sam", "Student", Role.STUDENT);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        UUID inside = person("in@x.test", "In", "Side", Role.MENTOR);
        UUID outside = person("out@x.test", "Out", "Side", Role.MENTOR);
        UUID abroad = person("abroad@x.test", "Ab", "Road", Role.MENTOR);
        UUID disabled = person("dina@x.test", "Dina", "Disabled", Role.MENTOR);
        tracks.addMentor(track, inside);
        tracks.addMentor(track, disabled);
        tracks.addMentor(other, abroad);
        disable(disabled);
        String base = "/api/v1/enrollments/" + enrollment;

        assertThat(send(HttpMethod.GET, base + "/mentor-candidates", "admin"))
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly(inside.toString());
        for (UUID refused : List.of(outside, abroad, disabled)) {
            assertProblem(
                    send(HttpMethod.PUT, base + "/mentors/" + refused, "admin"),
                    HttpStatus.CONFLICT,
                    MENTOR_NOT_ELIGIBLE);
        }
        assertThat(send(HttpMethod.PUT, base + "/mentors/" + inside, "admin")).hasStatusOk();
        assertThat(send(HttpMethod.GET, base + "/mentor-candidates", "admin"))
                .bodyJson()
                .extractingPath("$.totalItems")
                .isEqualTo(0);
    }

    @Test
    void anAssignedMentorLeftTheTrackAfterTheirRemovalFromTheEnrollmentCannotBeAssignedAgain() {
        UUID student = person("sam@x.test", "Sam", "Student", Role.STUDENT);
        UUID enrollment = enrollments.enroll(student, track, admin, START);
        UUID mia = person("mia@x.test", "Mia", "Mentor", Role.MENTOR);
        tracks.addMentor(track, mia);
        String enrollmentMentor = "/api/v1/enrollments/" + enrollment + "/mentors/" + mia;

        assertThat(send(HttpMethod.PUT, enrollmentMentor, "admin")).hasStatusOk();
        assertThat(send(HttpMethod.DELETE, enrollmentMentor, "admin")).hasStatusOk();
        assertThat(send(HttpMethod.DELETE, mentorPath(track, mia), "admin")).hasStatusOk();

        assertProblem(send(HttpMethod.PUT, enrollmentMentor, "admin"), HttpStatus.CONFLICT, MENTOR_NOT_ELIGIBLE);
    }

    @Test
    void historiesKeepTheNameOfTheTrackAtTheMomentAndRecordEachChangeOnce() {
        UUID mia = person("mia@x.test", "Mia", "Mentor", Role.MENTOR);
        assertThat(send(HttpMethod.PUT, mentorPath(track, mia), "admin")).hasStatusOk();
        assertThat(send(HttpMethod.PUT, mentorPath(track, mia), "admin")).hasStatusOk();
        assertThat(send(
                        HttpMethod.PUT,
                        "/api/v1/tracks/" + track,
                        "admin",
                        "{\"code\": \"QE\", \"name\": \"Quality\"}"))
                .hasStatusOk();
        assertThat(send(HttpMethod.DELETE, mentorPath(track, mia), "admin")).hasStatusOk();
        assertThat(send(HttpMethod.DELETE, mentorPath(track, mia), "admin")).hasStatusOk();

        MvcTestResult onUser = send(HttpMethod.GET, "/api/v1/users/" + mia + "/history", "admin");
        MvcTestResult onTrack = send(HttpMethod.GET, "/api/v1/tracks/" + track + "/history", "admin");

        List<Map<String, Object>> added = entries(onUser, "USER_TRACK_MENTOR_ADDED");
        List<Map<String, Object>> removed = entries(onUser, "USER_TRACK_MENTOR_REMOVED");
        assertThat(added).hasSize(1);
        assertThat(removed).hasSize(1);
        assertThat(inner(added.get(0), "track", "code")).isEqualTo("QA");
        assertThat(inner(removed.get(0), "track", "code")).isEqualTo("QE");
        assertThat(inner(removed.get(0), "track", "name")).isEqualTo("Quality");
        assertThat(entries(onTrack, "TRACK_MENTOR_ADDED")).hasSize(1);
        assertThat(entries(onTrack, "TRACK_MENTOR_REMOVED")).hasSize(1);
        assertThat(inner(entries(onTrack, "TRACK_MENTOR_ADDED").get(0), "actor", "id"))
                .isEqualTo(admin.toString());
    }

    @Test
    void aMentorNamedOnTwoTracksHasTwoHistoryEntries() {
        UUID other = tracks.active("SWE", "Software");
        UUID mia = person("mia@x.test", "Mia", "Mentor", Role.MENTOR);

        assertThat(send(HttpMethod.PUT, mentorPath(track, mia), "admin")).hasStatusOk();
        assertThat(send(HttpMethod.PUT, mentorPath(other, mia), "admin")).hasStatusOk();

        assertThat(entries(
                        send(HttpMethod.GET, "/api/v1/users/" + mia + "/history", "admin"), "USER_TRACK_MENTOR_ADDED"))
                .hasSize(2);
        assertThat(entries(send(HttpMethod.GET, "/api/v1/tracks/" + other + "/history", "admin"), "TRACK_MENTOR_ADDED"))
                .hasSize(1);
        assertThat(entries(send(HttpMethod.GET, "/api/v1/tracks/" + track + "/history", "admin"), "TRACK_MENTOR_ADDED"))
                .hasSize(1);
    }

    @Test
    void parallelRemovalsOfOneMentorNeverFailAndRecordOneEntry() throws Exception {
        UUID boris = person("boris@x.test", "Boris", "Admin", Role.ADMIN);
        users.link(boris, "boris");
        UUID mia = person("mia@x.test", "Mia", "Mentor", Role.MENTOR);
        tracks.addMentor(track, mia);

        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<MvcTestResult>> futures = List.of(
                    pool.submit(() -> {
                        go.await();
                        return send(HttpMethod.DELETE, mentorPath(track, mia), "admin");
                    }),
                    pool.submit(() -> {
                        go.await();
                        return send(HttpMethod.DELETE, mentorPath(track, mia), "boris");
                    }));
            go.countDown();
            for (Future<MvcTestResult> f : futures) {
                assertThat(f.get().getResponse().getStatus()).isEqualTo(200);
            }
        }

        assertThat(mentorIds(track)).isEmpty();
        assertThat(entries(
                        send(HttpMethod.GET, "/api/v1/tracks/" + track + "/history", "admin"), "TRACK_MENTOR_REMOVED"))
                .hasSize(1);
    }

    private void disable(UUID user) {
        jdbc.sql("update users set status = 'DISABLED', disabled_reason = 'SECURITY' where id = :id")
                .param("id", user)
                .update();
    }

    private String candidates(UUID id) {
        return "/api/v1/tracks/" + id + "/mentor-candidates";
    }

    private static String mentorPath(UUID id, UUID user) {
        return "/api/v1/tracks/" + id + "/mentors/" + user;
    }

    private List<String> mentorIds(UUID id) {
        MvcTestResult view = send(HttpMethod.GET, "/api/v1/tracks/" + id, "admin");
        assertThat(view).hasStatusOk();
        return JsonPath.read(
                new String(view.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.mentors[*].id");
    }

    private UUID person(String email, String first, String last, Role... roles) {
        UUID id = users.create(email, UserStatus.ACTIVE, roles);
        users.rename(id, first, last);
        return id;
    }

    private MvcTestResult send(HttpMethod method, String uri, String subject) {
        return send(method, uri, subject, "");
    }

    private MvcTestResult send(HttpMethod method, String uri, String subject, String body) {
        var request = mvc.method(method)
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, issuer.token(subject).bearer());
        if (!body.isEmpty()) {
            request = request.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content(body);
        }
        return request.exchange();
    }

    private static List<Map<String, Object>> entries(MvcTestResult history, String action) {
        return JsonPath.read(
                new String(history.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
                "$.items[?(@.action == '%s')]".formatted(action));
    }

    @SuppressWarnings("unchecked")
    private static Object inner(Map<String, Object> entry, String object, String field) {
        Map<String, Object> inner = (Map<String, Object>) entry.get(object);
        return inner == null ? null : inner.get(field);
    }
}
