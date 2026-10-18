package com.mentorhub.users.internal.security;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.web.Problems;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The real chain: token decoding per trusted issuer, the converter, the gate, permissions and Problem Details. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationChainTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    TestUsers users;

    @BeforeEach
    void cleanDatabase() {
        users = new TestUsers(jdbc);
        users.deleteAll();
    }

    // AC-2
    @Test
    void requestWithoutTokenIsUnauthorized() {
        for (String path : List.of("/api/v1/test/current-user", "/api/v1/me")) {
            MvcTestResult result = mvc.get().uri(path).exchange();

            assertProblem(result, HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
            assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                    .startsWith("Bearer");
        }
    }

    @Test
    void malformedTokenIsUnauthorized() {
        MvcTestResult result = get("/api/v1/test/current-user", "Bearer not-a-jwt");

        assertProblem(result, HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
        assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer");
    }

    // AC-23
    @Test
    void tokenOfUntrustedIssuerIsUnauthorized() {
        String token =
                issuer.token("subject").claim("iss", "https://untrusted.test").bearer();

        assertProblem(get("/api/v1/test/current-user", token), HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
    }

    // AC-23
    @Test
    void tokenWithoutMentorHubAudienceIsUnauthorized() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(userId, "anna");

        String otherAudience =
                issuer.token("anna").claim("aud", List.of("another-api")).bearer();
        String noAudience = issuer.token("anna").without("aud").bearer();

        assertProblem(get("/api/v1/test/current-user", otherAudience), HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
        assertProblem(get("/api/v1/test/current-user", noAudience), HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
    }

    @Test
    void tokenSignedWithUnknownKeyIsUnauthorized() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(userId, "anna");

        String token = issuer.token("anna").signedWithUnknownKey().bearer();

        assertProblem(get("/api/v1/test/current-user", token), HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
    }

    @Test
    void expiredTokenIsUnauthorized() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(userId, "anna");

        String token = issuer.token("anna").expired().bearer();

        assertProblem(get("/api/v1/test/current-user", token), HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
    }

    // AC-24
    @Test
    void publicEndpointIgnoresStaleOrInvalidToken() {
        assertThat(get("/api/v1/system/info", "Bearer not-a-jwt")).hasStatusOk();
        assertThat(get("/api/v1/system/info", issuer.token("anna").expired().bearer()))
                .hasStatusOk();
        assertThat(mvc.get().uri("/actuator/health").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .hasStatusOk();
    }

    // AC-3
    @Test
    void notInvitedCallerIsRefusedAndCreatesNoRows() {
        String token =
                issuer.token("stranger").email("stranger@example.com", true).bearer();

        assertProblem(get("/api/v1/test/current-user", token), HttpStatus.FORBIDDEN, Problems.NOT_INVITED);
        assertThat(users.count("users")).isZero();
        assertThat(users.count("user_identities")).isZero();
        assertThat(users.count("user_roles")).isZero();
    }

    // AC-4
    @Test
    void disabledUserIsRefusedOnTheNextRequestWithTheSameToken() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(userId, "anna");
        String token = issuer.token("anna").bearer();
        assertThat(get("/api/v1/test/current-user", token)).hasStatusOk();

        jdbc.sql("update users set status = 'DISABLED', disabled_reason = 'SECURITY' where id = :id")
                .param("id", userId)
                .update();

        assertProblem(get("/api/v1/test/current-user", token), HttpStatus.FORBIDDEN, Problems.ACCESS_ENDED);
    }

    @Test
    void activeUserIsIdentifiedByUsersIdNotTokenSubject() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(userId, "anna");

        MvcTestResult result =
                get("/api/v1/test/current-user", issuer.token("anna").bearer());

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(userId.toString());
        assertThat(result)
                .bodyJson()
                .extractingPath("$.permissions")
                .asArray()
                .containsExactlyInAnyOrder("USERS_READ", "USERS_MANAGE", "USERS_INVITE");
    }

    // AC-7, AC-17
    @Test
    void activeUserWithoutRolesIsAdmittedWithoutPermissions() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE);
        users.link(userId, "anna");
        String token = issuer.token("anna").bearer();

        MvcTestResult current = get("/api/v1/test/current-user", token);
        assertThat(current).hasStatusOk();
        assertThat(current).bodyJson().extractingPath("$.permissions").asArray().isEmpty();

        assertProblem(get("/api/v1/test/users-read", token), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
    }

    // AC-17
    @Test
    void permissionGrantsTheOperation() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(userId, "anna");

        MvcTestResult result =
                get("/api/v1/test/users-read", issuer.token("anna").bearer());

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.result").isEqualTo("granted");
    }

    // AC-17
    @Test
    void roleWithoutThePermissionIsForbidden() {
        UUID userId = users.create("mentor@example.com", UserStatus.ACTIVE, Role.MENTOR, Role.STUDENT);
        users.link(userId, "mentor");

        assertProblem(
                get("/api/v1/test/users-read", issuer.token("mentor").bearer()),
                HttpStatus.FORBIDDEN,
                Problems.FORBIDDEN);
    }

    // AC-22
    @Test
    void rolesAndGroupsInTheTokenGrantNothing() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE);
        users.link(userId, "anna");
        String token = issuer.token("anna")
                .claim("realm_access", Map.of("roles", List.of("admin", "ADMIN")))
                .claim("resource_access", Map.of("mentorhub-web", Map.of("roles", List.of("ADMIN"))))
                .claim("groups", List.of("admin", "ADMIN"))
                .claim("roles", List.of("ADMIN"))
                .claim("authorities", List.of("USERS_READ"))
                .claim("scope", "openid USERS_READ")
                .bearer();

        assertProblem(get("/api/v1/test/users-read", token), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
    }

    // AC-20
    @Test
    void unknownStoredRoleGrantsNothingAndIsReportedOnce(CapturedOutput output) {
        String unknownRole =
                "RETIRED_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        jdbc.sql("insert into roles (name) values (:name)")
                .param("name", unknownRole)
                .update();
        UUID admin = users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.assignStoredRole(admin, unknownRole);
        users.link(admin, "anna");
        UUID other = users.create("boris@example.com", UserStatus.ACTIVE);
        users.assignStoredRole(other, unknownRole);
        users.link(other, "boris");

        assertThat(get("/api/v1/test/users-read", issuer.token("anna").bearer()))
                .hasStatusOk();
        assertThat(get("/api/v1/test/users-read", issuer.token("anna").bearer()))
                .hasStatusOk();
        assertProblem(
                get("/api/v1/test/users-read", issuer.token("boris").bearer()),
                HttpStatus.FORBIDDEN,
                Problems.FORBIDDEN);

        assertThat(output.getOut()).containsOnlyOnce("Ignoring stored role '" + unknownRole + "'");
    }

    // AC-10
    @Test
    void storedProfileIsNotUpdatedFromTheToken() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE);
        users.link(userId, "anna");
        String token = issuer.token("anna")
                .email("anna.new@example.com", true)
                .claim("given_name", "Hanna")
                .claim("family_name", "Smith")
                .bearer();

        assertThat(get("/api/v1/test/current-user", token)).hasStatusOk();

        Map<String, Object> stored = jdbc.sql("select email, first_name, last_name from users where id = :id")
                .param("id", userId)
                .query()
                .singleRow();
        assertThat(stored)
                .containsEntry("email", "anna@example.com")
                .containsEntry("first_name", "Anna")
                .containsEntry("last_name", "Petrova");
    }

    // AC-19
    @Test
    void inaccessibleResourceRespondsLikeAMissingOne() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE);
        users.link(userId, "anna");

        MvcTestResult result = get("/api/v1/test/missing", issuer.token("anna").bearer());

        assertProblem(result, HttpStatus.NOT_FOUND, Problems.NOT_FOUND);
        assertThat(result).bodyJson().doesNotHavePath("$.detail");
    }

    @Test
    void accessDeniedFromAServiceIsForbiddenNotServerError() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(userId, "anna");

        assertProblem(
                get("/api/v1/test/denied", issuer.token("anna").bearer()), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
    }

    @Test
    void unexpectedErrorIsAProblemWithoutMentorHubType() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE);
        users.link(userId, "anna");

        MvcTestResult result = get("/api/v1/test/failure", issuer.token("anna").bearer());

        assertProblem(result, HttpStatus.INTERNAL_SERVER_ERROR, URI.create("about:blank"));
        assertThat(result).bodyText().doesNotContain("Unexpected failure");
    }

    @Test
    void unknownApiPathIsNotFoundForActiveUser() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE);
        users.link(userId, "anna");

        assertThat(get("/api/v1/no-such-endpoint", issuer.token("anna").bearer()))
                .hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void pathsOutsideTheApiAreDenied() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(userId, "anna");

        assertProblem(mvc.get().uri("/internal/anything").exchange(), HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
        assertProblem(
                get("/internal/anything", issuer.token("anna").bearer()), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
        assertThat(get("/openapi.yaml", issuer.token("anna").bearer())).hasStatus(HttpStatus.FORBIDDEN);
    }

    private MvcTestResult get(String path, String authorization) {
        return mvc.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange();
    }
}
