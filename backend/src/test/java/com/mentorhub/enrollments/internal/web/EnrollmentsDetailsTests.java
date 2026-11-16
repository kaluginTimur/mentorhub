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
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Details of enrollments from outside: the order of the checks, paging, ordering, search and edge cases. */
@EndpointTest
class EnrollmentsDetailsTests {

    static final URI TRACK_ARCHIVED = URI.create("urn:mentorhub:problem:track-archived");
    static final URI STUDENT_NOT_ELIGIBLE = URI.create("urn:mentorhub:problem:student-not-eligible");
    static final URI STUDENT_ALREADY_ENROLLED = URI.create("urn:mentorhub:problem:student-already-enrolled");
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
    UUID clara;
    UUID mentor;
    UUID ownTrack;
    UUID otherTrack;
    UUID student;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        tracks = new TestTracks(jdbc);
        enrollments = new TestEnrollments(jdbc);
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "admin");
        clara = person("clara@x.test", "Clara", "Coordinator", UserStatus.ACTIVE, Role.COORDINATOR);
        users.link(clara, "clara");
        mentor = person("mentor@x.test", "Mia", "Mentor", UserStatus.ACTIVE, Role.MENTOR);
        users.link(mentor, "mentor");
        ownTrack = tracks.active("QA", "Quality Assurance");
        otherTrack = tracks.active("SWE", "Software Engineering");
        tracks.addCoordinator(ownTrack, clara);
        student = person("sam@x.test", "Sam", "Student", UserStatus.ACTIVE, Role.STUDENT);
    }

    @Nested
    class OrderOfTheChecks {

        @Test
        void thePermissionComesBeforeValidityOfThePagingOfTheEnrollments() {
            assertProblem(
                    send(HttpMethod.GET, "/api/v1/tracks/" + UUID.randomUUID() + "/enrollments?size=0", "", "mentor"),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
        }

        @Test
        void thePermissionComesBeforeValidityOfTheCandidatesQuery() {
            assertProblem(
                    send(
                            HttpMethod.GET,
                            "/api/v1/tracks/" + UUID.randomUUID() + "/enrollment-candidates?page=-1&q="
                                    + "x".repeat(101),
                            "",
                            "mentor"),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
        }

        @Test
        void validityComesBeforeTheTrack() {
            UUID unknown = UUID.randomUUID();
            assertProblem(
                    send(HttpMethod.GET, "/api/v1/tracks/" + unknown + "/enrollments?size=0", "", "admin"),
                    HttpStatus.BAD_REQUEST,
                    Problems.VALIDATION_FAILED);
            assertProblem(
                    send(HttpMethod.GET, "/api/v1/tracks/" + unknown + "/enrollment-candidates?page=-1", "", "admin"),
                    HttpStatus.BAD_REQUEST,
                    Problems.VALIDATION_FAILED);
            assertProblem(
                    send(
                            HttpMethod.GET,
                            "/api/v1/tracks/" + unknown + "/enrollment-candidates?q=" + "x".repeat(101),
                            "",
                            "admin"),
                    HttpStatus.BAD_REQUEST,
                    Problems.VALIDATION_FAILED);
            assertProblem(
                    send(HttpMethod.POST, "/api/v1/tracks/" + unknown + "/enrollments", "{}", "admin"),
                    HttpStatus.BAD_REQUEST,
                    Problems.VALIDATION_FAILED);
        }

        @Test
        void validityComesBeforeTheRelation() {
            assertProblem(
                    send(
                            HttpMethod.GET,
                            "/api/v1/tracks/" + otherTrack + "/enrollment-candidates?q=" + "x".repeat(101),
                            "",
                            "clara"),
                    HttpStatus.BAD_REQUEST,
                    Problems.VALIDATION_FAILED);
            assertProblem(
                    send(
                            HttpMethod.GET,
                            "/api/v1/tracks/" + otherTrack + "/enrollment-candidates?size=101",
                            "",
                            "clara"),
                    HttpStatus.BAD_REQUEST,
                    Problems.VALIDATION_FAILED);
            assertProblem(
                    send(HttpMethod.POST, "/api/v1/tracks/" + otherTrack + "/enrollments", "{}", "clara"),
                    HttpStatus.BAD_REQUEST,
                    Problems.VALIDATION_FAILED);
        }

        @Test
        void theRelationComesBeforeTheStatusAndTheConflicts() {
            UUID archived = tracks.archived("OLD", "Old Track");
            UUID disabled = person("dina@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.STUDENT);

            assertProblem(
                    send(HttpMethod.GET, "/api/v1/tracks/" + archived + "/enrollment-candidates", "", "clara"),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
            assertProblem(enroll(archived, student, "clara"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
            assertProblem(enroll(otherTrack, disabled, "clara"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
            assertThat(users.count("audit_log")).isZero();
        }

        @Test
        void anArchivedTrackIsReportedBeforeTheStudent() {
            UUID archived = tracks.archived("OLD", "Old Track");
            UUID disabled = person("dina@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.STUDENT);
            UUID enrolled = person("edna@x.test", "Edna", "Enrolled", UserStatus.ACTIVE, Role.STUDENT);
            enrollments.enroll(enrolled, otherTrack, admin, START);

            for (UUID candidate : List.of(student, disabled, enrolled, UUID.randomUUID())) {
                assertProblem(enroll(archived, candidate, "admin"), HttpStatus.CONFLICT, TRACK_ARCHIVED);
            }
        }

        @Test
        void eligibilityIsReportedBeforeAnActiveEnrollment() {
            UUID enrolled = person("edna@x.test", "Edna", "Enrolled", UserStatus.ACTIVE, Role.STUDENT);
            enrollments.enroll(enrolled, otherTrack, admin, START);
            jdbc.sql("update users set status = 'DISABLED', disabled_reason = 'SECURITY' where id = :id")
                    .param("id", enrolled)
                    .update();

            assertProblem(enroll(ownTrack, enrolled, "admin"), HttpStatus.CONFLICT, STUDENT_NOT_ELIGIBLE);
        }
    }

    @Nested
    class PagingOfTheEnrollments {

        @BeforeEach
        void enrollFour() {
            // Enrolled out of order; the order of the answer is the student's name, then the email.
            enrollments.enroll(
                    person("d@x.test", "dora", "Zed", UserStatus.ACTIVE, Role.STUDENT), ownTrack, admin, START);
            enrollments.enroll(
                    person("b2@x.test", "Bob", "Same", UserStatus.ACTIVE, Role.STUDENT), ownTrack, admin, START);
            enrollments.enroll(
                    person("b1@x.test", "Bob", "Same", UserStatus.ACTIVE, Role.STUDENT), ownTrack, admin, START);
            enrollments.enroll(
                    person("a@x.test", "Ann", "Zed", UserStatus.ACTIVE, Role.STUDENT), ownTrack, admin, START);
        }

        @Test
        void theOrderIsTheNameIgnoringCaseThenTheEmail() {
            MvcTestResult result = send(HttpMethod.GET, path(ownTrack, ""), "", "admin");

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[*].student.email")
                    .asArray()
                    .containsExactly("a@x.test", "b1@x.test", "b2@x.test", "d@x.test");
        }

        @Test
        void aRequestedPageIsReturnedWithTheTotal() {
            MvcTestResult result = send(HttpMethod.GET, path(ownTrack, "?page=1&size=2"), "", "admin");

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[*].student.email")
                    .asArray()
                    .containsExactly("b2@x.test", "d@x.test");
            assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(1);
            assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(2);
            assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(4);
        }

        @Test
        void aPagePastTheLastIsEmptyWithTheTotal() {
            MvcTestResult result = send(HttpMethod.GET, path(ownTrack, "?page=5&size=2"), "", "admin");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.items").asArray().isEmpty();
            assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(4);
        }

        @Test
        void theDefaultsApplyWithoutPaging() {
            MvcTestResult result = send(HttpMethod.GET, path(ownTrack, ""), "", "admin");

            assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(0);
            assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(25);
        }

        @ParameterizedTest
        @ValueSource(strings = {"size=0", "size=101", "page=-1", "size=abc", "page=x"})
        void aPageOrSizeOutOfRangeIsRejected(String query) {
            assertProblem(
                    send(HttpMethod.GET, path(ownTrack, "?" + query), "", "admin"),
                    HttpStatus.BAD_REQUEST,
                    Problems.VALIDATION_FAILED);
        }

        @Test
        void bothOutOfRangeParametersAreNamed() {
            MvcTestResult result = send(HttpMethod.GET, path(ownTrack, "?page=-1&size=0"), "", "admin");

            assertProblem(result, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.errors[*].field")
                    .asArray()
                    .contains("page", "size");
        }

        @Test
        void aStudentWithoutANameIsOrderedAndShownByTheEmail() {
            UUID nameless = person("nameless@x.test", "x", "x", UserStatus.ACTIVE, Role.STUDENT);
            jdbc.sql("update users set first_name = null, last_name = null where id = :id")
                    .param("id", nameless)
                    .update();
            enrollments.enroll(nameless, ownTrack, admin, START);

            MvcTestResult result = send(HttpMethod.GET, path(ownTrack, "?size=100"), "", "admin");

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[?(@.student.id == '%s')].student.name".formatted(nameless))
                    .asArray()
                    .containsExactly("nameless@x.test");
        }

        @Test
        void anArchivedTracksEnrollmentsAreListed() {
            UUID archived = tracks.archived("OLD", "Old Track");
            UUID old = person("old@x.test", "Olaf", "Old", UserStatus.ACTIVE, Role.STUDENT);
            enrollments.enroll(old, archived, admin, START);

            MvcTestResult result = send(HttpMethod.GET, path(archived, ""), "", "clara");

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[*].student.id")
                    .asArray()
                    .containsExactly(old.toString());
        }

        @Test
        void theListShowsOnlyTheEnrollmentsOfItsTrack() {
            assertNoItems(send(HttpMethod.GET, path(otherTrack, ""), "", "admin"));
        }
    }

    @Nested
    class TheCandidates {

        @BeforeEach
        void addCandidates() {
            person("c@x.test", "carl", "Zed", UserStatus.ACTIVE, Role.STUDENT);
            person("b2@x.test", "Bob", "Same", UserStatus.ACTIVE, Role.STUDENT);
            person("b1@x.test", "Bob", "Same", UserStatus.ACTIVE, Role.STUDENT);
            person("a@x.test", "Ann", "Zed", UserStatus.ACTIVE, Role.STUDENT);
            // The fixture student of the class is one more candidate: Sam Student.
        }

        @Test
        void theOrderIsTheNameIgnoringCaseThenTheEmail() {
            MvcTestResult result = send(HttpMethod.GET, candidates(ownTrack, ""), "", "clara");

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[*].email")
                    .asArray()
                    .containsExactly("a@x.test", "b1@x.test", "b2@x.test", "c@x.test", "sam@x.test");
        }

        @Test
        void aRequestedPageIsReturnedWithTheTotal() {
            MvcTestResult result = send(HttpMethod.GET, candidates(ownTrack, "?page=1&size=2"), "", "admin");

            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[*].email")
                    .asArray()
                    .containsExactly("b2@x.test", "c@x.test");
            assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(1);
            assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(2);
            assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(5);
        }

        @Test
        void aPagePastTheLastIsEmptyWithTheTotal() {
            MvcTestResult result = send(HttpMethod.GET, candidates(ownTrack, "?page=9&size=2"), "", "admin");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.items").asArray().isEmpty();
            assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(5);
        }

        @ParameterizedTest
        @ValueSource(strings = {"size=0", "size=101", "page=-1"})
        void aPageOrSizeOutOfRangeIsRejected(String query) {
            assertProblem(
                    send(HttpMethod.GET, candidates(ownTrack, "?" + query), "", "admin"),
                    HttpStatus.BAD_REQUEST,
                    Problems.VALIDATION_FAILED);
        }

        @Test
        void theSearchMatchesTheLastNameAndIsTrimmed() {
            MvcTestResult result = send(HttpMethod.GET, candidates(ownTrack, "?q=  ZED "), "", "admin");

            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[*].email")
                    .asArray()
                    .containsExactly("a@x.test", "c@x.test");
            assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(2);
        }

        @Test
        void aBlankSearchListsEveryone() {
            MvcTestResult result = send(HttpMethod.GET, candidates(ownTrack, "?q=  "), "", "admin");

            assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(5);
        }

        @Test
        void wildcardsMatchThemselves() {
            person("pct@x.test", "Per", "50%off", UserStatus.ACTIVE, Role.STUDENT);
            person("under@x.test", "Una", "a_b", UserStatus.ACTIVE, Role.STUDENT);

            assertThat(send(HttpMethod.GET, candidates(ownTrack, "?q=%"), "", "admin"))
                    .bodyJson()
                    .extractingPath("$.items[*].email")
                    .asArray()
                    .containsExactly("pct@x.test");
            assertThat(send(HttpMethod.GET, candidates(ownTrack, "?q=a_b"), "", "admin"))
                    .bodyJson()
                    .extractingPath("$.items[*].email")
                    .asArray()
                    .containsExactly("under@x.test");
        }

        @Test
        void aSearchOfExactlyTheLimitIsAcceptedAndOneMoreIsNot() {
            assertThat(send(HttpMethod.GET, candidates(ownTrack, "?q=" + "x".repeat(100)), "", "admin"))
                    .hasStatusOk();
            assertThat(send(HttpMethod.GET, candidates(ownTrack, "?q= " + "x".repeat(100) + " "), "", "admin"))
                    .hasStatusOk();
            MvcTestResult tooLong = send(HttpMethod.GET, candidates(ownTrack, "?q=" + "x".repeat(101)), "", "admin");
            assertProblem(tooLong, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
            assertThat(tooLong)
                    .bodyJson()
                    .extractingPath("$.errors[*].field")
                    .asArray()
                    .contains("q");
        }

        @Test
        void anEnrolledStudentLeavesTheCandidatesAndTheSearchStillHonorsIt() {
            assertThat(enroll(ownTrack, student, "clara")).hasStatus(HttpStatus.CREATED);

            MvcTestResult result = send(HttpMethod.GET, candidates(otherTrack, "?q=sam@x"), "", "admin");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(0);
        }

        @Test
        void aCoordinatorOfTheTrackNeedsNoUsersReadAndAMentorIsRefused() {
            assertThat(send(HttpMethod.GET, candidates(ownTrack, ""), "", "clara"))
                    .hasStatusOk();
            assertProblem(
                    send(HttpMethod.GET, candidates(ownTrack, ""), "", "mentor"),
                    HttpStatus.FORBIDDEN,
                    Problems.FORBIDDEN);
        }
    }

    @Nested
    class Enrolling {

        @Test
        void anyStartDateIsAcceptedAndTheEnrollmentIsActive() {
            UUID past = person("past@x.test", "Pat", "Past", UserStatus.ACTIVE, Role.STUDENT);
            UUID future = person("future@x.test", "Fay", "Future", UserStatus.ACTIVE, Role.STUDENT);

            MvcTestResult first = send(HttpMethod.POST, enrollPath(ownTrack), body(past, "1999-01-31"), "admin");
            MvcTestResult second = send(HttpMethod.POST, enrollPath(ownTrack), body(future, "2999-12-31"), "admin");

            assertThat(first).hasStatus(HttpStatus.CREATED);
            assertThat(first).bodyJson().extractingPath("$.startDate").isEqualTo("1999-01-31");
            assertThat(first).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
            assertThat(second).hasStatus(HttpStatus.CREATED);
            assertThat(second).bodyJson().extractingPath("$.startDate").isEqualTo("2999-12-31");
        }

        @Test
        void theResponseCarriesTheStudentWithoutMarks() {
            MvcTestResult result = enroll(ownTrack, student, "clara");

            assertThat(result).hasStatus(HttpStatus.CREATED);
            assertThat(result).bodyJson().extractingPath("$.id").isNotNull();
            assertThat(result).bodyJson().extractingPath("$.student.id").isEqualTo(student.toString());
            assertThat(result).bodyJson().extractingPath("$.student.name").isEqualTo("Sam Student");
            assertThat(result).bodyJson().extractingPath("$.student.email").isEqualTo("sam@x.test");
            assertThat(result).bodyJson().extractingPath("$.student.disabled").isEqualTo(false);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.student.studentRole")
                    .isEqualTo(true);
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "{\"studentId\": \"not-a-uuid\", \"startDate\": \"2026-09-01\"}",
                    "{\"studentId\": \"%s\", \"startDate\": \"2026-02-30\"}",
                    "{\"studentId\": \"%s\", \"startDate\": \"01.09.2026\"}",
                    "{\"studentId\": \"%s\", \"startDate\": null}",
                    "{\"studentId\": null, \"startDate\": \"2026-09-01\"}",
                    "{\"studentId\": 12, \"startDate\": \"2026-09-01\"}"
                })
        void anInvalidBodyIsRejectedAndNothingChanges(String template) {
            String body = template.replace("%s", student.toString());

            MvcTestResult result = send(HttpMethod.POST, enrollPath(ownTrack), body, "admin");

            assertProblem(result, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
            assertNoItems(send(HttpMethod.GET, path(ownTrack, ""), "", "admin"));
            assertThat(users.count("audit_log")).isZero();
        }

        @ParameterizedTest
        @ValueSource(strings = {"[]", "not json", ""})
        void aBodyThatIsNotAnObjectIsAClientError(String body) {
            MvcTestResult result = mvc.post()
                    .uri(enrollPath(ownTrack))
                    .header(HttpHeaders.AUTHORIZATION, issuer.token("admin").bearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
            assertNoItems(send(HttpMethod.GET, path(ownTrack, ""), "", "admin"));
            assertThat(users.count("audit_log")).isZero();
        }

        @Test
        void aCoordinatorOfTheTrackWithTheStudentRoleIsEnrolledLikeAnyone() {
            UUID both = person("both@x.test", "Bea", "Both", UserStatus.ACTIVE, Role.COORDINATOR, Role.STUDENT);
            tracks.addCoordinator(ownTrack, both);

            assertThat(enroll(ownTrack, both, "admin")).hasStatus(HttpStatus.CREATED);
        }

        @Test
        void aCoordinatorMayEnrollThemselvesWhenTheyHoldTheStudentRole() {
            users.assignStoredRole(clara, "STUDENT");

            assertThat(enroll(ownTrack, clara, "clara")).hasStatus(HttpStatus.CREATED);
        }

        @Test
        void aRefusedRepeatChangesNothingAndTheOriginalStaysFirst() {
            assertThat(enroll(ownTrack, student, "clara")).hasStatus(HttpStatus.CREATED);
            int audit = users.count("audit_log");

            assertProblem(enroll(ownTrack, student, "clara"), HttpStatus.CONFLICT, STUDENT_ALREADY_ENROLLED);

            assertThat(users.count("audit_log")).isEqualTo(audit);
            assertThat(send(HttpMethod.GET, path(ownTrack, ""), "", "admin"))
                    .bodyJson()
                    .extractingPath("$.totalItems")
                    .isEqualTo(1);
        }

        @Test
        void anEnrollmentWritesExactlyTwoAuditEntries() {
            assertThat(enroll(ownTrack, student, "clara")).hasStatus(HttpStatus.CREATED);

            assertThat(users.count("audit_log")).isEqualTo(2);
        }

        @Test
        void theEnrolledStudentSeesItOnTheirOwnPage() {
            users.link(student, "sam");
            assertThat(enroll(ownTrack, student, "clara")).hasStatus(HttpStatus.CREATED);

            MvcTestResult mine = send(HttpMethod.GET, "/api/v1/me/enrollments", "", "sam");

            assertThat(mine).bodyJson().extractingPath("$.items[0].track.id").isEqualTo(ownTrack.toString());
            assertThat(mine)
                    .bodyJson()
                    .extractingPath("$.items[0].coordinators[0].email")
                    .isEqualTo("clara@x.test");
        }

        @Test
        void aTrackWithAnEnrollmentIsNotArchivedAndAnEmptyOneIs() {
            assertThat(enroll(ownTrack, student, "clara")).hasStatus(HttpStatus.CREATED);

            assertThat(send(HttpMethod.POST, "/api/v1/tracks/" + ownTrack + "/archive", "", "admin"))
                    .hasStatus(HttpStatus.CONFLICT);
            assertThat(send(HttpMethod.POST, "/api/v1/tracks/" + otherTrack + "/archive", "", "admin"))
                    .hasStatusOk();
        }
    }

    @Nested
    class CanEnroll {

        @Test
        void theTrackAnswersByThePermissionAndTheRelationWhateverItsStatus() {
            UUID archived = tracks.archived("OLD", "Old Track");
            tracks.addCoordinator(archived, clara);

            assertThat(canEnroll(archived, "clara")).isEqualTo(true);
            assertThat(canEnroll(archived, "admin")).isEqualTo(true);
            assertThat(canEnroll(ownTrack, "clara")).isEqualTo(true);
            assertThat(canEnroll(otherTrack, "clara")).isEqualTo(false);
            assertThat(canEnroll(otherTrack, "admin")).isEqualTo(true);
        }

        @Test
        void aRemovedCoordinatorLosesItAtOnce() {
            jdbc.sql("delete from track_coordinators where track_id = :track")
                    .param("track", ownTrack)
                    .update();

            assertThat(canEnroll(ownTrack, "clara")).isEqualTo(false);
        }

        private Object canEnroll(UUID track, String caller) {
            MvcTestResult result = send(HttpMethod.GET, "/api/v1/tracks/" + track, "", caller);
            assertThat(result).hasStatusOk();
            return com.jayway.jsonpath.JsonPath.read(
                    new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8),
                    "$.canEnroll");
        }
    }

    @Nested
    class MyEnrollments {

        @Test
        void aCallerWithoutATokenIsNotAnswered() {
            for (String path : List.of("/api/v1/me/enrollments", path(ownTrack, ""), candidates(ownTrack, ""))) {
                assertThat(mvc.get().uri(path).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
            }
            assertThat(mvc.post()
                            .uri(enrollPath(ownTrack))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(student, "2026-09-01"))
                            .exchange())
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void aTrackWithoutAnActiveCoordinatorHasAnEmptyContactList() {
            users.link(student, "sam");
            UUID disabled = person("dina@x.test", "Dina", "Disabled", UserStatus.DISABLED, Role.COORDINATOR);
            tracks.addCoordinator(otherTrack, disabled);
            enrollments.enroll(student, otherTrack, admin, START);

            MvcTestResult result = send(HttpMethod.GET, "/api/v1/me/enrollments", "", "sam");

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[0].coordinators")
                    .asArray()
                    .isEmpty();
        }

        @Test
        void theContactsAreOrderedByNameAndAMissingPhoneIsNull() {
            users.link(student, "sam");
            UUID zed = person("zed@x.test", "Zed", "Zulu", UserStatus.ACTIVE, Role.COORDINATOR);
            UUID abe = person("abe@x.test", "abe", "Able", UserStatus.ACTIVE, Role.COORDINATOR);
            tracks.addCoordinator(otherTrack, zed);
            tracks.addCoordinator(otherTrack, abe);
            enrollments.enroll(student, otherTrack, admin, START);

            MvcTestResult result = send(HttpMethod.GET, "/api/v1/me/enrollments", "", "sam");

            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[0].coordinators[*].email")
                    .asArray()
                    .containsExactly("abe@x.test", "zed@x.test");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[0].coordinators[0].phone")
                    .isNull();
        }

        @Test
        void aMentorEnrolledByDataStillReadsOnlyTheirOwn() {
            enrollments.enroll(student, ownTrack, admin, START);

            MvcTestResult result = send(HttpMethod.GET, "/api/v1/me/enrollments", "", "mentor");

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.items").asArray().isEmpty();
        }
    }

    private UUID person(String email, String firstName, String lastName, UserStatus status, Role... roles) {
        UUID id = users.create(email, status, roles);
        users.rename(id, firstName, lastName);
        return id;
    }

    private static String path(UUID track, String query) {
        return "/api/v1/tracks/" + track + "/enrollments" + query;
    }

    private static String enrollPath(UUID track) {
        return "/api/v1/tracks/" + track + "/enrollments";
    }

    private static String candidates(UUID track, String query) {
        return "/api/v1/tracks/" + track + "/enrollment-candidates" + query;
    }

    private static String body(UUID studentId, String date) {
        return "{\"studentId\": \"%s\", \"startDate\": \"%s\"}".formatted(studentId, date);
    }

    private MvcTestResult enroll(UUID track, UUID studentId, String caller) {
        return send(HttpMethod.POST, enrollPath(track), body(studentId, "2026-09-01"), caller);
    }

    private static void assertNoItems(MvcTestResult list) {
        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$.items").asArray().isEmpty();
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
}
