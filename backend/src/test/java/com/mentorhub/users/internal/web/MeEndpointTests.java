package com.mentorhub.users.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestIssuerConfiguration;
import com.mentorhub.users.internal.security.TestUsers;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** {@code GET /api/v1/me} through the real chain, from token to JSON. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
class MeEndpointTests {

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

    // AC-3
    @Test
    void notInvitedCallerGetsItsStatusWithoutUserAndCreatesNoRows() {
        MvcTestResult result =
                me(issuer.token("stranger").email("stranger@example.com", true).bearer());

        assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"status\": \"NOT_INVITED\"}");
        assertThat(users.count("users")).isZero();
        assertThat(users.count("user_identities")).isZero();
    }

    // AC-4
    @Test
    void disabledUserGetsItsStatusWithoutUser() {
        UUID userId = users.create("anna@example.com", UserStatus.DISABLED, Role.ADMIN);
        users.link(userId, "anna");

        assertThat(me(issuer.token("anna").bearer()))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("{\"status\": \"DISABLED\"}");
    }

    // AC-7
    @Test
    void activeUserWithoutRolesHasEmptyRolesAndPermissions() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE);
        users.link(userId, "anna");

        MvcTestResult result = me(issuer.token("anna").bearer());

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(result).bodyJson().extractingPath("$.user.roles").asArray().isEmpty();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.user.permissions")
                .asArray()
                .isEmpty();
    }

    // AC-9
    @Test
    void activeUserGetsUsersIdProfileRolesAndTheUnionOfPermissions() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE, Role.MENTOR, Role.ADMIN);
        users.link(userId, "anna");

        assertThat(me(issuer.token("anna").bearer())).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {
                  "status": "ACTIVE",
                  "user": {
                    "id": "%s",
                    "email": "anna@example.com",
                    "firstName": "Anna",
                    "lastName": "Petrova",
                    "phone": null,
                    "roles": ["ADMIN", "MENTOR"],
                    "permissions": ["USERS_READ", "USERS_MANAGE"]
                  }
                }
                """.formatted(userId));
    }

    @Test
    void missingNamesAreSentAsNull() {
        UUID userId = users.create("anna@example.com", UserStatus.ACTIVE);
        jdbc.sql("update users set first_name = null where id = :id")
                .param("id", userId)
                .update();
        users.link(userId, "anna");

        MvcTestResult result = me(issuer.token("anna").bearer());

        assertThat(result).bodyJson().hasPath("$.user.firstName");
        assertThat(result).bodyJson().extractingPath("$.user.firstName").isNull();
        assertThat(result).bodyJson().extractingPath("$.user.lastName").isEqualTo("Petrova");
    }

    // AC-11, seen through /me: the bootstrap administrator is active with the ADMIN role on the first request.
    @Test
    void bootstrapAdministratorSeesTheAdminRoleOnFirstRequest() {
        MvcTestResult result = me(issuer.token("first-admin")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer());

        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(result).bodyJson().extractingPath("$.user.roles").asArray().containsExactly("ADMIN");
    }

    private MvcTestResult me(String authorization) {
        return mvc.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange();
    }
}
