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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
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

/** ADR 0005 through the real chain; the bootstrap address is {@link TestIssuer#BOOTSTRAP_EMAIL}. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
@ExtendWith(OutputCaptureExtension.class)
class AdministratorBootstrapTests {

    private static final String APPLIED = "Administrator bootstrap applied to user ";
    private static final String REFUSED = "Administrator bootstrap refused";

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

    // AC-11
    @Test
    void createsAndLinksTheAdministratorOnFirstSignIn(CapturedOutput output) {
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
        assertThat(output.getOut()).containsOnlyOnce(APPLIED);
        assertThat(output.getOut()).contains(APPLIED + userId);
    }

    // AC-12
    @Test
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

    // AC-12
    @Test
    void grantsAdminToTheLinkedUserWhoLostIt(CapturedOutput output) {
        UUID userId = users.create(TestIssuer.BOOTSTRAP_EMAIL, UserStatus.ACTIVE);
        users.link(userId, "former-admin");
        String token = issuer.token("former-admin")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();

        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();

        assertThat(users.roles(userId)).containsExactly("ADMIN");
        assertThat(output.getOut()).contains(APPLIED + userId);
    }

    // AC-13
    @Test
    void neverMovesAnIdentityLinkedToAnotherUser(CapturedOutput output) {
        UUID otherUser = users.create("someone@example.com", UserStatus.ACTIVE);
        users.link(otherUser, "someone");
        String token =
                issuer.token("someone").email(TestIssuer.BOOTSTRAP_EMAIL, true).bearer();

        assertProblem(get("/api/v1/test/users-read", token), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);

        assertThat(linkedUser("someone")).isEqualTo(otherUser);
        assertThat(users.roles(otherUser)).isEmpty();
        assertThat(users.count("users")).isEqualTo(1);
        assertThat(output.getOut()).contains(REFUSED).contains(otherUser.toString());
        assertThat(output.getOut()).doesNotContain(APPLIED);
    }

    // AC-14
    @Test
    void grantsNothingWhileAnActiveAdministratorExists(CapturedOutput output) {
        UUID admin = users.create("admin@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "admin");
        UUID disabledBootstrapUser = users.create(TestIssuer.BOOTSTRAP_EMAIL, UserStatus.DISABLED);
        users.link(disabledBootstrapUser, "linked-bootstrap");

        String unlinked = issuer.token("unlinked-bootstrap")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();
        String linked = issuer.token("linked-bootstrap")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();

        assertProblem(get("/api/v1/test/current-user", unlinked), HttpStatus.FORBIDDEN, Problems.NOT_INVITED);
        assertProblem(get("/api/v1/test/current-user", linked), HttpStatus.FORBIDDEN, Problems.ACCESS_ENDED);

        assertThat(users.count("users")).isEqualTo(2);
        assertThat(users.count("user_identities")).isEqualTo(2);
        assertThat(users.status(disabledBootstrapUser)).isEqualTo("DISABLED");
        assertThat(users.roles(disabledBootstrapUser)).isEmpty();
        assertThat(output.getOut()).doesNotContain(APPLIED);
    }

    // AC-14: an administrator who is disabled does not count.
    @Test
    void disabledAdministratorDoesNotBlockTheRule() {
        users.create("admin@example.com", UserStatus.DISABLED, Role.ADMIN);
        String token = issuer.token("new-admin")
                .email(TestIssuer.BOOTSTRAP_EMAIL, true)
                .bearer();

        assertThat(get("/api/v1/test/users-read", token)).hasStatusOk();
    }

    // AC-15
    @Test
    void unverifiedOrMissingEmailGrantsNothing(CapturedOutput output) {
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
        assertThat(output.getOut()).doesNotContain(APPLIED);
    }

    // AC-16; repeated because a race shows only in some runs.
    @RepeatedTest(5)
    void parallelFirstRequestsCreateExactlyOneAdministrator(CapturedOutput output) throws Exception {
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
        assertThat(output.getOut()).containsOnlyOnce(APPLIED);
    }

    private MvcTestResult get(String path, String authorization) {
        return mvc.get()
                .uri(path)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .exchange();
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
