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

/** The mentors of a track through the real chain: tokens, the gate, permissions, services and the database. */
@EndpointTest
class TrackMentorsEndpointTests {

    static final URI TRACK_ARCHIVED = URI.create("urn:mentorhub:problem:track-archived");
    static final URI MENTOR_NOT_ELIGIBLE = URI.create("urn:mentorhub:problem:mentor-not-eligible");
    static final URI MENTOR_LEADS_ENROLLMENTS = URI.create("urn:mentorhub:problem:mentor-leads-enrollments");

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

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        tracks = new TestTracks(jdbc);
        enrollments = new TestEnrollments(jdbc);
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "admin");
    }

    @Test
    @DisplayName("ENR-1.7 A coordinator names the mentors only of a track they coordinate")
    void aCoordinatorNamesTheMentorsOnlyOfATrackTheyCoordinate() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        tracks.addMentor(track, mentor);
        UUID candidate = person("cand@x.test", "Cand", "Idate", UserStatus.ACTIVE, Role.MENTOR);
        UUID elsewhere = coordinator("elsa@x.test", "elsa");
        tracks.addCoordinator(tracks.active("SWE", "Software Engineering"), elsewhere);
        UUID removed = coordinator("rita@x.test", "rita");
        tracks.addCoordinator(track, removed);
        jdbc.sql("delete from track_coordinators where user_id = :user")
                .param("user", removed)
                .update();

        for (String caller : List.of("elsa", "rita")) {
            assertProblem(
                    send(HttpMethod.GET, path(track) + "/mentor-candidates", "", caller),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
            assertProblem(
                    send(HttpMethod.PUT, mentorPath(track, candidate), "", caller),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
            assertProblem(
                    send(HttpMethod.DELETE, mentorPath(track, mentor), "", caller),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
            MvcTestResult view = send(HttpMethod.GET, path(track), "", caller);
            assertThat(view).hasStatusOk();
            assertThat(view).bodyJson().extractingPath("$.canEnroll").isEqualTo(false);
        }

        assertThat(mentorIds(track)).containsExactly(mentor.toString());
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-6.3 Naming and removing a mentor of a track show in two histories")
    void namingAndRemovingAMentorOfATrackShowInTwoHistories() {
        UUID clara = coordinator("clara@x.test", "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        assertThat(send(HttpMethod.PUT, mentorPath(track, mentor), "", "clara")).hasStatusOk();
        assertThat(send(HttpMethod.DELETE, mentorPath(track, mentor), "", "clara"))
                .hasStatusOk();
        assertThat(send(HttpMethod.PUT, path(track), "{\"code\": \"QE\", \"name\": \"Quality Engineering\"}", "admin"))
                .hasStatusOk();

        MvcTestResult onTrack = send(HttpMethod.GET, path(track) + "/history", "", "admin");
        MvcTestResult onMentor = send(HttpMethod.GET, "/api/v1/users/" + mentor + "/history", "", "admin");

        for (String action : List.of("TRACK_MENTOR_ADDED", "TRACK_MENTOR_REMOVED")) {
            List<Map<String, Object>> entries = entries(onTrack, action);
            assertThat(entries).hasSize(1);
            assertThat(inner(entries.get(0), "actor", "id")).isEqualTo(clara.toString());
            assertThat(inner(entries.get(0), "user", "id")).isEqualTo(mentor.toString());
        }
        for (String action : List.of("USER_TRACK_MENTOR_ADDED", "USER_TRACK_MENTOR_REMOVED")) {
            List<Map<String, Object>> entries = entries(onMentor, action);
            assertThat(entries).hasSize(1);
            assertThat(inner(entries.get(0), "actor", "id")).isEqualTo(clara.toString());
            assertThat(inner(entries.get(0), "track", "id")).isEqualTo(track.toString());
            assertThat(inner(entries.get(0), "track", "code")).isEqualTo("QA");
            assertThat(inner(entries.get(0), "track", "name")).isEqualTo("Quality Assurance");
        }
    }

    @Test
    @DisplayName("ENR-13.1 A coordinator names and removes the mentors of their track")
    void aCoordinatorNamesAndRemovesTheMentorsOfTheirTrack() {
        UUID clara = coordinator("clara@x.test", "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID former = person("fred@x.test", "Fred", "Former", UserStatus.ACTIVE, Role.MENTOR);
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        tracks.addMentor(track, former);

        MvcTestResult named = send(HttpMethod.PUT, mentorPath(track, mentor), "", "clara");
        MvcTestResult removed = send(HttpMethod.DELETE, mentorPath(track, former), "", "clara");

        assertThat(named).hasStatusOk();
        assertThat(named).bodyJson().extractingPath("$.id").isEqualTo(track.toString());
        assertThat(removed).hasStatusOk();
        assertThat(removed)
                .bodyJson()
                .extractingPath("$.mentors[*].id")
                .asArray()
                .containsExactly(mentor.toString());
        assertThat(removed).bodyJson().extractingPath("$.mentors[0].name").isEqualTo("Mia Mentor");
        assertThat(removed).bodyJson().extractingPath("$.mentors[0].email").isEqualTo("mia@x.test");
        assertThat(mentorIds(track)).containsExactly(mentor.toString());
    }

    @Test
    @DisplayName("ENR-13.2 Only an active user with the mentor role is named")
    void onlyAnActiveUserWithTheMentorRoleIsNamed() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID disabled = person("dina@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.MENTOR);
        UUID withoutRole = person("cora@x.test", "Cora", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);

        for (UUID user : List.of(disabled, withoutRole, UUID.randomUUID())) {
            assertProblem(
                    send(HttpMethod.PUT, mentorPath(track, user), "", "admin"),
                    HttpStatus.CONFLICT,
                    MENTOR_NOT_ELIGIBLE);
        }

        assertThat(mentorIds(track)).isEmpty();
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-13.3 A repeated naming or removal records nothing")
    void aRepeatedNamingOrRemovalRecordsNothing() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID active = person("alice@x.test", "Alice", "Active", UserStatus.ACTIVE, Role.MENTOR);
        UUID disabled = person("dina@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.MENTOR);
        UUID bystander = person("bob@x.test", "Bob", "Bystander", UserStatus.ACTIVE, Role.MENTOR);
        tracks.addMentor(track, active);
        tracks.addMentor(track, disabled);

        List<MvcTestResult> results = List.of(
                send(HttpMethod.PUT, mentorPath(track, active), "", "admin"),
                send(HttpMethod.PUT, mentorPath(track, disabled), "", "admin"),
                send(HttpMethod.DELETE, mentorPath(track, bystander), "", "admin"),
                send(HttpMethod.DELETE, mentorPath(track, UUID.randomUUID()), "", "admin"));

        for (MvcTestResult result : results) {
            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.mentors[*].id")
                    .asArray()
                    .containsExactly(active.toString(), disabled.toString());
        }
        assertThat(mentorIds(track)).containsExactly(active.toString(), disabled.toString());
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-13.4 A mentor who leads a student on the track is not removed")
    void aMentorWhoLeadsAStudentOnTheTrackIsNotRemoved() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        UUID student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
        tracks.addMentor(track, mentor);
        UUID enrollment = enrollments.enroll(student, track, admin, LocalDate.of(2026, 9, 1));
        enrollments.addMentor(enrollment, mentor, admin);

        assertProblem(
                send(HttpMethod.DELETE, mentorPath(track, mentor), "", "admin"),
                HttpStatus.CONFLICT,
                MENTOR_LEADS_ENROLLMENTS);

        assertThat(mentorIds(track)).containsExactly(mentor.toString());
        MvcTestResult list = send(HttpMethod.GET, path(track) + "/enrollments", "", "admin");
        assertThat(list)
                .bodyJson()
                .extractingPath("$.items[0].mentors[*].id")
                .asArray()
                .containsExactly(mentor.toString());
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-13.5 The mentors of an archived track cannot be changed")
    void theMentorsOfAnArchivedTrackCannotBeChanged() {
        UUID track = tracks.archived("QA", "Quality Assurance");
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        UUID candidate = person("cand@x.test", "Cand", "Idate", UserStatus.ACTIVE, Role.MENTOR);
        tracks.addMentor(track, mentor);

        List<MvcTestResult> refusals = List.of(
                send(HttpMethod.GET, path(track) + "/mentor-candidates", "", "admin"),
                send(HttpMethod.PUT, mentorPath(track, candidate), "", "admin"),
                send(HttpMethod.PUT, mentorPath(track, mentor), "", "admin"),
                send(HttpMethod.DELETE, mentorPath(track, mentor), "", "admin"),
                send(HttpMethod.DELETE, mentorPath(track, candidate), "", "admin"));

        refusals.forEach(result -> assertProblem(result, HttpStatus.CONFLICT, TRACK_ARCHIVED));
        assertThat(mentorIds(track)).containsExactly(mentor.toString());
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("ENR-14.1 The candidates are the eligible mentors not yet named")
    void theCandidatesAreTheEligibleMentorsNotYetNamed() {
        UUID clara = coordinator("clara@x.test", "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID named = person("alice@x.test", "Alice", "Named", UserStatus.ACTIVE, Role.MENTOR);
        UUID candidate = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        person("dina@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.MENTOR);
        person("stan@x.test", "Stan", "Student", UserStatus.ACTIVE, Role.STUDENT);
        tracks.addMentor(track, named);

        MvcTestResult result = send(HttpMethod.GET, path(track) + "/mentor-candidates", "", "clara");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(candidate.toString());
        assertThat(result).bodyJson().extractingPath("$.items[0].name").isEqualTo("Mia Mentor");
        assertThat(result).bodyJson().extractingPath("$.items[0].email").isEqualTo("mia@x.test");
        assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(1);
    }

    @Test
    @DisplayName("TRK-4.3 A mentor of the track who is disabled or lost the role is marked")
    void aMentorOfTheTrackWhoIsDisabledOrLostTheRoleIsMarked() {
        UUID clara = coordinator("clara@x.test", "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, clara);
        UUID active = person("alice@x.test", "Alice", "Active", UserStatus.ACTIVE, Role.MENTOR);
        UUID disabled = person("boris@x.test", "Boris", "Disabled", UserStatus.DISABLED, Role.MENTOR);
        UUID withoutRole = person("cora@x.test", "Cora", "Demoted", UserStatus.ACTIVE, Role.COORDINATOR);
        // Named in reverse order, so the order of the answer is the sorting's.
        tracks.addMentor(track, withoutRole);
        tracks.addMentor(track, disabled);
        tracks.addMentor(track, active);

        MvcTestResult result = send(HttpMethod.GET, path(track), "", "clara");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.mentors[*].id")
                .asArray()
                .containsExactly(active.toString(), disabled.toString(), withoutRole.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.mentors[*].disabled")
                .asArray()
                .containsExactly(false, true, false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.mentors[*].mentorRole")
                .asArray()
                .containsExactly(true, true, false);
    }

    @Test
    @DisplayName("TRK-5.1 An archived track keeps its mentors and is restored with them")
    void anArchivedTrackKeepsItsMentorsAndIsRestoredWithThem() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID mentor = person("mia@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        tracks.addMentor(track, mentor);

        MvcTestResult archived = send(HttpMethod.POST, path(track) + "/archive", "", "admin");
        MvcTestResult restored = send(HttpMethod.POST, path(track) + "/restore", "", "admin");

        assertThat(archived).hasStatusOk();
        assertThat(archived).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
        assertThat(archived)
                .bodyJson()
                .extractingPath("$.mentors[*].id")
                .asArray()
                .containsExactly(mentor.toString());
        assertThat(restored).hasStatusOk();
        assertThat(restored).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(restored)
                .bodyJson()
                .extractingPath("$.mentors[*].id")
                .asArray()
                .containsExactly(mentor.toString());
    }

    @Test
    @DisplayName("ENR-15.1 Each mentor of a track has the number of students they lead on it")
    void eachMentorOfATrackHasTheNumberOfStudentsTheyLeadOnIt() {
        UUID clara = coordinator("clara@x.test", "clara");
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID other = tracks.active("SWE", "Software Engineering");
        tracks.addCoordinator(track, clara);
        UUID first = person("alice@x.test", "Alice", "First", UserStatus.ACTIVE, Role.MENTOR);
        UUID second = person("boris@x.test", "Boris", "Second", UserStatus.DISABLED, Role.MENTOR);
        UUID third = person("cora@x.test", "Cora", "Third", UserStatus.ACTIVE, Role.MENTOR);
        tracks.addMentor(track, first);
        tracks.addMentor(track, second);
        tracks.addMentor(track, third);
        UUID active = person("s1@x.test", "Sam", "One", UserStatus.ACTIVE, Role.STUDENT);
        UUID disabled = person("s2@x.test", "Sam", "Two", UserStatus.DISABLED, Role.STUDENT);
        UUID removed = person("s3@x.test", "Sam", "Three", UserStatus.ACTIVE, Role.STUDENT);
        UUID elsewhere = person("s4@x.test", "Sam", "Four", UserStatus.ACTIVE, Role.STUDENT);
        UUID ledBySecond = person("s5@x.test", "Sam", "Five", UserStatus.ACTIVE, Role.STUDENT);
        LocalDate start = LocalDate.of(2026, 9, 1);
        enrollments.addMentor(enrollments.enroll(active, track, admin, start), first, admin);
        enrollments.addMentor(enrollments.enroll(disabled, track, admin, start), first, admin);
        UUID thirdEnrollment = enrollments.enroll(removed, track, admin, start);
        enrollments.addMentor(thirdEnrollment, first, admin);
        enrollments.removeMentor(thirdEnrollment, first);
        enrollments.addMentor(enrollments.enroll(elsewhere, other, admin, start), first, admin);
        enrollments.addMentor(enrollments.enroll(ledBySecond, track, admin, start), second, admin);

        MvcTestResult result = send(HttpMethod.GET, path(track), "", "clara");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.mentors[*].id")
                .asArray()
                .containsExactly(first.toString(), second.toString(), third.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.mentors[*].students")
                .asArray()
                .containsExactly(2, 1, 0);
    }

    private static String path(UUID track) {
        return "/api/v1/tracks/" + track;
    }

    private static String mentorPath(UUID track, UUID user) {
        return path(track) + "/mentors/" + user;
    }

    private List<String> mentorIds(UUID track) {
        MvcTestResult view = send(HttpMethod.GET, path(track), "", "admin");
        assertThat(view).hasStatusOk();
        return JsonPath.read(
                new String(view.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.mentors[*].id");
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

    private MvcTestResult send(HttpMethod method, String uri, String body, String subject) {
        MockMvcRequestBuilder request = mvc.method(method)
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, issuer.token(subject).bearer());
        if (!body.isEmpty()) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
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
