package com.mentorhub.users.internal.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.mail.MailDelivery;
import com.mentorhub.mail.MailDeliveryException;
import com.mentorhub.mail.MailMessage;
import com.mentorhub.support.Mailpit;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.InvitationAuditAction;
import com.mentorhub.users.internal.domain.InvitationEmailReason;
import com.mentorhub.users.internal.domain.InvitationEmailStatus;
import com.mentorhub.users.internal.domain.InvitationStatus;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.invitation.InvitationTestData.AuditRow;
import com.mentorhub.users.internal.persistence.InvitationEmailRepository;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.users.internal.service.HistoryActor;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.NotFoundException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Spec invitation-emails with a mail server (Mailpit): requesting emails (AC-2 to AC-6) and delivering them after the
 * commit (AC-8 to AC-15). Data is committed; each test waits until the event publication registry is empty, which also
 * shows that completed publications are deleted.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class InvitationEmailTests {

    static final Mailpit mailpit = new Mailpit();
    private static boolean mailpitStarted;

    private static final String OK = "OK";
    private static final String QUEUED = InvitationProblems.EMAIL_QUEUED.toString();

    @DynamicPropertySource
    static void mailServer(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> startedMailpit().host());
        registry.add("spring.mail.port", () -> startedMailpit().smtpPort());
        registry.add("mentorhub.mail.from", () -> "MentorHub <no-reply@mentorhub.test>");
    }

    private static synchronized Mailpit startedMailpit() {
        if (!mailpitStarted) {
            mailpit.start();
            mailpitStarted = true;
        }
        return mailpit;
    }

    @AfterAll
    static void stopMailpit() {
        mailpit.stop();
    }

    @Autowired
    InvitationAdministrationService service;

    @Autowired
    InvitationEmailResubmission resubmission;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoSpyBean
    MailDelivery mail;

    @MockitoSpyBean
    InvitationEmailRepository emails;

    TestUsers users;
    InvitationTestData data;
    TransactionTemplate transaction;
    UUID admin;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        jdbc.sql("delete from event_publication").update();
        startedMailpit().deleteAll();
        data = new InvitationTestData(jdbc, jsonMapper);
        transaction = new TransactionTemplate(transactionManager);
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(adminAuthentication(admin));
    }

    @AfterEach
    void awaitIdle() {
        SecurityContextHolder.clearContext();
        await("the event publication registry to be empty", () -> data.count("event_publication") == 0);
    }

    @Test
    void emailIsEnabled() {
        assertThat(mail.isEnabled()).isTrue();
    }

    @Nested
    class Requesting {

        // AC-2: stored, audited, queued in the response, with the link as before; then sent (AC-8).
        @Test
        void creatingWithAnEmailQueuesItInTheSameTransaction() {
            IssuedInvitation issued = service.create("ivan@x.test", Set.of(Role.MENTOR), true);

            UUID invitation = issued.invitation().id();
            assertThat(issued.link().toString()).matches(InvitationAdministrationServiceTests.LINK_PATTERN);
            InvitationEmailDetails lastEmail = issued.invitation().lastEmail();
            assertThat(lastEmail).isNotNull();
            assertThat(lastEmail.status()).isEqualTo(InvitationEmailStatus.QUEUED);
            assertThat(lastEmail.requestedBy()).isEqualTo(new HistoryActor(admin, "Anna Petrova"));
            assertThat(lastEmail.finishedAt()).isNull();
            assertThat(data.auditEntries(invitation))
                    .extracting(AuditRow::action)
                    .containsExactlyInAnyOrder(
                            InvitationAuditAction.INVITATION_CREATED.name(),
                            InvitationAuditAction.INVITATION_EMAIL_REQUESTED.name());
            // One transaction writes both entries with the same time, so their order is not defined.
            assertThat(data.auditEntries(invitation))
                    .contains(new AuditRow(
                            InvitationAuditAction.INVITATION_EMAIL_REQUESTED.name(),
                            admin,
                            "INVITATION",
                            invitation,
                            Map.of("reason", "CREATED")));
            assertThat(requests(invitation)).containsExactly(new Request(InvitationEmailReason.CREATED, admin));

            awaitLatestStatus(invitation, InvitationEmailStatus.SENT);
            assertThat(mailpit.awaitMessages(1))
                    .singleElement()
                    .satisfies(message ->
                            assertThat(message.text()).contains(issued.link().toString()));
        }

        // AC-2
        @Test
        void creatingWithoutAnEmailRequestsNone() {
            IssuedInvitation issued = service.create("ivan@x.test", Set.of(), false);

            assertThat(issued.invitation().lastEmail()).isNull();
            assertThat(data.auditEntries(issued.invitation().id())).hasSize(1);
            assertThat(data.count("invitation_emails")).isZero();
            verify(mail, after(300).never()).send(any());
        }

        // AC-2: a failed create requests no email.
        @Test
        void aFailedCreateRequestsNoEmail() {
            create("ivan@x.test");

            assertConflict(() -> service.create("ivan@x.test", Set.of(), true), InvitationProblems.INVITATION_EXISTS);

            assertThat(data.count("invitation_emails")).isZero();
        }

        // AC-3: the email carries the new link.
        @Test
        void aNewLinkWithAnEmailSendsTheNewLink() {
            UUID invitation = create("ivan@x.test");

            IssuedInvitation reissued = service.reissueLink(invitation, true);

            assertThat(reissued.invitation().lastEmail().status()).isEqualTo(InvitationEmailStatus.QUEUED);
            assertThat(data.auditEntries(invitation))
                    .extracting(AuditRow::action, AuditRow::details)
                    .contains(
                            org.assertj.core.groups.Tuple.tuple(
                                    InvitationAuditAction.INVITATION_LINK_REISSUED.name(),
                                    Map.of(
                                            "expiresAt",
                                            reissued.invitation().expiresAt().toString())),
                            org.assertj.core.groups.Tuple.tuple(
                                    InvitationAuditAction.INVITATION_EMAIL_REQUESTED.name(),
                                    Map.of("reason", "LINK_REISSUED")));
            assertThat(requests(invitation)).containsExactly(new Request(InvitationEmailReason.LINK_REISSUED, admin));
            awaitLatestStatus(invitation, InvitationEmailStatus.SENT);
            assertThat(mailpit.awaitMessages(1))
                    .singleElement()
                    .satisfies(message ->
                            assertThat(message.text()).contains(reissued.link().toString()));
        }

        // AC-3: the link is issued, no second request or audit entry, and the queued request is returned.
        @Test
        void aNewLinkWithAnEmailWhileOneIsQueuedKeepsTheQueuedOne() {
            UUID invitation = create("ivan@x.test");
            emails.insertQueued(invitation, InvitationEmailReason.RESENT, admin);
            String token = data.storedToken(invitation);

            IssuedInvitation reissued = service.reissueLink(invitation, true);

            assertThat(data.storedToken(invitation)).isNotEqualTo(token);
            assertThat(reissued.invitation().lastEmail().status()).isEqualTo(InvitationEmailStatus.QUEUED);
            assertThat(requests(invitation)).containsExactly(new Request(InvitationEmailReason.RESENT, admin));
            assertThat(data.auditEntries(invitation))
                    .extracting(AuditRow::action)
                    .containsExactly(
                            InvitationAuditAction.INVITATION_CREATED.name(),
                            InvitationAuditAction.INVITATION_LINK_REISSUED.name());
        }

        // AC-4: the current link again, token and expiry unchanged; any administrator, not only the inviter.
        @Test
        void sendingAnEmailSendsTheCurrentLink() {
            IssuedInvitation created = service.create("ivan@x.test", Set.of(), false);
            UUID invitation = created.invitation().id();
            UUID otherAdmin = users.create("other@x.test", UserStatus.ACTIVE, Role.ADMIN);
            SecurityContextHolder.getContext().setAuthentication(adminAuthentication(otherAdmin));

            InvitationDetails sent = service.sendEmail(invitation);

            assertThat(sent.lastEmail().status()).isEqualTo(InvitationEmailStatus.QUEUED);
            assertThat(sent.lastEmail().requestedBy().id()).isEqualTo(otherAdmin);
            assertThat(sent.expiresAt()).isEqualTo(created.invitation().expiresAt());
            assertThat(data.storedToken(invitation)).isEqualTo(token(created.link()));
            assertThat(data.auditEntries(invitation).getLast())
                    .isEqualTo(new AuditRow(
                            InvitationAuditAction.INVITATION_EMAIL_REQUESTED.name(),
                            otherAdmin,
                            "INVITATION",
                            invitation,
                            Map.of("reason", "RESENT")));
            assertThat(requests(invitation)).containsExactly(new Request(InvitationEmailReason.RESENT, otherAdmin));
            awaitLatestStatus(invitation, InvitationEmailStatus.SENT);
            assertThat(mailpit.awaitMessages(1))
                    .singleElement()
                    .satisfies(message ->
                            assertThat(message.text()).contains(created.link().toString()));
        }

        // AC-4, AC-6: the invitation's errors come before email-queued, and nothing is stored.
        @Test
        void sendingRefusesUnknownClosedAndExpiredInvitations() {
            assertThatThrownBy(() -> service.sendEmail(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
            UUID accepted = queuedInvitation("accepted@x.test");
            data.markAccepted(accepted, users.create("accepted@x.test", UserStatus.ACTIVE));
            assertConflict(() -> service.sendEmail(accepted), InvitationProblems.INVITATION_CLOSED);
            UUID revoked = queuedInvitation("revoked@x.test");
            data.markRevoked(revoked, admin);
            assertConflict(() -> service.sendEmail(revoked), InvitationProblems.INVITATION_CLOSED);
            UUID expired = queuedInvitation("expired@x.test");
            data.expire(expired);
            assertConflict(() -> service.sendEmail(expired), InvitationProblems.INVITATION_EXPIRED);

            assertThat(data.count("invitation_emails")).isEqualTo(3);
            assertThat(auditCount(InvitationAuditAction.INVITATION_EMAIL_REQUESTED))
                    .isZero();
        }

        // AC-5: nothing is stored.
        @Test
        void sendingWhileAnEmailIsQueuedIsRefused() {
            UUID invitation = queuedInvitation("ivan@x.test");

            assertConflict(() -> service.sendEmail(invitation), InvitationProblems.EMAIL_QUEUED);

            assertThat(requests(invitation)).hasSize(1);
            assertThat(auditCount(InvitationAuditAction.INVITATION_EMAIL_REQUESTED))
                    .isZero();
        }

        // AC-5: two parallel calls end in one request and one email-queued, never a 500. Repeated because a race shows
        // only in some runs; the send is held so the first request stays queued.
        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 4, 5})
        void parallelSendsQueueOneEmail(int run) throws Exception {
            UUID invitation = create("ivan" + run + "@x.test");
            CountDownLatch release = holdSends();
            try {
                Authentication authentication = adminAuthentication(admin);
                List<String> outcomes = inParallel(
                        as(authentication, () -> service.sendEmail(invitation)),
                        as(authentication, () -> service.sendEmail(invitation)));

                assertThat(outcomes).containsExactlyInAnyOrder(OK, QUEUED);
                assertThat(requests(invitation)).hasSize(1);
                assertThat(auditCount(InvitationAuditAction.INVITATION_EMAIL_REQUESTED))
                        .isEqualTo(1);
            } finally {
                release.countDown();
            }
            awaitLatestStatus(invitation, InvitationEmailStatus.SENT);
        }
    }

    @Nested
    class Delivering {

        // AC-8, AC-14: one plain text email from the sender to the invitation's address; the request becomes SENT.
        @Test
        void sendsTheInvitationEmailAndMarksItSent() {
            IssuedInvitation issued = service.create("Ivan.Sidorov@x.test", Set.of(), true);
            UUID invitation = issued.invitation().id();

            awaitLatestStatus(invitation, InvitationEmailStatus.SENT);

            List<Mailpit.Message> messages = mailpit.awaitMessages(1);
            assertThat(messages).hasSize(1);
            Mailpit.Message message = messages.getFirst();
            assertThat(message.from()).isEqualTo("no-reply@mentorhub.test");
            assertThat(message.fromName()).isEqualTo("MentorHub");
            assertThat(message.to()).containsExactly("Ivan.Sidorov@x.test");
            assertThat(message.subject()).isEqualTo("You're invited to MentorHub");
            assertThat(message.text().replace("\r\n", "\n")).isEqualTo("""
                            You're invited to MentorHub, the training center's mentoring platform.

                            Open this link and sign in or create an account with Ivan.Sidorov@x.test:

                            %s

                            The link expires on %s.

                            If you didn't expect this invitation, ignore this email.
                            """.formatted(
                            issued.link(),
                            InvitationEmailTextTests.utc(issued.invitation().expiresAt())));
            InvitationEmailDetails lastEmail = service.list(new InvitationQuery(null, null, 0, 25))
                    .items()
                    .getFirst()
                    .lastEmail();
            assertThat(lastEmail.finishedAt()).isAfterOrEqualTo(lastEmail.requestedAt());
            verify(mail, times(1)).send(any());
        }

        // AC-9
        @Test
        void aRolledBackRequestSendsNothing() {
            transaction.executeWithoutResult(status -> {
                service.create("ivan@x.test", Set.of(), true);
                status.setRollbackOnly();
            });

            verify(mail, after(500).never()).send(any());
            assertThat(data.count("invitations")).isZero();
            assertThat(data.count("event_publication")).isZero();
        }

        // AC-10: an invitation that is no longer pending is skipped.
        @ParameterizedTest
        @ValueSource(strings = {"ACCEPTED", "REVOKED", "EXPIRED"})
        void skipsAnInvitationThatIsNoLongerPending(InvitationStatus status) {
            UUID invitation = create("ivan@x.test");
            UUID request = emails.insertQueued(invitation, InvitationEmailReason.RESENT, admin);
            switch (status) {
                case ACCEPTED -> data.markAccepted(invitation, users.create("ivan@x.test", UserStatus.ACTIVE));
                case REVOKED -> data.markRevoked(invitation, admin);
                default -> data.expire(invitation);
            }

            publish(request);

            awaitLatestStatus(invitation, InvitationEmailStatus.SKIPPED);
            verify(mail, never()).send(any());
            assertThat(mailpit.awaitMessages(0)).isEmpty();
        }

        // AC-10: the link that is current when the email is built.
        @Test
        void sendsTheLinkThatIsCurrentWhenTheEmailIsBuilt() {
            UUID invitation = create("ivan@x.test");
            UUID request = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);
            URI newLink = service.reissueLink(invitation, false).link();

            publish(request);

            awaitLatestStatus(invitation, InvitationEmailStatus.SENT);
            assertThat(mailpit.awaitMessages(1))
                    .singleElement()
                    .satisfies(message -> assertThat(message.text()).contains(newLink.toString()));
        }

        // AC-11, AC-15: FAILED with a reason without the token, a warning without the token or the body, no retry,
        // and the invitation is unchanged.
        @Test
        void aRefusedMessageIsMarkedFailed(CapturedOutput output) {
            doAnswer(invocation -> {
                        MailMessage message = invocation.getArgument(0);
                        throw new MailDeliveryException(
                                "550 5.7.1 rejected, suspicious URL " + link(message),
                                new IllegalStateException("refused"));
                    })
                    .when(mail)
                    .send(any());

            IssuedInvitation issued = service.create("ivan@x.test", Set.of(), true);
            UUID invitation = issued.invitation().id();
            String token = token(issued.link());

            awaitLatestStatus(invitation, InvitationEmailStatus.FAILED);
            String failure = jdbc.sql("select failure from invitation_emails where invitation_id = :id")
                    .param("id", invitation)
                    .query(String.class)
                    .single();
            assertThat(failure)
                    .startsWith("550 5.7.1 rejected")
                    .contains("[redacted]")
                    .doesNotContain(token);
            assertThat(output.getAll())
                    .contains("Invitation email")
                    .contains("failed")
                    .doesNotContain(token)
                    .doesNotContain("Open this link");
            verify(mail, after(500).times(1)).send(any());
            assertThat(data.storedToken(invitation)).isEqualTo(token);
            assertThat(service.list(new InvitationQuery(null, null, 0, 25))
                            .items()
                            .getFirst()
                            .status())
                    .isEqualTo(InvitationStatus.PENDING);
        }

        // AC-12: a publication replayed after the outcome was recorded sends nothing.
        @ParameterizedTest
        @ValueSource(strings = {"SENT", "SKIPPED"})
        void aRequestThatIsNoLongerQueuedIsNeverSentAgain(InvitationEmailStatus outcome) {
            UUID invitation = create("ivan@x.test");
            UUID request = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);
            if (outcome == InvitationEmailStatus.SENT) {
                emails.markSent(request);
            } else {
                emails.markSkipped(request);
            }

            publish(request);

            await("the publication to complete", () -> data.count("event_publication") == 0);
            verify(mail, never()).send(any());
            assertThat(latestStatus(invitation)).isEqualTo(outcome);
        }

        // AC-13: an unexpected failure leaves the request queued until stale publications are resubmitted; a recent
        // publication is not resubmitted, so a send in progress does not start twice.
        @Test
        void anUnexpectedFailureIsProcessedAgainWhenStale() {
            doThrow(new IllegalStateException("database unavailable"))
                    .doCallRealMethod()
                    .when(emails)
                    .lock(any());

            UUID invitation =
                    service.create("ivan@x.test", Set.of(), true).invitation().id();
            await("the failed publication", () -> incompleteStatuses().contains("FAILED"));
            assertThat(latestStatus(invitation)).isEqualTo(InvitationEmailStatus.QUEUED);

            resubmission.resubmitStalePublications();
            verify(mail, after(500).never()).send(any());
            assertThat(latestStatus(invitation)).isEqualTo(InvitationEmailStatus.QUEUED);

            jdbc.sql("update event_publication set publication_date = publication_date - interval '10 minutes'")
                    .update();
            resubmission.resubmitStalePublications();

            awaitLatestStatus(invitation, InvitationEmailStatus.SENT);
            assertThat(mailpit.awaitMessages(1)).hasSize(1);
        }

        // AC-15: the token is in no log line, audit detail, request row or registry row.
        @Test
        void theTokenIsStoredNowhereElse(CapturedOutput output) {
            CountDownLatch release = holdSends();
            IssuedInvitation issued;
            String token;
            try {
                issued = service.create("ivan@x.test", Set.of(), true);
                token = token(issued.link());
                await("the stored publication", () -> data.count("event_publication") == 1);
                assertThat(jdbc.sql("select serialized_event from event_publication")
                                .query(String.class)
                                .single())
                        .contains(
                                requestIds(issued.invitation().id()).getFirst().toString())
                        .doesNotContain(token);
            } finally {
                release.countDown();
            }
            awaitLatestStatus(issued.invitation().id(), InvitationEmailStatus.SENT);
            assertThat(data.allAuditDetails()).doesNotContain(token);
            assertThat(String.join(
                            "\n",
                            jdbc.sql("select row_to_json(e)::text from invitation_emails e")
                                    .query(String.class)
                                    .list()))
                    .doesNotContain(token);
            assertThat(output.getAll()).doesNotContain(token);
        }
    }

    private record Request(InvitationEmailReason reason, UUID requestedBy) {}

    private List<Request> requests(UUID invitation) {
        return jdbc.sql("select reason, requested_by from invitation_emails where invitation_id = :id"
                        + " order by requested_at, id")
                .param("id", invitation)
                .query((rs, rowNum) -> new Request(
                        InvitationEmailReason.valueOf(rs.getString("reason")),
                        rs.getObject("requested_by", UUID.class)))
                .list();
    }

    private List<UUID> requestIds(UUID invitation) {
        return jdbc.sql("select id from invitation_emails where invitation_id = :id")
                .param("id", invitation)
                .query(UUID.class)
                .list();
    }

    private InvitationEmailStatus latestStatus(UUID invitation) {
        return InvitationEmailStatus.valueOf(
                jdbc.sql("""
                        select status from invitation_emails where invitation_id = :id
                        order by requested_at desc, id desc limit 1
                        """).param("id", invitation).query(String.class).single());
    }

    private List<String> incompleteStatuses() {
        return jdbc.sql("select status from event_publication where completion_date is null")
                .query(String.class)
                .list();
    }

    private void awaitLatestStatus(UUID invitation, InvitationEmailStatus status) {
        await("email status " + status, () -> latestStatus(invitation) == status);
    }

    private int auditCount(InvitationAuditAction action) {
        return jdbc.sql("select count(*) from audit_log where action = :action")
                .param("action", action.name())
                .query(Integer.class)
                .single();
    }

    private UUID create(String email) {
        return service.create(email, Set.of(), false).invitation().id();
    }

    /** A pending invitation whose latest email is queued; no event is published, so it stays queued. */
    private UUID queuedInvitation(String email) {
        UUID invitation = create(email);
        emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);
        return invitation;
    }

    private void publish(UUID request) {
        transaction.executeWithoutResult(status -> publisher.publishEvent(new InvitationEmailRequested(request)));
    }

    /** Holds every send until the returned latch is released. */
    private CountDownLatch holdSends() {
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(invocation -> {
                    release.await(10, TimeUnit.SECONDS);
                    return invocation.callRealMethod();
                })
                .when(mail)
                .send(any());
        return release;
    }

    private static void await(String description, Supplier<Boolean> condition) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (!condition.get()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("Timed out waiting for " + description);
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }

    private static Authentication adminAuthentication(UUID userId) {
        return TestAuthentications.activeUserAuthentication(userId, Permission.USERS_INVITE);
    }

    /** The link line of an invitation email; a mail server's reply may echo it, never the whole body. */
    private static String link(MailMessage message) {
        return message.text()
                .lines()
                .filter(line -> line.startsWith("https://"))
                .findFirst()
                .orElseThrow();
    }

    private static String token(URI link) {
        String path = link.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, URI type) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        exception -> assertThat(exception.type()).isEqualTo(type));
    }

    private static Callable<String> as(Authentication authentication, Runnable operation) {
        return () -> {
            SecurityContextHolder.getContext().setAuthentication(authentication);
            try {
                operation.run();
                return OK;
            } catch (ConflictException exception) {
                return exception.type().toString();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    /** Any other exception, such as a duplicate key, fails the test. */
    @SafeVarargs
    private static List<String> inParallel(Callable<String>... operations) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(operations.length)) {
            for (Callable<String> operation : operations) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return operation.call();
                }));
            }
            start.countDown();
            List<String> outcomes = new ArrayList<>();
            for (Future<String> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        }
    }
}
