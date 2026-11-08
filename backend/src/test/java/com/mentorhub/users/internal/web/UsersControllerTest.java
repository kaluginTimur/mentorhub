package com.mentorhub.users.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mentorhub.support.ControllerSliceTest;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.AccessStatus;
import com.mentorhub.users.internal.domain.DisableReason;
import com.mentorhub.users.internal.domain.ProfileField;
import com.mentorhub.users.internal.domain.RoleFilter;
import com.mentorhub.users.internal.domain.UserChange;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.service.HistoryActor;
import com.mentorhub.users.internal.service.LockoutProblems;
import com.mentorhub.users.internal.service.ManagedUser;
import com.mentorhub.users.internal.service.MeDetails;
import com.mentorhub.users.internal.service.MeService;
import com.mentorhub.users.internal.service.ProfileInput;
import com.mentorhub.users.internal.service.ProfileService;
import com.mentorhub.users.internal.service.ResultPage;
import com.mentorhub.users.internal.service.UserAdministrationService;
import com.mentorhub.users.internal.service.UserHistoryEntry;
import com.mentorhub.users.internal.service.UserListEntry;
import com.mentorhub.users.internal.service.UserProfile;
import com.mentorhub.users.internal.service.UserQuery;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.Problems;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@ControllerSliceTest(UsersController.class)
class UsersControllerTest {

    static final UUID CALLER_ID = UUID.fromString("0b6f1d2e-6a51-4f36-9b0c-5f2d8f3a1c10");
    static final UUID USER_ID = UUID.fromString("5a1c7e2b-3d4f-4e5a-8b6c-7d8e9f0a1b2c");
    static final Instant CREATED_AT = Instant.parse("2026-10-12T09:00:30Z");
    static final String PROFILE_BODY = """
            {"firstName": "Ivan", "lastName": "Sidorov", "phone": "8 701 123 45 67"}""";

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    MeService meService;

    @MockitoBean
    ProfileService profileService;

    @MockitoBean
    UserAdministrationService administration;

    static RequestPostProcessor admin() {
        return TestAuthentications.activeUser(CALLER_ID, Permission.USERS_READ, Permission.USERS_MANAGE);
    }

    static ManagedUser activeUser() {
        return new ManagedUser(
                USER_ID,
                "ivan@example.com",
                "Ivan",
                "Sidorov",
                "+77011234567",
                UserStatus.ACTIVE,
                null,
                null,
                List.of(Role.MENTOR),
                CREATED_AT);
    }

    static ManagedUser disabledUser(@Nullable String comment) {
        return new ManagedUser(
                USER_ID,
                "ivan@example.com",
                null,
                null,
                null,
                UserStatus.DISABLED,
                DisableReason.GRADUATED,
                comment,
                List.of(),
                CREATED_AT);
    }

    static MeDetails activeMe(String phone) {
        return new MeDetails(
                AccessStatus.ACTIVE,
                new UserProfile(
                        CALLER_ID,
                        "anna@example.com",
                        "Anna",
                        "Petrova",
                        phone,
                        List.of(Role.ADMIN, Role.MENTOR),
                        List.of(Permission.USERS_READ, Permission.USERS_MANAGE)));
    }

    MvcTestResult send(HttpMethod method, String uri, String body, RequestPostProcessor caller) {
        MockMvcRequestBuilder request = mvc.method(method).uri(uri).with(caller);
        if (!body.isEmpty()) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    static void assertValidationProblem(MvcTestResult result, String... fields) {
        assertProblem(result, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly((Object[]) fields);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].message")
                .asArray()
                .hasSize(fields.length)
                .allSatisfy(message -> assertThat(message).isInstanceOf(String.class));
    }

    /** Every operation of {@code /api/v1/users/**} with a valid request, and the permission it needs. */
    static Stream<Arguments> userOperations() {
        String user = "/api/v1/users/" + USER_ID;
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/api/v1/users", "", Permission.USERS_READ),
                Arguments.of(HttpMethod.GET, user, "", Permission.USERS_READ),
                Arguments.of(HttpMethod.GET, user + "/history", "", Permission.USERS_READ),
                Arguments.of(
                        HttpMethod.POST, user + "/disable", "{\"reason\": \"GRADUATED\"}", Permission.USERS_MANAGE),
                Arguments.of(HttpMethod.POST, user + "/enable", "", Permission.USERS_MANAGE),
                Arguments.of(HttpMethod.PUT, user + "/roles", "{\"roles\": []}", Permission.USERS_MANAGE),
                Arguments.of(HttpMethod.PUT, user + "/profile", PROFILE_BODY, Permission.USERS_MANAGE));
    }

    static Stream<Arguments> readOperations() {
        return userOperations().filter(arguments -> arguments.get()[3] == Permission.USERS_READ);
    }

    static Stream<Arguments> manageOperations() {
        return userOperations().filter(arguments -> arguments.get()[3] == Permission.USERS_MANAGE);
    }

    @Nested
    class Me {

        // Any active user, without permissions, changes their own profile and gets the new Me.
        @Test
        void updatesTheOwnProfileAndReturnsTheNewMe() {
            given(meService.me()).willReturn(activeMe("+77011234567"));

            MvcTestResult result =
                    send(HttpMethod.PUT, "/api/v1/me/profile", PROFILE_BODY, TestAuthentications.activeUser(CALLER_ID));

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.user.phone").isEqualTo("+77011234567");
            then(profileService).should().updateOwnProfile(new ProfileInput("Ivan", "Sidorov", "8 701 123 45 67"));
        }
    }

    @Nested
    class Authorization {

        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.UsersControllerTest#readOperations")
        @DisplayName("USR-1.1 Reading users needs USERS_READ")
        void callerWithoutUsersReadIsForbiddenToRead(
                HttpMethod method, String uri, String body, Permission permission) {
            MvcTestResult result =
                    send(method, uri, body, TestAuthentications.activeUser(CALLER_ID, Permission.USERS_MANAGE));

            assertProblem(result, HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
            verifyNoInteractions(administration);
        }

        // Reading is not enough to change.
        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.UsersControllerTest#manageOperations")
        @DisplayName("USR-1.2 Changing a user needs USERS_MANAGE")
        void callerWithoutUsersManageIsForbiddenToChange(
                HttpMethod method, String uri, String body, Permission permission) {
            MvcTestResult result =
                    send(method, uri, body, TestAuthentications.activeUser(CALLER_ID, Permission.USERS_READ));

            assertProblem(result, HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
            verifyNoInteractions(administration);
        }
    }

    @Nested
    class ListUsers {

        @Test
        void returnsAPageOfUsersWithDefaults() {
            given(administration.list(any()))
                    .willReturn(new ResultPage<>(
                            List.of(
                                    new UserListEntry(
                                            USER_ID,
                                            "ivan@example.com",
                                            "Ivan",
                                            "Sidorov",
                                            UserStatus.ACTIVE,
                                            List.of(Role.MENTOR, Role.STUDENT)),
                                    new UserListEntry(
                                            CALLER_ID,
                                            "maria@example.com",
                                            null,
                                            null,
                                            UserStatus.DISABLED,
                                            List.of())),
                            0,
                            25,
                            2));

            MvcTestResult result = mvc.get().uri("/api/v1/users").with(admin()).exchange();

            assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                    {
                      "items": [
                        {
                          "id": "5a1c7e2b-3d4f-4e5a-8b6c-7d8e9f0a1b2c",
                          "email": "ivan@example.com",
                          "firstName": "Ivan",
                          "lastName": "Sidorov",
                          "status": "ACTIVE",
                          "roles": ["MENTOR", "STUDENT"]
                        },
                        {
                          "id": "0b6f1d2e-6a51-4f36-9b0c-5f2d8f3a1c10",
                          "email": "maria@example.com",
                          "firstName": null,
                          "lastName": null,
                          "status": "DISABLED",
                          "roles": []
                        }
                      ],
                      "page": 0,
                      "size": 25,
                      "totalItems": 2
                    }
                    """);
            then(administration).should().list(new UserQuery(null, null, null, 0, 25));
        }

        @Test
        void passesSearchFiltersAndPage() {
            given(administration.list(any())).willReturn(new ResultPage<>(List.of(), 2, 100, 0));

            MvcTestResult result = mvc.get()
                    .uri("/api/v1/users?q= ivan &status=DISABLED&role=MENTOR&page=2&size=100")
                    .with(admin())
                    .exchange();

            assertThat(result).hasStatusOk();
            then(administration)
                    .should()
                    .list(new UserQuery(" ivan ", UserStatus.DISABLED, new RoleFilter.WithRole(Role.MENTOR), 2, 100));
        }

        @Test
        void roleNoneFiltersUsersWithoutRoles() {
            given(administration.list(any())).willReturn(new ResultPage<>(List.of(), 0, 25, 0));

            assertThat(mvc.get().uri("/api/v1/users?role=NONE").with(admin()).exchange())
                    .hasStatusOk();

            ArgumentCaptor<UserQuery> query = ArgumentCaptor.forClass(UserQuery.class);
            then(administration).should().list(query.capture());
            assertThat(query.getValue().role()).isEqualTo(new RoleFilter.WithoutRoles());
        }

        @ParameterizedTest
        @ValueSource(strings = {"size=0", "size=101", "page=-1"})
        @DisplayName("PLT-3.3 A page or size out of range is rejected")
        void rejectsPageAndSizeOutOfRange(String parameter) {
            MvcTestResult result =
                    mvc.get().uri("/api/v1/users?" + parameter).with(admin()).exchange();

            assertValidationProblem(result, parameter.substring(0, parameter.indexOf('=')));
            verifyNoInteractions(administration);
        }

        @Test
        void reportsEveryInvalidParameter() {
            MvcTestResult result =
                    mvc.get().uri("/api/v1/users?page=-1&size=0").with(admin()).exchange();

            assertValidationProblem(result, "page", "size");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.errors[1].message")
                    .isEqualTo("must be greater than or equal to 1");
        }

        @ParameterizedTest
        @ValueSource(strings = {"status=active", "status=GONE", "role=SUPERUSER", "size=many"})
        void rejectsUnknownValues(String parameter) {
            MvcTestResult result =
                    mvc.get().uri("/api/v1/users?" + parameter).with(admin()).exchange();

            assertValidationProblem(result, parameter.substring(0, parameter.indexOf('=')));
            verifyNoInteractions(administration);
        }

        @Test
        @DisplayName("PLT-2.4 A query value outside the set lists the allowed values")
        void unknownEnumValueNamesTheAllowedValues() {
            MvcTestResult result =
                    mvc.get().uri("/api/v1/users?status=GONE").with(admin()).exchange();

            assertValidationProblem(result, "status");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.errors[0].message")
                    .isEqualTo("must be one of ACTIVE, DISABLED");
        }
    }

    @Nested
    class GetUser {

        // An active user has neither a reason nor a comment in the JSON.
        @Test
        void returnsAnActiveUser() {
            given(administration.get(USER_ID)).willReturn(activeUser());

            MvcTestResult result =
                    mvc.get().uri("/api/v1/users/" + USER_ID).with(admin()).exchange();

            assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                    {
                      "id": "5a1c7e2b-3d4f-4e5a-8b6c-7d8e9f0a1b2c",
                      "email": "ivan@example.com",
                      "firstName": "Ivan",
                      "lastName": "Sidorov",
                      "phone": "+77011234567",
                      "status": "ACTIVE",
                      "roles": ["MENTOR"],
                      "createdAt": "2026-10-12T09:00:30Z"
                    }
                    """);
        }

        // Required nullable properties stay explicit null.
        @Test
        void returnsADisabledUserWithReasonAndComment() {
            given(administration.get(USER_ID)).willReturn(disabledUser("Finished the spring cohort"));

            MvcTestResult result =
                    mvc.get().uri("/api/v1/users/" + USER_ID).with(admin()).exchange();

            assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                    {
                      "id": "5a1c7e2b-3d4f-4e5a-8b6c-7d8e9f0a1b2c",
                      "email": "ivan@example.com",
                      "firstName": null,
                      "lastName": null,
                      "phone": null,
                      "status": "DISABLED",
                      "disabledReason": "GRADUATED",
                      "disabledComment": "Finished the spring cohort",
                      "roles": [],
                      "createdAt": "2026-10-12T09:00:30Z"
                    }
                    """);
        }

        @Test
        void disabledUserWithoutCommentHasNoComment() {
            given(administration.get(USER_ID)).willReturn(disabledUser(null));

            MvcTestResult result =
                    mvc.get().uri("/api/v1/users/" + USER_ID).with(admin()).exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.disabledReason").isEqualTo("GRADUATED");
            assertThat(result).bodyJson().doesNotHavePath("$.disabledComment");
        }
    }

    @Nested
    class History {

        static final Instant AT = Instant.parse("2026-10-17T10:12:05Z");
        static final UUID ENTRY_ID = UUID.fromString("9e8d7c6b-5a49-4382-9170-6f5e4d3c2b1a");
        static final HistoryActor ACTOR = new HistoryActor(CALLER_ID, "Anna Petrova");
        static final UUID INVITATION_ID = UUID.fromString("5d1c2b3a-4e5f-4a6b-8c7d-9e0f1a2b3c4d");
        static final HistoryActor INVITER =
                new HistoryActor(UUID.fromString("7a6b5c4d-3e2f-4a1b-9c8d-7e6f5a4b3c2d"), "Ada Admin");

        MvcTestResult historyWith(UserHistoryEntry entry) {
            given(administration.history(USER_ID, 0, 25)).willReturn(new ResultPage<>(List.of(entry), 0, 25, 1));
            MvcTestResult result = mvc.get()
                    .uri("/api/v1/users/" + USER_ID + "/history")
                    .with(admin())
                    .exchange();
            assertThat(result).hasStatusOk();
            return result;
        }

        String page(String entry) {
            return """
                    {"items": [%s], "page": 0, "size": 25, "totalItems": 1}
                    """.formatted(entry);
        }

        @Test
        void disabledEntryHasReasonAndComment() {
            MvcTestResult result = historyWith(new UserHistoryEntry(
                    ENTRY_ID, AT, ACTOR, new UserChange.Disabled(DisableReason.GRADUATED, "Finished")));

            assertThat(result).bodyJson().isStrictlyEqualTo(page("""
                    {
                      "id": "9e8d7c6b-5a49-4382-9170-6f5e4d3c2b1a",
                      "occurredAt": "2026-10-17T10:12:05Z",
                      "action": "USER_DISABLED",
                      "actor": {"id": "0b6f1d2e-6a51-4f36-9b0c-5f2d8f3a1c10", "name": "Anna Petrova"},
                      "reason": "GRADUATED",
                      "comment": "Finished"
                    }"""));
        }

        @Test
        void enabledEntryWithoutCommentHasOnlyTheReason() {
            MvcTestResult result = historyWith(
                    new UserHistoryEntry(ENTRY_ID, AT, ACTOR, new UserChange.Enabled(DisableReason.SECURITY, null)));

            assertThat(result).bodyJson().isStrictlyEqualTo(page("""
                    {
                      "id": "9e8d7c6b-5a49-4382-9170-6f5e4d3c2b1a",
                      "occurredAt": "2026-10-17T10:12:05Z",
                      "action": "USER_ENABLED",
                      "actor": {"id": "0b6f1d2e-6a51-4f36-9b0c-5f2d8f3a1c10", "name": "Anna Petrova"},
                      "reason": "SECURITY"
                    }"""));
        }

        @Test
        @DisplayName("An entry has its time, action, actor and details")
        void rolesChangedEntryListsAddedAndRemovedRoles() {
            MvcTestResult result = historyWith(new UserHistoryEntry(
                    ENTRY_ID, AT, ACTOR, new UserChange.RolesChanged(List.of("MENTOR"), List.of())));

            assertThat(result).bodyJson().isStrictlyEqualTo(page("""
                    {
                      "id": "9e8d7c6b-5a49-4382-9170-6f5e4d3c2b1a",
                      "occurredAt": "2026-10-17T10:12:05Z",
                      "action": "USER_ROLES_CHANGED",
                      "actor": {"id": "0b6f1d2e-6a51-4f36-9b0c-5f2d8f3a1c10", "name": "Anna Petrova"},
                      "rolesAdded": ["MENTOR"],
                      "rolesRemoved": []
                    }"""));
        }

        // Old and new values are required nullable, so a missing one is null.
        @Test
        void profileEntryListsTheChangedFields() {
            MvcTestResult result = historyWith(new UserHistoryEntry(
                    ENTRY_ID,
                    AT,
                    ACTOR,
                    new UserChange.ProfileUpdated(List.of(
                            new UserChange.FieldChange(ProfileField.PHONE, "+77011234567", "+77017654321"),
                            new UserChange.FieldChange(ProfileField.LAST_NAME, null, "Petrova")))));

            assertThat(result).bodyJson().isStrictlyEqualTo(page("""
                    {
                      "id": "9e8d7c6b-5a49-4382-9170-6f5e4d3c2b1a",
                      "occurredAt": "2026-10-17T10:12:05Z",
                      "action": "USER_PROFILE_UPDATED",
                      "actor": {"id": "0b6f1d2e-6a51-4f36-9b0c-5f2d8f3a1c10", "name": "Anna Petrova"},
                      "changes": [
                        {"field": "phone", "oldValue": "+77011234567", "newValue": "+77017654321"},
                        {"field": "lastName", "oldValue": null, "newValue": "Petrova"}
                      ]
                    }"""));
        }

        // A change made by the system has an explicit null actor.
        @Test
        @DisplayName("A change by the system has no actor")
        void bootstrapEntryHasANullActorAndWhatTheBootstrapDid() {
            MvcTestResult result = historyWith(new UserHistoryEntry(
                    ENTRY_ID, AT, null, new UserChange.BootstrappedAdmin(true, true, false, true)));

            assertThat(result).bodyJson().isStrictlyEqualTo(page("""
                    {
                      "id": "9e8d7c6b-5a49-4382-9170-6f5e4d3c2b1a",
                      "occurredAt": "2026-10-17T10:12:05Z",
                      "action": "USER_BOOTSTRAPPED_ADMIN",
                      "actor": null,
                      "bootstrap": {"userCreated": true, "identityLinked": true, "enabled": false, "adminAdded": true}
                    }"""));
        }

        @Test
        void invitationAcceptedEntryHasTheInvitationAndTheRoles() {
            MvcTestResult result = historyWith(new UserHistoryEntry(
                    ENTRY_ID,
                    AT,
                    ACTOR,
                    new UserChange.InvitationAccepted(
                            INVITATION_ID, INVITER.id(), false, true, true, List.of("MENTOR"), List.of("ADMIN")),
                    INVITER));

            assertThat(result).bodyJson().isStrictlyEqualTo(page("""
                    {
                      "id": "9e8d7c6b-5a49-4382-9170-6f5e4d3c2b1a",
                      "occurredAt": "2026-10-17T10:12:05Z",
                      "action": "USER_INVITATION_ACCEPTED",
                      "actor": {"id": "0b6f1d2e-6a51-4f36-9b0c-5f2d8f3a1c10", "name": "Anna Petrova"},
                      "invitation": {
                        "invitationId": "5d1c2b3a-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
                        "invitedBy": {"id": "7a6b5c4d-3e2f-4a1b-9c8d-7e6f5a4b3c2d", "name": "Ada Admin"},
                        "userCreated": false,
                        "identityLinked": true,
                        "enabled": true
                      },
                      "rolesAdded": ["MENTOR"],
                      "rolesRemoved": ["ADMIN"]
                    }"""));
        }

        @Test
        void invitationAcceptedEntryWithAnUnresolvedInviterHasANullInviter() {
            MvcTestResult result = historyWith(new UserHistoryEntry(
                    ENTRY_ID,
                    AT,
                    ACTOR,
                    new UserChange.InvitationAccepted(
                            INVITATION_ID, INVITER.id(), true, true, false, List.of(), List.of()),
                    null));

            assertThat(result).bodyJson().isStrictlyEqualTo(page("""
                    {
                      "id": "9e8d7c6b-5a49-4382-9170-6f5e4d3c2b1a",
                      "occurredAt": "2026-10-17T10:12:05Z",
                      "action": "USER_INVITATION_ACCEPTED",
                      "actor": {"id": "0b6f1d2e-6a51-4f36-9b0c-5f2d8f3a1c10", "name": "Anna Petrova"},
                      "invitation": {
                        "invitationId": "5d1c2b3a-4e5f-4a6b-8c7d-9e0f1a2b3c4d",
                        "invitedBy": null,
                        "userCreated": true,
                        "identityLinked": true,
                        "enabled": false
                      },
                      "rolesAdded": [],
                      "rolesRemoved": []
                    }"""));
        }

        @Test
        void passesThePage() {
            given(administration.history(USER_ID, 3, 10)).willReturn(new ResultPage<>(List.of(), 3, 10, 30));

            MvcTestResult result = mvc.get()
                    .uri("/api/v1/users/" + USER_ID + "/history?page=3&size=10")
                    .with(admin())
                    .exchange();

            assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                    {"items": [], "page": 3, "size": 10, "totalItems": 30}
                    """);
        }

        @ParameterizedTest
        @ValueSource(strings = {"size=0", "size=101", "page=-1"})
        void rejectsPageAndSizeOutOfRange(String parameter) {
            MvcTestResult result = mvc.get()
                    .uri("/api/v1/users/" + USER_ID + "/history?" + parameter)
                    .with(admin())
                    .exchange();

            assertValidationProblem(result, parameter.substring(0, parameter.indexOf('=')));
            verifyNoInteractions(administration);
        }
    }

    @Nested
    class Disable {

        static final String URI = "/api/v1/users/" + USER_ID + "/disable";

        @Test
        void disablesWithReasonAndComment() {
            given(administration.disable(USER_ID, DisableReason.GRADUATED, "  Finished  "))
                    .willReturn(disabledUser("Finished"));

            MvcTestResult result =
                    send(HttpMethod.POST, URI, "{\"reason\": \"GRADUATED\", \"comment\": \"  Finished  \"}", admin());

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("DISABLED");
            assertThat(result).bodyJson().extractingPath("$.disabledComment").isEqualTo("Finished");
        }

        // The comment is optional.
        @Test
        void commentMayBeMissing() {
            given(administration.disable(USER_ID, DisableReason.SECURITY, null)).willReturn(disabledUser(null));

            assertThat(send(HttpMethod.POST, URI, "{\"reason\": \"SECURITY\"}", admin()))
                    .hasStatusOk();
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "{}",
                    "{\"reason\": null}",
                    "{\"reason\": \"RETIRED\"}",
                    "{\"reason\": \"graduated\"}",
                    "{\"reason\": 7}"
                })
        void missingOrUnknownReasonIsAValidationProblem(String body) {
            MvcTestResult result = send(HttpMethod.POST, URI, body, admin());

            assertValidationProblem(result, "reason");
            verifyNoInteractions(administration);
        }

        @Test
        void unknownReasonNamesTheAllowedValues() {
            MvcTestResult result = send(HttpMethod.POST, URI, "{\"reason\": \"RETIRED\"}", admin());

            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.errors[0].message")
                    .isEqualTo("must be one of GRADUATED, EXPELLED, LEFT_PROGRAM, STAFF_OFFBOARDED, SECURITY, OTHER");
        }

        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.UsersControllerTest#lockoutProblems")
        void lockoutIsAConflict(URI type, String detail) {
            given(administration.disable(any(), any(), any())).willThrow(new ConflictException(type, detail));

            MvcTestResult result = send(HttpMethod.POST, URI, "{\"reason\": \"GRADUATED\"}", admin());

            assertProblem(result, HttpStatus.CONFLICT, type);
            assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(detail);
        }

        @Test
        @DisplayName("PLT-2.3 A body that is not JSON is a plain bad request")
        void bodyThatIsNotJsonIsABadRequest() {
            MvcTestResult result = send(HttpMethod.POST, URI, "{\"reason\": ", admin());

            assertProblem(result, HttpStatus.BAD_REQUEST, Problems.ABOUT_BLANK);
            assertThat(result).bodyJson().doesNotHavePath("$.errors");
            verifyNoInteractions(administration);
        }
    }

    static Stream<Arguments> lockoutProblems() {
        return Stream.of(
                Arguments.of(LockoutProblems.SELF_LOCKOUT, "You can't lock yourself out."),
                Arguments.of(LockoutProblems.LAST_ADMIN, "MentorHub must keep at least one active administrator."));
    }

    @Nested
    class Enable {

        @Test
        void enablesAndReturnsTheUser() {
            given(administration.enable(USER_ID)).willReturn(activeUser());

            MvcTestResult result = send(HttpMethod.POST, "/api/v1/users/" + USER_ID + "/enable", "", admin());

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
            assertThat(result).bodyJson().doesNotHavePath("$.disabledReason");
        }
    }

    @Nested
    class SetRoles {

        static final String URI = "/api/v1/users/" + USER_ID + "/roles";

        @Test
        void setsTheRequestedRoles() {
            given(administration.setRoles(USER_ID, Set.of(Role.MENTOR, Role.STUDENT)))
                    .willReturn(activeUser());

            MvcTestResult result =
                    send(HttpMethod.PUT, URI, "{\"roles\": [\"STUDENT\", \"MENTOR\", \"MENTOR\"]}", admin());

            assertThat(result).hasStatusOk();
            then(administration).should().setRoles(USER_ID, Set.of(Role.MENTOR, Role.STUDENT));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "{}",
                    "{\"roles\": null}",
                    "{\"roles\": [null]}",
                    "{\"roles\": [\"SUPERUSER\"]}",
                    "{\"roles\": \"ADMIN\"}"
                })
        void invalidRolesAreAValidationProblem(String body) {
            MvcTestResult result = send(HttpMethod.PUT, URI, body, admin());

            assertValidationProblem(result, "roles");
            verifyNoInteractions(administration);
        }

        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.UsersControllerTest#lockoutProblems")
        void lockoutIsAConflict(URI type, String detail) {
            given(administration.setRoles(any(), any())).willThrow(new ConflictException(type, detail));

            MvcTestResult result = send(HttpMethod.PUT, URI, "{\"roles\": [\"MENTOR\"]}", admin());

            assertProblem(result, HttpStatus.CONFLICT, type);
            assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(detail);
        }
    }

    @Nested
    class UpdateProfile {

        static final String URI = "/api/v1/users/" + USER_ID + "/profile";

        @Test
        void updatesTheProfile() {
            given(administration.updateProfile(USER_ID, new ProfileInput("Ivan", "Sidorov", "8 701 123 45 67")))
                    .willReturn(activeUser());

            MvcTestResult result = send(HttpMethod.PUT, URI, PROFILE_BODY, admin());

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.phone").isEqualTo("+77011234567");
        }

        // Null and missing properties both mean none.
        @Test
        void nullValuesArePassedOn() {
            given(administration.updateProfile(any(), any())).willReturn(activeUser());

            assertThat(send(HttpMethod.PUT, URI, "{\"firstName\": null, \"lastName\": null, \"phone\": null}", admin()))
                    .hasStatusOk();
            then(administration).should().updateProfile(USER_ID, new ProfileInput(null, null, null));
        }

        @Test
        void nameThatIsNotAStringIsAValidationProblem() {
            MvcTestResult result =
                    send(HttpMethod.PUT, URI, "{\"firstName\": {}, \"lastName\": null, \"phone\": null}", admin());

            assertValidationProblem(result, "firstName");
            verifyNoInteractions(administration);
        }
    }
}
