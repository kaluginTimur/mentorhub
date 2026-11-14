package com.mentorhub.users.internal.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mentorhub.mail.MailDelivery;
import com.mentorhub.mail.MailDeliveryException;
import com.mentorhub.mail.MailMessage;
import com.mentorhub.support.IntegrationTest;
import com.mentorhub.support.Mailpit;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.InvitationAuditAction;
import com.mentorhub.users.internal.domain.InvitationEmailFailure;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Invitation emails with a mail server (Mailpit): requesting emails, listing them, delivering them after the commit and
 * resubmitting stale deliveries. Data is committed; each test waits until the event publication registry is empty, which
 * also shows that completed publications are deleted.
 */
@IntegrationTest
@Import(InvitationEmailTests.FailingListener.class)
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

    @Autowired
    FailingListener otherListener;

    @Autowired
    @Qualifier("applicationTaskExecutor")
    ThreadPoolTaskExecutor listeners;

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

    @Nested
    class Requesting {

        @Test
        @DisplayName("IEM-3.1 An email is queued with a new invitation or a new link")
        void aNewInvitationOrANewLinkQueuesAnEmail() {
            IssuedInvitation created = service.create("ivan@x.test", Set.of(Role.MENTOR), true);
            UUID other = create("petr@x.test");
            IssuedInvitation reissued = service.reissueLink(other, true);

            for (IssuedInvitation issued : List.of(created, reissued)) {
                assertThat(issued.link().toString()).matches(InvitationAdministrationServiceTests.LINK_PATTERN);
                InvitationEmailDetails lastEmail = issued.invitation().lastEmail();
                assertThat(lastEmail).isNotNull();
                assertThat(lastEmail.status()).isEqualTo(InvitationEmailStatus.QUEUED);
                assertThat(lastEmail.requestedAt()).isNotNull();
                assertThat(lastEmail.requestedBy()).isEqualTo(new HistoryActor(admin, "Anna Petrova"));
                assertThat(lastEmail.finishedAt()).isNull();
            }
            UUID invitation = created.invitation().id();
            assertThat(requests(invitation)).containsExactly(new Request(InvitationEmailReason.CREATED, admin));
            assertThat(requests(other)).containsExactly(new Request(InvitationEmailReason.LINK_REISSUED, admin));
            // One transaction writes both entries with the same time, so their order is not defined.
            assertThat(data.auditEntries(invitation))
                    .extracting(AuditRow::action)
                    .containsExactlyInAnyOrder(
                            InvitationAuditAction.INVITATION_CREATED.name(),
                            InvitationAuditAction.INVITATION_EMAIL_REQUESTED.name());
            assertThat(data.auditEntries(invitation))
                    .contains(new AuditRow(
                            InvitationAuditAction.INVITATION_EMAIL_REQUESTED.name(),
                            admin,
                            "INVITATION",
                            invitation,
                            Map.of("reason", "CREATED")));
            assertThat(data.auditEntries(other))
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
        }

        @Test
        @DisplayName("IEM-3.2 Without the flag no email is requested")
        void withoutTheFlagNoEmailIsRequested() {
            IssuedInvitation created = service.create("ivan@x.test", Set.of(), false);
            IssuedInvitation reissued = service.reissueLink(created.invitation().id(), false);

            assertThat(created.invitation().lastEmail()).isNull();
            assertThat(reissued.invitation().lastEmail()).isNull();
            assertThat(auditCount(InvitationAuditAction.INVITATION_EMAIL_REQUESTED))
                    .isZero();
            // Nothing is stored for a listener to send, and nothing was published.
            assertThat(data.count("invitation_emails")).isZero();
            assertThat(data.count("event_publication")).isZero();
            verify(mail, never()).send(any());
        }

        @Test
        @DisplayName("AUD-5.1 The invitation token is not in any entry")
        void noEntryHoldsTheTokenOrTheLink() {
            IssuedInvitation issued = service.create("ivan@x.test", Set.of(Role.MENTOR), true);
            UUID invitation = issued.invitation().id();
            awaitLatestStatus(invitation, InvitationEmailStatus.SENT);
            IssuedInvitation reissued = service.reissueLink(invitation, true);
            awaitLatestStatus(invitation, InvitationEmailStatus.SENT);

            assertThat(data.auditEntries(invitation)).hasSizeGreaterThanOrEqualTo(4);
            String details = data.allAuditDetails();
            assertThat(details).doesNotContain(token(issued.link()));
            assertThat(details).doesNotContain(token(reissued.link()));
            assertThat(details).doesNotContain(issued.link().toString());
            assertThat(details).doesNotContain(reissued.link().toString());
        }

        @Test
        void aFailedCreateRequestsNoEmail() {
            create("ivan@x.test");

            assertConflict(() -> service.create("ivan@x.test", Set.of(), true), InvitationProblems.INVITATION_EXISTS);

            assertThat(data.count("invitation_emails")).isZero();
        }

        @Test
        @DisplayName("IEM-3.3 A new link while an email is queued requests no second one")
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

        @Test
        @DisplayName("IEM-4.1 A pending invitation is sent again")
        void aPendingInvitationIsSentAgain() {
            IssuedInvitation created = service.create("ivan@x.test", Set.of(), false);
            UUID invitation = created.invitation().id();

            InvitationDetails sent = service.sendEmail(invitation);

            assertThat(sent.id()).isEqualTo(invitation);
            assertThat(sent.lastEmail().status()).isEqualTo(InvitationEmailStatus.QUEUED);
            assertThat(sent.lastEmail().requestedBy()).isEqualTo(new HistoryActor(admin, "Anna Petrova"));
            assertThat(sent.expiresAt()).isEqualTo(created.invitation().expiresAt());
            assertThat(service.link(invitation).link()).isEqualTo(created.link());
            assertThat(data.auditEntries(invitation).getLast())
                    .isEqualTo(new AuditRow(
                            InvitationAuditAction.INVITATION_EMAIL_REQUESTED.name(),
                            admin,
                            "INVITATION",
                            invitation,
                            Map.of("reason", "RESENT")));
            assertThat(requests(invitation)).containsExactly(new Request(InvitationEmailReason.RESENT, admin));
        }

        @Test
        @DisplayName("IEM-2.2 Any holder sends the email of any invitation")
        void anyHolderSendsTheEmailOfAnyInvitation() {
            UUID invitation = create("ivan@x.test");
            UUID otherAdmin = users.create("other@x.test", UserStatus.ACTIVE, Role.ADMIN);
            SecurityContextHolder.getContext().setAuthentication(adminAuthentication(otherAdmin));

            InvitationDetails sent = service.sendEmail(invitation);

            assertThat(sent.lastEmail().status()).isEqualTo(InvitationEmailStatus.QUEUED);
            assertThat(sent.lastEmail().requestedBy().id()).isEqualTo(otherAdmin);
        }

        @Test
        @DisplayName("IEM-4.2 An invitation that is not pending is refused")
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

        @Test
        @DisplayName("IEM-4.3 A queued email is not queued twice")
        void sendingWhileAnEmailIsQueuedIsRefused() {
            UUID invitation = queuedInvitation("ivan@x.test");

            assertConflict(() -> service.sendEmail(invitation), InvitationProblems.EMAIL_QUEUED);

            assertThat(requests(invitation)).hasSize(1);
            assertThat(auditCount(InvitationAuditAction.INVITATION_EMAIL_REQUESTED))
                    .isZero();
        }

        // Repeated because a race shows only in some runs; the send is held so the first request stays queued.
        @ParameterizedTest
        @DisplayName("IEM-4.4 Two requests at the same moment queue one email")
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
    class Listing {

        @Test
        @DisplayName("IEM-9.1 The list shows only the latest email")
        void theListShowsOnlyTheLatestEmail() {
            UUID twice = create("twice@x.test");
            UUID first = emails.insertQueued(twice, InvitationEmailReason.CREATED, admin);
            emails.markFailed(first, InvitationEmailFailure.of("550 refused"));
            jdbc.sql("update invitation_emails set requested_at = now() - interval '1 hour' where id = :id")
                    .param("id", first)
                    .update();
            emails.markSent(emails.insertQueued(twice, InvitationEmailReason.RESENT, admin));
            emails.insertQueued(create("queued@x.test"), InvitationEmailReason.CREATED, admin);
            emails.markFailed(
                    emails.insertQueued(create("failed@x.test"), InvitationEmailReason.CREATED, admin),
                    InvitationEmailFailure.of("550 refused"));
            create("none@x.test");

            Map<String, InvitationDetails> items = service.list(new InvitationQuery(null, null, 0, 25)).items().stream()
                    .collect(Collectors.toMap(InvitationDetails::email, Function.identity()));

            assertThat(items).hasSize(4);
            InvitationEmailDetails latest = items.get("twice@x.test").lastEmail();
            assertThat(latest.status()).isEqualTo(InvitationEmailStatus.SENT);
            assertThat(latest.requestedAt()).isNotNull();
            assertThat(latest.requestedBy()).isEqualTo(new HistoryActor(admin, "Anna Petrova"));
            assertThat(latest.finishedAt()).isNotNull();
            InvitationEmailDetails queued = items.get("queued@x.test").lastEmail();
            assertThat(queued.status()).isEqualTo(InvitationEmailStatus.QUEUED);
            assertThat(queued.finishedAt()).isNull();
            assertThat(items.get("failed@x.test").lastEmail().status()).isEqualTo(InvitationEmailStatus.FAILED);
            assertThat(items.get("none@x.test").lastEmail()).isNull();
        }
    }

    @Nested
    class Delivering {

        @Test
        @DisplayName("IEM-7.1 The sender, the recipient and the body")
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

        @Test
        @DisplayName("IEM-6.10 A rolled-back request sends nothing")
        void aRolledBackRequestSendsNothing() {
            transaction.executeWithoutResult(status -> {
                service.create("ivan@x.test", Set.of(), true);
                status.setRollbackOnly();
            });

            // Nothing is stored for a listener to send, and nothing was published.
            assertThat(data.count("invitations")).isZero();
            assertThat(data.count("invitation_emails")).isZero();
            assertThat(data.count("event_publication")).isZero();
            verify(mail, never()).send(any());
        }

        @ParameterizedTest
        @DisplayName("IEM-6.3 An invitation that is no longer pending is skipped")
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
            assertThat(service.list(new InvitationQuery(null, null, 0, 25))
                            .items()
                            .getFirst()
                            .lastEmail()
                            .finishedAt())
                    .isNotNull();
        }

        @Test
        @DisplayName("IEM-6.1 A queued email is sent with the current link and recorded")
        void sendsTheLinkThatIsCurrentWhenTheEmailIsBuilt() {
            UUID invitation = create("ivan@x.test");
            UUID request = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);
            URI newLink = service.reissueLink(invitation, false).link();

            publish(request);

            awaitLatestStatus(invitation, InvitationEmailStatus.SENT);
            assertThat(mailpit.awaitMessages(1)).singleElement().satisfies(message -> {
                assertThat(message.to()).containsExactly("ivan@x.test");
                assertThat(message.text()).contains(newLink.toString());
            });
            InvitationDetails details =
                    service.list(new InvitationQuery(null, null, 0, 25)).items().getFirst();
            assertThat(details.lastEmail().finishedAt()).isNotNull();
            assertThat(details.status()).isEqualTo(InvitationStatus.PENDING);
            assertThat(data.storedToken(invitation)).isEqualTo(token(newLink));
        }

        @Test
        @DisplayName("IEM-6.4 A refused email is recorded as failed")
        void aRefusedMessageIsMarkedFailed() {
            refuseEveryMessage();
            IssuedInvitation issued = service.create("ivan@x.test", Set.of(), true);
            UUID invitation = issued.invitation().id();

            awaitLatestStatus(invitation, InvitationEmailStatus.FAILED);

            InvitationDetails details =
                    service.list(new InvitationQuery(null, null, 0, 25)).items().getFirst();
            assertThat(details.lastEmail().finishedAt()).isNotNull();
            assertThat(details.status()).isEqualTo(InvitationStatus.PENDING);
            assertThat(details.expiresAt()).isEqualTo(issued.invitation().expiresAt());
            assertThat(service.link(invitation).link()).isEqualTo(issued.link());
        }

        @Test
        void theStoredFailureReasonHoldsNoToken() {
            refuseEveryMessage();
            IssuedInvitation issued = service.create("ivan@x.test", Set.of(), true);
            UUID invitation = issued.invitation().id();

            awaitLatestStatus(invitation, InvitationEmailStatus.FAILED);

            assertThat(jdbc.sql("select failure from invitation_emails where invitation_id = :id")
                            .param("id", invitation)
                            .query(String.class)
                            .single())
                    .contains("[redacted]")
                    .doesNotContain(token(issued.link()));
        }

        @ParameterizedTest
        @DisplayName("IEM-6.9 An email that is already done is never sent again")
        @ValueSource(strings = {"SENT", "FAILED", "SKIPPED"})
        void aRequestThatIsNoLongerQueuedIsNeverSentAgain(InvitationEmailStatus outcome) {
            UUID invitation = create("ivan@x.test");
            UUID request = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);
            switch (outcome) {
                case SENT -> emails.markSent(request);
                case FAILED -> emails.markFailed(request, InvitationEmailFailure.of("refused"));
                default -> emails.markSkipped(request);
            }

            publish(request);

            await("the publication to complete", () -> data.count("event_publication") == 0);
            verify(mail, never()).send(any());
            assertThat(latestStatus(invitation)).isEqualTo(outcome);
        }

        @Test
        @DisplayName("IEM-6.6 An unexpected failure is processed again")
        void anUnexpectedFailureIsProcessedAgainWhenStale() {
            doThrow(new IllegalStateException("database unavailable"))
                    .doCallRealMethod()
                    .when(emails)
                    .lock(any());

            UUID invitation =
                    service.create("ivan@x.test", Set.of(), true).invitation().id();
            await("the failed publication", () -> incompleteStatuses().contains("FAILED"));
            assertThat(latestStatus(invitation)).isEqualTo(InvitationEmailStatus.QUEUED);

            // Not stale yet: the resubmission marks a publication before it delivers it again, and leaves this one.
            resubmission.resubmitStalePublications();
            assertThat(resubmittedPublications()).isZero();
            assertThat(latestStatus(invitation)).isEqualTo(InvitationEmailStatus.QUEUED);

            jdbc.sql("update event_publication set publication_date = publication_date - interval '10 minutes'")
                    .update();
            resubmission.resubmitStalePublications();

            awaitLatestStatus(invitation, InvitationEmailStatus.SENT);
            assertThat(mailpit.awaitMessages(1)).hasSize(1);
        }

        @Test
        @DisplayName("IEM-8.1 Sending and failing leave no token in the logs")
        void sendingAndFailingLeaveNoTokenInTheLogs(CapturedOutput output) {
            doAnswer(invocation -> {
                        MailMessage message = invocation.getArgument(0);
                        if (message.to().equals("refused@x.test")) {
                            throw new MailDeliveryException(
                                    "550 rejected " + link(message), new IllegalStateException("refused"));
                        }
                        return invocation.callRealMethod();
                    })
                    .when(mail)
                    .send(any());

            IssuedInvitation sent = service.create("sent@x.test", Set.of(), true);
            IssuedInvitation refused = service.create("refused@x.test", Set.of(), true);

            awaitLatestStatus(sent.invitation().id(), InvitationEmailStatus.SENT);
            awaitLatestStatus(refused.invitation().id(), InvitationEmailStatus.FAILED);
            assertThat(output.getAll())
                    .doesNotContain(token(sent.link()))
                    .doesNotContain(token(refused.link()))
                    .doesNotContain(sent.link().toString())
                    .doesNotContain(refused.link().toString());
        }

        @Test
        void theEventHoldsNoToken() {
            CountDownLatch release = holdSends();
            IssuedInvitation issued;
            try {
                issued = service.create("ivan@x.test", Set.of(), true);
                await("the stored publication", () -> data.count("event_publication") == 1);
                assertThat(jdbc.sql("select serialized_event from event_publication")
                                .query(String.class)
                                .single())
                        .contains(
                                requestIds(issued.invitation().id()).getFirst().toString())
                        .doesNotContain(token(issued.link()));
            } finally {
                release.countDown();
            }
        }
    }

    /**
     * The stale resubmission picks up a publication whose send is still in progress (a backlog makes it look stale),
     * and that must not send the email a second time. It resubmits only invitation email publications.
     */
    @Nested
    class Resubmitting {

        @Test
        void aSendStillInProgressIsNotStartedAgain() {
            AtomicInteger sends = new AtomicInteger();
            CountDownLatch release = new CountDownLatch(1);
            doAnswer(invocation -> {
                        sends.incrementAndGet();
                        release.await(10, TimeUnit.SECONDS);
                        return invocation.callRealMethod();
                    })
                    .when(mail)
                    .send(any());
            UUID invitation =
                    service.create("ivan@x.test", Set.of(), true).invitation().id();
            UUID request = requestIds(invitation).getFirst();
            try {
                await("the first send", () -> sends.get() == 1);
                jdbc.sql("update event_publication set publication_date = publication_date - interval '6 minutes'")
                        .update();

                resubmission.resubmitStalePublications();
                assertThat(completionAttempts()).isEqualTo(2);
            } finally {
                release.countDown();
            }

            // The second delivery waits for the first, then finds the email sent; it ends when the executor is idle.
            verify(emails, timeout(10_000).times(2)).lock(request);
            await("the deliveries to end", () -> listeners.getActiveCount() == 0 && listeners.getQueueSize() == 0);
            assertThat(latestStatus(invitation)).isEqualTo(InvitationEmailStatus.SENT);
            assertThat(sends).hasValue(1);
            verify(mail, times(1)).send(any());
        }

        @Test
        void otherEventsAreNotResubmitted() {
            transaction.executeWithoutResult(status -> publisher.publishEvent(new OtherEvent()));
            await(
                    "the failed publication",
                    () -> otherListener.calls() == 1 && incompleteStatuses().equals(List.of("FAILED")));
            jdbc.sql("update event_publication set publication_date = publication_date - interval '6 minutes'")
                    .update();

            resubmission.resubmitStalePublications();

            assertThat(resubmittedPublications()).isZero();
            assertThat(otherListener.calls()).isEqualTo(1);
            jdbc.sql("delete from event_publication").update();
        }
    }

    record OtherEvent() {}

    static class FailingListener {

        private final AtomicInteger calls = new AtomicInteger();

        int calls() {
            return calls.get();
        }

        @ApplicationModuleListener
        void on(OtherEvent event) {
            calls.incrementAndGet();
            throw new IllegalStateException("unexpected failure");
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

    /** Publications resubmitted: the registry increments completion attempts when resubmitting. */
    private int resubmittedPublications() {
        return jdbc.sql("select count(*) from event_publication where completion_attempts > 1")
                .query(Integer.class)
                .single();
    }

    private int completionAttempts() {
        return jdbc.sql("select completion_attempts from event_publication")
                .query(Integer.class)
                .single();
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

    /** The mail server refuses every message, echoing its link line as some servers do. */
    private void refuseEveryMessage() {
        doAnswer(invocation -> {
                    MailMessage message = invocation.getArgument(0);
                    throw new MailDeliveryException(
                            "550 5.7.1 rejected, suspicious URL " + link(message),
                            new IllegalStateException("refused"));
                })
                .when(mail)
                .send(any());
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
