package com.mentorhub.users.internal.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.AccessStatus;
import com.mentorhub.users.internal.domain.InvitationStatus;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.invitation.InvitationTestData.AuditRow;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.users.internal.service.MeDetails;
import com.mentorhub.users.internal.service.UserProfile;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.NotFoundException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

/** Preview and acceptance against a real database, as the caller set in the security context. Data is committed. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class InvitationAcceptanceServiceTests {

    static final String SUBJECT = "invited-subject";

    @Autowired
    InvitationAcceptanceService service;

    @Autowired
    InvitationAdministrationService administration;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper jsonMapper;

    TestUsers users;
    InvitationTestData data;
    UUID admin;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        data = new InvitationTestData(jdbc, jsonMapper);
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
    }

    @AfterEach
    void clearCaller() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class Previewing {

        // AC-8
        @Test
        void showsTheEmailAndExpiryOfAValidLink() {
            Invited invited = invite("Ivan@x.test", Role.MENTOR);
            SecurityContextHolder.clearContext();

            PreviewedInvitation preview = service.preview(invited.token());

            assertThat(preview)
                    .isEqualTo(new PreviewedInvitation(
                            "Ivan@x.test", invited.details().expiresAt()));
        }

        // AC-9
        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.invitation.InvitationAcceptanceServiceTests#invalidTokens")
        void anInvalidLinkIsTheSameNotFoundInEveryCase(Function<InvitationAcceptanceServiceTests, String> token) {
            String value = token.apply(InvitationAcceptanceServiceTests.this);
            SecurityContextHolder.clearContext();

            assertInvalid(() -> service.preview(value));
        }
    }

    @Nested
    class Accepting {

        // AC-10, AC-16
        @Test
        void createsTheUserLinksTheIdentityAndAssignsTheRoles() {
            Invited invited = invite("ivan@x.test", Role.MENTOR, Role.STUDENT);
            signIn(AccessStatus.NOT_INVITED, null, "IVAN@X.test", true);

            MeDetails me = service.accept(invited.token());

            UUID userId = userId("ivan@x.test");
            assertThat(me)
                    .isEqualTo(new MeDetails(
                            AccessStatus.ACTIVE,
                            new UserProfile(
                                    userId,
                                    "ivan@x.test",
                                    "Ivan",
                                    "Sidorov",
                                    null,
                                    List.of(Role.MENTOR, Role.STUDENT),
                                    List.of())));
            assertThat(users.status(userId)).isEqualTo("ACTIVE");
            assertThat(linkedUser(SUBJECT)).isEqualTo(userId);
            assertThat(users.roles(userId)).containsExactly("MENTOR", "STUDENT");
            assertAccepted(invited, userId);
            assertThat(data.auditEntries(userId))
                    .containsExactly(new AuditRow(
                            "USER_INVITATION_ACCEPTED",
                            userId,
                            "USER",
                            userId,
                            acceptance(invited, true, true, false, List.of("MENTOR", "STUDENT"), List.of())));
            assertThat(data.allAuditDetails()).doesNotContain(invited.token());
        }

        // AC-11: the roles become exactly the invitation's known roles; unknown stored roles stay.
        @Test
        void reactivatesADisabledUserWithExactlyTheInvitationsRoles() {
            UUID former = users.create("ivan@x.test", UserStatus.DISABLED, Role.ADMIN, Role.STUDENT);
            jdbc.sql("insert into roles (name) values ('AUDITOR') on conflict do nothing")
                    .update();
            users.assignStoredRole(former, "AUDITOR");
            Invited invited = invite("ivan@x.test", Role.MENTOR, Role.STUDENT);
            signIn(AccessStatus.NOT_INVITED, null, "ivan@x.test", true);

            MeDetails me = service.accept(invited.token());

            assertThat(me.status()).isEqualTo(AccessStatus.ACTIVE);
            assertThat(me.user()).isNotNull();
            assertThat(me.user().id()).isEqualTo(former);
            assertThat(users.status(former)).isEqualTo("ACTIVE");
            assertThat(disableReason(former)).isEmpty();
            assertThat(users.roles(former)).containsExactly("AUDITOR", "MENTOR", "STUDENT");
            // The profile stays unchanged: names from the token are not applied.
            assertThat(me.user().firstName()).isEqualTo("Anna");
            assertThat(linkedUser(SUBJECT)).isEqualTo(former);
            assertAccepted(invited, former);
            assertThat(data.auditEntries(former))
                    .extracting(AuditRow::details)
                    .containsExactly(acceptance(invited, false, true, true, List.of("MENTOR"), List.of("ADMIN")));
        }

        // AC-11: a disabled caller whose identity is already linked to that user.
        @Test
        void reactivatesTheDisabledCallerWithoutLinkingAgain() {
            UUID former = users.create("ivan@x.test", UserStatus.DISABLED, Role.STUDENT);
            users.link(former, SUBJECT);
            Invited invited = invite("ivan@x.test");
            signIn(AccessStatus.DISABLED, former, "ivan@x.test", true);

            MeDetails me = service.accept(invited.token());

            assertThat(me.status()).isEqualTo(AccessStatus.ACTIVE);
            assertThat(users.roles(former)).isEmpty();
            assertThat(users.count("user_identities")).isEqualTo(1);
            assertThat(data.auditEntries(former))
                    .extracting(AuditRow::details)
                    .containsExactly(acceptance(invited, false, false, true, List.of(), List.of("STUDENT")));
        }

        // AC-11: a user who became active since the invitation was created only gains roles.
        @Test
        void addsTheRolesToAUserWhoBecameActive() {
            UUID user = users.create("ivan@x.test", UserStatus.DISABLED, Role.ADMIN);
            Invited invited = invite("ivan@x.test", Role.MENTOR);
            jdbc.sql("update users set status = 'ACTIVE', disabled_reason = null where id = :id")
                    .param("id", user)
                    .update();
            users.link(user, "other-subject");
            signIn(AccessStatus.NOT_INVITED, null, "ivan@x.test", true);

            service.accept(invited.token());

            assertThat(users.roles(user)).containsExactly("ADMIN", "MENTOR");
            assertThat(users.count("user_identities")).isEqualTo(2);
            assertThat(data.auditEntries(user))
                    .extracting(AuditRow::details)
                    .containsExactly(acceptance(invited, false, true, false, List.of("MENTOR"), List.of()));
        }

        // AC-17: a stored role the code does not know is skipped on acceptance.
        @Test
        void skipsStoredRolesTheCodeDoesNotKnow() {
            Invited invited = invite("ivan@x.test", Role.MENTOR);
            data.addStoredRole(invited.details().id(), "AUDITOR");
            signIn(AccessStatus.NOT_INVITED, null, "ivan@x.test", true);

            service.accept(invited.token());

            UUID userId = userId("ivan@x.test");
            assertThat(users.roles(userId)).containsExactly("MENTOR");
            assertThat(data.auditEntries(userId))
                    .extracting(AuditRow::details)
                    .containsExactly(acceptance(invited, true, true, false, List.of("MENTOR"), List.of()));
        }

        // AC-12
        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.invitation.InvitationAcceptanceServiceTests#mismatchedEmails")
        void refusesAnUnverifiedOrDifferentEmail(@Nullable String email, boolean verified) {
            Invited invited = invite("ivan@x.test", Role.MENTOR);
            signIn(AccessStatus.NOT_INVITED, null, email, verified);

            assertConflict(() -> service.accept(invited.token()), InvitationProblems.INVITATION_EMAIL_MISMATCH);
            assertNothingChanged(invited);
        }

        // AC-13
        @Test
        void refusesAnIdentityLinkedToAnotherUser(CapturedOutput output) {
            UUID invitedUser = users.create("ivan@x.test", UserStatus.DISABLED);
            UUID other = users.create("other@x.test", UserStatus.ACTIVE);
            users.link(other, SUBJECT);
            Invited invited = invite("ivan@x.test", Role.MENTOR);
            signIn(AccessStatus.ACTIVE, other, "ivan@x.test", true);

            assertConflict(() -> service.accept(invited.token()), InvitationProblems.IDENTITY_LINKED_ELSEWHERE);
            assertNothingChanged(invited);
            assertThat(users.status(invitedUser)).isEqualTo("DISABLED");
            assertThat(output.getOut())
                    .contains("the identity is linked to user " + other + ", not to user " + invitedUser);
        }

        // AC-13: when no user has the invited email, a linked identity is linked elsewhere too.
        @Test
        void refusesALinkedIdentityWhenNoUserHasTheEmail(CapturedOutput output) {
            UUID other = users.create("other@x.test", UserStatus.ACTIVE);
            users.link(other, SUBJECT);
            Invited invited = invite("ivan@x.test", Role.MENTOR);
            signIn(AccessStatus.ACTIVE, other, "ivan@x.test", true);

            assertConflict(() -> service.accept(invited.token()), InvitationProblems.IDENTITY_LINKED_ELSEWHERE);
            assertNothingChanged(invited);
            assertThat(userIdOrNull("ivan@x.test")).isNull();
            assertThat(output.getOut())
                    .contains("the identity is linked to user " + other + ", and no user has the invited email");
        }

        // AC-14
        @Test
        void acceptingAgainAsTheSameUserChangesNothing() {
            Invited invited = invite("ivan@x.test", Role.MENTOR);
            signIn(AccessStatus.NOT_INVITED, null, "ivan@x.test", true);
            MeDetails first = service.accept(invited.token());
            UUID userId = userId("ivan@x.test");
            // The authentication of a request made before the first acceptance committed.
            signIn(AccessStatus.NOT_INVITED, null, "ivan@x.test", true);

            MeDetails again = service.accept(invited.token());

            assertThat(again).isEqualTo(first);
            assertThat(data.auditEntries(userId)).hasSize(1);
            assertThat(users.roles(userId)).containsExactly("MENTOR");
        }

        // AC-14 applies only to the user who accepted.
        @Test
        void anInvitationAcceptedByAnotherUserIsInvalid() {
            Invited invited = invite("ivan@x.test", Role.MENTOR);
            signIn(AccessStatus.NOT_INVITED, null, "ivan@x.test", true);
            service.accept(invited.token());
            String otherSubject = "another-subject";
            SecurityContextHolder.getContext()
                    .setAuthentication(TestAuthentications.tokenAuthentication(
                            AccessStatus.NOT_INVITED, null, otherSubject, "ivan@x.test", true));

            assertInvalid(() -> service.accept(invited.token()));
            assertThat(users.count("user_identities")).isEqualTo(1);
        }

        // AC-9
        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.invitation.InvitationAcceptanceServiceTests#invalidTokens")
        void anInvalidLinkIsTheSameNotFoundInEveryCase(Function<InvitationAcceptanceServiceTests, String> token) {
            String value = token.apply(InvitationAcceptanceServiceTests.this);
            int invitations = data.count("invitations");
            int usersBefore = data.count("users");
            signIn(AccessStatus.NOT_INVITED, null, "ivan@x.test", true);

            assertInvalid(() -> service.accept(value));
            assertThat(data.count("users")).isEqualTo(usersBefore);
            assertThat(data.count("invitations")).isEqualTo(invitations);
            assertThat(users.count("user_identities")).isZero();
        }
    }

    /** Tokens that are invalid for every reason AC-9 lists; each builds its own state. */
    static Stream<Arguments> invalidTokens() {
        return Stream.of(
                invalid("unknown", test -> InvitationTokens.newToken()),
                invalid("too short", test -> "abc"),
                invalid("too long", test -> InvitationTokens.newToken() + "A"),
                invalid("not base64url", test -> InvitationTokens.newToken().substring(1) + "+"),
                invalid("replaced by a new link", test -> {
                    Invited invited = test.invite("ivan@x.test");
                    test.actAsAdmin();
                    test.administration.reissueLink(invited.details().id(), false);
                    return invited.token();
                }),
                invalid("expired", test -> {
                    Invited invited = test.invite("ivan@x.test");
                    test.data.expire(invited.details().id());
                    return invited.token();
                }),
                invalid("revoked", test -> {
                    Invited invited = test.invite("ivan@x.test");
                    test.actAsAdmin();
                    test.administration.revoke(invited.details().id());
                    return invited.token();
                }),
                invalid("accepted", test -> {
                    Invited invited = test.invite("ivan@x.test");
                    test.data.markAccepted(invited.details().id(), test.users.create("ivan@x.test", UserStatus.ACTIVE));
                    return invited.token();
                }));
    }

    static Stream<Arguments> mismatchedEmails() {
        return Stream.of(
                Arguments.of("ivan@x.test", false), Arguments.of("maria@x.test", true), Arguments.of(null, true));
    }

    private static Arguments invalid(String name, Function<InvitationAcceptanceServiceTests, String> token) {
        return Arguments.argumentSet(name, token);
    }

    record Invited(String token, InvitationDetails details) {}

    Invited invite(String email, Role... roles) {
        actAsAdmin();
        IssuedInvitation issued = administration.create(email, Set.of(roles), false);
        String path = issued.link().getPath();
        return new Invited(path.substring(path.lastIndexOf('/') + 1), issued.invitation());
    }

    void actAsAdmin() {
        SecurityContextHolder.getContext()
                .setAuthentication(TestAuthentications.activeUserAuthentication(admin, Permission.USERS_INVITE));
    }

    private static void signIn(
            AccessStatus status, @Nullable UUID userId, @Nullable String email, boolean emailVerified) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        TestAuthentications.tokenAuthentication(status, userId, SUBJECT, email, emailVerified));
    }

    private void assertAccepted(Invited invited, UUID userId) {
        actAsAdmin();
        InvitationDetails details = administration
                .list(new InvitationQuery(invited.details().email(), null, 0, 25))
                .items()
                .getFirst();
        assertThat(details.status()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(details.acceptedUser()).isNotNull();
        assertThat(details.acceptedUser().id()).isEqualTo(userId);
        assertThat(details.acceptedAt()).isNotNull();
    }

    private void assertNothingChanged(Invited invited) {
        actAsAdmin();
        assertThat(administration
                        .list(new InvitationQuery(invited.details().email(), null, 0, 25))
                        .items()
                        .getFirst()
                        .status())
                .isEqualTo(InvitationStatus.PENDING);
        assertThat(jdbc.sql("select count(*) from audit_log where subject_type = 'USER'")
                        .query(Integer.class)
                        .single())
                .isZero();
    }

    private Map<String, Object> acceptance(
            Invited invited,
            boolean userCreated,
            boolean identityLinked,
            boolean enabled,
            List<String> rolesAdded,
            List<String> rolesRemoved) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("invitationId", invited.details().id().toString());
        details.put("invitedBy", admin.toString());
        details.put("userCreated", userCreated);
        details.put("identityLinked", identityLinked);
        details.put("enabled", enabled);
        details.put("rolesAdded", rolesAdded);
        details.put("rolesRemoved", rolesRemoved);
        return details;
    }

    private UUID userId(String email) {
        UUID id = userIdOrNull(email);
        assertThat(id).as("user with " + email).isNotNull();
        return id;
    }

    private @Nullable UUID userIdOrNull(String email) {
        return jdbc.sql("select id from users where lower(email) = lower(:email)")
                .param("email", email)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    private @Nullable UUID linkedUser(String subject) {
        return jdbc.sql("select user_id from user_identities where issuer = :issuer and subject = :subject")
                .param("issuer", TestIssuer.ISSUER)
                .param("subject", subject)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    /** The disable reason, or an empty string when there is none. */
    private String disableReason(UUID userId) {
        return jdbc.sql("select coalesce(disabled_reason, '') from users where id = :id")
                .param("id", userId)
                .query(String.class)
                .single();
    }

    private static void assertInvalid(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(NotFoundException.class, exception -> {
            assertThat(exception.type()).isEqualTo(InvitationProblems.INVITATION_INVALID);
            assertThat(exception.detail()).isEqualTo("This invitation link is invalid or has expired.");
        });
    }

    private static void assertConflict(ThrowingCallable call, URI type) {
        assertThatThrownBy(call)
                .isInstanceOfSatisfying(
                        ConflictException.class,
                        exception -> assertThat(exception.type()).isEqualTo(type));
    }
}
