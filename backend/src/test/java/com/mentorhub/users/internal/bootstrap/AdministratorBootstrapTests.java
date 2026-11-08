package com.mentorhub.users.internal.bootstrap;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestIssuerConfiguration;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.web.Problems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
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
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Decision 0005 through the real chain; the bootstrap address is {@link TestIssuer#BOOTSTRAP_EMAIL}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
class AdministratorBootstrapTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper jsonMapper;

    TestUsers users;

    @BeforeEach
    void cleanDatabase() {
        users = new TestUsers(jdbc);
        users.deleteAll();
    }

    @Test
    @DisplayName("IDN-6.1 The first administrator is created")
    void createsAndLinksTheAdministratorOnFirstSignIn() {
        String token = issuer.token("first-admin")
                .email("Bootstrap@MentorHub.TEST", true)
                .claim("given_name", "Anna")
                .bearer();

        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();
        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();

        UUID userId = userIdByEmail(TestIssuer.BOOTSTRAP_EMAIL);
        assertThat(users.status(userId)).isEqualTo("ACTIVE");
        assertThat(users.roles(userId)).containsExactly("ADMIN");
        assertThat(linkedUser("first-admin")).isEqualTo(userId);
        assertThat(jdbc.sql("select email, first_name from users where id = :id")
                        .param("id", userId)
                        .query()
                        .singleRow())
                .containsEntry("email", "Bootstrap@MentorHub.TEST")
                .containsEntry("first_name", "Anna");
    }

    @Test
    @DisplayName("AUD-7.1 The administrator bootstrap is audited without an actor")
    void bootstrapIsAuditedWithoutAnActor() {
        String token = issuer.token("audited-admin")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();

        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();

        UUID userId = userIdByEmail(TestIssuer.BOOTSTRAP_EMAIL);
        assertThat(users.count("audit_log")).isOne();
        assertThat(bootstrapEntries(userId)).containsExactly(changes(true, true, false, true));
    }

    @Test
    @DisplayName("AUD-7.2 A bootstrap that changes nothing writes no entry")
    void bootstrapThatChangesNothingWritesNoEntry() {
        UUID userId = users.create(TestIssuer.BOOTSTRAP_EMAIL, UserStatus.ACTIVE, Role.ADMIN);
        users.link(userId, "settled-admin");
        String token = issuer.token("settled-admin")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();

        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();

        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("IDN-6.2 An existing user is recovered (identity not linked)")
    void enablesAndLinksAnExistingDisabledUser() {
        UUID userId = users.create(TestIssuer.BOOTSTRAP_EMAIL, UserStatus.DISABLED, Role.MENTOR);
        String token = issuer.token("returning-admin")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();

        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();

        assertThat(users.status(userId)).isEqualTo("ACTIVE");
        assertThat(users.roles(userId)).containsExactly("ADMIN", "MENTOR");
        assertThat(linkedUser("returning-admin")).isEqualTo(userId);
        assertThat(users.count("users")).isEqualTo(1);
    }

    @Test
    void grantsAdminToTheLinkedUserWhoLostIt() {
        UUID userId = users.create(TestIssuer.BOOTSTRAP_EMAIL, UserStatus.ACTIVE);
        users.link(userId, "former-admin");
        String token = issuer.token("former-admin")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();

        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();

        assertThat(users.roles(userId)).containsExactly("ADMIN");
    }

    @Test
    void enablesTheLinkedDisabledUser() {
        UUID userId = users.create(TestIssuer.BOOTSTRAP_EMAIL, UserStatus.DISABLED);
        users.link(userId, "disabled-admin");
        String token = issuer.token("disabled-admin")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();

        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();

        assertThat(users.status(userId)).isEqualTo("ACTIVE");
        assertThat(users.roles(userId)).containsExactly("ADMIN");
        assertThat(users.count("user_identities")).isEqualTo(1);
        assertThat(bootstrapEntries(userId)).containsExactly(changes(false, false, true, true));
    }

    @Test
    @DisplayName("IDN-6.10 Recovery clears the reason and comment of a disabling")
    void enablingClearsTheDisableReasonAndComment() {
        UUID userId = users.create(TestIssuer.BOOTSTRAP_EMAIL, UserStatus.ACTIVE, Role.ADMIN);
        users.link(userId, "recovering-admin");
        jdbc.sql("update users set status = 'DISABLED', disabled_reason = 'OTHER', disabled_comment = 'On leave'"
                        + " where id = :id")
                .param("id", userId)
                .update();
        String token = issuer.token("recovering-admin")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();

        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();

        assertThat(jdbc.sql("select status, disabled_reason, disabled_comment from users where id = :id")
                        .param("id", userId)
                        .query()
                        .singleRow())
                .containsEntry("status", "ACTIVE")
                .containsEntry("disabled_reason", null)
                .containsEntry("disabled_comment", null);
        assertThat(bootstrapEntries(userId)).containsExactly(changes(false, false, true, false));
    }

    @Test
    @DisplayName("IDN-6.7 An identity linked to another user is never moved")
    void neverMovesAnIdentityLinkedToAnotherUser() {
        UUID otherUser = users.create("someone@example.com", UserStatus.ACTIVE);
        users.link(otherUser, "someone");
        String token =
                issuer.token("someone").email(TestIssuer.BOOTSTRAP_EMAIL, true).bearer();

        assertProblem(get("/api/v1/test/users-read", token), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);

        assertThat(linkedUser("someone")).isEqualTo(otherUser);
        assertThat(users.roles(otherUser)).isEmpty();
        assertThat(users.count("users")).isEqualTo(1);
    }

    @Test
    @DisplayName("IDN-6.3 An active administrator switches the rule off for an identity that is not linked")
    void grantsNothingWhileAnActiveAdministratorExists() {
        UUID admin = users.create("admin@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "admin");

        String unlinked = issuer.token("unlinked-bootstrap")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();

        assertProblem(get("/api/v1/test/current-user", unlinked), HttpStatus.FORBIDDEN, Problems.NOT_INVITED);

        assertThat(users.count("users")).isOne();
        assertThat(users.count("user_identities")).isOne();
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    void disabledAdministratorDoesNotBlockTheRule() {
        users.create("admin@example.com", UserStatus.DISABLED, Role.ADMIN);
        String token = issuer.token("new-admin")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();

        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();
    }

    @Test
    @DisplayName("IDN-6.5 An email that is not verified grants nothing")
    void unverifiedOrMissingEmailGrantsNothing() {
        String unverified = issuer.token("unverified")
                .email(TestIssuer.BOOTSTRAP_EMAIL, false)
                .bearer();
        String noVerificationClaim = issuer.token("no-claim")
                .claim("email", TestIssuer.BOOTSTRAP_EMAIL)
                .bearer();
        String noEmail = issuer.token("no-email").claim("email_verified", true).bearer();

        for (String token : List.of(unverified, noVerificationClaim, noEmail)) {
            assertProblem(get("/api/v1/test/current-user", token), HttpStatus.FORBIDDEN, Problems.NOT_INVITED);
        }
        assertThat(users.count("users")).isZero();
    }

    @RepeatedTest(5)
    @DisplayName("IDN-6.9 Parallel first requests create one administrator")
    void parallelFirstRequestsCreateExactlyOneAdministrator() throws Exception {
        String token = issuer.token("parallel-admin")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();
        int requests = 8;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> statuses = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(requests)) {
            for (int i = 0; i < requests; i++) {
                statuses.add(executor.submit(() -> {
                    start.await();
                    return get("/api/v1/test/users-read", token).getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> status : statuses) {
                assertThat(status.get()).isEqualTo(200);
            }
        }

        assertThat(users.count("users")).isEqualTo(1);
        assertThat(users.count("user_identities")).isEqualTo(1);
        assertThat(users.roles(userIdByEmail(TestIssuer.BOOTSTRAP_EMAIL))).containsExactly("ADMIN");
        assertThat(users.count("audit_log")).isEqualTo(1);
    }

    @Test
    @DisplayName("IDN-6.4 An active administrator switches the rule off for a linked user")
    void linkedDisabledUserStaysDisabledWhileAnActiveAdministratorExists() {
        UUID admin = users.create("admin@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "admin");
        UUID disabled = users.create(TestIssuer.BOOTSTRAP_EMAIL, UserStatus.DISABLED, Role.MENTOR);
        users.link(disabled, "linked-disabled");
        String token = issuer.token("linked-disabled")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();

        assertProblem(get("/api/v1/test/current-user", token), HttpStatus.FORBIDDEN, Problems.ACCESS_ENDED);

        assertThat(users.status(disabled)).isEqualTo("DISABLED");
        assertThat(users.roles(disabled)).containsExactly("MENTOR");
    }

    @Test
    @DisplayName("IDN-6.6 Another email grants nothing")
    void verifiedEmailOtherThanTheBootstrapAddressGrantsNothing() {
        String token =
                issuer.token("someone").email("someone@example.com", true).bearer();

        assertProblem(get("/api/v1/test/current-user", token), HttpStatus.FORBIDDEN, Problems.NOT_INVITED);

        assertThat(users.count("users")).isZero();
        assertThat(users.count("user_identities")).isZero();
        assertThat(users.count("user_roles")).isZero();
    }

    @Test
    @DisplayName("IDN-6.8 A repeated sign-in changes nothing")
    void repeatedSignInChangesNothing() {
        String token = issuer.token("repeat-admin")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();
        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();
        UUID userId = userIdByEmail(TestIssuer.BOOTSTRAP_EMAIL);
        List<Map<String, Object>> entries = bootstrapEntries(userId);

        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();

        assertThat(users.count("users")).isOne();
        assertThat(users.count("user_identities")).isOne();
        assertThat(users.roles(userId)).containsExactly("ADMIN");
        assertThat(users.count("audit_log")).isOne();
        assertThat(bootstrapEntries(userId)).isEqualTo(entries);
    }

    private MvcTestResult get(String path, String authorization) {
        return mvc.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange();
    }

    /** The details of the user's USER_BOOTSTRAPPED_ADMIN entries; each has no actor. */
    private List<Map<String, Object>> bootstrapEntries(UUID userId) {
        assertThat(jdbc.sql("select count(*) from audit_log where actor_user_id is not null")
                        .query(Integer.class)
                        .single())
                .isZero();
        return jdbc
                .sql("select details::text from audit_log where action = 'USER_BOOTSTRAPPED_ADMIN'"
                        + " and subject_type = 'USER' and subject_id = :id")
                .param("id", userId)
                .query(String.class)
                .list()
                .stream()
                .map(details -> jsonMapper.readValue(details, new TypeReference<Map<String, Object>>() {}))
                .toList();
    }

    private static Map<String, Object> changes(
            boolean userCreated, boolean identityLinked, boolean enabled, boolean adminAdded) {
        return Map.of(
                "userCreated", userCreated,
                "identityLinked", identityLinked,
                "enabled", enabled,
                "adminAdded", adminAdded);
    }

    private UUID userIdByEmail(String email) {
        return jdbc.sql("select id from users where lower(email) = lower(:email)")
                .param("email", email)
                .query(UUID.class)
                .single();
    }

    private UUID linkedUser(String subject) {
        return jdbc.sql("select user_id from user_identities where issuer = :issuer and subject = :subject")
                .param("issuer", TestIssuer.ISSUER)
                .param("subject", subject)
                .query(UUID.class)
                .single();
    }
}
