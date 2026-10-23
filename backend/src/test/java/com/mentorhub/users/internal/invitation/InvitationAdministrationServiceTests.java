package com.mentorhub.users.internal.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.mentorhub.TestcontainersConfiguration;
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
import com.mentorhub.users.internal.service.ResultPage;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.FieldViolation;
import com.mentorhub.web.NotFoundException;
import com.mentorhub.web.ValidationException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

/** The service against a real database, as the caller set in the security context. Data is committed. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InvitationAdministrationServiceTests {

    static final String LINK_PATTERN = "https://app\\.test/invite/[A-Za-z0-9_-]{43}";

    @Autowired
    InvitationAdministrationService service;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    InvitationEmailRepository emails;

    TestUsers users;
    InvitationTestData data;

    /** The calling administrator, "Anna Petrova". */
    UUID admin;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        data = new InvitationTestData(jdbc, jsonMapper);
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        actAs(admin, Permission.USERS_READ, Permission.USERS_MANAGE, Permission.USERS_INVITE);
    }

    @AfterEach
    void clearCaller() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class Creating {

        // AC-1
        @Test
        void createsAPendingInvitationWithALinkAndAnAuditEntry() {
            IssuedInvitation issued = service.create("ivan@x.test", Set.of(Role.STUDENT, Role.MENTOR));

            InvitationDetails invitation = issued.invitation();
            assertThat(invitation.email()).isEqualTo("ivan@x.test");
            assertThat(invitation.roles()).containsExactly(Role.MENTOR, Role.STUDENT);
            assertThat(invitation.status()).isEqualTo(InvitationStatus.PENDING);
            assertThat(invitation.createdBy()).isEqualTo(new HistoryActor(admin, "Anna Petrova"));
            assertThat(invitation.expiresAt()).isEqualTo(invitation.createdAt().plus(Duration.ofDays(7)));
            assertThat(invitation.createdAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
            assertThat(invitation.acceptedAt()).isNull();
            assertThat(invitation.acceptedUser()).isNull();
            assertThat(invitation.revokedAt()).isNull();
            assertThat(invitation.revokedBy()).isNull();
            assertThat(issued.link().toString()).matches(LINK_PATTERN);
            assertThat(data.auditEntries(invitation.id()))
                    .containsExactly(new AuditRow(
                            InvitationAuditAction.INVITATION_CREATED.name(),
                            admin,
                            "INVITATION",
                            invitation.id(),
                            Map.of("email", "ivan@x.test", "roles", List.of("MENTOR", "STUDENT"))));
        }

        // Spec invitation-links: the token is stored in plaintext; audit details never contain it (AC-7).
        @Test
        void storesTheTokenOfTheLink() {
            IssuedInvitation issued = service.create("ivan@x.test", Set.of());
            String token = token(issued.link());

            assertThat(data.storedToken(issued.invitation().id())).isEqualTo(token);
            assertThat(data.allAuditDetails()).doesNotContain(token);
        }

        // AC-1
        @Test
        void acceptsAnEmptyRoleSet() {
            IssuedInvitation issued = service.create("ivan@x.test", Set.of());

            assertThat(issued.invitation().roles()).isEmpty();
            assertThat(data.auditEntries(issued.invitation().id()).getFirst().details())
                    .isEqualTo(Map.of("email", "ivan@x.test", "roles", List.of()));
        }

        // AC-2
        @Test
        void storesTheEmailAsEnteredAfterTrimming() {
            IssuedInvitation issued = service.create(" \tIvan.Sidorov@Example.COM  ", Set.of());

            assertThat(issued.invitation().email()).isEqualTo("Ivan.Sidorov@Example.COM");
        }

        // AC-2
        @Test
        void acceptsAnEmailOf254Characters() {
            String email = longEmail(254);

            assertThat(service.create(email, Set.of()).invitation().email()).isEqualTo(email);
        }

        // AC-2
        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   "})
        void rejectsAMissingEmail(@Nullable String email) {
            assertValidationErrors(
                    () -> service.create(email, Set.of()), new FieldViolation("email", "must not be blank"));
            assertThat(data.count("invitations")).isZero();
        }

        // AC-2
        @ParameterizedTest
        @ValueSource(strings = {"not-an-email", "a@b@x.test", "ivan @x.test", "@x.test", "ivan@"})
        void rejectsAnInvalidEmail(String email) {
            assertValidationErrors(
                    () -> service.create(email, Set.of()),
                    new FieldViolation("email", "must be a well-formed email address"));
            assertThat(data.count("invitations")).isZero();
        }

        // AC-2
        @Test
        void rejectsAnEmailLongerThan254Characters() {
            assertValidationErrors(
                    () -> service.create(longEmail(255), Set.of()),
                    new FieldViolation("email", "must be at most 254 characters"));
            assertThat(data.count("invitations")).isZero();
        }

        // AC-2: one error per invalid field.
        @Test
        void reportsANullRoleTogetherWithAnInvalidEmail() {
            assertValidationErrors(
                    () -> service.create("not-an-email", Arrays.asList(Role.MENTOR, null)),
                    new FieldViolation("email", "must be a well-formed email address"),
                    new FieldViolation("roles", "must not contain null"));
            assertValidationErrors(
                    () -> service.create("ivan@x.test", Arrays.asList((Role) null)),
                    new FieldViolation("roles", "must not contain null"));
            assertThat(data.count("invitations")).isZero();
        }

        // AC-3
        @Test
        void refusesTheEmailOfAnActiveUserIgnoringCase() {
            UUID active = users.create("ivan@x.test", UserStatus.ACTIVE);

            assertThatThrownBy(() -> service.create("IVAN@x.test", Set.of()))
                    .isInstanceOfSatisfying(ConflictException.class, exception -> {
                        assertThat(exception.type()).isEqualTo(InvitationProblems.USER_ACTIVE);
                        assertThat(exception.extensions()).isEqualTo(Map.of("userId", active));
                    });
            assertThat(data.count("invitations")).isZero();
            assertThat(data.count("audit_log")).isZero();
        }

        // AC-3, S9
        @Test
        void invitesTheEmailOfADisabledUser() {
            users.create("ivan@x.test", UserStatus.DISABLED);

            assertThat(service.create("ivan@x.test", Set.of()).invitation().status())
                    .isEqualTo(InvitationStatus.PENDING);
        }

        // AC-3
        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void refusesASecondOpenInvitationForTheEmailIgnoringCase(boolean expired) {
            UUID open = service.create("ivan@x.test", Set.of()).invitation().id();
            if (expired) {
                data.expire(open);
            }

            assertThatThrownBy(() -> service.create("Ivan@X.test", Set.of(Role.MENTOR)))
                    .isInstanceOfSatisfying(ConflictException.class, exception -> {
                        assertThat(exception.type()).isEqualTo(InvitationProblems.INVITATION_EXISTS);
                        assertThat(exception.extensions()).isEqualTo(Map.of("invitationId", open));
                    });
            assertThat(data.count("invitations")).isEqualTo(1);
            assertThat(data.auditEntries(open)).hasSize(1);
        }

        // AC-3: accepted and revoked invitations are closed and do not block a new one.
        @Test
        void invitesAgainAfterTheEarlierInvitationsClosed() {
            UUID formerUser = users.create("ivan@x.test", UserStatus.DISABLED);
            data.markAccepted(
                    service.create("ivan@x.test", Set.of()).invitation().id(), formerUser);
            data.markRevoked(
                    service.create("ivan@x.test", Set.of()).invitation().id(), admin);

            assertThat(service.create("ivan@x.test", Set.of()).invitation().status())
                    .isEqualTo(InvitationStatus.PENDING);
            assertThat(data.count("invitations")).isEqualTo(3);
        }
    }

    @Nested
    class Listing {

        // AC-4
        @Test
        void sortsNewestFirstThenById() {
            UUID first = create("a@x.test");
            UUID second = create("b@x.test");
            UUID third = create("c@x.test");
            jdbc.sql("update invitations set created_at = '2026-10-18T10:00:00Z' where id in (:ids)")
                    .param("ids", List.of(second, third))
                    .update();
            jdbc.sql("update invitations set created_at = '2026-10-17T10:00:00Z' where id = :id")
                    .param("id", first)
                    .update();
            // PostgreSQL compares uuids as unsigned bytes, which is the order of their lowercase text, not
            // UUID.compareTo.
            List<UUID> tied = Stream.of(second, third)
                    .sorted(Comparator.comparing(UUID::toString))
                    .toList();

            ResultPage<InvitationDetails> page = service.list(query(null, null, 0, 25));

            assertThat(ids(page)).containsExactly(tied.get(1), tied.get(0), first);
            assertThat(page.totalItems()).isEqualTo(3);
        }

        // AC-4
        @Test
        void showsWhoAcceptedAndWhoRevoked() {
            UUID accepted = create("a@x.test");
            UUID revoked = create("b@x.test");
            UUID acceptingUser = users.create("a@x.test", UserStatus.ACTIVE);
            UUID otherAdmin = users.create("other@x.test", UserStatus.ACTIVE, Role.ADMIN);
            data.markAccepted(accepted, acceptingUser);
            data.markRevoked(revoked, otherAdmin);

            List<InvitationDetails> items =
                    service.list(query(null, null, 0, 25)).items();

            InvitationDetails revokedItem = items.get(0);
            assertThat(revokedItem.status()).isEqualTo(InvitationStatus.REVOKED);
            assertThat(revokedItem.revokedBy()).isEqualTo(new HistoryActor(otherAdmin, "Anna Petrova"));
            assertThat(revokedItem.revokedAt()).isNotNull();
            assertThat(revokedItem.acceptedUser()).isNull();
            InvitationDetails acceptedItem = items.get(1);
            assertThat(acceptedItem.status()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(acceptedItem.acceptedUser()).isEqualTo(new HistoryActor(acceptingUser, "Anna Petrova"));
            assertThat(acceptedItem.acceptedAt()).isNotNull();
            assertThat(acceptedItem.revokedBy()).isNull();
        }

        // AC-4
        @Test
        void returnsTheRequestedPageWithTheTotal() {
            UUID a = create("a@x.test");
            UUID b = create("b@x.test");
            create("c@x.test");
            create("d@x.test");

            ResultPage<InvitationDetails> page = service.list(query(null, null, 1, 2));

            assertThat(ids(page)).containsExactly(b, a);
            assertThat(page.page()).isEqualTo(1);
            assertThat(page.size()).isEqualTo(2);
            assertThat(page.totalItems()).isEqualTo(4);
        }

        // AC-4
        @Test
        void searchesTheEmailIgnoringCaseAfterTrimming() {
            UUID match = create("Ivan.Sidorov@x.test");
            create("maria@x.test");

            ResultPage<InvitationDetails> page = service.list(query("  sIdOrOv \t", null, 0, 25));

            assertThat(ids(page)).containsExactly(match);
            assertThat(page.totalItems()).isEqualTo(1);
        }

        // AC-4
        @ParameterizedTest
        @ValueSource(strings = {"%", "_"})
        void searchMatchesWildcardsLiterally(String wildcard) {
            UUID match = create("a" + wildcard + "b@x.test");
            create("ab@x.test");
            create("axb@x.test");

            assertThat(ids(service.list(query("a" + wildcard + "b", null, 0, 25))))
                    .containsExactly(match);
        }

        // AC-4: an unescaped backslash would escape the next character and match "ab".
        @Test
        void searchMatchesABackslashLiterally() {
            create("ab@x.test");

            assertThat(service.list(query("a\\b", null, 0, 25)).items()).isEmpty();
        }

        // AC-4: the status filter uses the same derivation as the status itself.
        @ParameterizedTest
        @EnumSource(InvitationStatus.class)
        void filtersByStatus(InvitationStatus status) {
            UUID pending = create("pending@x.test");
            UUID expired = create("expired@x.test");
            data.expire(expired);
            UUID accepted = create("accepted@x.test");
            data.expire(accepted);
            data.markAccepted(accepted, users.create("accepted@x.test", UserStatus.ACTIVE));
            UUID revoked = create("revoked@x.test");
            data.expire(revoked);
            data.markRevoked(revoked, admin);
            Map<InvitationStatus, UUID> byStatus = Map.of(
                    InvitationStatus.PENDING, pending,
                    InvitationStatus.EXPIRED, expired,
                    InvitationStatus.ACCEPTED, accepted,
                    InvitationStatus.REVOKED, revoked);

            ResultPage<InvitationDetails> page = service.list(query(null, status, 0, 25));

            assertThat(ids(page)).containsExactly(byStatus.get(status));
            assertThat(page.items().getFirst().status()).isEqualTo(status);
            assertThat(page.totalItems()).isEqualTo(1);
        }

        // AC-4
        @Test
        void acceptsASearchOf254Characters() {
            assertThat(service.list(query("x".repeat(254), null, 0, 25)).items())
                    .isEmpty();
        }

        // AC-4
        @Test
        void rejectsInvalidParametersWithAnErrorEach() {
            assertValidationErrors(
                    () -> service.list(query(" " + "x".repeat(255) + " ", null, -1, 101)),
                    new FieldViolation("q", "must be at most 254 characters"),
                    new FieldViolation("page", "must be greater than or equal to 0"),
                    new FieldViolation("size", "must be between 1 and 100"));
            assertValidationErrors(
                    () -> service.list(query(null, null, 0, 0)),
                    new FieldViolation("size", "must be between 1 and 100"));
        }

        // AC-17: a stored role the code does not know is skipped on reads.
        @Test
        void leavesOutStoredRolesTheCodeDoesNotKnow() {
            UUID invitation = service.create("ivan@x.test", Set.of(Role.MENTOR))
                    .invitation()
                    .id();
            data.addStoredRole(invitation, "AUDITOR");

            assertThat(service.list(query(null, null, 0, 25)).items().getFirst().roles())
                    .containsExactly(Role.MENTOR);
        }
    }

    @Nested
    class Revoking {

        // AC-6
        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void revokesAnOpenInvitation(boolean expired) {
            UUID invitation = create("ivan@x.test");
            if (expired) {
                data.expire(invitation);
            }

            InvitationDetails revoked = service.revoke(invitation);

            assertThat(revoked.status()).isEqualTo(InvitationStatus.REVOKED);
            assertThat(revoked.revokedBy()).isEqualTo(new HistoryActor(admin, "Anna Petrova"));
            assertThat(revoked.revokedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
            assertThat(data.auditEntries(invitation))
                    .extracting(AuditRow::action)
                    .containsExactly("INVITATION_CREATED", "INVITATION_REVOKED");
            assertThat(data.auditEntries(invitation).getLast())
                    .isEqualTo(new AuditRow("INVITATION_REVOKED", admin, "INVITATION", invitation, Map.of()));
        }

        // AC-6
        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void refusesAClosedInvitation(boolean accepted) {
            UUID invitation = create("ivan@x.test");
            close(invitation, accepted);

            assertConflict(() -> service.revoke(invitation), InvitationProblems.INVITATION_CLOSED);
            assertThat(service.list(query(null, null, 0, 25)).items().getFirst().status())
                    .isEqualTo(accepted ? InvitationStatus.ACCEPTED : InvitationStatus.REVOKED);
            assertThat(data.auditEntries(invitation)).hasSize(1);
        }

        // AC-6
        @Test
        void unknownIdIsNotFound() {
            assertThatThrownBy(() -> service.revoke(ANY_ID)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class NewLinks {

        // AC-7
        @Test
        void replacesTheTokenAndRenewsTheExpiry() {
            IssuedInvitation created = service.create("ivan@x.test", Set.of(Role.MENTOR));
            UUID invitation = created.invitation().id();
            data.expire(invitation);

            IssuedInvitation reissued = service.reissueLink(invitation);

            String newToken = token(reissued.link());
            assertThat(reissued.link().toString())
                    .matches(LINK_PATTERN)
                    .isNotEqualTo(created.link().toString());
            assertThat(data.storedToken(invitation)).isEqualTo(newToken);
            assertThat(reissued.invitation().status()).isEqualTo(InvitationStatus.PENDING);
            assertThat(reissued.invitation().roles()).containsExactly(Role.MENTOR);
            assertThat(reissued.invitation().expiresAt())
                    .isCloseTo(Instant.now().plus(Duration.ofDays(7)), within(1, ChronoUnit.MINUTES));
            assertThat(data.auditEntries(invitation).getLast())
                    .isEqualTo(new AuditRow(
                            "INVITATION_LINK_REISSUED",
                            admin,
                            "INVITATION",
                            invitation,
                            Map.of(
                                    "expiresAt",
                                    reissued.invitation().expiresAt().toString())));
            assertThat(data.allAuditDetails()).doesNotContain(newToken);
        }

        // AC-7
        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void refusesAClosedInvitation(boolean accepted) {
            UUID invitation = create("ivan@x.test");
            String token = data.storedToken(invitation);
            close(invitation, accepted);

            assertConflict(() -> service.reissueLink(invitation), InvitationProblems.INVITATION_CLOSED);
            assertThat(data.storedToken(invitation)).isEqualTo(token);
        }

        // AC-7
        @Test
        void unknownIdIsNotFound() {
            assertThatThrownBy(() -> service.reissueLink(ANY_ID)).isInstanceOf(NotFoundException.class);
        }
    }

    /** Spec invitation-links. */
    @Nested
    class Links {

        // invitation-links AC-1
        @Test
        void returnsTheLinkOfTheCreatedInvitation() {
            IssuedInvitation created = service.create("Ivan@x.test", Set.of(Role.MENTOR));

            PendingInvitationLink link = service.link(created.invitation().id());

            assertThat(link)
                    .isEqualTo(new PendingInvitationLink(
                            created.link(), "Ivan@x.test", created.invitation().expiresAt()));
        }

        // invitation-links AC-1: after a new link, the new one is returned.
        @Test
        void returnsTheLinkOfTheLatestNewLink() {
            UUID invitation = create("ivan@x.test");
            data.expire(invitation);
            IssuedInvitation reissued = service.reissueLink(invitation);

            PendingInvitationLink link = service.link(invitation);

            assertThat(link.link()).isEqualTo(reissued.link());
            assertThat(link.expiresAt()).isEqualTo(reissued.invitation().expiresAt());
        }

        // invitation-links AC-1: any holder of USERS_INVITE, not only the creator.
        @Test
        void anotherAdministratorGetsTheSameLink() {
            IssuedInvitation created = service.create("ivan@x.test", Set.of());
            actAs(users.create("other@x.test", UserStatus.ACTIVE, Role.ADMIN), Permission.USERS_INVITE);

            assertThat(service.link(created.invitation().id()).link()).isEqualTo(created.link());
        }

        // invitation-links AC-7
        @Test
        void writesNoAuditEntry() {
            UUID invitation = create("ivan@x.test");

            service.link(invitation);

            assertThat(data.auditEntries(invitation))
                    .extracting(AuditRow::action)
                    .containsExactly("INVITATION_CREATED");
        }

        // invitation-links AC-2: expiry is decided with the database's now().
        @Test
        void refusesAnExpiredInvitation() {
            UUID invitation = create("ivan@x.test");
            String token = data.storedToken(invitation);
            data.expire(invitation);

            assertConflict(() -> service.link(invitation), InvitationProblems.INVITATION_EXPIRED);
            assertThat(data.storedToken(invitation)).isEqualTo(token);
            assertThat(data.auditEntries(invitation)).hasSize(1);
        }

        // invitation-links AC-2: closed wins over expired, as in the status derivation.
        @ParameterizedTest
        @ValueSource(booleans = {false, true})
        void refusesAClosedInvitation(boolean accepted) {
            UUID pending = create("ivan@x.test");
            UUID expired = create("maria@x.test");
            data.expire(expired);
            for (UUID invitation : List.of(pending, expired)) {
                if (accepted) {
                    data.markAccepted(invitation, users.create(invitation + "@x.test", UserStatus.ACTIVE));
                } else {
                    data.markRevoked(invitation, admin);
                }
            }

            assertConflict(() -> service.link(pending), InvitationProblems.INVITATION_CLOSED);
            assertConflict(() -> service.link(expired), InvitationProblems.INVITATION_CLOSED);
        }

        // invitation-links AC-2
        @Test
        void unknownIdIsNotFound() {
            create("ivan@x.test");

            assertThatThrownBy(() -> service.link(ANY_ID)).isInstanceOf(NotFoundException.class);
        }
    }

    // Spec invitation-emails, AC-16.
    @Nested
    class LastEmail {

        @Test
        void isAbsentWithoutEmailRequests() {
            UUID invitation = create("ivan@x.test");

            assertThat(service.list(query(null, null, 0, 25)).items().getFirst().lastEmail())
                    .isNull();
            assertThat(service.reissueLink(invitation).invitation().lastEmail()).isNull();
            assertThat(service.revoke(invitation).lastEmail()).isNull();
        }

        @Test
        void showsTheLatestRequestWithItsRequester() {
            UUID invitation = create("ivan@x.test");
            UUID otherAdmin = users.create("other@x.test", UserStatus.ACTIVE, Role.ADMIN);
            UUID earlier = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);
            emails.markSent(earlier);
            jdbc.sql("update invitation_emails set requested_at = now() - interval '1 hour' where id = :id")
                    .param("id", earlier)
                    .update();
            emails.insertQueued(invitation, InvitationEmailReason.RESENT, otherAdmin);

            InvitationEmailDetails queued =
                    service.list(query(null, null, 0, 25)).items().getFirst().lastEmail();

            assertThat(queued).isNotNull();
            assertThat(queued.status()).isEqualTo(InvitationEmailStatus.QUEUED);
            assertThat(queued.requestedBy()).isEqualTo(new HistoryActor(otherAdmin, "Anna Petrova"));
            assertThat(queued.requestedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
            assertThat(queued.finishedAt()).isNull();
        }

        @Test
        void showsTheOutcomeOnEveryResponseWithAnInvitation() {
            UUID invitation = create("ivan@x.test");
            UUID request = emails.insertQueued(invitation, InvitationEmailReason.CREATED, admin);
            emails.markFailed(request, InvitationEmailFailure.of("550 mailbox unavailable"));

            InvitationEmailDetails reissued =
                    service.reissueLink(invitation).invitation().lastEmail();
            InvitationEmailDetails revoked = service.revoke(invitation).lastEmail();

            for (InvitationEmailDetails email : Arrays.asList(reissued, revoked)) {
                assertThat(email).isNotNull();
                assertThat(email.status()).isEqualTo(InvitationEmailStatus.FAILED);
                assertThat(email.requestedBy()).isEqualTo(new HistoryActor(admin, "Anna Petrova"));
                assertThat(email.finishedAt()).isNotNull();
            }
        }

        @Test
        void eachInvitationShowsItsOwnLatestRequest() {
            UUID withEmail = create("a@x.test");
            create("b@x.test");
            emails.insertQueued(withEmail, InvitationEmailReason.CREATED, admin);

            List<InvitationDetails> items =
                    service.list(query(null, null, 0, 25)).items();

            assertThat(items)
                    .extracting(InvitationDetails::email, item -> item.lastEmail() == null)
                    .containsExactlyInAnyOrder(tuple("a@x.test", false), tuple("b@x.test", true));
        }
    }

    // AC-5: every management operation needs USERS_INVITE; other permissions do not grant it.
    @ParameterizedTest
    @MethodSource("operations")
    void callersWithoutTheInvitePermissionAreRefused(Consumer<InvitationAdministrationService> operation) {
        UUID invitation = create("open@x.test");
        String token = data.storedToken(invitation);
        UUID caller = users.create("mentor@x.test", UserStatus.ACTIVE, Role.MENTOR);
        for (Permission[] permissions :
                List.of(new Permission[0], new Permission[] {Permission.USERS_READ}, new Permission[] {
                    Permission.USERS_READ, Permission.USERS_MANAGE
                })) {
            actAs(caller, permissions);
            assertThatThrownBy(() -> operation.accept(service)).isInstanceOf(AccessDeniedException.class);
        }
        assertThat(data.count("invitations")).isEqualTo(1);
        assertThat(data.storedToken(invitation)).isEqualTo(token);
        assertThat(data.auditEntries(invitation)).hasSize(1);
    }

    // The permission check runs before anything is looked up, so any id does.
    private static final UUID ANY_ID = UUID.fromString("00000000-0000-4000-8000-0000000000ff");

    static Stream<Arguments> operations() {
        return Stream.of(
                operation(service -> service.create("new@x.test", Set.of())),
                operation(service -> service.list(new InvitationQuery(null, null, 0, 25))),
                operation(service -> service.revoke(ANY_ID)),
                operation(service -> service.reissueLink(ANY_ID)),
                // invitation-links AC-3
                operation(service -> service.link(ANY_ID)));
    }

    private static Arguments operation(Consumer<InvitationAdministrationService> operation) {
        return Arguments.of(operation);
    }

    private UUID create(String email) {
        return service.create(email, Set.of()).invitation().id();
    }

    private void close(UUID invitation, boolean accepted) {
        if (accepted) {
            data.markAccepted(invitation, users.create("ivan@x.test", UserStatus.ACTIVE));
        } else {
            data.markRevoked(invitation, admin);
        }
    }

    private static String token(URI link) {
        String path = link.getPath();
        return path.substring(path.lastIndexOf('/') + 1);
    }

    /** A valid email of the given length; local parts are at most 64 characters, so the length goes into the domain. */
    private static String longEmail(int length) {
        StringBuilder domain = new StringBuilder("test");
        int remaining = length - "ivan@".length() - domain.length();
        while (remaining > 0) {
            int label = Math.min(63, remaining - 1);
            if (label < 1) {
                return "ivann@" + domain;
            }
            domain.insert(0, "a".repeat(label) + ".");
            remaining -= label + 1;
        }
        return "ivan@" + domain;
    }

    private static InvitationQuery query(@Nullable String text, @Nullable InvitationStatus status, int page, int size) {
        return new InvitationQuery(text, status, page, size);
    }

    private static List<UUID> ids(ResultPage<InvitationDetails> page) {
        return page.items().stream().map(InvitationDetails::id).toList();
    }

    private static void actAs(UUID userId, Permission... permissions) {
        SecurityContextHolder.getContext()
                .setAuthentication(TestAuthentications.activeUserAuthentication(userId, permissions));
    }

    private static void assertConflict(ThrowingCallable call, URI type) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        exception -> assertThat(exception.type()).isEqualTo(type));
    }

    private static void assertValidationErrors(ThrowingCallable call, FieldViolation... errors) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        exception -> assertThat(exception.errors()).containsExactly(errors));
    }
}
