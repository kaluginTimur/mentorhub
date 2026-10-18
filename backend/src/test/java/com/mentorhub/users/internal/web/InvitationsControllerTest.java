package com.mentorhub.users.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.AccessStatus;
import com.mentorhub.users.internal.domain.InvitationStatus;
import com.mentorhub.users.internal.invitation.InvitationAcceptanceService;
import com.mentorhub.users.internal.invitation.InvitationAdministrationService;
import com.mentorhub.users.internal.invitation.InvitationDetails;
import com.mentorhub.users.internal.invitation.InvitationProblems;
import com.mentorhub.users.internal.invitation.InvitationQuery;
import com.mentorhub.users.internal.invitation.IssuedInvitation;
import com.mentorhub.users.internal.invitation.PreviewedInvitation;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.WebSliceSecurity;
import com.mentorhub.users.internal.service.HistoryActor;
import com.mentorhub.users.internal.service.MeDetails;
import com.mentorhub.users.internal.service.ResultPage;
import com.mentorhub.users.internal.service.UserProfile;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.NotFoundException;
import com.mentorhub.web.Problems;
import com.mentorhub.web.ValidationException;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(InvitationsController.class)
@Import(WebSliceSecurity.class)
class InvitationsControllerTest {

    static final UUID CALLER_ID = UUID.fromString("0b6f1d2e-6a51-4f36-9b0c-5f2d8f3a1c10");
    static final UUID INVITATION_ID = UUID.fromString("7c2d9a10-1b3e-4f5a-8c6d-9e0f1a2b3c4d");
    static final UUID OTHER_ID = UUID.fromString("5a1c7e2b-3d4f-4e5a-8b6c-7d8e9f0a1b2c");
    static final Instant CREATED_AT = Instant.parse("2026-10-12T09:00:30Z");
    static final Instant EXPIRES_AT = Instant.parse("2026-10-19T09:00:30Z");
    static final String TOKEN = "x".repeat(43);
    static final String TOKEN_BODY = "{\"token\": \"" + TOKEN + "\"}";
    static final String CREATE_BODY = "{\"email\": \"ivan@example.com\", \"roles\": [\"MENTOR\"]}";

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    InvitationAdministrationService administration;

    @MockitoBean
    InvitationAcceptanceService acceptance;

    static RequestPostProcessor admin() {
        return TestAuthentications.activeUser(CALLER_ID, Permission.USERS_READ, Permission.USERS_INVITE);
    }

    static InvitationDetails pending() {
        return new InvitationDetails(
                INVITATION_ID,
                "ivan@example.com",
                List.of(Role.MENTOR),
                InvitationStatus.PENDING,
                CREATED_AT,
                EXPIRES_AT,
                new HistoryActor(CALLER_ID, "Anna Petrova"),
                null,
                null,
                null,
                null);
    }

    static IssuedInvitation issued() {
        return new IssuedInvitation(pending(), URI.create("http://localhost:4200/invite/" + TOKEN));
    }

    static MeDetails activeMe() {
        return new MeDetails(
                AccessStatus.ACTIVE,
                new UserProfile(
                        OTHER_ID, "ivan@example.com", "Ivan", "Sidorov", null, List.of(Role.MENTOR), List.of()));
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
    }

    /** Every management operation with a valid request. */
    static Stream<Arguments> managementOperations() {
        String invitation = "/api/v1/invitations/" + INVITATION_ID;
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/api/v1/invitations", CREATE_BODY),
                Arguments.of(HttpMethod.GET, "/api/v1/invitations", ""),
                Arguments.of(HttpMethod.POST, invitation + "/revoke", ""),
                Arguments.of(HttpMethod.POST, invitation + "/link", ""));
    }

    @Nested
    class Authorization {

        // AC-5
        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.InvitationsControllerTest#managementOperations")
        void callerWithoutTheInvitePermissionIsForbidden(HttpMethod method, String uri, String body) {
            MvcTestResult result = send(
                    method,
                    uri,
                    body,
                    TestAuthentications.activeUser(CALLER_ID, Permission.USERS_READ, Permission.USERS_MANAGE));

            assertProblem(result, HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
            verifyNoInteractions(administration);
        }

        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.InvitationsControllerTest#managementOperations")
        void disabledCallerIsRefusedByTheGate(HttpMethod method, String uri, String body) {
            assertProblem(
                    send(method, uri, body, TestAuthentications.disabledUser(CALLER_ID)),
                    HttpStatus.FORBIDDEN,
                    Problems.ACCESS_ENDED);
            verifyNoInteractions(administration);
        }

        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.InvitationsControllerTest#managementOperations")
        void notInvitedCallerIsRefusedByTheGate(HttpMethod method, String uri, String body) {
            assertProblem(
                    send(method, uri, body, TestAuthentications.notInvited()),
                    HttpStatus.FORBIDDEN,
                    Problems.NOT_INVITED);
            verifyNoInteractions(administration);
        }

        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.InvitationsControllerTest#managementOperations")
        void requestWithoutTokenIsUnauthorized(HttpMethod method, String uri, String body) {
            MockMvcRequestBuilder request = mvc.method(method).uri(uri);
            if (!body.isEmpty()) {
                request = request.contentType(MediaType.APPLICATION_JSON).content(body);
            }

            assertProblem(request.exchange(), HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
            verifyNoInteractions(administration);
        }

        // AC-17
        @Test
        void acceptRequiresAuthentication() {
            MvcTestResult result = mvc.post()
                    .uri("/api/v1/invitations/accept")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TOKEN_BODY)
                    .exchange();

            assertProblem(result, HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
            verifyNoInteractions(acceptance);
        }

        // AC-17: the gate admits callers in every access status.
        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.InvitationsControllerTest#callersInAnyStatus")
        void acceptAdmitsCallersInAnyStatus(String name, RequestPostProcessor caller) {
            given(acceptance.accept(TOKEN)).willReturn(activeMe());

            MvcTestResult result = send(HttpMethod.POST, "/api/v1/invitations/accept", TOKEN_BODY, caller);

            assertThat(result).hasStatusOk();
            then(acceptance).should().accept(TOKEN);
        }

        // AC-8: public, and a bearer token is ignored, even a bad one (the slice rejects every bearer token).
        @ParameterizedTest
        @ValueSource(strings = {"", "Bearer not-a-valid-token"})
        void previewIsPublicAndIgnoresTheBearerToken(String authorization) {
            given(acceptance.preview(TOKEN)).willReturn(new PreviewedInvitation("ivan@example.com", EXPIRES_AT));
            MockMvcRequestBuilder request = mvc.post()
                    .uri("/api/v1/invitations/preview")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TOKEN_BODY);
            if (!authorization.isEmpty()) {
                request = request.header(HttpHeaders.AUTHORIZATION, authorization);
            }

            MvcTestResult result = request.exchange();

            assertThat(result).hasStatusOk();
        }
    }

    static Stream<Arguments> callersInAnyStatus() {
        return Stream.of(
                Arguments.of("not invited", TestAuthentications.notInvited()),
                Arguments.of("disabled", TestAuthentications.disabledUser(CALLER_ID)),
                Arguments.of("active without roles", TestAuthentications.activeUser(CALLER_ID)));
    }

    @Nested
    class Create {

        // AC-1
        @Test
        void createsAnInvitationAndReturnsItsLinkOnce() {
            given(administration.create("ivan@example.com", List.of(Role.MENTOR)))
                    .willReturn(issued());

            MvcTestResult result = send(HttpMethod.POST, "/api/v1/invitations", CREATE_BODY, admin());

            assertThat(result).hasStatus(HttpStatus.CREATED);
            assertThat(result).bodyJson().isStrictlyEqualTo("""
                    {
                      "invitation": {
                        "id": "7c2d9a10-1b3e-4f5a-8c6d-9e0f1a2b3c4d",
                        "email": "ivan@example.com",
                        "roles": ["MENTOR"],
                        "status": "PENDING",
                        "createdAt": "2026-10-12T09:00:30Z",
                        "expiresAt": "2026-10-19T09:00:30Z",
                        "createdBy": {"id": "0b6f1d2e-6a51-4f36-9b0c-5f2d8f3a1c10", "name": "Anna Petrova"}
                      },
                      "link": "http://localhost:4200/invite/%s"
                    }
                    """.formatted(TOKEN));
        }

        // AC-2: the service reports a null role together with the email's violations.
        @Test
        void nullRoleIsPassedToTheService() {
            given(administration.create("ivan@example.com", Arrays.asList((Role) null)))
                    .willThrow(ValidationException.of("roles", "must not contain null"));

            MvcTestResult result = send(
                    HttpMethod.POST,
                    "/api/v1/invitations",
                    "{\"email\": \"ivan@example.com\", \"roles\": [null]}",
                    admin());

            assertValidationProblem(result, "roles");
        }

        // AC-2
        @Test
        void unknownRoleAndMissingFieldsAreValidationProblems() {
            assertValidationProblem(
                    send(
                            HttpMethod.POST,
                            "/api/v1/invitations",
                            "{\"email\": \"ivan@example.com\", \"roles\": [\"BOSS\"]}",
                            admin()),
                    "roles");
            assertValidationProblem(send(HttpMethod.POST, "/api/v1/invitations", "{\"roles\": []}", admin()), "email");
            verifyNoInteractions(administration);
        }

        // AC-3
        @Test
        void activeUserConflictCarriesTheUserId() {
            given(administration.create(any(), any()))
                    .willThrow(new ConflictException(
                            InvitationProblems.USER_ACTIVE,
                            "This person already has access.",
                            Map.of("userId", OTHER_ID)));

            MvcTestResult result = send(HttpMethod.POST, "/api/v1/invitations", CREATE_BODY, admin());

            assertProblem(result, HttpStatus.CONFLICT, InvitationProblems.USER_ACTIVE);
            assertThat(result).bodyJson().extractingPath("$.userId").isEqualTo(OTHER_ID.toString());
            assertThat(result).bodyJson().doesNotHavePath("$.invitationId");
        }

        // AC-3
        @Test
        void existingInvitationConflictCarriesTheInvitationId() {
            given(administration.create(any(), any()))
                    .willThrow(new ConflictException(
                            InvitationProblems.INVITATION_EXISTS,
                            "There is already an invitation for this email.",
                            Map.of("invitationId", INVITATION_ID)));

            MvcTestResult result = send(HttpMethod.POST, "/api/v1/invitations", CREATE_BODY, admin());

            assertProblem(result, HttpStatus.CONFLICT, InvitationProblems.INVITATION_EXISTS);
            assertThat(result).bodyJson().extractingPath("$.invitationId").isEqualTo(INVITATION_ID.toString());
            assertThat(result).bodyJson().doesNotHavePath("$.userId");
        }
    }

    @Nested
    class ListInvitations {

        // AC-4
        @Test
        void returnsAPageWithDefaultsAndOmitsInapplicableProperties() {
            InvitationDetails accepted = new InvitationDetails(
                    OTHER_ID,
                    "maria@example.com",
                    List.of(),
                    InvitationStatus.ACCEPTED,
                    CREATED_AT,
                    EXPIRES_AT,
                    new HistoryActor(CALLER_ID, "Anna Petrova"),
                    CREATED_AT,
                    new HistoryActor(OTHER_ID, "Maria K."),
                    null,
                    null);
            given(administration.list(any())).willReturn(new ResultPage<>(List.of(pending(), accepted), 0, 25, 2));

            MvcTestResult result =
                    mvc.get().uri("/api/v1/invitations").with(admin()).exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(2);
            assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(25);
            assertThat(result)
                    .bodyJson()
                    .doesNotHavePath("$.items[0].acceptedAt")
                    .doesNotHavePath("$.items[0].acceptedUser")
                    .doesNotHavePath("$.items[0].revokedAt")
                    .doesNotHavePath("$.items[0].revokedBy");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[1].acceptedAt")
                    .isEqualTo("2026-10-12T09:00:30Z");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[1].acceptedUser.name")
                    .isEqualTo("Maria K.");
            assertThat(result).bodyJson().doesNotHavePath("$.items[1].revokedBy");
            then(administration).should().list(new InvitationQuery(null, null, 0, 25));
        }

        // AC-4
        @Test
        void passesSearchFiltersAndPage() {
            given(administration.list(any())).willReturn(new ResultPage<>(List.of(), 2, 100, 0));

            MvcTestResult result = mvc.get()
                    .uri("/api/v1/invitations?q= ivan &status=EXPIRED&page=2&size=100")
                    .with(admin())
                    .exchange();

            assertThat(result).hasStatusOk();
            then(administration).should().list(new InvitationQuery(" ivan ", InvitationStatus.EXPIRED, 2, 100));
        }

        // AC-4
        @Test
        void invalidParametersAreValidationProblemsNamingTheParameter() {
            assertValidationProblem(
                    mvc.get().uri("/api/v1/invitations?size=101").with(admin()).exchange(), "size");
            assertValidationProblem(
                    mvc.get().uri("/api/v1/invitations?page=-1").with(admin()).exchange(), "page");
            assertValidationProblem(
                    mvc.get()
                            .uri("/api/v1/invitations?status=GONE")
                            .with(admin())
                            .exchange(),
                    "status");
            verifyNoInteractions(administration);
        }
    }

    @Nested
    class RevokeAndReissue {

        // AC-6
        @Test
        void revokesAndShowsWhoAndWhen() {
            InvitationDetails revoked = new InvitationDetails(
                    INVITATION_ID,
                    "ivan@example.com",
                    List.of(),
                    InvitationStatus.REVOKED,
                    CREATED_AT,
                    EXPIRES_AT,
                    new HistoryActor(CALLER_ID, "Anna Petrova"),
                    null,
                    null,
                    CREATED_AT,
                    new HistoryActor(CALLER_ID, "Anna Petrova"));
            given(administration.revoke(INVITATION_ID)).willReturn(revoked);

            MvcTestResult result =
                    send(HttpMethod.POST, "/api/v1/invitations/" + INVITATION_ID + "/revoke", "", admin());

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("REVOKED");
            assertThat(result).bodyJson().extractingPath("$.revokedBy.name").isEqualTo("Anna Petrova");
            assertThat(result).bodyJson().doesNotHavePath("$.acceptedAt");
        }

        // AC-6
        @Test
        void closedInvitationIsAConflictAndUnknownIdIsNotFound() {
            given(administration.revoke(INVITATION_ID))
                    .willThrow(new ConflictException(InvitationProblems.INVITATION_CLOSED, "Closed."));
            given(administration.reissueLink(OTHER_ID)).willThrow(new NotFoundException());

            assertProblem(
                    send(HttpMethod.POST, "/api/v1/invitations/" + INVITATION_ID + "/revoke", "", admin()),
                    HttpStatus.CONFLICT,
                    InvitationProblems.INVITATION_CLOSED);
            assertProblem(
                    send(HttpMethod.POST, "/api/v1/invitations/" + OTHER_ID + "/link", "", admin()),
                    HttpStatus.NOT_FOUND,
                    Problems.NOT_FOUND);
        }

        // AC-7
        @Test
        void reissuesTheLink() {
            given(administration.reissueLink(INVITATION_ID)).willReturn(issued());

            MvcTestResult result = send(HttpMethod.POST, "/api/v1/invitations/" + INVITATION_ID + "/link", "", admin());

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.link").isEqualTo("http://localhost:4200/invite/" + TOKEN);
            assertThat(result).bodyJson().extractingPath("$.invitation.id").isEqualTo(INVITATION_ID.toString());
        }

        @Test
        void nonUuidIdIsAValidationProblem() {
            assertValidationProblem(send(HttpMethod.POST, "/api/v1/invitations/nope/revoke", "", admin()), "id");
            assertValidationProblem(send(HttpMethod.POST, "/api/v1/invitations/nope/link", "", admin()), "id");
        }
    }

    @Nested
    class PreviewAndAccept {

        // AC-8
        @Test
        void previewReturnsTheEmailAndExpiry() {
            given(acceptance.preview(TOKEN)).willReturn(new PreviewedInvitation("ivan@example.com", EXPIRES_AT));

            MvcTestResult result = mvc.post()
                    .uri("/api/v1/invitations/preview")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(TOKEN_BODY)
                    .exchange();

            assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                    {"email": "ivan@example.com", "expiresAt": "2026-10-19T09:00:30Z"}
                    """);
        }

        // AC-9
        @ParameterizedTest
        @ValueSource(strings = {"/api/v1/invitations/preview", "/api/v1/invitations/accept"})
        void invalidInvitationIsANotFoundProblemWithItsOwnType(String uri) {
            NotFoundException invalid = new NotFoundException(
                    InvitationProblems.INVITATION_INVALID, "This invitation link is invalid or has expired.");
            given(acceptance.preview(any())).willThrow(invalid);
            given(acceptance.accept(any())).willThrow(invalid);

            MvcTestResult result = send(HttpMethod.POST, uri, TOKEN_BODY, TestAuthentications.notInvited());

            assertProblem(result, HttpStatus.NOT_FOUND, InvitationProblems.INVITATION_INVALID);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.detail")
                    .isEqualTo("This invitation link is invalid or has expired.");
        }

        // Empty, missing and oversized tokens are validation problems; any other malformed token reaches the service.
        @ParameterizedTest
        @ValueSource(strings = {"/api/v1/invitations/preview", "/api/v1/invitations/accept"})
        void emptyMissingOrOversizedTokenIsAValidationProblemNamingTheToken(String uri) {
            for (String body : List.of(
                    "{\"token\": \"\"}", "{}", "{\"token\": null}", "{\"token\": \"" + "x".repeat(201) + "\"}")) {
                assertValidationProblem(send(HttpMethod.POST, uri, body, TestAuthentications.notInvited()), "token");
            }
            verifyNoInteractions(acceptance);
        }

        // AC-12, AC-13
        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.InvitationsControllerTest#acceptConflicts")
        void acceptConflictsAreProblemsWithTheirType(URI type) {
            given(acceptance.accept(any())).willThrow(new ConflictException(type, "Conflict."));

            MvcTestResult result =
                    send(HttpMethod.POST, "/api/v1/invitations/accept", TOKEN_BODY, TestAuthentications.notInvited());

            assertProblem(result, HttpStatus.CONFLICT, type);
        }

        // AC-10
        @Test
        void acceptReturnsMe() {
            given(acceptance.accept(TOKEN)).willReturn(activeMe());

            MvcTestResult result =
                    send(HttpMethod.POST, "/api/v1/invitations/accept", TOKEN_BODY, TestAuthentications.notInvited());

            assertThat(result).hasStatusOk();
            assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
            assertThat(result).bodyJson().extractingPath("$.user.email").isEqualTo("ivan@example.com");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.user.roles")
                    .asArray()
                    .containsExactly("MENTOR");
        }
    }

    static Stream<Arguments> acceptConflicts() {
        return Stream.of(
                Arguments.of(InvitationProblems.INVITATION_EMAIL_MISMATCH),
                Arguments.of(InvitationProblems.IDENTITY_LINKED_ELSEWHERE));
    }
}
