package com.mentorhub.users.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.invitation.InvitationProblems;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestIssuerConfiguration;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.web.Problems;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** Invitations through the real chain: signed tokens, the gate, permissions, method validation and the database. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
class InvitationsEndpointTests {

    static final String INVITED_EMAIL = "ivan@x.test";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper jsonMapper;

    TestUsers users;
    UUID admin;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "admin");
    }

    /** Every non-admin role and no roles at all, against every management operation. */
    static Stream<Arguments> forbiddenCalls() {
        List<@Nullable Role> callerRoles = new ArrayList<>();
        callerRoles.add(null);
        Stream.of(Role.values()).filter(role -> role != Role.ADMIN).forEach(callerRoles::add);
        List<Arguments> calls = new ArrayList<>();
        for (Role role : callerRoles) {
            for (String[] operation : List.of(
                    new String[] {"POST", "/api/v1/invitations", "{\"email\": \"a@x.test\", \"roles\": []}"},
                    new String[] {"GET", "/api/v1/invitations", ""},
                    new String[] {"POST", "/api/v1/invitations/{id}/revoke", ""},
                    new String[] {"POST", "/api/v1/invitations/{id}/link", ""},
                    new String[] {"POST", "/api/v1/invitations/{id}/link", "{\"sendEmail\": true}"},
                    new String[] {
                        "POST", "/api/v1/invitations", "{\"email\": \"a@x.test\", \"roles\": [], \"sendEmail\": true}"
                    },
                    new String[] {"POST", "/api/v1/invitations/{id}/email", ""},
                    new String[] {"GET", "/api/v1/invitations/{id}/link", ""})) {
                calls.add(Arguments.of(
                        role == null ? "no roles" : role.name(), operation[0], operation[1], operation[2]));
            }
        }
        return calls.stream();
    }

    @Test
    @DisplayName("AUD-4.3 A read writes no entry")
    void readingALinkWritesNoEntry() {
        InvitationRef invitation = create(INVITED_EMAIL, "[\"MENTOR\"]");
        int entries = users.count("audit_log");

        assertThat(send(HttpMethod.GET, "/api/v1/invitations/" + invitation.id() + "/link", "", "admin"))
                .hasStatusOk();

        assertThat(users.count("audit_log")).isEqualTo(entries);
    }

    // Nothing is created, changed or audited.
    @ParameterizedTest(name = "{0}: {1} {2}")
    @MethodSource("forbiddenCalls")
    @DisplayName("INV-1.1 Every management action needs the permission")
    void callersWithoutTheInvitePermissionAreForbidden(String role, String method, String path, String body) {
        InvitationRef invitation = create(INVITED_EMAIL, "[\"MENTOR\"]");
        UUID caller = role.equals("no roles")
                ? users.create("caller@x.test", UserStatus.ACTIVE)
                : users.create("caller@x.test", UserStatus.ACTIVE, Role.valueOf(role));
        users.link(caller, "caller");

        MvcTestResult result = send(
                HttpMethod.valueOf(method), path.replace("{id}", invitation.id().toString()), body, "caller");

        assertProblem(result, HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
        assertThat(users.count("invitations")).isEqualTo(1);
        assertThat(jdbc.sql("select count(*) from invitations where revoked_at is null and accepted_at is null")
                        .query(Integer.class)
                        .single())
                .isEqualTo(1);
        assertThat(users.count("audit_log")).isEqualTo(1);
    }

    // The gate answers a disabled caller before any permission is looked at.
    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("disabledCalls")
    void disabledCallerIsRefusedByTheGate(String method, String path, String body) {
        InvitationRef invitation = create(INVITED_EMAIL, "[]");
        jdbc.sql("update users set status = 'DISABLED', disabled_reason = 'SECURITY' where id = :id")
                .param("id", admin)
                .update();

        MvcTestResult result = send(
                HttpMethod.valueOf(method), path.replace("{id}", invitation.id().toString()), body, "admin");

        assertProblem(result, HttpStatus.FORBIDDEN, Problems.ACCESS_ENDED);
        assertThat(users.count("invitations")).isEqualTo(1);
    }

    static Stream<Arguments> disabledCalls() {
        return forbiddenCalls()
                .filter(arguments -> "no roles".equals(arguments.get()[0]))
                .map(arguments -> Arguments.of(
                        arguments.get()[1], arguments.get()[2], arguments.get()[3]));
    }

    // From creation to a repeated accept, including the link's absence from the list.
    @Test
    void invitedPersonJoinsThroughTheLink() {
        InvitationRef invitation = create(INVITED_EMAIL, "[\"MENTOR\", \"STUDENT\"]");
        assertThat(invitation.token()).hasSize(43);
        assertThat(invitation.link()).endsWith("/invite/" + invitation.token());
        // The pending invitation's link can be read again, uncached.
        MvcTestResult link = send(HttpMethod.GET, "/api/v1/invitations/" + invitation.id() + "/link", "", "admin");
        assertThat(link).hasStatusOk();
        assertThat(link).bodyJson().extractingPath("$.link").isEqualTo(invitation.link());
        assertThat(link).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store");

        MvcTestResult preview = preview(invitation.token(), "Bearer not-a-valid-token");
        assertThat(preview).hasStatusOk();
        assertThat(preview).bodyJson().extractingPath("$.email").isEqualTo(INVITED_EMAIL);
        assertThat(preview).bodyJson().doesNotHavePath("$.id");

        assertProblem(
                send(HttpMethod.GET, "/api/v1/users", "", "stranger"), HttpStatus.FORBIDDEN, Problems.NOT_INVITED);

        MvcTestResult accepted = accept(invitation.token(), "stranger", INVITED_EMAIL, true);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(accepted).bodyJson().extractingPath("$.user.email").isEqualTo(INVITED_EMAIL);
        assertThat(accepted).bodyJson().extractingPath("$.user.roles").asArray().containsExactly("MENTOR", "STUDENT");
        assertProblem(
                send(HttpMethod.GET, "/api/v1/invitations/" + invitation.id() + "/link", "", "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.INVITATION_CLOSED);
        // The next request passes the gate; the caller only lacks the permission.
        assertProblem(send(HttpMethod.GET, "/api/v1/users", "", "stranger"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);

        MvcTestResult list = send(HttpMethod.GET, "/api/v1/invitations?q=IVAN&status=ACCEPTED", "", "admin");
        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$.totalItems").isEqualTo(1);
        assertThat(list).bodyJson().extractingPath("$.items[0].status").isEqualTo("ACCEPTED");
        assertThat(list).bodyJson().extractingPath("$.items[0].createdBy.id").isEqualTo(admin.toString());
        assertThat(list).bodyJson().hasPath("$.items[0].acceptedAt").doesNotHavePath("$.items[0].revokedAt");
        assertThat(list).bodyJson().doesNotHavePath("$.items[0].link").doesNotHavePath("$.items[0].token");

        UUID userId = jdbc.sql("select id from users where email = :email")
                .param("email", INVITED_EMAIL)
                .query(UUID.class)
                .single();
        MvcTestResult history = send(HttpMethod.GET, "/api/v1/users/" + userId + "/history", "", "admin");
        assertThat(history).bodyJson().extractingPath("$.items[0].action").isEqualTo("USER_INVITATION_ACCEPTED");
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[0].invitation.userCreated")
                .isEqualTo(true);
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[0].invitation.invitedBy.id")
                .isEqualTo(admin.toString());
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[0].rolesAdded")
                .asArray()
                .containsExactly("MENTOR", "STUDENT");

        // A retry changes nothing; the link is invalid for everyone else and for preview.
        assertThat(accept(invitation.token(), "stranger", INVITED_EMAIL, true)).hasStatusOk();
        assertThat(users.count("audit_log")).isEqualTo(2);
        assertInvalid(preview(invitation.token(), ""));
        assertInvalid(accept(invitation.token(), "other", INVITED_EMAIL, true));

        // The token is stored in plaintext, but never in audit details.
        assertThat(jdbc.sql("select count(*) from invitations where token = :token")
                        .param("token", invitation.token())
                        .query(Integer.class)
                        .single())
                .isOne();
        assertThat(jdbc.sql("select count(*) from audit_log where details::text like :token")
                        .param("token", "%" + invitation.token() + "%")
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    // A disabled user accepts despite the gate, and is active on the next request.
    @Test
    void disabledUserIsReactivatedThroughTheLink() {
        UUID former = users.create(INVITED_EMAIL, UserStatus.DISABLED, Role.STUDENT);
        users.link(former, "former");
        InvitationRef invitation = create(INVITED_EMAIL, "[\"MENTOR\"]");
        assertProblem(send(HttpMethod.GET, "/api/v1/users", "", "former"), HttpStatus.FORBIDDEN, Problems.ACCESS_ENDED);

        MvcTestResult accepted = accept(invitation.token(), "former", INVITED_EMAIL, true);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.user.roles").asArray().containsExactly("MENTOR");
        assertThat(users.status(former)).isEqualTo("ACTIVE");
        assertProblem(send(HttpMethod.GET, "/api/v1/users", "", "former"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
    }

    @ParameterizedTest
    @MethodSource("mismatchedEmails")
    @DisplayName("INV-13.1 An email that is not verified or differs is refused")
    void emailThatIsUnverifiedOrDifferentIsAConflictAndChangesNothing(String email, boolean verified) {
        InvitationRef invitation = create(INVITED_EMAIL, "[\"MENTOR\"]");

        MvcTestResult result = accept(invitation.token(), "stranger", email, verified);

        assertProblem(result, HttpStatus.CONFLICT, InvitationProblems.INVITATION_EMAIL_MISMATCH);
        assertThat(users.count("users")).isEqualTo(1);
        assertThat(users.count("user_identities")).isEqualTo(1);
        assertThat(preview(invitation.token(), "")).hasStatusOk();
    }

    static Stream<Arguments> mismatchedEmails() {
        return Stream.of(Arguments.of(INVITED_EMAIL, false), Arguments.of("someone-else@x.test", true));
    }

    @Test
    void identityLinkedToAnotherUserIsAConflict() {
        UUID other = users.create("other@x.test", UserStatus.ACTIVE);
        users.link(other, "stranger");
        InvitationRef invitation = create(INVITED_EMAIL, "[]");

        MvcTestResult result = accept(invitation.token(), "stranger", INVITED_EMAIL, true);

        assertProblem(result, HttpStatus.CONFLICT, InvitationProblems.IDENTITY_LINKED_ELSEWHERE);
        assertThat(users.count("users")).isEqualTo(2);
    }

    @Test
    @DisplayName("INV-11.3 Acceptance needs a signed-in caller")
    void acceptWithoutATokenIsUnauthorizedAndPreviewDoesNotNeedOne() {
        assertProblem(
                mvc.post()
                        .uri("/api/v1/invitations/accept")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"abc\"}")
                        .exchange(),
                HttpStatus.UNAUTHORIZED,
                Problems.UNAUTHORIZED);
        assertInvalid(preview("abc", ""));
    }

    // Malformed, empty and oversized tokens.
    @Test
    void malformedTokenIsInvalidAndEmptyTokenIsAValidationProblem() {
        assertInvalid(preview("not a token", ""));
        assertInvalid(preview("x".repeat(200), ""));
        MvcTestResult empty = preview("", "");
        assertProblem(empty, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(empty).bodyJson().extractingPath("$.errors[0].field").isEqualTo("token");
        MvcTestResult oversized = accept("x".repeat(201), "stranger", INVITED_EMAIL, true);
        assertProblem(oversized, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(oversized).bodyJson().extractingPath("$.errors[0].field").isEqualTo("token");
    }

    // The problems carry their extensions through the whole chain.
    @Test
    void conflictsOfCreateRenderTheirExtensions() {
        InvitationRef existing = create(INVITED_EMAIL, "[]");

        MvcTestResult duplicate = createRequest("IVAN@x.test", "[]");
        assertProblem(duplicate, HttpStatus.CONFLICT, InvitationProblems.INVITATION_EXISTS);
        assertThat(duplicate)
                .bodyJson()
                .extractingPath("$.invitationId")
                .isEqualTo(existing.id().toString());

        UUID mentor = users.create("mentor@x.test", UserStatus.ACTIVE, Role.MENTOR);
        MvcTestResult active = createRequest("MENTOR@x.test", "[]");
        assertProblem(active, HttpStatus.CONFLICT, InvitationProblems.USER_ACTIVE);
        assertThat(active).bodyJson().extractingPath("$.userId").isEqualTo(mentor.toString());
        assertThat(users.count("invitations")).isEqualTo(1);
    }

    @Test
    @DisplayName("PLT-2.1 An invalid body field is named")
    void invalidCreateRequestsAreValidationProblemsAndCreateNothing() {
        assertProblem(createRequest("not-an-email", "[]"), HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertProblem(createRequest(INVITED_EMAIL, "[null]"), HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertProblem(createRequest(INVITED_EMAIL, "[\"BOSS\"]"), HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        MvcTestResult bothInvalid = createRequest("bad", "[null]");
        assertProblem(bothInvalid, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(bothInvalid)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("email", "roles");
        assertThat(users.count("invitations")).isZero();
    }

    @Test
    void revokedAndReplacedLinksAreInvalid() {
        InvitationRef first = create(INVITED_EMAIL, "[]");

        MvcTestResult reissued = send(HttpMethod.POST, "/api/v1/invitations/" + first.id() + "/link", "", "admin");

        assertThat(reissued).hasStatusOk();
        String newToken = tokenOf(reissued);
        assertThat(newToken).isNotEqualTo(first.token());
        assertInvalid(preview(first.token(), ""));
        assertThat(preview(newToken, "")).hasStatusOk();

        MvcTestResult revoked = send(HttpMethod.POST, "/api/v1/invitations/" + first.id() + "/revoke", "", "admin");

        assertThat(revoked).hasStatusOk();
        assertThat(revoked).bodyJson().extractingPath("$.status").isEqualTo("REVOKED");
        assertThat(revoked).bodyJson().extractingPath("$.revokedBy.id").isEqualTo(admin.toString());
        assertThat(revoked).bodyJson().doesNotHavePath("$.acceptedAt");
        assertInvalid(preview(newToken, ""));
        assertProblem(
                send(HttpMethod.POST, "/api/v1/invitations/" + first.id() + "/revoke", "", "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.INVITATION_CLOSED);
        assertProblem(
                send(HttpMethod.POST, "/api/v1/invitations/" + first.id() + "/link", "", "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.INVITATION_CLOSED);
        assertProblem(
                send(HttpMethod.POST, "/api/v1/invitations/" + UUID.randomUUID() + "/revoke", "", "admin"),
                HttpStatus.NOT_FOUND,
                Problems.NOT_FOUND);
    }

    @Test
    @DisplayName("IEM-1.1 Without a mail server emails are refused")
    void emailIsUnavailableWithoutAMailServer() {
        assertThat(send(HttpMethod.GET, "/api/v1/system/client-config", "", "admin"))
                .bodyJson()
                .extractingPath("$.invitationEmails")
                .isEqualTo(false);
        InvitationRef invitation = create(INVITED_EMAIL, "[\"MENTOR\"]");
        int audit = users.count("audit_log");
        assertThat(users.count("invitations")).isEqualTo(1);

        assertProblem(
                send(
                        HttpMethod.POST,
                        "/api/v1/invitations",
                        "{\"email\": \"b@x.test\", \"roles\": [], \"sendEmail\": true}",
                        "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.EMAIL_UNAVAILABLE);
        assertProblem(
                send(
                        HttpMethod.POST,
                        "/api/v1/invitations/" + invitation.id() + "/link",
                        "{\"sendEmail\": true}",
                        "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.EMAIL_UNAVAILABLE);
        assertProblem(
                send(HttpMethod.POST, "/api/v1/invitations/" + invitation.id() + "/email", "", "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.EMAIL_UNAVAILABLE);

        assertThat(users.count("invitations")).isEqualTo(1);
        assertThat(users.count("audit_log")).isEqualTo(audit);
        assertThat(users.count("invitation_emails")).isZero();
    }

    @Test
    @DisplayName("IEM-1.3 Without a mail server invitations work as before")
    void invitationsWorkAsBeforeWithoutAMailServer() {
        InvitationRef invitation = create(INVITED_EMAIL, "[\"MENTOR\"]");
        assertThat(send(
                        HttpMethod.POST,
                        "/api/v1/invitations",
                        "{\"email\": \"a@x.test\", \"roles\": [], \"sendEmail\": false}",
                        "admin"))
                .hasStatus(HttpStatus.CREATED);
        assertThat(users.count("invitations")).isEqualTo(2);

        assertThat(send(HttpMethod.GET, "/api/v1/invitations/" + invitation.id() + "/link", "", "admin"))
                .bodyJson()
                .extractingPath("$.link")
                .isEqualTo(invitation.link());
        assertThat(send(HttpMethod.POST, "/api/v1/invitations/" + invitation.id() + "/link", "{}", "admin"))
                .hasStatusOk();
        assertThat(send(HttpMethod.GET, "/api/v1/invitations", "", "admin"))
                .bodyJson()
                .doesNotHavePath("$.items[0].lastEmail");
    }

    @Test
    @DisplayName("IEM-5.1 An invitation or request problem is reported before the missing mail server")
    void invitationErrorsComeBeforeEmailUnavailable() {
        InvitationRef invitation = create(INVITED_EMAIL, "[]");
        assertThat(send(HttpMethod.POST, "/api/v1/invitations/" + invitation.id() + "/revoke", "", "admin"))
                .hasStatusOk();

        assertProblem(
                send(HttpMethod.POST, "/api/v1/invitations/" + invitation.id() + "/email", "", "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.INVITATION_CLOSED);
        assertProblem(
                send(
                        HttpMethod.POST,
                        "/api/v1/invitations/" + invitation.id() + "/link",
                        "{\"sendEmail\": true}",
                        "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.INVITATION_CLOSED);
        assertProblem(
                send(HttpMethod.POST, "/api/v1/invitations/" + UUID.randomUUID() + "/email", "", "admin"),
                HttpStatus.NOT_FOUND,
                Problems.NOT_FOUND);
        assertProblem(
                send(
                        HttpMethod.POST,
                        "/api/v1/invitations",
                        "{\"email\": \"nope\", \"roles\": [], \"sendEmail\": true}",
                        "admin"),
                HttpStatus.BAD_REQUEST,
                Problems.VALIDATION_FAILED);
        create("open@x.test", "[]");
        assertProblem(
                send(
                        HttpMethod.POST,
                        "/api/v1/invitations",
                        "{\"email\": \"open@x.test\", \"roles\": [], \"sendEmail\": true}",
                        "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.INVITATION_EXISTS);
    }

    // A stored role the code does not know is skipped on reads.
    @Test
    void unknownStoredRoleIsLeftOutOfTheList() {
        InvitationRef invitation = create(INVITED_EMAIL, "[\"MENTOR\"]");
        jdbc.sql("insert into roles (name) values ('LEGACY')").update();
        jdbc.sql("insert into invitation_roles (invitation_id, role) values (:id, 'LEGACY')")
                .param("id", invitation.id())
                .update();

        MvcTestResult list = send(HttpMethod.GET, "/api/v1/invitations", "", "admin");

        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$.items[0].roles").asArray().containsExactly("MENTOR");
    }

    // The search limit is the email length, not the user search's 100.
    @Test
    void searchAcceptsUpTo254CharactersAndRejectsMore() {
        assertThat(send(HttpMethod.GET, "/api/v1/invitations?q=" + "x".repeat(254), "", "admin"))
                .hasStatusOk();
        MvcTestResult tooLong = send(HttpMethod.GET, "/api/v1/invitations?q=" + "x".repeat(255), "", "admin");
        assertProblem(tooLong, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(tooLong).bodyJson().extractingPath("$.errors[0].field").isEqualTo("q");
    }

    @Test
    @DisplayName("INV-1.2 Any holder manages any invitation")
    void anotherAdministratorRevokesAnInvitation() {
        InvitationRef invitation = create(INVITED_EMAIL, "[]");
        UUID other = users.create("other@x.test", UserStatus.ACTIVE, Role.ADMIN);
        users.link(other, "other");

        MvcTestResult revoked =
                send(HttpMethod.POST, "/api/v1/invitations/" + invitation.id() + "/revoke", "", "other");

        assertThat(revoked).hasStatusOk();
        assertThat(revoked).bodyJson().extractingPath("$.status").isEqualTo("REVOKED");
        assertThat(revoked).bodyJson().extractingPath("$.revokedBy.id").isEqualTo(other.toString());
    }

    static Stream<Arguments> invalidEmails() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of("   "),
                Arguments.of("not-an-email"),
                Arguments.of("a".repeat(64) + "@" + "b".repeat(190) + ".test"));
    }

    @ParameterizedTest
    @MethodSource("invalidEmails")
    @DisplayName("INV-2.3 An invalid email is rejected")
    void anInvalidEmailIsRejected(String email) {
        MvcTestResult result = createRequest(email, "[]");

        assertProblem(result, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .contains("email");
        assertThat(users.count("invitations")).isZero();
    }

    private record ListedInvitations(
            UUID joined, InvitationRef accepted, InvitationRef pending, InvitationRef legacy) {}

    private ListedInvitations createListedInvitations() {
        UUID joined = users.create("joined@x.test", UserStatus.ACTIVE);
        InvitationRef accepted = create("accepted@x.test", "[\"STUDENT\"]");
        InvitationRef pending = create("pending@x.test", "[\"MENTOR\", \"STUDENT\"]");
        InvitationRef legacy = create("legacy@x.test", "[\"MENTOR\"]");
        jdbc.sql("insert into roles (name) values ('LEGACY')").update();
        jdbc.sql("insert into invitation_roles (invitation_id, role) values (:id, 'LEGACY')")
                .param("id", legacy.id())
                .update();
        age(legacy.id(), 1);
        age(pending.id(), 2);
        age(accepted.id(), 3);
        jdbc.sql("update invitations set accepted_at = now(), accepted_user_id = :user where id = :id")
                .param("user", joined)
                .param("id", accepted.id())
                .update();
        return new ListedInvitations(joined, accepted, pending, legacy);
    }

    @Test
    @DisplayName("INV-5.4 A role the application does not know is left out of an item")
    void aRoleTheApplicationDoesNotKnowIsLeftOutOfAnItem() {
        createListedInvitations();

        MvcTestResult list = send(HttpMethod.GET, "/api/v1/invitations", "", "admin");

        assertThat(list).hasStatusOk();
        assertThat(list).bodyJson().extractingPath("$.items[0].roles").asArray().containsExactly("MENTOR");
    }

    @Test
    @DisplayName("INV-5.1 An item has its data, newest first")
    void listItemsHaveTheirDataNewestFirst() {
        ListedInvitations seeded = createListedInvitations();
        UUID joined = seeded.joined();

        MvcTestResult list = send(HttpMethod.GET, "/api/v1/invitations", "", "admin");

        assertThat(list).hasStatusOk();
        assertThat(list)
                .bodyJson()
                .extractingPath("$.items[*].email")
                .asArray()
                .containsExactly("legacy@x.test", "pending@x.test", "accepted@x.test");
        assertThat(list)
                .bodyJson()
                .extractingPath("$.items[1].roles")
                .asArray()
                .containsExactlyInAnyOrder("MENTOR", "STUDENT");
        for (int i = 0; i < 3; i++) {
            assertThat(list).bodyJson().hasPath("$.items[" + i + "].status");
            assertThat(list).bodyJson().hasPath("$.items[" + i + "].createdAt");
            assertThat(list).bodyJson().hasPath("$.items[" + i + "].expiresAt");
            assertThat(list)
                    .bodyJson()
                    .extractingPath("$.items[" + i + "].createdBy.id")
                    .isEqualTo(admin.toString());
        }
        assertThat(list).bodyJson().extractingPath("$.items[2].status").isEqualTo("ACCEPTED");
        assertThat(list).bodyJson().hasPath("$.items[2].acceptedAt");
        assertThat(list).bodyJson().extractingPath("$.items[2].acceptedUser.id").isEqualTo(joined.toString());
        assertThat(list).bodyJson().doesNotHavePath("$.items[1].acceptedAt");
    }

    @Test
    @DisplayName("INV-5.2 The text and the status narrow the list")
    void theTextAndTheStatusNarrowTheList() {
        create("ivan@example.com", "[]");
        create("a_b@example.com", "[]");
        create("axb@example.com", "[]");
        InvitationRef expired = create("expired@example.com", "[]");
        expire(expired.id());
        create("pending@example.com", "[]");

        MvcTestResult byText = send(HttpMethod.GET, "/api/v1/invitations?q=IVAN", "", "admin");
        assertThat(byText).bodyJson().extractingPath("$.totalItems").isEqualTo(1);
        assertThat(byText).bodyJson().extractingPath("$.items[0].email").isEqualTo("ivan@example.com");

        MvcTestResult byWildcard = send(HttpMethod.GET, "/api/v1/invitations?q=a_b", "", "admin");
        assertThat(byWildcard).bodyJson().extractingPath("$.totalItems").isEqualTo(1);
        assertThat(byWildcard).bodyJson().extractingPath("$.items[0].email").isEqualTo("a_b@example.com");

        MvcTestResult byStatus = send(HttpMethod.GET, "/api/v1/invitations?status=EXPIRED", "", "admin");
        assertThat(byStatus).bodyJson().extractingPath("$.totalItems").isEqualTo(1);
        assertThat(byStatus).bodyJson().extractingPath("$.items[0].email").isEqualTo("expired@example.com");
    }

    @Test
    @DisplayName("INV-5.3 A text longer than an email is rejected")
    void aTextLongerThanAnEmailIsRejected() {
        MvcTestResult tooLong = send(HttpMethod.GET, "/api/v1/invitations?q=" + "x".repeat(255), "", "admin");
        assertProblem(tooLong, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(tooLong).bodyJson().extractingPath("$.errors[0].field").isEqualTo("q");
    }

    @Test
    @DisplayName("INV-6.2 A closed invitation is not revoked")
    void aClosedInvitationIsNotRevoked() {
        UUID joined = users.create("joined@x.test", UserStatus.ACTIVE);
        InvitationRef accepted = create("accepted@x.test", "[]");
        jdbc.sql("update invitations set accepted_at = now(), accepted_user_id = :user where id = :id")
                .param("user", joined)
                .param("id", accepted.id())
                .update();
        InvitationRef revoked = create("revoked@x.test", "[]");
        assertThat(send(HttpMethod.POST, "/api/v1/invitations/" + revoked.id() + "/revoke", "", "admin"))
                .hasStatusOk();
        String revocation = revocationOf(revoked.id());

        assertProblem(
                send(HttpMethod.POST, "/api/v1/invitations/" + accepted.id() + "/revoke", "", "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.INVITATION_CLOSED);
        assertProblem(
                send(HttpMethod.POST, "/api/v1/invitations/" + revoked.id() + "/revoke", "", "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.INVITATION_CLOSED);

        assertThat(revocationOf(revoked.id())).isEqualTo(revocation);
        assertThat(revocationOf(accepted.id())).isEqualTo("null");
    }

    @Test
    @DisplayName("INV-6.3 An unknown invitation is not revoked")
    void anUnknownInvitationIsNotRevoked() {
        assertProblem(
                send(HttpMethod.POST, "/api/v1/invitations/" + UUID.randomUUID() + "/revoke", "", "admin"),
                HttpStatus.NOT_FOUND,
                Problems.NOT_FOUND);
    }

    @Test
    @DisplayName("INV-7.1 A new link has a new token and a new expiry")
    void aNewLinkHasANewTokenAndANewExpiry() {
        InvitationRef first = create(INVITED_EMAIL, "[]");

        MvcTestResult reissued = send(HttpMethod.POST, "/api/v1/invitations/" + first.id() + "/link", "", "admin");

        assertThat(reissued).hasStatusOk();
        assertThat(tokenOf(reissued)).isNotEqualTo(first.token());
        assertThat(Instant.parse(body(reissued, "invitation.expiresAt")))
                .isCloseTo(Instant.now().plus(Duration.ofDays(7)), within(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("INV-7.4 The old link stops working")
    void theOldLinkStopsWorking() {
        InvitationRef first = create(INVITED_EMAIL, "[]");

        MvcTestResult reissued = send(HttpMethod.POST, "/api/v1/invitations/" + first.id() + "/link", "", "admin");

        assertThat(reissued).hasStatusOk();
        assertThat(tokenOf(reissued)).isNotEqualTo(first.token());
        assertInvalid(preview(first.token(), ""));
    }

    @Test
    @DisplayName("INV-7.2 An expired invitation is renewed")
    void anExpiredInvitationIsRenewed() {
        InvitationRef expired = create(INVITED_EMAIL, "[]");
        expire(expired.id());

        MvcTestResult reissued = send(HttpMethod.POST, "/api/v1/invitations/" + expired.id() + "/link", "", "admin");

        assertThat(reissued).hasStatusOk();
        assertThat(reissued).bodyJson().extractingPath("$.invitation.status").isEqualTo("PENDING");
        String newToken = tokenOf(reissued);
        assertThat(preview(newToken, "")).hasStatusOk();
        assertThat(accept(newToken, "stranger", INVITED_EMAIL, true)).hasStatusOk();
    }

    @Test
    @DisplayName("INV-7.3 A closed invitation gets no new link")
    void aClosedInvitationGetsNoNewLink() {
        UUID joined = users.create("joined@x.test", UserStatus.ACTIVE);
        InvitationRef accepted = create("accepted@x.test", "[]");
        jdbc.sql("update invitations set accepted_at = now(), accepted_user_id = :user where id = :id")
                .param("user", joined)
                .param("id", accepted.id())
                .update();
        InvitationRef revoked = create("revoked@x.test", "[]");
        assertThat(send(HttpMethod.POST, "/api/v1/invitations/" + revoked.id() + "/revoke", "", "admin"))
                .hasStatusOk();

        for (InvitationRef closed : List.of(accepted, revoked)) {
            assertProblem(
                    send(HttpMethod.POST, "/api/v1/invitations/" + closed.id() + "/link", "", "admin"),
                    HttpStatus.CONFLICT,
                    InvitationProblems.INVITATION_CLOSED);
            assertThat(storedToken(closed.id())).isEqualTo(closed.token());
        }
    }

    @Test
    @DisplayName("INV-8.1 A pending invitation returns its link to any administrator")
    void aPendingInvitationReturnsItsLinkToAnyAdministrator() {
        InvitationRef invitation = create(INVITED_EMAIL, "[]");
        UUID other = users.create("other@x.test", UserStatus.ACTIVE, Role.ADMIN);
        users.link(other, "other");
        MvcTestResult reissued = send(HttpMethod.POST, "/api/v1/invitations/" + invitation.id() + "/link", "", "admin");
        assertThat(reissued).hasStatusOk();

        MvcTestResult link = send(HttpMethod.GET, "/api/v1/invitations/" + invitation.id() + "/link", "", "other");

        assertThat(link).hasStatusOk();
        assertThat(link).bodyJson().extractingPath("$.link").isEqualTo(body(reissued, "link"));
        assertThat(link).bodyJson().extractingPath("$.email").isEqualTo(INVITED_EMAIL);
        assertThat(link).bodyJson().hasPath("$.expiresAt");
    }

    @Test
    @DisplayName("INV-8.2 An invitation that is not pending has no link to read")
    void anInvitationThatIsNotPendingHasNoLinkToRead() {
        InvitationRef expired = create("expired@x.test", "[]");
        expire(expired.id());
        UUID joined = users.create("joined@x.test", UserStatus.ACTIVE);
        InvitationRef accepted = create("accepted@x.test", "[]");
        jdbc.sql("update invitations set accepted_at = now(), accepted_user_id = :user where id = :id")
                .param("user", joined)
                .param("id", accepted.id())
                .update();
        InvitationRef revoked = create("revoked@x.test", "[]");
        assertThat(send(HttpMethod.POST, "/api/v1/invitations/" + revoked.id() + "/revoke", "", "admin"))
                .hasStatusOk();

        assertProblem(
                send(HttpMethod.GET, "/api/v1/invitations/" + expired.id() + "/link", "", "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.INVITATION_EXPIRED);
        assertProblem(
                send(HttpMethod.GET, "/api/v1/invitations/" + accepted.id() + "/link", "", "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.INVITATION_CLOSED);
        assertProblem(
                send(HttpMethod.GET, "/api/v1/invitations/" + revoked.id() + "/link", "", "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.INVITATION_CLOSED);
    }

    @Test
    @DisplayName("INV-8.3 An unknown invitation has no link to read")
    void anUnknownInvitationHasNoLinkToRead() {
        assertProblem(
                send(HttpMethod.GET, "/api/v1/invitations/" + UUID.randomUUID() + "/link", "", "admin"),
                HttpStatus.NOT_FOUND,
                Problems.NOT_FOUND);
    }

    @Test
    @DisplayName("INV-9.1 Other responses carry neither the link nor the token")
    void otherResponsesCarryNeitherTheLinkNorTheToken() {
        MvcTestResult created = createRequest(INVITED_EMAIL, "[]");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        String id = body(created, "invitation.id");
        String token = tokenOf(created);

        MvcTestResult reissued = send(HttpMethod.POST, "/api/v1/invitations/" + id + "/link", "", "admin");
        assertThat(reissued).hasStatusOk();
        String newToken = tokenOf(reissued);

        MvcTestResult list = send(HttpMethod.GET, "/api/v1/invitations", "", "admin");
        MvcTestResult revoked = send(HttpMethod.POST, "/api/v1/invitations/" + id + "/revoke", "", "admin");
        for (MvcTestResult result : List.of(list, revoked)) {
            assertThat(result).hasStatusOk();
            assertThat(contentOf(result)).doesNotContain(token).doesNotContain(newToken);
            assertThat(contentOf(result)).doesNotContain("\"link\"").doesNotContain("\"token\"");
        }
    }

    @Test
    @DisplayName("INV-9.2 The responses with a link are not cached")
    void theResponsesWithALinkAreNotCached() {
        MvcTestResult created = createRequest(INVITED_EMAIL, "[]");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
        String id = body(created, "invitation.id");

        MvcTestResult read = send(HttpMethod.GET, "/api/v1/invitations/" + id + "/link", "", "admin");
        assertThat(read).hasStatusOk();
        assertThat(read).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
        MvcTestResult reissued = send(HttpMethod.POST, "/api/v1/invitations/" + id + "/link", "", "admin");
        assertThat(reissued).hasStatusOk();
        assertThat(reissued).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @Test
    @DisplayName("INV-10.1 A visitor previews a valid link")
    void aVisitorPreviewsAValidLink() {
        InvitationRef invitation = create(INVITED_EMAIL, "[]");

        for (String authorization : List.of("", "Bearer not-a-valid-token")) {
            MvcTestResult preview = preview(invitation.token(), authorization);

            assertThat(preview).hasStatusOk();
            assertThat(preview).bodyJson().extractingPath("$.email").isEqualTo(INVITED_EMAIL);
            assertThat(preview).bodyJson().hasPath("$.expiresAt");
        }
    }

    @Test
    @DisplayName("INV-11.1 A new person joins")
    void aNewPersonJoins() {
        InvitationRef invitation = create(INVITED_EMAIL, "[\"MENTOR\"]");

        MvcTestResult accepted = accept(invitation.token(), "stranger", INVITED_EMAIL, true);

        assertThat(accepted).hasStatusOk();
        assertThat(accepted).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(accepted).bodyJson().extractingPath("$.user.email").isEqualTo(INVITED_EMAIL);
        assertThat(accepted).bodyJson().extractingPath("$.user.firstName").isEqualTo("Ivan");
        assertThat(accepted).bodyJson().extractingPath("$.user.lastName").isEqualTo("Sidorov");
        assertThat(accepted).bodyJson().extractingPath("$.user.roles").asArray().containsExactly("MENTOR");
        UUID userId = jdbc.sql("select id from users where email = :email")
                .param("email", INVITED_EMAIL)
                .query(UUID.class)
                .single();
        assertThat(users.status(userId)).isEqualTo("ACTIVE");
        assertThat(users.roles(userId)).containsExactly("MENTOR");
        assertThat(jdbc.sql("select user_id from user_identities where subject = 'stranger'")
                        .query(UUID.class)
                        .single())
                .isEqualTo(userId);
        assertThat(jdbc.sql("select accepted_user_id from invitations where id = :id")
                        .param("id", invitation.id())
                        .query(UUID.class)
                        .single())
                .isEqualTo(userId);
        assertThat(jdbc.sql("select accepted_at is not null from invitations where id = :id")
                        .param("id", invitation.id())
                        .query(Boolean.class)
                        .single())
                .isTrue();
    }

    @Test
    @DisplayName("INV-11.4 The next request of the new user passes the gate")
    void theNextRequestOfTheNewUserPassesTheGate() {
        InvitationRef invitation = create(INVITED_EMAIL, "[\"MENTOR\"]");

        MvcTestResult accepted = accept(invitation.token(), "stranger", INVITED_EMAIL, true);

        assertThat(accepted).hasStatusOk();
        assertProblem(send(HttpMethod.GET, "/api/v1/users", "", "stranger"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
    }

    private void expire(UUID invitation) {
        jdbc.sql("update invitations set expires_at = now() - interval '1 minute' where id = :id")
                .param("id", invitation)
                .update();
    }

    private void age(UUID invitation, int hours) {
        jdbc.sql("update invitations set created_at = now() - make_interval(hours => :hours) where id = :id")
                .param("hours", hours)
                .param("id", invitation)
                .update();
    }

    private String storedToken(UUID invitation) {
        return jdbc.sql("select token from invitations where id = :id")
                .param("id", invitation)
                .query(String.class)
                .single();
    }

    private String revocationOf(UUID invitation) {
        return jdbc.sql("select revoked_at || ' ' || revoked_by from invitations where id = :id")
                .param("id", invitation)
                .query(String.class)
                .optional()
                .orElse("null");
    }

    private String contentOf(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private record InvitationRef(UUID id, String link, String token) {}

    private InvitationRef create(String email, String roles) {
        MvcTestResult result = createRequest(email, roles);
        assertThat(result).hasStatus(HttpStatus.CREATED);
        String link = body(result, "link");
        return new InvitationRef(UUID.fromString(body(result, "invitation.id")), link, tokenOf(result));
    }

    private MvcTestResult createRequest(String email, String roles) {
        return send(
                HttpMethod.POST,
                "/api/v1/invitations",
                "{\"email\": \"%s\", \"roles\": %s}".formatted(email, roles),
                "admin");
    }

    private String body(MvcTestResult result, String path) {
        return jsonMapper
                .readTree(result.getResponse().getContentAsByteArray())
                .at("/" + path.replace('.', '/'))
                .asString();
    }

    private String tokenOf(MvcTestResult result) {
        String link = body(result, "link");
        return link.substring(link.lastIndexOf('/') + 1);
    }

    private MvcTestResult preview(String token, String authorization) {
        MockMvcRequestBuilder request = mvc.post()
                .uri("/api/v1/invitations/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token));
        if (!authorization.isEmpty()) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return request.exchange();
    }

    private MvcTestResult accept(String token, String subject, String email, boolean verified) {
        return mvc.post()
                .uri("/api/v1/invitations/accept")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        issuer.token(subject)
                                .email(email, verified)
                                .claim("given_name", "Ivan")
                                .claim("family_name", "Sidorov")
                                .bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"%s\"}".formatted(token))
                .exchange();
    }

    private void assertInvalid(MvcTestResult result) {
        assertProblem(result, HttpStatus.NOT_FOUND, InvitationProblems.INVITATION_INVALID);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("This invitation link is invalid or has expired.");
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
