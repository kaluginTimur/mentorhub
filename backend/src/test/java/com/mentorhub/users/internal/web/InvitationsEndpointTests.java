package com.mentorhub.users.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.invitation.InvitationProblems;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestIssuerConfiguration;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.web.Problems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
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
                    new String[] {"POST", "/api/v1/invitations/{id}/link", ""})) {
                calls.add(Arguments.of(
                        role == null ? "no roles" : role.name(), operation[0], operation[1], operation[2]));
            }
        }
        return calls.stream();
    }

    // AC-5: nothing is created, changed or audited.
    @ParameterizedTest(name = "{0}: {1} {2}")
    @MethodSource("forbiddenCalls")
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

    // AC-5: the gate answers a disabled caller before any permission is looked at.
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

    // AC-1, AC-4, AC-8, AC-10, AC-14, AC-16 and the link's single appearance, from creation to a repeated accept.
    @Test
    void invitedPersonJoinsThroughTheLink() {
        InvitationRef invitation = create(INVITED_EMAIL, "[\"MENTOR\", \"STUDENT\"]");
        assertThat(invitation.token()).hasSize(43);
        assertThat(invitation.link()).endsWith("/invite/" + invitation.token());

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
        // AC-10: the next request passes the gate; the caller only lacks the permission.
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

        // AC-14: a retry changes nothing; AC-9: the link is invalid for everyone else and for preview.
        assertThat(accept(invitation.token(), "stranger", INVITED_EMAIL, true)).hasStatusOk();
        assertThat(users.count("audit_log")).isEqualTo(2);
        assertInvalid(preview(invitation.token(), ""));
        assertInvalid(accept(invitation.token(), "other", INVITED_EMAIL, true));

        assertThat(jdbc.sql("select count(*) from invitations i where i::text like :token")
                        .param("token", "%" + invitation.token() + "%")
                        .query(Integer.class)
                        .single())
                .isZero();
        assertThat(jdbc.sql("select count(*) from audit_log where details::text like :token")
                        .param("token", "%" + invitation.token() + "%")
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    // AC-17, AC-11: a disabled user accepts despite the gate, and is active on the next request.
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

    // AC-12
    @ParameterizedTest
    @MethodSource("mismatchedEmails")
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

    // AC-13
    @Test
    void identityLinkedToAnotherUserIsAConflict() {
        UUID other = users.create("other@x.test", UserStatus.ACTIVE);
        users.link(other, "stranger");
        InvitationRef invitation = create(INVITED_EMAIL, "[]");

        MvcTestResult result = accept(invitation.token(), "stranger", INVITED_EMAIL, true);

        assertProblem(result, HttpStatus.CONFLICT, InvitationProblems.IDENTITY_LINKED_ELSEWHERE);
        assertThat(users.count("users")).isEqualTo(2);
    }

    // AC-17
    @Test
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

    // AC-9: malformed, empty and oversized tokens.
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

    // AC-3: the problems carry their extensions through the whole chain.
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

    // AC-2
    @Test
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

    // AC-6, AC-7
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

    // AC-17: a stored role the code does not know is skipped on reads.
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

    // AC-4: the search limit is the email length, not the user search's 100.
    @Test
    void searchAcceptsUpTo254CharactersAndRejectsMore() {
        assertThat(send(HttpMethod.GET, "/api/v1/invitations?q=" + "x".repeat(254), "", "admin"))
                .hasStatusOk();
        MvcTestResult tooLong = send(HttpMethod.GET, "/api/v1/invitations?q=" + "x".repeat(255), "", "admin");
        assertProblem(tooLong, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(tooLong).bodyJson().extractingPath("$.errors[0].field").isEqualTo("q");
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
