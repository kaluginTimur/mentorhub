package com.mentorhub.users.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.DisableReason;
import com.mentorhub.users.internal.domain.ProfileField;
import com.mentorhub.users.internal.domain.RoleFilter;
import com.mentorhub.users.internal.domain.UserChange;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.FieldViolation;
import com.mentorhub.web.NotFoundException;
import com.mentorhub.web.ValidationException;
import java.net.URI;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The service against a real database, as the caller set in the security context. Data is committed. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserAdministrationServiceTests {

    static final DisableReason GRADUATED = DisableReason.GRADUATED;

    @Autowired
    UserAdministrationService service;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    TransactionTemplate transactions;

    TestUsers testUsers;

    /** The calling administrator. */
    UUID admin;

    @BeforeEach
    void setUp() {
        testUsers = new TestUsers(jdbc);
        testUsers.deleteAll();
        admin = user("admin@x.test", "Ada", "Admin", UserStatus.ACTIVE, Role.ADMIN);
        actAs(admin, Permission.USERS_READ, Permission.USERS_MANAGE);
    }

    @AfterEach
    void clearCaller() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    class Listing {

        // AC-1
        @Test
        void sortsByLastFirstNameAndEmailIgnoringCaseWithMissingNamesLast() {
            UUID ivan = user("b@x.test", "Ivan", "sidorov", UserStatus.ACTIVE);
            UUID anna = user("a@x.test", "anna", "Sidorov", UserStatus.ACTIVE);
            UUID petrova = user("c@x.test", null, "Petrova", UserStatus.ACTIVE);
            UUID zed = user("d@x.test", "Zed", null, UserStatus.ACTIVE);
            UUID nameless = user("e@x.test", null, null, UserStatus.ACTIVE);
            UUID otherAnna = user("f@x.test", "Anna", "SIDOROV", UserStatus.DISABLED);

            ResultPage<UserListEntry> page = service.list(query(null, null, null, 0, 25));

            assertThat(page.items())
                    .extracting(UserListEntry::id)
                    .containsExactly(admin, petrova, anna, otherAnna, ivan, zed, nameless);
            assertThat(page.totalItems()).isEqualTo(7);
            assertThat(page.items().getFirst())
                    .isEqualTo(new UserListEntry(
                            admin, "admin@x.test", "Ada", "Admin", UserStatus.ACTIVE, List.of(Role.ADMIN)));
        }

        // AC-1
        @Test
        void returnsTheRequestedPageWithTheTotal() {
            user("b@x.test", "B", "B", UserStatus.ACTIVE);
            UUID c = user("c@x.test", "C", "C", UserStatus.ACTIVE);
            UUID d = user("d@x.test", "D", "D", UserStatus.ACTIVE);

            ResultPage<UserListEntry> page = service.list(query(null, null, null, 1, 2));

            assertThat(page.items()).extracting(UserListEntry::id).containsExactly(c, d);
            assertThat(page.page()).isEqualTo(1);
            assertThat(page.size()).isEqualTo(2);
            assertThat(page.totalItems()).isEqualTo(4);
        }

        // AC-2
        @Test
        void searchesEmailAndNamesIgnoringCaseAfterTrimming() {
            UUID byLastName = user("b@x.test", "Ivan", "Sidorov", UserStatus.ACTIVE);
            UUID byFirstName = user("c@x.test", "Sidor", "Ivanov", UserStatus.ACTIVE);
            UUID byEmail = user("SIDOROVA@x.test", null, null, UserStatus.ACTIVE);
            user("d@x.test", "Maria", "Petrova", UserStatus.ACTIVE);

            ResultPage<UserListEntry> page = service.list(query("  sIdOr \t", null, null, 0, 25));

            assertThat(page.items())
                    .extracting(UserListEntry::id)
                    .containsExactlyInAnyOrder(byLastName, byFirstName, byEmail);
            assertThat(page.totalItems()).isEqualTo(3);
        }

        // AC-2
        @Test
        void wildcardsMatchThemselves() {
            UUID percent = user("100%@x.test", null, null, UserStatus.ACTIVE);
            UUID underscore = user("under_score@x.test", null, null, UserStatus.ACTIVE);
            UUID backslash = user("back\\slash@x.test", null, null, UserStatus.ACTIVE);
            user("plain@x.test", null, null, UserStatus.ACTIVE);

            assertThat(ids(service.list(query("%", null, null, 0, 25)))).containsExactly(percent);
            assertThat(ids(service.list(query("_", null, null, 0, 25)))).containsExactly(underscore);
            assertThat(ids(service.list(query("\\", null, null, 0, 25)))).containsExactly(backslash);
        }

        // AC-2
        @Test
        void filtersByStatusAndRoleCombinedWithAnd() {
            UUID activeMentor = user("b@x.test", "B", "B", UserStatus.ACTIVE, Role.MENTOR);
            UUID disabledMentor = user("c@x.test", "C", "C", UserStatus.DISABLED, Role.MENTOR, Role.STUDENT);
            UUID disabledNoRoles = user("d@x.test", "D", "D", UserStatus.DISABLED);
            UUID unknownRoleOnly = user("e@x.test", "E", "E", UserStatus.ACTIVE);
            storeUnknownRole(unknownRoleOnly);

            assertThat(ids(service.list(query(null, UserStatus.DISABLED, null, 0, 25))))
                    .containsExactly(disabledMentor, disabledNoRoles);
            assertThat(ids(service.list(query(null, null, new RoleFilter.WithRole(Role.MENTOR), 0, 25))))
                    .containsExactly(activeMentor, disabledMentor);
            assertThat(ids(service.list(query(null, UserStatus.ACTIVE, new RoleFilter.WithRole(Role.MENTOR), 0, 25))))
                    .containsExactly(activeMentor);
            assertThat(ids(service.list(query(null, null, new RoleFilter.WithoutRoles(), 0, 25))))
                    .containsExactly(disabledNoRoles, unknownRoleOnly);
            assertThat(ids(service.list(query("E", UserStatus.ACTIVE, new RoleFilter.WithoutRoles(), 0, 25))))
                    .containsExactly(unknownRoleOnly);
        }

        // AC-12: an unknown stored role is never reported
        @Test
        void listsOnlyKnownRoles() {
            UUID mentor = user("b@x.test", "B", "B", UserStatus.ACTIVE, Role.MENTOR);
            storeUnknownRole(mentor);

            assertThat(service.list(query("b@x", null, null, 0, 25))
                            .items()
                            .getFirst()
                            .roles())
                    .containsExactly(Role.MENTOR);
        }

        // AC-3
        @Test
        void queryMayHave100CharactersAfterTrimming() {
            assertThatCode(() -> service.list(query("  " + "q".repeat(100) + "  ", null, null, 0, 25)))
                    .doesNotThrowAnyException();
            assertValidationErrors(
                    () -> service.list(query("q".repeat(101), null, null, 0, 25)),
                    new FieldViolation("q", "must be at most 100 characters"));
        }

        // AC-3
        @Test
        void rejectsInvalidPagesWithoutClamping() {
            assertValidationErrors(
                    () -> service.list(query(null, null, null, -1, 101)),
                    new FieldViolation("page", "must be greater than or equal to 0"),
                    new FieldViolation("size", "must be between 1 and 100"));
            assertValidationErrors(
                    () -> service.list(query(null, null, null, 0, 0)),
                    new FieldViolation("size", "must be between 1 and 100"));
        }
    }

    @Nested
    class Reading {

        // AC-4
        @Test
        void returnsTheDetailsOfADisabledUser() {
            UUID user = user("b@x.test", "Ivan", "Sidorov", UserStatus.ACTIVE, Role.STUDENT, Role.MENTOR);
            storeUnknownRole(user);
            jdbc.sql("update users set phone = '+77011234567', status = 'DISABLED', disabled_reason = 'GRADUATED',"
                            + " disabled_comment = 'Finished' where id = :id")
                    .param("id", user)
                    .update();

            ManagedUser details = service.get(user);

            assertThat(details)
                    .usingRecursiveComparison()
                    .ignoringFields("createdAt")
                    .isEqualTo(new ManagedUser(
                            user,
                            "b@x.test",
                            "Ivan",
                            "Sidorov",
                            "+77011234567",
                            UserStatus.DISABLED,
                            DisableReason.GRADUATED,
                            "Finished",
                            List.of(Role.MENTOR, Role.STUDENT),
                            details.createdAt()));
            assertThat(details.createdAt()).isNotNull();
        }

        // AC-4
        @Test
        void anActiveUserHasNoReason() {
            ManagedUser details = service.get(admin);

            assertThat(details.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(details.disabledReason()).isNull();
            assertThat(details.disabledComment()).isNull();
            assertThat(details.phone()).isNull();
        }

        // AC-4
        @Test
        void unknownIdIsNotFound() {
            assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> service.history(UUID.randomUUID(), 0, 25)).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Disabling {

        // AC-6
        @Test
        void disablesWithTheReasonAndTrimmedCommentAndAudits() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE, Role.MENTOR);

            ManagedUser disabled = service.disable(user, GRADUATED, "  Finished the spring cohort \n");

            assertThat(disabled.status()).isEqualTo(UserStatus.DISABLED);
            assertThat(disabled.disabledReason()).isEqualTo(GRADUATED);
            assertThat(disabled.disabledComment()).isEqualTo("Finished the spring cohort");
            assertThat(disabled.roles()).containsExactly(Role.MENTOR);
            assertThat(testUsers.status(user)).isEqualTo("DISABLED");
            assertThat(auditEntries(user))
                    .containsExactly(new Entry(
                            "USER_DISABLED",
                            admin,
                            Map.of("reason", "GRADUATED", "comment", "Finished the spring cohort")));
        }

        // AC-6
        @Test
        void blankCommentIsStoredAsNull() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE);

            ManagedUser disabled = service.disable(user, DisableReason.SECURITY, "   ");

            assertThat(disabled.disabledComment()).isNull();
            assertThat(auditEntries(user))
                    .containsExactly(new Entry("USER_DISABLED", admin, Map.of("reason", "SECURITY")));
        }

        // AC-7
        @Test
        void otherRequiresAComment() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE);

            for (String blank : new String[] {null, "", "  "}) {
                assertValidationErrors(
                        () -> service.disable(user, DisableReason.OTHER, blank),
                        new FieldViolation("comment", "must not be blank when the reason is OTHER"));
            }
            assertUnchanged(user);
        }

        // AC-7
        @Test
        void commentMayHave500CharactersAfterTrimming() {
            UUID tooLong = user("b@x.test", "B", "B", UserStatus.ACTIVE);
            UUID longest = user("c@x.test", "C", "C", UserStatus.ACTIVE);

            assertValidationErrors(
                    () -> service.disable(tooLong, GRADUATED, "c".repeat(501)),
                    new FieldViolation("comment", "must be at most 500 characters"));
            assertUnchanged(tooLong);

            ManagedUser disabled = service.disable(longest, DisableReason.OTHER, "  " + "c".repeat(500) + "  ");
            assertThat(disabled.disabledComment()).hasSize(500);
        }

        // AC-9
        @Test
        void disablingADisabledUserChangesNothing() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE);
            service.disable(user, GRADUATED, "First");

            ManagedUser again = service.disable(user, DisableReason.SECURITY, "Second");

            assertThat(again.disabledReason()).isEqualTo(GRADUATED);
            assertThat(again.disabledComment()).isEqualTo("First");
            assertThat(auditEntries(user)).hasSize(1);
        }

        // AC-13
        @Test
        void administratorCannotDisableThemselves() {
            user("b@x.test", "B", "B", UserStatus.ACTIVE, Role.ADMIN);

            assertConflict(() -> service.disable(admin, GRADUATED, null), LockoutProblems.SELF_LOCKOUT);
            assertUnchanged(admin);
        }

        // AC-14: unreachable through the API (only an administrator has USERS_MANAGE), but the rule holds alone.
        @Test
        void lastActiveAdministratorCannotBeDisabled() {
            UUID manager = user("manager@x.test", "M", "M", UserStatus.ACTIVE);
            user("former@x.test", "F", "F", UserStatus.DISABLED, Role.ADMIN);
            actAs(manager, Permission.USERS_MANAGE);

            assertConflict(() -> service.disable(admin, GRADUATED, null), LockoutProblems.LAST_ADMIN);
            assertUnchanged(admin);
        }

        @Test
        void anotherAdministratorCanBeDisabledWhileOneRemains() {
            UUID other = user("b@x.test", "B", "B", UserStatus.ACTIVE, Role.ADMIN);

            assertThat(service.disable(other, GRADUATED, null).status()).isEqualTo(UserStatus.DISABLED);
        }

        @Test
        void unknownIdIsNotFound() {
            assertThatThrownBy(() -> service.disable(UUID.randomUUID(), GRADUATED, null))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class Enabling {

        // AC-8
        @Test
        void enablesClearsTheReasonAndAuditsWhatItWas() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE, Role.STUDENT);
            service.disable(user, DisableReason.LEFT_PROGRAM, "Moved abroad");

            ManagedUser enabled = service.enable(user);

            assertThat(enabled.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(enabled.disabledReason()).isNull();
            assertThat(enabled.disabledComment()).isNull();
            assertThat(enabled.roles()).containsExactly(Role.STUDENT);
            assertThat(jdbc.sql("select disabled_reason is null and disabled_comment is null from users where id = :id")
                            .param("id", user)
                            .query(Boolean.class)
                            .single())
                    .isTrue();
            assertThat(auditEntries(user).getLast())
                    .isEqualTo(new Entry(
                            "USER_ENABLED", admin, Map.of("reason", "LEFT_PROGRAM", "comment", "Moved abroad")));
        }

        // AC-9
        @Test
        void enablingAnActiveUserChangesNothing() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE);

            assertThat(service.enable(user).status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(auditEntries(user)).isEmpty();
        }

        @Test
        void unknownIdIsNotFound() {
            assertThatThrownBy(() -> service.enable(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class SettingRoles {

        // AC-10
        @Test
        void storesExactlyTheRequestedRolesAndAuditsTheDifference() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE, Role.MENTOR, Role.STUDENT);

            ManagedUser changed = service.setRoles(user, Set.of(Role.COORDINATOR, Role.MENTOR, Role.OBSERVER));

            assertThat(changed.roles()).containsExactly(Role.COORDINATOR, Role.MENTOR, Role.OBSERVER);
            assertThat(testUsers.roles(user)).containsExactly("COORDINATOR", "MENTOR", "OBSERVER");
            assertThat(auditEntries(user))
                    .containsExactly(new Entry(
                            "USER_ROLES_CHANGED",
                            admin,
                            Map.of(
                                    "rolesAdded",
                                    List.of("COORDINATOR", "OBSERVER"),
                                    "rolesRemoved",
                                    List.of("STUDENT"))));
        }

        // AC-10
        @Test
        void sameRolesWriteNoEntry() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE, Role.MENTOR);

            service.setRoles(user, Set.of(Role.MENTOR));

            assertThat(auditEntries(user)).isEmpty();
        }

        // AC-11
        @Test
        void emptySetLeavesTheUserActiveWithoutRoles() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE, Role.MENTOR);

            ManagedUser changed = service.setRoles(user, Set.of());

            assertThat(changed.roles()).isEmpty();
            assertThat(changed.status()).isEqualTo(UserStatus.ACTIVE);
            assertThat(testUsers.roles(user)).isEmpty();
        }

        // AC-12
        @Test
        void leavesUnknownStoredRolesUntouchedAndUnreported() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE, Role.MENTOR);
            storeUnknownRole(user);

            ManagedUser changed = service.setRoles(user, Set.of(Role.STUDENT));

            assertThat(changed.roles()).containsExactly(Role.STUDENT);
            assertThat(testUsers.roles(user)).containsExactly("AUDITOR", "STUDENT");
            assertThat(auditEntries(user))
                    .containsExactly(new Entry(
                            "USER_ROLES_CHANGED",
                            admin,
                            Map.of("rolesAdded", List.of("STUDENT"), "rolesRemoved", List.of("MENTOR"))));
        }

        // AC-13
        @Test
        void administratorCannotRemoveTheirOwnAdminRole() {
            user("b@x.test", "B", "B", UserStatus.ACTIVE, Role.ADMIN);

            assertConflict(() -> service.setRoles(admin, Set.of(Role.MENTOR)), LockoutProblems.SELF_LOCKOUT);
            assertThat(testUsers.roles(admin)).containsExactly("ADMIN");
            assertThat(auditEntries(admin)).isEmpty();
        }

        @Test
        void administratorMayAddOtherRolesToThemselves() {
            ManagedUser changed = service.setRoles(admin, Set.of(Role.ADMIN, Role.MENTOR));

            assertThat(changed.roles()).containsExactly(Role.ADMIN, Role.MENTOR);
        }

        // AC-14
        @Test
        void lastActiveAdministratorCannotLoseTheRole() {
            UUID manager = user("manager@x.test", "M", "M", UserStatus.ACTIVE);
            actAs(manager, Permission.USERS_MANAGE);

            assertConflict(() -> service.setRoles(admin, Set.of()), LockoutProblems.LAST_ADMIN);
            assertThat(testUsers.roles(admin)).containsExactly("ADMIN");
        }

        // A disabled administrator does not count, so removing the role cannot lock anyone out.
        @Test
        void disabledAdministratorMayLoseTheRole() {
            UUID manager = user("manager@x.test", "M", "M", UserStatus.ACTIVE);
            UUID disabledAdmin = user("former@x.test", "F", "F", UserStatus.DISABLED, Role.ADMIN);
            actAs(manager, Permission.USERS_MANAGE);
            jdbc.sql("delete from user_roles where user_id = :id")
                    .param("id", admin)
                    .update();

            assertThat(service.setRoles(disabledAdmin, Set.of()).roles()).isEmpty();
        }

        @Test
        void unknownIdIsNotFound() {
            assertThatThrownBy(() -> service.setRoles(UUID.randomUUID(), Set.of(Role.ADMIN)))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> service.setRoles(UUID.randomUUID(), Set.of()))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    class EditingProfiles {

        // AC-16, AC-18
        @Test
        void storesNormalizedValuesAndAuditsEachChangedField() {
            UUID user = user("b@x.test", "Anna", "Petrova", UserStatus.ACTIVE);

            ManagedUser changed = service.updateProfile(user, new ProfileInput(" Ivan ", "Petrova", "8 701 123 45 67"));

            assertThat(changed.firstName()).isEqualTo("Ivan");
            assertThat(changed.lastName()).isEqualTo("Petrova");
            assertThat(changed.phone()).isEqualTo("+77011234567");
            assertThat(changed.email()).isEqualTo("b@x.test");
            assertThat(auditEntries(user))
                    .containsExactly(new Entry(
                            "USER_PROFILE_UPDATED",
                            admin,
                            Map.of(
                                    "changes",
                                    List.of(
                                            change("firstName", "Anna", "Ivan"),
                                            change("phone", null, "+77011234567")))));
        }

        // AC-16
        @Test
        void unchangedValuesWriteNoEntry() {
            UUID user = user("b@x.test", "Anna", null, UserStatus.ACTIVE);
            jdbc.sql("update users set phone = '+77011234567' where id = :id")
                    .param("id", user)
                    .update();

            service.updateProfile(user, new ProfileInput("Anna ", "  ", "+7 701 123-45-67"));

            assertThat(auditEntries(user)).isEmpty();
        }

        // AC-18
        @Test
        void blankValuesClearTheFields() {
            UUID user = user("b@x.test", "Anna", "Petrova", UserStatus.ACTIVE);

            ManagedUser changed = service.updateProfile(user, new ProfileInput("", null, " "));

            assertThat(changed.firstName()).isNull();
            assertThat(changed.lastName()).isNull();
        }

        // AC-18
        @Test
        void invalidValuesStoreNothing() {
            UUID user = user("b@x.test", "Anna", "Petrova", UserStatus.ACTIVE);

            assertValidationErrors(
                    () -> service.updateProfile(user, new ProfileInput("a".repeat(101), "Sidorova", "12345")),
                    new FieldViolation("firstName", "must be at most 100 characters"),
                    new FieldViolation("phone", "must be a valid phone number"));

            assertThat(service.get(user).lastName()).isEqualTo("Petrova");
            assertThat(auditEntries(user)).isEmpty();
        }

        @Test
        void unknownIdIsNotFound() {
            assertThatThrownBy(() -> service.updateProfile(UUID.randomUUID(), new ProfileInput(null, null, null)))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension.class)
    class History {

        // AC-19
        @Test
        void returnsTheEntriesNewestFirstWithActorsAndDetails() {
            UUID user = user("b@x.test", "Anna", null, UserStatus.ACTIVE, Role.STUDENT);
            UUID nameless = user("nameless@x.test", null, null, UserStatus.ACTIVE, Role.ADMIN);
            service.updateProfile(user, new ProfileInput("Anna", "Petrova", null));
            service.setRoles(user, Set.of(Role.MENTOR));
            actAs(nameless, Permission.USERS_READ, Permission.USERS_MANAGE);
            service.disable(user, GRADUATED, "Finished");
            service.enable(user);

            ResultPage<UserHistoryEntry> history = service.history(user, 0, 25);

            assertThat(history.totalItems()).isEqualTo(4);
            assertThat(history.items())
                    .extracting(UserHistoryEntry::change)
                    .containsExactly(
                            new UserChange.Enabled(GRADUATED, "Finished"),
                            new UserChange.Disabled(GRADUATED, "Finished"),
                            new UserChange.RolesChanged(List.of("MENTOR"), List.of("STUDENT")),
                            new UserChange.ProfileUpdated(
                                    List.of(new UserChange.FieldChange(ProfileField.LAST_NAME, null, "Petrova"))));
            assertThat(history.items())
                    .extracting(UserHistoryEntry::actor)
                    .containsExactly(
                            new HistoryActor(nameless, "nameless@x.test"),
                            new HistoryActor(nameless, "nameless@x.test"),
                            new HistoryActor(admin, "Ada Admin"),
                            new HistoryActor(admin, "Ada Admin"));
        }

        // AC-19
        @Test
        void pagesTheEntries() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE);
            for (int i = 0; i < 3; i++) {
                storeEntry(user, null, "USER_PROFILE_UPDATED", "{\"changes\": []}", i);
            }

            ResultPage<UserHistoryEntry> second = service.history(user, 1, 2);

            assertThat(second.items()).hasSize(1);
            assertThat(second.page()).isEqualTo(1);
            assertThat(second.size()).isEqualTo(2);
            assertThat(second.totalItems()).isEqualTo(3);
        }

        // Invitations AC-16, AC-17: the inviter is resolved and only known roles remain.
        @Test
        void invitationAcceptedEntryHasTheInviterAndKnownRoles() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE);
            UUID invitation = UUID.fromString("5d1c2b3a-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
            storeEntry(user, user, "USER_INVITATION_ACCEPTED", invitationAccepted(invitation, admin), 0);

            UserHistoryEntry entry = service.history(user, 0, 25).items().getFirst();

            assertThat(entry.actor()).isEqualTo(new HistoryActor(user, "B B"));
            assertThat(entry.invitedBy()).isEqualTo(new HistoryActor(admin, "Ada Admin"));
            assertThat(entry.change())
                    .isEqualTo(new UserChange.InvitationAccepted(
                            invitation, admin, false, true, true, List.of("MENTOR"), List.of("ADMIN")));
        }

        // Invitations AC-16: an inviter that cannot be resolved leaves the entry without one.
        @Test
        void invitationAcceptedEntryWithAnUnknownInviterHasNoInviter() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE);
            UUID unknown = UUID.fromString("00000000-0000-4000-8000-0000000000aa");
            storeEntry(user, user, "USER_INVITATION_ACCEPTED", invitationAccepted(unknown, unknown), 0);

            UserHistoryEntry entry = service.history(user, 0, 25).items().getFirst();

            assertThat(entry.invitedBy()).isNull();
            assertThat(entry.change()).isInstanceOf(UserChange.InvitationAccepted.class);
        }

        // AC-19, AC-20
        @Test
        void bootstrapEntryHasNoActor() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE);
            storeEntry(
                    user,
                    null,
                    "USER_BOOTSTRAPPED_ADMIN",
                    "{\"userCreated\": false, \"identityLinked\": true, \"enabled\": false, \"adminAdded\": true}",
                    0);

            UserHistoryEntry entry = service.history(user, 0, 25).items().getFirst();

            assertThat(entry.actor()).isNull();
            assertThat(entry.change()).isEqualTo(new UserChange.BootstrappedAdmin(false, true, false, true));
        }

        // AC-19
        @Test
        void leavesOutUnknownActionsAndWarnsOncePerAction(CapturedOutput output) {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE);
            String action = "USER_TELEPORTED_" + UUID.randomUUID();
            storeEntry(user, admin, action, "{}", 0);
            storeEntry(user, admin, action, "{}", 1);
            storeEntry(user, admin, "USER_ENABLED", "{\"reason\": \"SECURITY\"}", 2);

            service.history(user, 0, 25);
            ResultPage<UserHistoryEntry> history = service.history(user, 0, 25);

            assertThat(history.items())
                    .extracting(UserHistoryEntry::change)
                    .containsExactly(new UserChange.Enabled(DisableReason.SECURITY, null));
            assertThat(output.getOut()).containsOnlyOnce(action);
        }

        // AC-12 for the history: a role the code does not know is not reported
        @Test
        void leavesOutUnknownRolesOfARoleChange() {
            UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE);
            storeEntry(
                    user,
                    admin,
                    "USER_ROLES_CHANGED",
                    "{\"rolesAdded\": [\"MENTOR\", \"AUDITOR\"], \"rolesRemoved\": [\"AUDITOR\"]}",
                    0);

            assertThat(service.history(user, 0, 25).items().getFirst().change())
                    .isEqualTo(new UserChange.RolesChanged(List.of("MENTOR"), List.of()));
        }

        @Test
        void rejectsInvalidPages() {
            assertValidationErrors(
                    () -> service.history(admin, -1, 0),
                    new FieldViolation("page", "must be greater than or equal to 0"),
                    new FieldViolation("size", "must be between 1 and 100"));
        }
    }

    // AC-21
    @Test
    void failedTransactionStoresNeitherTheChangeNorItsEntry() {
        UUID user = user("b@x.test", "B", "B", UserStatus.ACTIVE, Role.MENTOR);

        transactions.executeWithoutResult(status -> {
            service.disable(user, GRADUATED, "Finished");
            service.setRoles(user, Set.of());
            status.setRollbackOnly();
        });

        assertUnchanged(user);
        assertThat(testUsers.roles(user)).containsExactly("MENTOR");
    }

    // AC-5 at the service: reads need USERS_READ
    @ParameterizedTest
    @MethodSource("reads")
    void readsNeedUsersRead(Consumer<UserAdministrationService> read) {
        UUID caller = user("caller@x.test", "C", "C", UserStatus.ACTIVE, Role.MENTOR);

        actAs(caller);
        assertThatThrownBy(() -> read.accept(service)).isInstanceOf(AccessDeniedException.class);
        actAs(caller, Permission.USERS_MANAGE);
        assertThatThrownBy(() -> read.accept(service)).isInstanceOf(AccessDeniedException.class);
    }

    // AC-5 at the service: changes need USERS_MANAGE, also with USERS_READ
    @ParameterizedTest
    @MethodSource("changes")
    void changesNeedUsersManage(Consumer<UserAdministrationService> change) {
        UUID caller = user("caller@x.test", "C", "C", UserStatus.ACTIVE, Role.MENTOR);

        for (Permission[] permissions : List.of(new Permission[0], new Permission[] {Permission.USERS_READ})) {
            actAs(caller, permissions);
            assertThatThrownBy(() -> change.accept(service)).isInstanceOf(AccessDeniedException.class);
        }
        assertUnchanged(admin);
        assertThat(testUsers.roles(admin)).containsExactly("ADMIN");
        assertThat(service.get(admin).firstName()).isEqualTo("Ada");
    }

    // The permission check runs before the target is looked up, so any id does.
    private static final UUID ANY_ID = UUID.fromString("00000000-0000-4000-8000-0000000000ff");

    static Stream<Arguments> reads() {
        return Stream.of(
                call(service -> service.list(new UserQuery(null, null, null, 0, 25))),
                call(service -> service.get(ANY_ID)),
                call(service -> service.history(ANY_ID, 0, 25)));
    }

    static Stream<Arguments> changes() {
        return Stream.of(
                call(service -> service.disable(ANY_ID, GRADUATED, null)),
                call(service -> service.enable(ANY_ID)),
                call(service -> service.setRoles(ANY_ID, Set.of())),
                call(service -> service.updateProfile(ANY_ID, new ProfileInput("X", "Y", null))));
    }

    private static Arguments call(Consumer<UserAdministrationService> call) {
        return Arguments.of(call);
    }

    record Entry(String action, @Nullable UUID actor, Map<String, Object> details) {}

    private List<Entry> auditEntries(UUID subject) {
        return jdbc.sql("""
                        select action, actor_user_id, details::text as details from audit_log
                        where subject_type = 'USER' and subject_id = :subject
                        order by occurred_at, id
                        """)
                .param("subject", subject)
                .query((rs, rowNum) -> new Entry(
                        rs.getString("action"),
                        rs.getObject("actor_user_id", UUID.class),
                        jsonMapper.readValue(rs.getString("details"), new TypeReference<Map<String, Object>>() {})))
                .list();
    }

    private void storeEntry(UUID subject, @Nullable UUID actor, String action, String details, int minutes) {
        jdbc.sql("""
                        insert into audit_log (occurred_at, actor_user_id, action, subject_type, subject_id, details)
                        values (:at, :actor, :action, 'USER', :subject, cast(:details as jsonb))
                        """)
                .param("at", OffsetDateTime.parse("2026-10-18T10:00:00Z").plusMinutes(minutes))
                .param("actor", actor, Types.OTHER)
                .param("action", action)
                .param("subject", subject)
                .param("details", details)
                .update();
    }

    private static String invitationAccepted(UUID invitationId, UUID invitedBy) {
        return """
                {"invitationId": "%s", "invitedBy": "%s", "userCreated": false, "identityLinked": true,
                 "enabled": true, "rolesAdded": ["MENTOR", "AUDITOR"], "rolesRemoved": ["ADMIN", "AUDITOR"]}
                """.formatted(invitationId, invitedBy);
    }

    private static Map<String, @Nullable Object> change(String field, @Nullable String oldValue, String newValue) {
        var change = new LinkedHashMap<String, @Nullable Object>();
        change.put("field", field);
        change.put("oldValue", oldValue);
        change.put("newValue", newValue);
        return change;
    }

    private UUID user(
            String email, @Nullable String firstName, @Nullable String lastName, UserStatus status, Role... roles) {
        UUID id = jdbc.sql("insert into users (email, first_name, last_name, status, disabled_reason)"
                        + " values (:email, :firstName, :lastName, :status, :reason) returning id")
                .param("email", email)
                .param("firstName", firstName, Types.VARCHAR)
                .param("lastName", lastName, Types.VARCHAR)
                .param("status", status.name())
                .param("reason", status == UserStatus.DISABLED ? "SECURITY" : null, Types.VARCHAR)
                .query(UUID.class)
                .single();
        for (Role role : roles) {
            testUsers.assignStoredRole(id, role.name());
        }
        return id;
    }

    private void storeUnknownRole(UUID userId) {
        jdbc.sql("insert into roles (name) values ('AUDITOR') on conflict do nothing")
                .update();
        testUsers.assignStoredRole(userId, "AUDITOR");
    }

    private void assertUnchanged(UUID userId) {
        assertThat(testUsers.status(userId)).isEqualTo("ACTIVE");
        assertThat(auditEntries(userId)).isEmpty();
    }

    private static List<UUID> ids(ResultPage<UserListEntry> page) {
        return page.items().stream().map(UserListEntry::id).toList();
    }

    private static UserQuery query(
            @Nullable String text, @Nullable UserStatus status, @Nullable RoleFilter role, int page, int size) {
        return new UserQuery(text, status, role, page, size);
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
