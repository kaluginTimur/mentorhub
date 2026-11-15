package com.mentorhub.tracks.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.mentorhub.support.EndpointTest;
import com.mentorhub.tracks.internal.TestTracks;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.web.Problems;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Tracks through the real chain: signed tokens, the gate, permissions expanded from stored roles, the services and the
 * database. Tracks are set up in the database; what a request changes is read back through the API.
 */
@EndpointTest
class TracksEndpointTests {

    static final URI TRACK_ARCHIVED = URI.create("urn:mentorhub:problem:track-archived");
    static final URI COORDINATOR_NOT_ELIGIBLE = URI.create("urn:mentorhub:problem:coordinator-not-eligible");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    TestUsers users;
    TestTracks tracks;
    UUID admin;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        tracks = new TestTracks(jdbc);
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "admin");
    }

    @Test
    @DisplayName("TRK-1.1 Reading tracks needs TRACKS_READ")
    void readingTracksNeedsTracksRead() {
        UUID coordinator = users.create("coordinator@x.test", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(coordinator, "coordinator");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, coordinator);

        for (String uri :
                List.of("/api/v1/tracks", "/api/v1/tracks/" + track, "/api/v1/tracks/" + track + "/history")) {
            assertProblem(send(HttpMethod.GET, uri, "", "coordinator"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
        }
    }

    // No role holds TRACKS_READ without TRACKS_MANAGE, so the caller is authenticated with exactly that permission.
    @Test
    @DisplayName("TRK-1.2 Changing a track needs TRACKS_MANAGE")
    void changingATrackNeedsTracksManage() {
        UUID reader = users.create("reader@x.test", UserStatus.ACTIVE);
        UUID coordinator = coordinator("coordinator@x.test", "Clara", "Coordinator");
        UUID candidate = coordinator("candidate@x.test", "Boris", "Candidate");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, coordinator);
        RequestPostProcessor caller = TestAuthentications.activeUser(reader, Permission.TRACKS_READ);
        String path = "/api/v1/tracks/" + track;

        List<MvcTestResult> refusals = List.of(
                send(HttpMethod.POST, "/api/v1/tracks", "{\"code\": \"QE\", \"name\": \"New\"}", caller),
                send(HttpMethod.PUT, path, "{\"code\": \"QE\", \"name\": \"Quality Engineering\"}", caller),
                send(HttpMethod.POST, path + "/archive", "", caller),
                send(HttpMethod.POST, path + "/restore", "", caller),
                send(HttpMethod.GET, path + "/coordinator-candidates", "", caller),
                send(HttpMethod.PUT, path + "/coordinators/" + candidate, "", caller),
                send(HttpMethod.DELETE, path + "/coordinators/" + coordinator, "", caller));

        refusals.forEach(result -> assertProblem(result, HttpStatus.FORBIDDEN, Problems.FORBIDDEN));
        MvcTestResult all = send(HttpMethod.GET, "/api/v1/tracks?status=ALL", "", "admin");
        assertThat(all).hasStatusOk();
        assertThat(all).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(track.toString());
        assertThat(all).bodyJson().extractingPath("$.items[0].code").isEqualTo("QA");
        assertThat(all).bodyJson().extractingPath("$.items[0].name").isEqualTo("Quality Assurance");
        assertThat(all).bodyJson().extractingPath("$.items[0].status").isEqualTo("ACTIVE");
        assertThat(all)
                .bodyJson()
                .extractingPath("$.items[0].coordinators[*].id")
                .asArray()
                .containsExactly(coordinator.toString());
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("TRK-2.1 A track is created active without coordinators")
    void trackIsCreatedActiveWithoutCoordinators() {
        MvcTestResult result = send(
                HttpMethod.POST, "/api/v1/tracks", "{\"code\": \" QA \", \"name\": \"Quality Assurance\"}", "admin");

        assertThat(result).hasStatus(HttpStatus.CREATED);
        assertThat(result).bodyJson().extractingPath("$.id").isNotNull();
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("QA");
        assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Quality Assurance");
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(result).bodyJson().extractingPath("$.coordinators").asArray().isEmpty();
    }

    @Test
    @DisplayName("TRK-2.2 An invalid track is rejected")
    void invalidTrackIsRejected() {
        UUID track = tracks.active("QA", "Quality Assurance");
        String path = "/api/v1/tracks/" + track;

        assertValidationProblem(
                send(HttpMethod.POST, "/api/v1/tracks", "{\"code\": \"   \", \"name\": \"Quality\"}", "admin"), "code");
        assertValidationProblem(
                send(HttpMethod.POST, "/api/v1/tracks", "{\"code\": \"QE\", \"name\": \"\"}", "admin"), "name");
        assertValidationProblem(send(HttpMethod.PUT, path, "{\"code\": \"\", \"name\": \"Quality\"}", "admin"), "code");
        assertValidationProblem(send(HttpMethod.PUT, path, "{\"code\": \"QE\", \"name\": \"  \"}", "admin"), "name");

        MvcTestResult all = send(HttpMethod.GET, "/api/v1/tracks?status=ALL", "", "admin");
        assertThat(all).hasStatusOk();
        assertThat(all).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(track.toString());
        assertThat(all).bodyJson().extractingPath("$.items[0].code").isEqualTo("QA");
        assertThat(all).bodyJson().extractingPath("$.items[0].name").isEqualTo("Quality Assurance");
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("TRK-2.3 The code and the name of another track are accepted")
    void codeAndNameOfAnotherTrackAreAccepted() {
        UUID first = tracks.active("QA", "Quality Assurance");

        MvcTestResult created =
                send(HttpMethod.POST, "/api/v1/tracks", "{\"code\": \"QA\", \"name\": \"Quality Assurance\"}", "admin");

        assertThat(created).hasStatus(HttpStatus.CREATED);
        String second = jsonString(created, "$.id");
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/tracks", "", "admin");
        assertThat(list).hasStatusOk();
        assertThat(list)
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactlyInAnyOrder(first.toString(), second);
    }

    @Test
    @DisplayName("TRK-3.1 The status narrows the list")
    void statusNarrowsTheList() {
        // Inserted out of order, so the order of the answer is the sorting's.
        UUID software = tracks.active("SWE", "Software Engineering");
        UUID testing = tracks.active("QA", "Testing");
        UUID automation = tracks.archived("QA", "Automation");

        MvcTestResult active = send(HttpMethod.GET, "/api/v1/tracks", "", "admin");
        MvcTestResult archived = send(HttpMethod.GET, "/api/v1/tracks?status=ARCHIVED", "", "admin");
        MvcTestResult all = send(HttpMethod.GET, "/api/v1/tracks?status=ALL", "", "admin");

        assertThat(active).hasStatusOk();
        assertThat(active)
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly(testing.toString(), software.toString());
        assertThat(archived).hasStatusOk();
        assertThat(archived)
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly(automation.toString());
        assertThat(all).hasStatusOk();
        assertThat(all)
                .bodyJson()
                .extractingPath("$.items[*].id")
                .asArray()
                .containsExactly(automation.toString(), testing.toString(), software.toString());
    }

    @Test
    @DisplayName("TRK-4.1 A coordinator who is disabled or lost the role is marked")
    void coordinatorWhoIsDisabledOrLostTheRoleIsMarked() {
        UUID active = coordinator("alice@x.test", "Alice", "Active");
        UUID disabled = users.create("boris@x.test", UserStatus.DISABLED, Role.COORDINATOR);
        users.rename(disabled, "Boris", "Disabled");
        UUID withoutRole = users.create("clara@x.test", UserStatus.ACTIVE, Role.MENTOR);
        users.rename(withoutRole, "Clara", "Mentor");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, active);
        tracks.addCoordinator(track, disabled);
        tracks.addCoordinator(track, withoutRole);

        MvcTestResult result = send(HttpMethod.GET, "/api/v1/tracks/" + track, "", "admin");

        assertThat(result).hasStatusOk();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.coordinators[*].id")
                .asArray()
                .containsExactly(active.toString(), disabled.toString(), withoutRole.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.coordinators[*].disabled")
                .asArray()
                .containsExactly(false, true, false);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.coordinators[*].coordinatorRole")
                .asArray()
                .containsExactly(true, true, false);
    }

    @Test
    @DisplayName("TRK-4.2 An unknown track is not found")
    void unknownTrackIsNotFound() {
        UUID candidate = coordinator("candidate@x.test", "Boris", "Candidate");
        String path = "/api/v1/tracks/" + UUID.randomUUID();

        List<MvcTestResult> results = List.of(
                send(HttpMethod.GET, path, "", "admin"),
                send(HttpMethod.GET, path + "/history", "", "admin"),
                send(HttpMethod.PUT, path, "{\"code\": \"QA\", \"name\": \"Quality Assurance\"}", "admin"),
                send(HttpMethod.POST, path + "/archive", "", "admin"),
                send(HttpMethod.POST, path + "/restore", "", "admin"),
                send(HttpMethod.PUT, path + "/coordinators/" + candidate, "", "admin"),
                send(HttpMethod.DELETE, path + "/coordinators/" + candidate, "", "admin"));

        results.forEach(result -> assertProblem(result, HttpStatus.NOT_FOUND, Problems.NOT_FOUND));
    }

    @Test
    @DisplayName("TRK-5.1 An archived track keeps its coordinators and is restored with them")
    void archivedTrackKeepsItsCoordinatorsAndIsRestoredWithThem() {
        UUID coordinator = coordinator("clara@x.test", "Clara", "Coordinator");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, coordinator);
        String path = "/api/v1/tracks/" + track;

        MvcTestResult archived = send(HttpMethod.POST, path + "/archive", "", "admin");

        assertThat(archived).hasStatusOk();
        assertThat(archived).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
        assertThat(archived)
                .bodyJson()
                .extractingPath("$.coordinators[*].id")
                .asArray()
                .containsExactly(coordinator.toString());

        MvcTestResult restored = send(HttpMethod.POST, path + "/restore", "", "admin");

        assertThat(restored).hasStatusOk();
        assertThat(restored).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(restored)
                .bodyJson()
                .extractingPath("$.coordinators[*].id")
                .asArray()
                .containsExactly(coordinator.toString());
    }

    @Test
    @DisplayName("TRK-5.2 An archived track cannot be changed")
    void archivedTrackCannotBeChanged() {
        UUID coordinator = coordinator("clara@x.test", "Clara", "Coordinator");
        UUID candidate = coordinator("boris@x.test", "Boris", "Candidate");
        UUID track = tracks.archived("QA", "Quality Assurance");
        tracks.addCoordinator(track, coordinator);
        String path = "/api/v1/tracks/" + track;

        List<MvcTestResult> refusals = List.of(
                send(HttpMethod.PUT, path, "{\"code\": \"QA\", \"name\": \"Quality Engineering\"}", "admin"),
                send(HttpMethod.PUT, path, "{\"code\": \"QA\", \"name\": \"Quality Assurance\"}", "admin"),
                send(HttpMethod.GET, path + "/coordinator-candidates", "", "admin"),
                send(HttpMethod.PUT, path + "/coordinators/" + candidate, "", "admin"),
                send(HttpMethod.PUT, path + "/coordinators/" + coordinator, "", "admin"),
                send(HttpMethod.DELETE, path + "/coordinators/" + coordinator, "", "admin"),
                send(HttpMethod.DELETE, path + "/coordinators/" + candidate, "", "admin"));

        refusals.forEach(result -> assertProblem(result, HttpStatus.CONFLICT, TRACK_ARCHIVED));
        MvcTestResult after = send(HttpMethod.GET, path, "", "admin");
        assertThat(after).hasStatusOk();
        assertThat(after).bodyJson().extractingPath("$.code").isEqualTo("QA");
        assertThat(after).bodyJson().extractingPath("$.name").isEqualTo("Quality Assurance");
        assertThat(after).bodyJson().extractingPath("$.status").isEqualTo("ARCHIVED");
        assertThat(after)
                .bodyJson()
                .extractingPath("$.coordinators[*].id")
                .asArray()
                .containsExactly(coordinator.toString());
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("TRK-5.3 A change to the same values records nothing")
    void changeToTheSameValuesRecordsNothing() {
        UUID track = tracks.active("QA", "Quality Assurance");
        String path = "/api/v1/tracks/" + track;

        MvcTestResult saved =
                send(HttpMethod.PUT, path, "{\"code\": \"QA\", \"name\": \"Quality Assurance\"}", "admin");
        MvcTestResult restored = send(HttpMethod.POST, path + "/restore", "", "admin");

        for (MvcTestResult result : List.of(saved, restored)) {
            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(track.toString());
            assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("QA");
            assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Quality Assurance");
            assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        }
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("TRK-6.1 Only an active user with the coordinator role is named")
    void onlyAnActiveUserWithTheCoordinatorRoleIsNamed() {
        UUID disabled = users.create("boris@x.test", UserStatus.DISABLED, Role.COORDINATOR);
        UUID withoutRole = users.create("clara@x.test", UserStatus.ACTIVE, Role.MENTOR);
        UUID track = tracks.active("QA", "Quality Assurance");
        String path = "/api/v1/tracks/" + track + "/coordinators/";

        for (UUID user : List.of(disabled, withoutRole, UUID.randomUUID())) {
            assertProblem(
                    send(HttpMethod.PUT, path + user, "", "admin"), HttpStatus.CONFLICT, COORDINATOR_NOT_ELIGIBLE);
        }
        MvcTestResult after = send(HttpMethod.GET, "/api/v1/tracks/" + track, "", "admin");
        assertThat(after).hasStatusOk();
        assertThat(after).bodyJson().extractingPath("$.coordinators").asArray().isEmpty();
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("TRK-6.2 A disabled coordinator can be removed")
    void disabledCoordinatorCanBeRemoved() {
        UUID disabled = users.create("boris@x.test", UserStatus.DISABLED, Role.COORDINATOR);
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, disabled);

        MvcTestResult removed =
                send(HttpMethod.DELETE, "/api/v1/tracks/" + track + "/coordinators/" + disabled, "", "admin");

        assertThat(removed).hasStatusOk();
        assertThat(removed)
                .bodyJson()
                .extractingPath("$.coordinators")
                .asArray()
                .isEmpty();
        MvcTestResult after = send(HttpMethod.GET, "/api/v1/tracks/" + track, "", "admin");
        assertThat(after).bodyJson().extractingPath("$.coordinators").asArray().isEmpty();
    }

    @Test
    @DisplayName("TRK-6.3 A repeated naming or removal records nothing")
    void repeatedNamingOrRemovalRecordsNothing() {
        UUID coordinator = coordinator("clara@x.test", "Clara", "Coordinator");
        UUID other = coordinator("boris@x.test", "Boris", "Other");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, coordinator);
        String path = "/api/v1/tracks/" + track + "/coordinators/";

        List<MvcTestResult> results = List.of(
                send(HttpMethod.PUT, path + coordinator, "", "admin"),
                send(HttpMethod.DELETE, path + other, "", "admin"),
                send(HttpMethod.DELETE, path + UUID.randomUUID(), "", "admin"));

        for (MvcTestResult result : results) {
            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(track.toString());
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.coordinators[*].id")
                    .asArray()
                    .containsExactly(coordinator.toString());
        }
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("TRK-6.5 The candidates are the eligible users not yet named")
    void candidatesAreTheEligibleUsersNotYetNamed() {
        UUID manager = users.create("manager@x.test", UserStatus.ACTIVE);
        UUID coordinator = coordinator("clara@x.test", "Clara", "Coordinator");
        UUID other = coordinator("boris@x.test", "Boris", "Other");
        users.create("dina@x.test", UserStatus.DISABLED, Role.COORDINATOR);
        users.create("egor@x.test", UserStatus.ACTIVE, Role.MENTOR);
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, coordinator);

        MvcTestResult result = send(
                HttpMethod.GET,
                "/api/v1/tracks/" + track + "/coordinator-candidates",
                "",
                TestAuthentications.activeUser(manager, Permission.TRACKS_MANAGE));

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(other.toString());
    }

    @Test
    @DisplayName("TRK-7.1 The history tells each change of the track")
    void historyTellsEachChangeOfTheTrack() {
        UUID coordinator = coordinator("clara@x.test", "Clara", "Coordinator");

        MvcTestResult created =
                send(HttpMethod.POST, "/api/v1/tracks", "{\"code\": \"QA\", \"name\": \"Quality Assurance\"}", "admin");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String path = "/api/v1/tracks/" + jsonString(created, "$.id");
        assertThat(send(HttpMethod.PUT, path, "{\"code\": \"QA\", \"name\": \"Quality Engineering\"}", "admin"))
                .hasStatusOk();
        assertThat(send(HttpMethod.PUT, path + "/coordinators/" + coordinator, "", "admin"))
                .hasStatusOk();
        assertThat(send(HttpMethod.DELETE, path + "/coordinators/" + coordinator, "", "admin"))
                .hasStatusOk();
        assertThat(send(HttpMethod.POST, path + "/archive", "", "admin")).hasStatusOk();
        assertThat(send(HttpMethod.POST, path + "/restore", "", "admin")).hasStatusOk();

        MvcTestResult history = send(HttpMethod.GET, path + "/history", "", "admin");

        assertThat(history).hasStatusOk();
        assertThat(history).bodyJson().extractingPath("$.totalItems").isEqualTo(6);
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[*].action")
                .asArray()
                .containsExactly(
                        "TRACK_RESTORED",
                        "TRACK_ARCHIVED",
                        "TRACK_COORDINATOR_REMOVED",
                        "TRACK_COORDINATOR_ADDED",
                        "TRACK_UPDATED",
                        "TRACK_CREATED");
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[*].actor.id")
                .asArray()
                .hasSize(6)
                .containsOnly(admin.toString());
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[*].actor.name")
                .asArray()
                .containsOnly("Anna Petrova");
        for (int i : new int[] {0, 1}) {
            assertThat(history)
                    .bodyJson()
                    .doesNotHavePath("$.items[%d].code".formatted(i))
                    .doesNotHavePath("$.items[%d].name".formatted(i))
                    .doesNotHavePath("$.items[%d].changes".formatted(i))
                    .doesNotHavePath("$.items[%d].user".formatted(i));
        }
        for (int i : new int[] {2, 3}) {
            assertThat(history)
                    .bodyJson()
                    .extractingPath("$.items[%d].user.id".formatted(i))
                    .isEqualTo(coordinator.toString());
            assertThat(history)
                    .bodyJson()
                    .extractingPath("$.items[%d].user.name".formatted(i))
                    .isEqualTo("Clara Coordinator");
        }
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[4].changes")
                .isEqualTo(List.of(
                        Map.of("field", "name", "oldValue", "Quality Assurance", "newValue", "Quality Engineering")));
        assertThat(history).bodyJson().extractingPath("$.items[5].code").isEqualTo("QA");
        assertThat(history).bodyJson().extractingPath("$.items[5].name").isEqualTo("Quality Assurance");
    }

    @Test
    @DisplayName("USR-10.3 Naming a coordinator of a track shows in the user's history")
    void namingACoordinatorOfATrackShowsInTheUsersHistory() {
        UUID coordinator = coordinator("clara@x.test", "Clara", "Coordinator");
        UUID track = tracks.active("QA", "Quality Assurance");
        String path = "/api/v1/tracks/" + track;

        assertThat(send(HttpMethod.PUT, path + "/coordinators/" + coordinator, "", "admin"))
                .hasStatusOk();
        assertThat(send(HttpMethod.PUT, path, "{\"code\": \"QE\", \"name\": \"Quality Engineering\"}", "admin"))
                .hasStatusOk();

        MvcTestResult history = send(HttpMethod.GET, "/api/v1/users/" + coordinator + "/history", "", "admin");

        assertThat(history).hasStatusOk();
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[*].action")
                .asArray()
                .containsExactly("USER_TRACK_COORDINATOR_ADDED");
        assertThat(history).bodyJson().extractingPath("$.items[0].actor.id").isEqualTo(admin.toString());
        assertThat(history).bodyJson().extractingPath("$.items[0].track.id").isEqualTo(track.toString());
        assertThat(history).bodyJson().extractingPath("$.items[0].track.code").isEqualTo("QA");
        assertThat(history).bodyJson().extractingPath("$.items[0].track.name").isEqualTo("Quality Assurance");
    }

    // The user's side of a removal, which no scenario names: the catalog's USER_TRACK_COORDINATOR_REMOVED row.
    @Test
    @DisplayName("Removing a coordinator shows in the user's history with the track as it was")
    void removingACoordinatorShowsInTheUsersHistory() {
        UUID coordinator = coordinator("clara@x.test", "Clara", "Coordinator");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(track, coordinator);
        String path = "/api/v1/tracks/" + track;

        assertThat(send(HttpMethod.DELETE, path + "/coordinators/" + coordinator, "", "admin"))
                .hasStatusOk();
        assertThat(send(HttpMethod.PUT, path, "{\"code\": \"QE\", \"name\": \"Quality Engineering\"}", "admin"))
                .hasStatusOk();

        MvcTestResult history = send(HttpMethod.GET, "/api/v1/users/" + coordinator + "/history", "", "admin");

        assertThat(history).hasStatusOk();
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[*].action")
                .asArray()
                .containsExactly("USER_TRACK_COORDINATOR_REMOVED");
        assertThat(history).bodyJson().extractingPath("$.items[0].actor.id").isEqualTo(admin.toString());
        assertThat(history).bodyJson().extractingPath("$.items[0].track.id").isEqualTo(track.toString());
        assertThat(history).bodyJson().extractingPath("$.items[0].track.code").isEqualTo("QA");
        assertThat(history).bodyJson().extractingPath("$.items[0].track.name").isEqualTo("Quality Assurance");
    }

    private UUID coordinator(String email, String firstName, String lastName) {
        UUID id = users.create(email, UserStatus.ACTIVE, Role.COORDINATOR);
        users.rename(id, firstName, lastName);
        return id;
    }

    private MvcTestResult send(HttpMethod method, String uri, String body, String subject) {
        return send(method, uri, body, request -> {
            request.addHeader(HttpHeaders.AUTHORIZATION, issuer.token(subject).bearer());
            return request;
        });
    }

    private MvcTestResult send(HttpMethod method, String uri, String body, RequestPostProcessor caller) {
        MockMvcRequestBuilder request = mvc.method(method).uri(uri).with(caller);
        if (!body.isEmpty()) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    private static String jsonString(MvcTestResult result, String path) {
        return JsonPath.read(new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
    }

    private static void assertValidationProblem(MvcTestResult result, String field) {
        assertProblem(result, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .contains(field);
    }
}
