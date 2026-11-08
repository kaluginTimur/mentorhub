package com.mentorhub.users.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.support.EndpointTest;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.invitation.InvitationProblems;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.web.Problems;
import java.util.List;
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
import tools.jackson.databind.json.JsonMapper;

/** Invitations through the real chain: signed tokens, the gate, the public preview and what a later request sees. */
@EndpointTest
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

    // The acceptance, written by one service, is shown in the history of the user, read by another.
    @Test
    void anAcceptanceIsShownInTheHistoryOfTheUser() {
        InvitationRef invitation = create(INVITED_EMAIL, "[\"MENTOR\", \"STUDENT\"]");

        MvcTestResult accepted = accept(invitation.token(), "stranger", INVITED_EMAIL, true);

        assertThat(accepted).hasStatusOk();
        MvcTestResult history =
                send(HttpMethod.GET, "/api/v1/users/" + body(accepted, "user.id") + "/history", "", "admin");
        assertThat(history).hasStatusOk();
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
