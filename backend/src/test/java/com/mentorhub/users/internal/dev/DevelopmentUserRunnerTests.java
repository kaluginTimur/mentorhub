package com.mentorhub.users.internal.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestIssuerConfiguration;
import com.mentorhub.users.internal.security.TestUsers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The runner has already run at startup; TestIssuerConfiguration links the development users to TestIssuer. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
class DevelopmentUserRunnerTests {

    private static final String ADMIN = "00000000-0000-4000-8000-000000000001";
    private static final String MENTOR = "00000000-0000-4000-8000-000000000002";
    private static final String STUDENT = "00000000-0000-4000-8000-000000000003";
    private static final String NO_ROLES = "00000000-0000-4000-8000-000000000004";
    private static final String DISABLED = "00000000-0000-4000-8000-000000000005";
    private static final String STRANGER = "00000000-0000-4000-8000-000000000006";

    @Autowired
    DevelopmentUserRunner runner;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    TestUsers users;

    @BeforeEach
    void restoreDevelopmentUsers() {
        users = new TestUsers(jdbc);
        runner.run(new DefaultApplicationArguments());
    }

    // AC-30, S8: every access status is available without manual setup.
    @Test
    void everyDevelopmentUserSignsInWithItsStatus() {
        assertMe(ADMIN, "ACTIVE", List.of("ADMIN"));
        assertMe(MENTOR, "ACTIVE", List.of("MENTOR"));
        assertMe(STUDENT, "ACTIVE", List.of("STUDENT"));
        assertMe(NO_ROLES, "ACTIVE", List.of());
        assertThat(me(DISABLED)).bodyJson().isStrictlyEqualTo("{\"status\": \"DISABLED\"}");
        assertThat(me(STRANGER)).bodyJson().isStrictlyEqualTo("{\"status\": \"NOT_INVITED\"}");
    }

    @Test
    void runningAgainChangesNothing() {
        Map<String, Integer> before = counts();

        runner.run(new DefaultApplicationArguments());

        assertThat(counts()).isEqualTo(before);
    }

    @Test
    void restoresStatusAndExactlyTheConfiguredRoles() {
        UUID mentor = userId("mentor@mentorhub.local");
        jdbc.sql("update users set status = 'DISABLED' where id = :id")
                .param("id", mentor)
                .update();
        jdbc.sql("delete from user_roles where user_id = :id")
                .param("id", mentor)
                .update();
        users.assignStoredRole(mentor, "ADMIN");
        UUID disabled = userId("disabled@mentorhub.local");
        jdbc.sql("update users set status = 'ACTIVE' where id = :id")
                .param("id", disabled)
                .update();

        runner.run(new DefaultApplicationArguments());

        assertThat(users.status(mentor)).isEqualTo("ACTIVE");
        assertThat(users.roles(mentor)).containsExactly("MENTOR");
        assertThat(users.status(disabled)).isEqualTo("DISABLED");
    }

    @Test
    void keepsTheProfileOfAnExistingUser() {
        UUID admin = userId("admin@mentorhub.local");
        jdbc.sql("update users set first_name = 'Renamed' where id = :id")
                .param("id", admin)
                .update();

        runner.run(new DefaultApplicationArguments());

        assertThat(jdbc.sql("select first_name from users where id = :id")
                        .param("id", admin)
                        .query(String.class)
                        .single())
                .isEqualTo("Renamed");
    }

    @Test
    void strangerExistsOnlyInKeycloak() {
        assertThat(jdbc.sql("select count(*) from users where email = 'stranger@mentorhub.local'")
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    private void assertMe(String subject, String status, List<String> roles) {
        MvcTestResult result = me(subject);
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status);
        assertThat(result).bodyJson().extractingPath("$.user.roles").asArray().containsExactlyElementsOf(roles);
    }

    private MvcTestResult me(String subject) {
        return mvc.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, issuer.token(subject).bearer())
                .exchange();
    }

    private UUID userId(String email) {
        return jdbc.sql("select id from users where email = :email")
                .param("email", email)
                .query(UUID.class)
                .single();
    }

    private Map<String, Integer> counts() {
        return Map.of(
                "users", users.count("users"),
                "user_identities", users.count("user_identities"),
                "user_roles", users.count("user_roles"));
    }
}
