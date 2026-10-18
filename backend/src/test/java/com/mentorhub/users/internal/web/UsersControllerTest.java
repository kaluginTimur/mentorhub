package com.mentorhub.users.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.AccessStatus;
import com.mentorhub.users.internal.domain.DisableReason;
import com.mentorhub.users.internal.domain.ProfileField;
import com.mentorhub.users.internal.domain.RoleFilter;
import com.mentorhub.users.internal.domain.UserChange;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.WebSliceSecurity;
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
import com.mentorhub.web.FieldViolation;
import com.mentorhub.web.NotFoundException;
import com.mentorhub.web.Problems;
import com.mentorhub.web.ValidationException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(UsersController.class)
@Import(WebSliceSecurity.class)
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

    @Nested
    class Me {

        // identity-foundation AC-3
        @Test
        void notInvitedCallerGetsOnlyItsStatus() {
            given(meService.me()).willReturn(new MeDetails(AccessStatus.NOT_INVITED, null));

            MvcTestResult result = mvc.get()
                    .uri("/api/v1/me")
                    .with(TestAuthentications.notInvited())
                    .exchange();

            assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"status\": \"NOT_INVITED\"}");
        }

        // identity-foundation AC-4
        @Test
        void disabledUserGetsOnlyItsStatus() {
            given(meService.me()).willReturn(new MeDetails(AccessStatus.DISABLED, null));

            MvcTestResult result = mvc.get()
                    .uri("/api/v1/me")
                    .with(TestAuthentications.disabledUser(CALLER_ID))
                    .exchange();

            assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"status\": \"DISABLED\"}");
        }

        // identity-foundation AC-7
        @Test
        void activeUserWithoutRolesHasEmptyRolesAndPermissions() {
            given(meService.me())
                    .willReturn(new MeDetails(
                            AccessStatus.ACTIVE,
                            new UserProfile(
                                    CALLER_ID, "anna@example.com", "Anna", "Petrova", null, List.of(), List.of())));

            MvcTestResult result = mvc.get()
                    .uri("/api/v1/me")
                    .with(TestAuthentications.activeUser(CALLER_ID))
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.user.roles")
                    .asArray()
                    .isEmpty();
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.user.permissions")
                    .asArray()
                    .isEmpty();
        }

        // identity-foundation AC-9, AC-17
        @Test
        void activeUserGetsProfileWithPhoneRolesAndPermissions() {
            given(meService.me()).willReturn(activeMe("+77011234567"));

            MvcTestResult result = mvc.get()
                    .uri("/api/v1/me")
                    .with(TestAuthentications.activeUser(CALLER_ID, Permission.USERS_READ, Permission.USERS_MANAGE))
                    .exchange();

            assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                    {
                      "status": "ACTIVE",
                      "user": {
                        "id": "0b6f1d2e-6a51-4f36-9b0c-5f2d8f3a1c10",
                        "email": "anna@example.com",
                        "firstName": "Anna",
                        "lastName": "Petrova",
                        "phone": "+77011234567",
                        "roles": ["ADMIN", "MENTOR"],
                        "permissions": ["USERS_READ", "USERS_MANAGE"]
                      }
                    }
                    """);
        }

        @Test
        void missingNamesAndPhoneAreSentAsNull() {
            given(meService.me())
                    .willReturn(new MeDetails(
                            AccessStatus.ACTIVE,
                            new UserProfile(CALLER_ID, "anna@example.com", null, null, null, List.of(), List.of())));

            MvcTestResult result = mvc.get()
                    .uri("/api/v1/me")
                    .with(TestAuthentications.activeUser(CALLER_ID))
                    .exchange();

            assertThat(result).hasStatusOk();
            assertThat(result)
                    .bodyJson()
                    .hasPath("$.user.firstName")
                    .hasPath("$.user.lastName")
                    .hasPath("$.user.phone");
            assertThat(result).bodyJson().extractingPath("$.user.firstName").isNull();
            assertThat(result).bodyJson().extractingPath("$.user.phone").isNull();
        }

        // AC-17: any active user, without permissions, changes their own profile and gets the new Me.
        @Test
        void updatesTheOwnProfileAndReturnsTheNewMe() {
            given(meService.me()).willReturn(activeMe("+77011234567"));

            MvcTestResult result =
                    send(HttpMethod.PUT, "/api/v1/me/profile", PROFILE_BODY, TestAuthentications.activeUser(CALLER_ID));

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.user.phone").isEqualTo("+77011234567");
            then(profileService).should().updateOwnProfile(new ProfileInput("Ivan", "Sidorov", "8 701 123 45 67"));
        }

        // AC-18
        @Test
        void invalidOwnProfileIsAValidationProblem() {
            willThrow(new ValidationException(List.of(
                            new FieldViolation("firstName", "must be at most 100 characters"),
                            new FieldViolation("phone", "must be a valid phone number"))))
                    .given(profileService)
                    .updateOwnProfile(any());

            MvcTestResult result =
                    send(HttpMethod.PUT, "/api/v1/me/profile", PROFILE_BODY, TestAuthentications.activeUser(CALLER_ID));

            assertValidationProblem(result, "firstName", "phone");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.errors[1].message")
                    .isEqualTo("must be a valid phone number");
        }

        // Disabled and not invited callers are refused by the gate, as on every operation but GET /me.
        @Test
        void gateRefusesCallersThatAreNotActive() {
            assertProblem(
                    send(
                            HttpMethod.PUT,
                            "/api/v1/me/profile",
                            PROFILE_BODY,
                            TestAuthentications.disabledUser(CALLER_ID)),
                    HttpStatus.FORBIDDEN,
                    Problems.ACCESS_ENDED);
            assertProblem(
                    send(HttpMethod.PUT, "/api/v1/me/profile", PROFILE_BODY, TestAuthentications.notInvited()),
                    HttpStatus.FORBIDDEN,
                    Problems.NOT_INVITED);
            verifyNoInteractions(profileService);
        }
    }

    @Nested
    class Authorization {

        // AC-5
        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.UsersControllerTest#userOperations")
        void callerWithoutPermissionsIsForbidden(HttpMethod method, String uri, String body, Permission permission) {
            MvcTestResult result = send(method, uri, body, TestAuthentications.activeUser(CALLER_ID));

            assertProblem(result, HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
            verifyNoInteractions(administration);
        }

        // AC-5: reading is not enough to change.
        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.UsersControllerTest#userOperations")
        void callerWithTheOtherPermissionOnlyIsForbidden(
                HttpMethod method, String uri, String body, Permission permission) {
            Permission other = permission == Permission.USERS_READ ? Permission.USERS_MANAGE : Permission.USERS_READ;

            MvcTestResult result = send(method, uri, body, TestAuthentications.activeUser(CALLER_ID, other));

            assertProblem(result, HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
            verifyNoInteractions(administration);
        }

        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.UsersControllerTest#userOperations")
        void disabledCallerIsRefusedByTheGate(HttpMethod method, String uri, String body, Permission permission) {
            assertProblem(
                    send(method, uri, body, TestAuthentications.disabledUser(CALLER_ID)),
                    HttpStatus.FORBIDDEN,
                    Problems.ACCESS_ENDED);
            verifyNoInteractions(administration);
        }

        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.UsersControllerTest#userOperations")
        void notInvitedCallerIsRefusedByTheGate(HttpMethod method, String uri, String body, Permission permission) {
            assertProblem(
                    send(method, uri, body, TestAuthentications.notInvited()),
                    HttpStatus.FORBIDDEN,
                    Problems.NOT_INVITED);
            verifyNoInteractions(administration);
        }

        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.UsersControllerTest#userOperations")
        void requestWithoutTokenIsUnauthorized(HttpMethod method, String uri, String body, Permission permission) {
            MockMvcRequestBuilder request = mvc.method(method).uri(uri);
            if (!body.isEmpty()) {
                request = request.contentType(MediaType.APPLICATION_JSON).content(body);
            }

            assertProblem(request.exchange(), HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
            verifyNoInteractions(administration);
        }
    }

    @Nested
    class ListUsers {

        // AC-1
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

        // AC-2
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

        // AC-2
        @Test
        void roleNoneFiltersUsersWithoutRoles() {
            given(administration.list(any())).willReturn(new ResultPage<>(List.of(), 0, 25, 0));

            assertThat(mvc.get().uri("/api/v1/users?role=NONE").with(admin()).exchange())
                    .hasStatusOk();

            ArgumentCaptor<UserQuery> query = ArgumentCaptor.forClass(UserQuery.class);
            then(administration).should().list(query.capture());
            assertThat(query.getValue().role()).isEqualTo(new RoleFilter.WithoutRoles());
        }

        // AC-3
        @ParameterizedTest
        @ValueSource(strings = {"size=0", "size=101", "page=-1"})
        void rejectsPageAndSizeOutOfRange(String parameter) {
            MvcTestResult result =
                    mvc.get().uri("/api/v1/users?" + parameter).with(admin()).exchange();

            assertValidationProblem(result, parameter.substring(0, parameter.indexOf('=')));
            verifyNoInteractions(administration);
        }

        // AC-3
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

        // AC-3
        @ParameterizedTest
        @ValueSource(strings = {"status=active", "status=GONE", "role=SUPERUSER", "size=many"})
        void rejectsUnknownValues(String parameter) {
            MvcTestResult result =
                    mvc.get().uri("/api/v1/users?" + parameter).with(admin()).exchange();

            assertValidationProblem(result, parameter.substring(0, parameter.indexOf('=')));
            verifyNoInteractions(administration);
        }

        @Test
        void unknownEnumValueNamesTheAllowedValues() {
            MvcTestResult result =
                    mvc.get().uri("/api/v1/users?status=GONE").with(admin()).exchange();

            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.errors[0].message")
                    .isEqualTo("must be one of ACTIVE, DISABLED");
        }

        // AC-3: the service checks q after trimming.
        @Test
        void serviceValidationIsAValidationProblem() {
            given(administration.list(any())).willThrow(ValidationException.of("q", "must be at most 100 characters"));

            MvcTestResult result = mvc.get()
                    .uri("/api/v1/users?q=" + "x".repeat(101))
                    .with(admin())
                    .exchange();

            assertValidationProblem(result, "q");
            assertThat(result).bodyJson().isLenientlyEqualTo("""
                    {
                      "type": "urn:mentorhub:problem:validation-failed",
                      "status": 400,
                      "errors": [{"field": "q", "message": "must be at most 100 characters"}]
                    }
                    """);
        }
    }

    @Nested
    class GetUser {

        // AC-4: an active user has neither a reason nor a comment in the JSON.
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

        // AC-4: required nullable properties stay explicit null.
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

        // AC-4
        @Test
        void disabledUserWithoutCommentHasNoComment() {
            given(administration.get(USER_ID)).willReturn(disabledUser(null));

            MvcTestResult result =
                    mvc.get().uri("/api/v1/users/" + USER_ID).with(admin()).exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.disabledReason").isEqualTo("GRADUATED");
            assertThat(result).bodyJson().doesNotHavePath("$.disabledComment");
        }

        // AC-4
        @Test
        void unknownIdIsNotFound() {
            given(administration.get(USER_ID)).willThrow(new NotFoundException());

            assertProblem(
                    mvc.get().uri("/api/v1/users/" + USER_ID).with(admin()).exchange(),
                    HttpStatus.NOT_FOUND,
                    Problems.NOT_FOUND);
        }
    }

    static Stream<Arguments> operationsWithId() {
        return userOperations().filter(arguments -> ((String) arguments.get()[1]).contains(USER_ID.toString()));
    }

    // AC-3: an {id} that is not a UUID, on every operation that has one.
    @ParameterizedTest
    @MethodSource("operationsWithId")
    void idThatIsNotAUuidIsAValidationProblem(HttpMethod method, String uri, String body, Permission permission) {

        MvcTestResult result = send(method, uri.replace(USER_ID.toString(), "not-a-uuid"), body, admin());

        assertValidationProblem(result, "id");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("must be a UUID");
        verifyNoInteractions(administration);
    }

    // AC-4, AC-19: unknown ids respond 404 on every operation that has one.
    @ParameterizedTest
    @MethodSource("operationsWithId")
    void unknownIdIsNotFoundOnEveryOperation(HttpMethod method, String uri, String body, Permission permission) {
        given(administration.get(any())).willThrow(new NotFoundException());
        given(administration.history(any(), anyInt(), anyInt())).willThrow(new NotFoundException());
        given(administration.disable(any(), any(), any())).willThrow(new NotFoundException());
        given(administration.enable(any())).willThrow(new NotFoundException());
        given(administration.setRoles(any(), any())).willThrow(new NotFoundException());
        given(administration.updateProfile(any(), any())).willThrow(new NotFoundException());

        assertProblem(send(method, uri, body, admin()), HttpStatus.NOT_FOUND, Problems.NOT_FOUND);
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

        // AC-19
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

        // AC-19
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

        // AC-19
        @Test
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

        // AC-19: old and new values are required nullable, so a missing one is null.
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

        // AC-19, AC-20: a change made by the system has an explicit null actor.
        @Test
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

        // Invitations AC-16, AC-22
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

        // Invitations AC-16: the inviter is required nullable.
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

        // AC-19
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

        // AC-3
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

        // AC-6
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

        // AC-6: the comment is optional.
        @Test
        void commentMayBeMissing() {
            given(administration.disable(USER_ID, DisableReason.SECURITY, null)).willReturn(disabledUser(null));

            assertThat(send(HttpMethod.POST, URI, "{\"reason\": \"SECURITY\"}", admin()))
                    .hasStatusOk();
        }

        // AC-7
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

        // AC-7
        @Test
        void unknownReasonNamesTheAllowedValues() {
            MvcTestResult result = send(HttpMethod.POST, URI, "{\"reason\": \"RETIRED\"}", admin());

            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.errors[0].message")
                    .isEqualTo("must be one of GRADUATED, EXPELLED, LEFT_PROGRAM, STAFF_OFFBOARDED, SECURITY, OTHER");
        }

        // AC-7
        @Test
        void invalidCommentIsAValidationProblem() {
            given(administration.disable(USER_ID, DisableReason.OTHER, " "))
                    .willThrow(ValidationException.of("comment", "must not be blank when the reason is OTHER"));

            MvcTestResult result = send(HttpMethod.POST, URI, "{\"reason\": \"OTHER\", \"comment\": \" \"}", admin());

            assertValidationProblem(result, "comment");
        }

        // AC-13, AC-14
        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.UsersControllerTest#lockoutProblems")
        void lockoutIsAConflict(URI type, String detail) {
            given(administration.disable(any(), any(), any())).willThrow(new ConflictException(type, detail));

            MvcTestResult result = send(HttpMethod.POST, URI, "{\"reason\": \"GRADUATED\"}", admin());

            assertProblem(result, HttpStatus.CONFLICT, type);
            assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo(detail);
        }

        @Test
        void bodyThatIsNotJsonIsABadRequest() {
            MvcTestResult result = send(HttpMethod.POST, URI, "{\"reason\": ", admin());

            assertProblem(result, HttpStatus.BAD_REQUEST, Problems.ABOUT_BLANK);
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

        // AC-8, AC-9
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

        // AC-10
        @Test
        void setsTheRequestedRoles() {
            given(administration.setRoles(USER_ID, Set.of(Role.MENTOR, Role.STUDENT)))
                    .willReturn(activeUser());

            MvcTestResult result =
                    send(HttpMethod.PUT, URI, "{\"roles\": [\"STUDENT\", \"MENTOR\", \"MENTOR\"]}", admin());

            assertThat(result).hasStatusOk();
            then(administration).should().setRoles(USER_ID, Set.of(Role.MENTOR, Role.STUDENT));
        }

        // AC-11
        @Test
        void emptySetIsAllowed() {
            given(administration.setRoles(USER_ID, Set.of())).willReturn(activeUser());

            assertThat(send(HttpMethod.PUT, URI, "{\"roles\": []}", admin())).hasStatusOk();
            then(administration).should().setRoles(eq(USER_ID), eq(Set.of()));
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

        // AC-13, AC-14
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

        // AC-16
        @Test
        void updatesTheProfile() {
            given(administration.updateProfile(USER_ID, new ProfileInput("Ivan", "Sidorov", "8 701 123 45 67")))
                    .willReturn(activeUser());

            MvcTestResult result = send(HttpMethod.PUT, URI, PROFILE_BODY, admin());

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.phone").isEqualTo("+77011234567");
        }

        // AC-18: null and missing properties both mean none.
        @Test
        void nullValuesArePassedOn() {
            given(administration.updateProfile(any(), any())).willReturn(activeUser());

            assertThat(send(HttpMethod.PUT, URI, "{\"firstName\": null, \"lastName\": null, \"phone\": null}", admin()))
                    .hasStatusOk();
            then(administration).should().updateProfile(USER_ID, new ProfileInput(null, null, null));
        }

        // AC-18
        @Test
        void invalidValuesAreAValidationProblemWithAnErrorPerField() {
            given(administration.updateProfile(any(), any()))
                    .willThrow(new ValidationException(List.of(
                            new FieldViolation("firstName", "must be at most 100 characters"),
                            new FieldViolation("lastName", "must be at most 100 characters"),
                            new FieldViolation("phone", "must be a valid phone number"))));

            MvcTestResult result = send(HttpMethod.PUT, URI, PROFILE_BODY, admin());

            assertValidationProblem(result, "firstName", "lastName", "phone");
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
