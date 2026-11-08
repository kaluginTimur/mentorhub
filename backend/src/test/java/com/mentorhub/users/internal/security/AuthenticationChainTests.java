package com.mentorhub.users.internal.security;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
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
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    @DisplayName("IDN-1.1 A request without a token is refused")
    void requestWithoutTokenIsUnauthorized() {
        for (String path : List.of("/api/v1/test/current-user", "/api/v1/me")) {
            MvcTestResult result = mvc.get().uri(path).exchange();

            assertProblem(result, HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
            assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE))
                    .startsWith("Bearer");
        }
    }

    @Test
    @DisplayName("IDN-1.3 A token of an untrusted issuer or not meant for MentorHub is refused")
    void tokenWithoutMentorHubAudienceIsUnauthorized() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(userId, "anna");
        String untrustedIssuer =
                issuer.token("anna").claim("iss", "https://untrusted.test").bearer();
        assertProblem(
                get("/api/v1/test/current-user", untrustedIssuer), HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);

        String otherAudience =
                issuer.token("anna").claim("aud", List.of("another-api")).bearer();
        String noAudience = issuer.token("anna").without("aud").bearer();

        assertProblem(get("/api/v1/test/current-user", otherAudience), HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
        assertProblem(get("/api/v1/test/current-user", noAudience), HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
    }

    @Test
    @DisplayName("IDN-1.2 A token that is malformed, expired or wrongly signed is refused")
    void malformedExpiredOrWronglySignedTokenIsUnauthorized() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(userId, "anna");

        for (String token : List.of(
                "Bearer not-a-jwt",
                issuer.token("anna").expired().bearer(),
                issuer.token("anna").signedWithUnknownKey().bearer())) {
            assertProblem(get("/api/v1/test/current-user", token), HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
        }
    }

    @Test
    @DisplayName("IDN-3.1 An identity that is not linked is refused and nothing is created")
    void notInvitedCallerIsRefusedAndCreatesNoRows() {
        UUID admin = users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "anna");
        String token =
                issuer.token("stranger").email("stranger@example.com", true).bearer();

        assertProblem(get("/api/v1/test/current-user", token), HttpStatus.FORBIDDEN, Problems.NOT_INVITED);
        assertThat(users.count("users")).isOne();
        assertThat(users.count("user_identities")).isOne();
        assertThat(users.count("user_roles")).isOne();
    }

    @Test
    @DisplayName("IDN-3.2 A disabled user is refused")
    void disabledUserIsRefused() {
        UUID userId = users.create("anna@example.com", UserStatus.DISABLED, Role.ADMIN);
        users.link(userId, "anna");

        assertProblem(
                get("/api/v1/test/current-user", issuer.token("anna").bearer()),
                HttpStatus.FORBIDDEN,
                Problems.ACCESS_ENDED);
    }

    @Test
    @DisplayName("IDN-4.1 An email that matches a user is not enough")
    void verifiedEmailOfAnExistingUserDoesNotLinkTheIdentity() {
        users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        String token =
                issuer.token("someone-else").email("anna@example.com", true).bearer();

        assertProblem(get("/api/v1/test/current-user", token), HttpStatus.FORBIDDEN, Problems.NOT_INVITED);
        assertThat(users.count("user_identities")).isZero();
    }

    @Test
    @DisplayName("IDN-3.3 Disabling applies to the next request")
    void disablingAppliesToTheNextRequestWithTheSameToken() {
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
    @DisplayName("IDN-3.4 Enabling applies to the next request")
    void enablingAppliesToTheNextRequestWithTheSameToken() {
        UUID userId = users.create("anna@example.com", UserStatus.DISABLED, Role.ADMIN);
        users.link(userId, "anna");
        String token = issuer.token("anna").bearer();
        assertProblem(get("/api/v1/test/current-user", token), HttpStatus.FORBIDDEN, Problems.ACCESS_ENDED);

        jdbc.sql("update users set status = 'ACTIVE', disabled_reason = null where id = :id")
                .param("id", userId)
                .update();

        assertThat(get("/api/v1/test/current-user", token)).hasStatusOk();
    }

    @Test
    @DisplayName("PERM-2.2 A user without roles has no permissions")
    void activeUserWithoutRolesIsRefusedAnOperationThatNeedsAPermission() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE);
        users.link(userId, "anna");
        String token = issuer.token("anna").bearer();

        assertProblem(get("/api/v1/test/users-read", token), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
    }

    @Test
    @DisplayName("PERM-4.1 Roles and groups in the token are ignored")
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

    @Test
    @DisplayName("PERM-5.1 An unknown role is skipped")
    void unknownStoredRoleGrantsNothing() {
        String unknownRole = storeUnknownRole();
        UUID mentor = users.create("anna@example.com", UserStatus.ACTIVE, Role.MENTOR);
        users.assignStoredRole(mentor, unknownRole);
        users.link(mentor, "anna");
        UUID admin = users.create("boris@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.assignStoredRole(admin, unknownRole);
        users.link(admin, "boris");

        MvcTestResult current =
                get("/api/v1/test/current-user", issuer.token("anna").bearer());
        assertThat(current).hasStatusOk();
        assertThat(current).bodyJson().extractingPath("$.permissions").asArray().isEmpty();
        assertProblem(
                get("/api/v1/test/users-read", issuer.token("anna").bearer()),
                HttpStatus.FORBIDDEN,
                Problems.FORBIDDEN);
        MvcTestResult administrator =
                get("/api/v1/test/current-user", issuer.token("boris").bearer());
        assertThat(administrator).hasStatusOk();
        assertThat(administrator)
                .bodyJson()
                .extractingPath("$.permissions")
                .asArray()
                .containsExactlyInAnyOrder("USERS_READ", "USERS_MANAGE", "USERS_INVITE");
    }

    private String storeUnknownRole() {
        String unknownRole =
                "RETIRED_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
        jdbc.sql("insert into roles (name) values (:name)")
                .param("name", unknownRole)
                .update();
        return unknownRole;
    }

    @Test
    @DisplayName("PERM-3.3 Removing a role takes effect on the next request")
    void removedRoleTakesEffectWithTheSameToken() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(userId, "anna");
        String token = issuer.token("anna").bearer();
        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();

        jdbc.sql("delete from user_roles where user_id = :userId")
                .param("userId", userId)
                .update();

        assertProblem(get("/api/v1/test/users-read", token), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
    }

    @Test
    @DisplayName("PERM-3.4 Adding a role takes effect on the next request")
    void addedRoleTakesEffectWithTheSameToken() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE);
        users.link(userId, "anna");
        String token = issuer.token("anna").bearer();
        assertProblem(get("/api/v1/test/users-read", token), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);

        users.assignStoredRole(userId, Role.ADMIN.name());

        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();
    }

    @Test
    @DisplayName("PERM-6.3 An inaccessible resource looks like a missing one")
    void inaccessibleResourceRespondsLikeAMissingOne() throws Exception {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE);
        users.link(userId, "anna");
        String token = issuer.token("anna").bearer();

        MvcTestResult inaccessible = get("/api/v1/test/resources/restricted", token);
        MvcTestResult missing = get("/api/v1/test/resources/no-such-resource", token);

        assertProblem(inaccessible, HttpStatus.NOT_FOUND, Problems.NOT_FOUND);
        assertThat(inaccessible).bodyJson().doesNotHavePath("$.detail");
        assertProblem(missing, HttpStatus.NOT_FOUND, Problems.NOT_FOUND);
        assertThat(inaccessible.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(missing.getResponse()
                        .getContentAsString(StandardCharsets.UTF_8)
                        .replace("no-such-resource", "restricted"));
    }

    @Test
    @DisplayName("PLT-1.2 An unexpected failure is a generic 500")
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
    @DisplayName("PLT-6.3 A path outside the API is denied")
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
