package com.mentorhub.users.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.mail.MailDelivery;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.invitation.InvitationProblems;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestIssuerConfiguration;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.web.Problems;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/**
 * Invitation emails through the real chain with a configured mail server whose delivery is held and then released
 * (the mail server itself is covered by {@code InvitationEmailTests}): the web-layer side of invitation-emails AC-1 to
 * AC-7 and AC-16.
 */
@SpringBootTest(properties = {"spring.mail.host=localhost", "mentorhub.mail.from=MentorHub <no-reply@mentorhub.test>"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
class InvitationEmailsEndpointTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper jsonMapper;

    @MockitoSpyBean
    MailDelivery mail;

    TestUsers users;
    UUID admin;
    CountDownLatch release;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        jdbc.sql("delete from event_publication").update();
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "admin");
        release = new CountDownLatch(1);
        doAnswer(invocation -> {
                    release.await();
                    return null;
                })
                .when(mail)
                .send(any());
    }

    @AfterEach
    void awaitIdle() {
        release.countDown();
        await(() -> users.count("event_publication") == 0);
    }

    @Test
    void emailFlowFromQueuedToSent() {
        // AC-1
        assertThat(send(HttpMethod.GET, "/api/v1/system/client-config", "", "admin"))
                .bodyJson()
                .extractingPath("$.invitationEmails")
                .isEqualTo(true);

        // AC-2: 201 with the link and the queued email.
        MvcTestResult created = send(
                HttpMethod.POST,
                "/api/v1/invitations",
                "{\"email\": \"ivan@x.test\", \"roles\": [\"MENTOR\"], \"sendEmail\": true}",
                "admin");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        assertThat(created).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
        assertThat(created).bodyJson().hasPath("$.link");
        assertThat(created)
                .bodyJson()
                .extractingPath("$.invitation.lastEmail.status")
                .isEqualTo("QUEUED");
        assertThat(created)
                .bodyJson()
                .extractingPath("$.invitation.lastEmail.requestedBy.id")
                .isEqualTo(admin.toString());
        assertThat(created).bodyJson().doesNotHavePath("$.invitation.lastEmail.finishedAt");
        String id = text(created, "/invitation/id");
        String link = text(created, "/link");
        String path = "/api/v1/invitations/" + id;

        // AC-5: a second request while one is queued stores nothing.
        assertProblem(
                send(HttpMethod.POST, path + "/email", "", "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.EMAIL_QUEUED);
        assertThat(users.count("invitation_emails")).isEqualTo(1);

        // AC-3: a new link with the flag while queued returns the queued email and requests no second one.
        MvcTestResult reissued = send(HttpMethod.POST, path + "/link", "{\"sendEmail\": true}", "admin");
        assertThat(reissued).hasStatusOk();
        assertThat(reissued)
                .bodyJson()
                .extractingPath("$.invitation.lastEmail.status")
                .isEqualTo("QUEUED");
        assertThat(text(reissued, "/link")).isNotEqualTo(link);
        assertThat(users.count("invitation_emails")).isEqualTo(1);
        link = text(reissued, "/link");

        // AC-16: the list shows the queued email, then the sent one with its time.
        assertThat(send(HttpMethod.GET, "/api/v1/invitations", "", "admin"))
                .bodyJson()
                .extractingPath("$.items[0].lastEmail.status")
                .isEqualTo("QUEUED");
        release.countDown();
        await(() -> "SENT".equals(latestStatus()));
        MvcTestResult list = send(HttpMethod.GET, "/api/v1/invitations", "", "admin");
        assertThat(list)
                .bodyJson()
                .extractingPath("$.items[0].lastEmail.status")
                .isEqualTo("SENT");
        assertThat(list).bodyJson().hasPath("$.items[0].lastEmail.finishedAt");
        assertThat(list).bodyText().doesNotContain("failure");

        // AC-4: 202 with the invitation; the link stays the same and is not in the response.
        MvcTestResult resent = send(HttpMethod.POST, path + "/email", "", "admin");
        assertThat(resent).hasStatus(HttpStatus.ACCEPTED);
        assertThat(resent).bodyJson().extractingPath("$.id").isEqualTo(id);
        assertThat(resent).bodyJson().extractingPath("$.lastEmail.status").isIn("QUEUED", "SENT");
        assertThat(resent).bodyJson().doesNotHavePath("$.link");
        assertThat(resent).bodyText().doesNotContain(link.substring(link.lastIndexOf('/') + 1));
        assertThat(send(HttpMethod.GET, path + "/link", "", "admin"))
                .bodyJson()
                .extractingPath("$.link")
                .isEqualTo(link);
        assertThat(users.count("invitation_emails")).isEqualTo(2);
    }

    // AC-6: the invitation's errors come before email-queued.
    @Test
    void closedAndUnknownInvitationsAreRefusedBeforeTheQueue() {
        MvcTestResult created = send(
                HttpMethod.POST,
                "/api/v1/invitations",
                "{\"email\": \"ivan@x.test\", \"roles\": [], \"sendEmail\": true}",
                "admin");
        String id = text(created, "/invitation/id");
        assertThat(send(HttpMethod.POST, "/api/v1/invitations/" + id + "/revoke", "", "admin"))
                .hasStatusOk();

        assertProblem(
                send(HttpMethod.POST, "/api/v1/invitations/" + id + "/email", "", "admin"),
                HttpStatus.CONFLICT,
                InvitationProblems.INVITATION_CLOSED);
        assertProblem(
                send(HttpMethod.POST, "/api/v1/invitations/" + UUID.randomUUID() + "/email", "", "admin"),
                HttpStatus.NOT_FOUND,
                Problems.NOT_FOUND);
    }

    // AC-7: the permission check comes first, also when email is enabled.
    @Test
    void callerWithoutThePermissionIsForbidden() {
        MvcTestResult created =
                send(HttpMethod.POST, "/api/v1/invitations", "{\"email\": \"ivan@x.test\", \"roles\": []}", "admin");
        String id = text(created, "/invitation/id");
        UUID mentor = users.create("mentor@x.test", UserStatus.ACTIVE, Role.MENTOR);
        users.link(mentor, "mentor");

        assertProblem(
                send(HttpMethod.POST, "/api/v1/invitations/" + id + "/email", "", "mentor"),
                HttpStatus.FORBIDDEN,
                Problems.FORBIDDEN);
        assertProblem(
                send(
                        HttpMethod.POST,
                        "/api/v1/invitations",
                        "{\"email\": \"b@x.test\", \"roles\": [], \"sendEmail\": true}",
                        "mentor"),
                HttpStatus.FORBIDDEN,
                Problems.FORBIDDEN);
        assertThat(users.count("invitation_emails")).isZero();
    }

    private String text(MvcTestResult result, String pointer) {
        return jsonMapper
                .readTree(result.getResponse().getContentAsByteArray())
                .at(pointer)
                .asString();
    }

    private String latestStatus() {
        return jdbc.sql("select status from invitation_emails order by requested_at desc limit 1")
                .query(String.class)
                .optional()
                .orElse("");
    }

    private static void await(BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("condition not reached within 10 seconds");
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
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
