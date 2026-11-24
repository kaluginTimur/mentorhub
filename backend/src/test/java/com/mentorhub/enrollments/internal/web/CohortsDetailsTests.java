package com.mentorhub.enrollments.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Details of the cohorts that no scenario pins: edges of the request, ordering, refusals and the schema. */
@EndpointTest
class CohortsDetailsTests {

    static final URI TRACK_ARCHIVED = URI.create("urn:mentorhub:problem:track-archived");
    static final URI COHORT_NOT_ELIGIBLE = URI.create("urn:mentorhub:problem:cohort-not-eligible");
    static final URI COHORT_NOT_EMPTY = URI.create("urn:mentorhub:problem:cohort-not-empty");

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
    void anIdGivenTwiceCountsOnceAndANameOfTheLimitIsKept() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID e = enrollments.enroll(student("a@x.test", "Anna"), track, admin, START);
        String name = "x".repeat(100);

        MvcTestResult created = send(
                HttpMethod.POST,
                base(track) + "/cohorts",
                "{\"name\": \" %s \", \"startDate\": \"2026-03-01\", \"enrollmentIds\": [\"%s\", \"%s\"]}"
                        .formatted(name, e, e),
                "admin");

        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).bodyJson().extractingPath("$.name").isEqualTo(name);
        assertThat(created).bodyJson().extractingPath("$.enrollmentCount").isEqualTo(1);
        MvcTestResult history = send(HttpMethod.GET, base(track) + "/history", "", "admin");
        assertThat(JsonPath.<List<String>>read(body(history), "$.items[*].action"))
                .containsExactlyInAnyOrder("TRACK_COHORT_CREATED", "TRACK_ENROLLMENT_COHORT_CHANGED");
    }

    @Test
    void aNullAmongTheEnrollmentIdsIsRejectedAndNothingIsStoredOrAudited() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID e = enrollments.enroll(student("a@x.test", "Anna"), track, admin, START);

        for (String ids : List.of("[null]", "[\"%s\", null]".formatted(e))) {
            assertProblem(
                    send(
                            HttpMethod.POST,
                            base(track) + "/cohorts",
                            "{\"name\": \"Winter\", \"startDate\": \"2027-01-11\", \"enrollmentIds\": %s}"
                                    .formatted(ids),
                            "admin"),
                    HttpStatus.BAD_REQUEST,
                    Problems.VALIDATION_FAILED);
            assertProblem(
                    send(
                            HttpMethod.POST,
                            base(track) + "/cohorts/" + cohort + "/enrollments",
                            "{\"enrollmentIds\": %s}".formatted(ids),
                            "admin"),
                    HttpStatus.BAD_REQUEST,
                    Problems.VALIDATION_FAILED);
        }

        assertThat(count("cohorts")).isEqualTo(1);
        assertThat(cohortOf(track, e)).isNull();
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    void aNameOverTheLimitOrAnInvalidDateIsRejectedAndNothingIsStored() {
        UUID track = tracks.active("QA", "Quality Assurance");
        String path = base(track) + "/cohorts";

        MvcTestResult tooLong = send(
                HttpMethod.POST,
                path,
                "{\"name\": \"%s\", \"startDate\": \"2026-03-01\"}".formatted("x".repeat(101)),
                "admin");
        MvcTestResult badDate =
                send(HttpMethod.POST, path, "{\"name\": \"Spring\", \"startDate\": \"2026-02-30\"}", "admin");

        assertProblem(tooLong, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertProblem(badDate, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(count("cohorts")).isZero();
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    void anAdditionWithoutEnrollmentsIsBadEvenForAnUnknownCohortOrAForeignCoordinator() {
        UUID eva = coordinator("eva@x.test", "eva");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(tracks.active("SWE", "Software Engineering"), eva);
        UUID cohort = cohorts.create(track, "Spring", START);

        assertProblem(
                send(
                        HttpMethod.POST,
                        base(track) + "/cohorts/" + UUID.randomUUID() + "/enrollments",
                        "{\"enrollmentIds\": []}",
                        "admin"),
                HttpStatus.BAD_REQUEST,
                Problems.VALIDATION_FAILED);
        assertProblem(
                send(HttpMethod.POST, base(track) + "/cohorts/" + cohort + "/enrollments", "{}", "eva"),
                HttpStatus.BAD_REQUEST,
                Problems.VALIDATION_FAILED);
    }

    @Test
    void aMissingCohortIsNotFoundBeforeTheRelationIsChecked() {
        UUID eva = coordinator("eva@x.test", "eva");
        UUID track = tracks.active("QA", "Quality Assurance");
        tracks.addCoordinator(tracks.active("SWE", "Software Engineering"), eva);
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID missing = UUID.randomUUID();

        assertProblem(
                send(HttpMethod.DELETE, base(track) + "/cohorts/" + missing, "", "eva"),
                HttpStatus.NOT_FOUND,
                Problems.NOT_FOUND);
        assertProblem(
                send(HttpMethod.GET, base(track) + "/cohorts/" + missing + "/candidates", "", "eva"),
                HttpStatus.NOT_FOUND,
                Problems.NOT_FOUND);
        assertProblem(
                send(HttpMethod.DELETE, base(track) + "/cohorts/" + cohort, "", "eva"),
                HttpStatus.FORBIDDEN,
                Problems.FORBIDDEN);
    }

    @Test
    void takingOutThroughAnotherCohortChangesNothing() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID first = cohorts.create(track, "First", START);
        UUID second = cohorts.create(track, "Second", START);
        UUID e = enrollments.enroll(student("a@x.test", "Anna"), track, admin, START);
        cohorts.put(e, first);

        MvcTestResult result =
                send(HttpMethod.DELETE, base(track) + "/cohorts/" + second + "/enrollments/" + e, "", "admin");

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(cohortOf(track, e)).isEqualTo(first.toString());
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    void aDeletedCohortIsNotFoundForAnAdditionAndNotEligibleForEnrolling() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Gone", START);
        UUID e = enrollments.enroll(student("a@x.test", "Anna"), track, admin, START);
        UUID other = student("b@x.test", "Boris");
        assertThat(send(HttpMethod.DELETE, base(track) + "/cohorts/" + cohort, "", "admin"))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertProblem(
                send(
                        HttpMethod.POST,
                        base(track) + "/cohorts/" + cohort + "/enrollments",
                        "{\"enrollmentIds\": [\"%s\"]}".formatted(e),
                        "admin"),
                HttpStatus.NOT_FOUND,
                Problems.NOT_FOUND);
        assertProblem(
                send(
                        HttpMethod.POST,
                        base(track) + "/enrollments",
                        "{\"studentId\": \"%s\", \"startDate\": \"2026-09-01\", \"cohortId\": \"%s\"}"
                                .formatted(other, cohort),
                        "admin"),
                HttpStatus.CONFLICT,
                COHORT_NOT_ELIGIBLE);
        assertThat(cohortOf(track, e)).isNull();
    }

    @Test
    void enrollingIntoACohortOfAnArchivedTrackIsRefusedAsArchived() {
        UUID track = tracks.archived("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID other = student("b@x.test", "Boris");

        for (UUID id : List.of(cohort, UUID.randomUUID())) {
            assertProblem(
                    send(
                            HttpMethod.POST,
                            base(track) + "/enrollments",
                            "{\"studentId\": \"%s\", \"startDate\": \"2026-09-01\", \"cohortId\": \"%s\"}"
                                    .formatted(other, id),
                            "admin"),
                    HttpStatus.CONFLICT,
                    TRACK_ARCHIVED);
        }
    }

    @Test
    void theCohortsOfAnArchivedTrackStayReadableAndNarrowTheEnrollments() {
        UUID track = tracks.archived("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID e = enrollments.enroll(student("a@x.test", "Anna"), track, admin, START);
        cohorts.put(e, cohort);

        MvcTestResult list = send(HttpMethod.GET, base(track) + "/cohorts", "", "admin");
        MvcTestResult narrowed = send(HttpMethod.GET, base(track) + "/enrollments?cohortId=" + cohort, "", "admin");

        assertThat(JsonPath.<List<Integer>>read(body(list), "$.items[*].enrollmentCount"))
                .containsExactly(1);
        assertThat(JsonPath.<List<String>>read(body(narrowed), "$.items[*].id")).containsExactly(e.toString());
    }

    @Test
    void cohortsOfOneStartDateAreOrderedByNameWithoutRegardToCaseThenById() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID b = cohorts.create(track, "b", START);
        UUID a = cohorts.create(track, "A", START);
        UUID c = cohorts.create(track, "C", START);
        UUID later = cohorts.create(track, "Z", START.plusDays(1));

        MvcTestResult list = send(HttpMethod.GET, base(track) + "/cohorts", "", "admin");

        assertThat(JsonPath.<List<String>>read(body(list), "$.items[*].id"))
                .containsExactly(later.toString(), a.toString(), b.toString(), c.toString());
    }

    @Test
    void aDisabledStudentsEnrollmentCountsAndKeepsACohortFromBeingDeleted() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID e = enrollments.enroll(person("d@x.test", "Dina", UserStatus.DISABLED, Role.STUDENT), track, admin, START);
        cohorts.put(e, cohort);

        assertProblem(
                send(HttpMethod.DELETE, base(track) + "/cohorts/" + cohort, "", "admin"),
                HttpStatus.CONFLICT,
                COHORT_NOT_EMPTY);
        MvcTestResult candidates = send(HttpMethod.GET, base(track) + "/cohort-candidates", "", "admin");
        assertThat(JsonPath.<List<Boolean>>read(body(candidates), "$.items[*].student.disabled"))
                .containsExactly(true);
    }

    @Test
    void candidatesTreatWildcardsAsTextAreOrderedByNameAndPaged() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID cohort = cohorts.create(track, "Spring", START);
        UUID percent = enrollments.enroll(student("100%@x.test", "Zed"), track, admin, START);
        enrollments.enroll(student("plain@x.test", "Yan"), track, admin, START);
        UUID carol = enrollments.enroll(student("carol@x.test", "carol"), track, admin, START);
        UUID bob = enrollments.enroll(student("bob@x.test", "Bob"), track, admin, START);
        enrollments.enroll(student("d@x.test", "Dan"), track, admin, START);
        String path = base(track) + "/cohorts/" + cohort + "/candidates";

        MvcTestResult percentSearch = mvc.get()
                .uri(path + "?q={q}", "%")
                .header(HttpHeaders.AUTHORIZATION, issuer.token("admin").bearer())
                .exchange();
        MvcTestResult underscore = send(HttpMethod.GET, path + "?q=_", "", "admin");
        MvcTestResult firstPage = send(HttpMethod.GET, path + "?size=2", "", "admin");
        MvcTestResult secondPage = send(HttpMethod.GET, path + "?size=2&page=1", "", "admin");

        assertThat(JsonPath.<List<String>>read(body(percentSearch), "$.items[*].id"))
                .containsExactly(percent.toString());
        assertThat(JsonPath.<List<String>>read(body(underscore), "$.items[*].id"))
                .isEmpty();
        assertThat(JsonPath.<List<String>>read(body(firstPage), "$.items[*].id"))
                .containsExactly(bob.toString(), carol.toString());
        assertThat(firstPage).bodyJson().extractingPath("$.totalItems").isEqualTo(5);
        assertThat(JsonPath.<List<String>>read(body(secondPage), "$.items").size())
                .isEqualTo(2);
    }

    @Test
    void anAdditionNeverChangesTheEnrollmentsOfOtherCohorts() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID first = cohorts.create(track, "First", START);
        UUID second = cohorts.create(track, "Second", START);
        UUID kept = enrollments.enroll(student("a@x.test", "Anna"), track, admin, START);
        UUID moved = enrollments.enroll(student("b@x.test", "Boris"), track, admin, START);
        cohorts.put(kept, second);
        cohorts.put(moved, second);

        MvcTestResult added = send(
                HttpMethod.POST,
                base(track) + "/cohorts/" + first + "/enrollments",
                "{\"enrollmentIds\": [\"%s\"]}".formatted(moved),
                "admin");

        assertThat(added).hasStatusOk();
        assertThat(added).bodyJson().extractingPath("$.enrollmentCount").isEqualTo(1);
        assertThat(cohortOf(track, kept)).isEqualTo(second.toString());
        assertThat(cohortOf(track, moved)).isEqualTo(first.toString());
    }

    // The plan relies on the composite foreign key (cohort_id, track_id) and on the absence of on delete cascade, so
    // that no code path can break them.
    @Test
    void theDatabaseRefusesACohortOfAnotherTrackAndTheDeletionOfACohortWithMembers() {
        UUID track = tracks.active("QA", "Quality Assurance");
        UUID other = tracks.active("SWE", "Software Engineering");
        UUID foreign = cohorts.create(other, "Foreign", START);
        UUID own = cohorts.create(track, "Own", START);
        UUID e = enrollments.enroll(student("a@x.test", "Anna"), track, admin, START);

        assertThatThrownBy(() -> cohorts.put(e, foreign)).isInstanceOf(DataIntegrityViolationException.class);
        cohorts.put(e, own);
        assertThatThrownBy(() -> jdbc.sql("delete from cohorts where id = :id")
                        .param("id", own)
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long count(String table) {
        return users.count(table);
    }

    private UUID coordinator(String email, String subject) {
        UUID id = person(email, "Eva", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(id, subject);
        return id;
    }

    private UUID student(String email, String firstName) {
        return person(email, firstName, UserStatus.ACTIVE, Role.STUDENT);
    }

    private UUID person(String email, String firstName, UserStatus status, Role... roles) {
        UUID id = users.create(email, status, roles);
        users.rename(id, firstName, "Person");
        return id;
    }

    private static String base(UUID track) {
        return "/api/v1/tracks/" + track;
    }

    private String cohortOf(UUID track, UUID enrollment) {
        MvcTestResult list = send(HttpMethod.GET, base(track) + "/enrollments?size=100", "", "admin");
        assertThat(list).hasStatusOk();
        List<Object> found = JsonPath.read(body(list), "$.items[?(@.id == '" + enrollment + "')].cohort.id");
        return found.isEmpty() ? null : (String) found.get(0);
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

    private static String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }
}
