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
import org.junit.jupiter.api.DisplayName;
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

        @Test
        void showsTheEmailAndExpiryOfAValidLink() {
            Invited invited = invite("Ivan@x.test", Role.MENTOR);
            SecurityContextHolder.clearContext();

            PreviewedInvitation preview = service.preview(invited.token());

            assertThat(preview)
                    .isEqualTo(new PreviewedInvitation(
                            "Ivan@x.test", invited.details().expiresAt()));
        }

        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.invitation.InvitationAcceptanceServiceTests#invalidTokens")
        @DisplayName("INV-10.2 Every invalid link is previewed alike")
        void anInvalidLinkIsTheSameNotFoundInEveryCase(Function<InvitationAcceptanceServiceTests, String> token) {
            String value = token.apply(InvitationAcceptanceServiceTests.this);
            SecurityContextHolder.clearContext();

            assertInvalid(() -> service.preview(value));
        }
    }

    @Nested
    class Accepting {

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

        private UUID formerWithInvitation(Invited[] invitedOut) {
            UUID former = users.create("ivan@x.test", UserStatus.DISABLED, Role.ADMIN, Role.STUDENT);
            jdbc.sql("update users set disabled_comment = 'Left the program' where id = :id")
                    .param("id", former)
                    .update();
            jdbc.sql("""
                            insert into audit_log (actor_user_id, action, subject_type, subject_id, details)
                            values (:actor, 'USER_DISABLED', 'USER', :subject, '{}'::jsonb)
                            """).param("actor", admin).param("subject", former).update();
            jdbc.sql("insert into roles (name) values ('AUDITOR') on conflict do nothing")
                    .update();
            users.assignStoredRole(former, "AUDITOR");
            invitedOut[0] = invite("ivan@x.test", Role.MENTOR, Role.STUDENT);
            signIn(AccessStatus.NOT_INVITED, null, "ivan@x.test", true);
            return former;
        }

        @Test
        @DisplayName("INV-12.1 A disabled user is reactivated")
        void reactivatesADisabledUser() {
            Invited[] invitedOut = new Invited[1];
            UUID former = formerWithInvitation(invitedOut);
            Invited invited = invitedOut[0];

            MeDetails me = service.accept(invited.token());

            assertThat(me.status()).isEqualTo(AccessStatus.ACTIVE);
            assertThat(me.user()).isNotNull();
            assertThat(me.user().id()).isEqualTo(former);
            assertThat(users.status(former)).isEqualTo("ACTIVE");
            assertThat(disableReason(former)).isEmpty();
            assertThat(jdbc.sql("select disabled_comment from users where id = :id")
                            .param("id", former)
                            .query(String.class)
                            .optional())
                    .isEmpty();
            // The profile stays unchanged: names from the token are not applied.
            assertThat(me.user().firstName()).isEqualTo("Anna");
            assertThat(linkedUser(SUBJECT)).isEqualTo(former);
            assertAccepted(invited, former);
            assertThat(data.auditEntries(former))
                    .extracting(AuditRow::action)
                    .containsExactly("USER_DISABLED", "USER_INVITATION_ACCEPTED");
            assertThat(data.auditEntries(former))
                    .extracting(AuditRow::details)
                    .contains(acceptance(invited, false, true, true, List.of("MENTOR"), List.of("ADMIN")));
        }

        @Test
        @DisplayName("INV-12.4 A reactivated user has exactly the roles of the invitation")
        void reactivatedUserHasExactlyTheInvitationsRoles() {
            Invited[] invitedOut = new Invited[1];
            UUID former = formerWithInvitation(invitedOut);

            service.accept(invitedOut[0].token());

            assertThat(users.roles(former)).containsExactly("AUDITOR", "MENTOR", "STUDENT");
        }

        // A disabled caller whose identity is already linked to that user.
        @Test
        @DisplayName("INV-12.5 An identity that was linked already is not linked twice")
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

        // A user who became active since the invitation was created only gains roles.
        @Test
        @DisplayName("INV-12.3 An active user only gains roles")
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

        // A stored role the code does not know is skipped on acceptance.
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

        @Test
        @DisplayName("INV-11.2 The email is compared ignoring case")
        void emailIsComparedIgnoringCase() {
            Invited invited = invite("Anna@Example.com", Role.STUDENT);
            signIn(AccessStatus.NOT_INVITED, null, "anna@example.com", true);

            service.accept(invited.token());

            UUID userId = userId("anna@example.com");
            assertAccepted(invited, userId);
            assertThat(users.roles(userId)).containsExactly("STUDENT");
        }

        @Test
        @DisplayName("INV-11.5 An unknown stored role is skipped")
        void unknownStoredRoleIsSkipped() {
            Invited invited = invite("Anna@Example.com", Role.STUDENT);
            data.addStoredRole(invited.details().id(), "AUDITOR");
            signIn(AccessStatus.NOT_INVITED, null, "anna@example.com", true);

            service.accept(invited.token());

            UUID userId = userId("anna@example.com");
            assertAccepted(invited, userId);
            assertThat(users.roles(userId)).containsExactly("STUDENT");
        }

        @Test
        @DisplayName("INV-12.2 A second identity is linked to the user")
        void aSecondIdentityIsLinkedToTheUser() {
            UUID user = users.create("ivan@x.test", UserStatus.DISABLED);
            users.link(user, "first-subject");
            Invited invited = invite("ivan@x.test", Role.MENTOR);
            signIn(AccessStatus.NOT_INVITED, null, "ivan@x.test", true);

            service.accept(invited.token());

            assertThat(linkedUser("first-subject")).isEqualTo(user);
            assertThat(linkedUser(SUBJECT)).isEqualTo(user);
            assertThat(users.count("user_identities")).isEqualTo(2);
        }

        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.invitation.InvitationAcceptanceServiceTests#mismatchedEmails")
        void refusesAnUnverifiedOrDifferentEmail(@Nullable String email, boolean verified) {
            Invited invited = invite("ivan@x.test", Role.MENTOR);
            signIn(AccessStatus.NOT_INVITED, null, email, verified);

            assertConflict(() -> service.accept(invited.token()), InvitationProblems.INVITATION_EMAIL_MISMATCH);
            assertNothingChanged(invited);
        }

        @Test
        @DisplayName("INV-13.2 An identity linked to another user is refused")
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

        // When no user has the invited email, a linked identity is linked elsewhere too.
        @Test
        @DisplayName("INV-13.2 An identity linked to a user while no user has the email is refused")
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

        @Test
        @DisplayName("INV-14.1 The same user accepts again")
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

        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.invitation.InvitationAcceptanceServiceTests#invalidTokens")
        @DisplayName("INV-10.2 Every invalid link is accepted alike")
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

    /** Tokens that are invalid for every reason; each builds its own state. */
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
