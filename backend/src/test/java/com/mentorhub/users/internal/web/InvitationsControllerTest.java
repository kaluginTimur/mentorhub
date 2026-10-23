package com.mentorhub.users.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;

import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.AccessStatus;
import com.mentorhub.users.internal.domain.InvitationEmailStatus;
import com.mentorhub.users.internal.domain.InvitationStatus;
import com.mentorhub.users.internal.invitation.InvitationAcceptanceService;
import com.mentorhub.users.internal.invitation.InvitationAdministrationService;
import com.mentorhub.users.internal.invitation.InvitationDetails;
import com.mentorhub.users.internal.invitation.InvitationEmailDetails;
import com.mentorhub.users.internal.invitation.InvitationProblems;
import com.mentorhub.users.internal.invitation.InvitationQuery;
import com.mentorhub.users.internal.invitation.IssuedInvitation;
import com.mentorhub.users.internal.invitation.PendingInvitationLink;
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
import org.jspecify.annotations.Nullable;
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
    static final String CREATE_WITH_EMAIL_BODY =
            "{\"email\": \"ivan@example.com\", \"roles\": [\"MENTOR\"], \"sendEmail\": true}";
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
        return pending(null);
    }

    static InvitationDetails pending(@Nullable InvitationEmailDetails lastEmail) {
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
                null,
                lastEmail);
    }

    static InvitationEmailDetails queued() {
        return new InvitationEmailDetails(
                InvitationEmailStatus.QUEUED, CREATED_AT, new HistoryActor(CALLER_ID, "Anna Petrova"), null);
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
                Arguments.of(HttpMethod.POST, invitation + "/link", ""),
                Arguments.of(HttpMethod.POST, invitation + "/link", "{\"sendEmail\": true}"),
                Arguments.of(HttpMethod.POST, "/api/v1/invitations", CREATE_WITH_EMAIL_BODY),
                Arguments.of(HttpMethod.POST, invitation + "/email", ""),
                Arguments.of(HttpMethod.GET, invitation + "/link", ""));
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
            given(administration.create("ivan@example.com", List.of(Role.MENTOR), false))
                    .willReturn(issued());

            MvcTestResult result = send(HttpMethod.POST, "/api/v1/invitations", CREATE_BODY, admin());

            assertThat(result).hasStatus(HttpStatus.CREATED);
            assertThat(result).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
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

        // AC-2: the flag reaches the service; absent and false mean no email.
        @Test
        void passesTheEmailFlagToTheService() {
            given(administration.create(any(), any(), anyBoolean())).willReturn(issued());

            send(HttpMethod.POST, "/api/v1/invitations", CREATE_WITH_EMAIL_BODY, admin());
            send(HttpMethod.POST, "/api/v1/invitations", CREATE_BODY, admin());
            send(
                    HttpMethod.POST,
                    "/api/v1/invitations",
                    "{\"email\": \"ivan@example.com\", \"roles\": [\"MENTOR\"], \"sendEmail\": false}",
                    admin());

            then(administration).should().create("ivan@example.com", List.of(Role.MENTOR), true);
            then(administration).should(times(2)).create("ivan@example.com", List.of(Role.MENTOR), false);
        }

        // AC-2, AC-16: 201 as today, the link, and the queued email without finishedAt.
        @Test
        void responseCarriesTheQueuedEmail() {
            given(administration.create("ivan@example.com", List.of(Role.MENTOR), true))
                    .willReturn(new IssuedInvitation(
                            pending(queued()), URI.create("http://localhost:4200/invite/" + TOKEN)));

            MvcTestResult result = send(HttpMethod.POST, "/api/v1/invitations", CREATE_WITH_EMAIL_BODY, admin());

            assertThat(result).hasStatus(HttpStatus.CREATED);
            assertThat(result).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
            assertThat(result).bodyJson().extractingPath("$.link").isEqualTo("http://localhost:4200/invite/" + TOKEN);
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.invitation.lastEmail")
                    .isEqualTo(Map.of(
                            "status", "QUEUED",
                            "requestedAt", "2026-10-12T09:00:30Z",
                            "requestedBy", Map.of("id", CALLER_ID.toString(), "name", "Anna Petrova")));
            assertThat(result).bodyJson().doesNotHavePath("$.invitation.lastEmail.finishedAt");
        }

        // AC-1, AC-6
        @Test
        void unavailableEmailIsAConflict() {
            given(administration.create(any(), any(), anyBoolean()))
                    .willThrow(new ConflictException(InvitationProblems.EMAIL_UNAVAILABLE, "Unavailable."));

            assertProblem(
                    send(HttpMethod.POST, "/api/v1/invitations", CREATE_WITH_EMAIL_BODY, admin()),
                    HttpStatus.CONFLICT,
                    InvitationProblems.EMAIL_UNAVAILABLE);
        }

        @Test
        void nonBooleanFlagIsAValidationProblem() {
            assertValidationProblem(
                    send(
                            HttpMethod.POST,
                            "/api/v1/invitations",
                            "{\"email\": \"ivan@example.com\", \"roles\": [], \"sendEmail\": \"yes\"}",
                            admin()),
                    "sendEmail");
            verifyNoInteractions(administration);
        }

        // AC-2: the service reports a null role together with the email's violations.
        @Test
        void nullRoleIsPassedToTheService() {
            given(administration.create("ivan@example.com", Arrays.asList((Role) null), false))
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
            given(administration.create(any(), any(), anyBoolean()))
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
            given(administration.create(any(), any(), anyBoolean()))
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
                    null,
                    null);
            given(administration.list(any())).willReturn(new ResultPage<>(List.of(pending(), accepted), 0, 25, 2));

            MvcTestResult result =
                    mvc.get().uri("/api/v1/invitations").with(admin()).exchange();

            assertThat(result).hasStatusOk();
            assertThat(result).bodyText().doesNotContain(TOKEN);
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

        // AC-16: every status maps; finishedAt only when present.
        @Test
        void itemsCarryTheLatestEmail() {
            Instant finished = Instant.parse("2026-10-12T09:01:00Z");
            HistoryActor actor = new HistoryActor(CALLER_ID, "Anna Petrova");
            given(administration.list(any()))
                    .willReturn(new ResultPage<>(
                            List.of(
                                    pending(new InvitationEmailDetails(
                                            InvitationEmailStatus.FAILED, CREATED_AT, actor, finished)),
                                    pending(new InvitationEmailDetails(
                                            InvitationEmailStatus.SKIPPED, CREATED_AT, actor, finished)),
                                    pending()),
                            0,
                            25,
                            3));

            MvcTestResult result =
                    mvc.get().uri("/api/v1/invitations").with(admin()).exchange();

            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[0].lastEmail.status")
                    .isEqualTo("FAILED");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[0].lastEmail.finishedAt")
                    .isEqualTo("2026-10-12T09:01:00Z");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.items[1].lastEmail.status")
                    .isEqualTo("SKIPPED");
            assertThat(result).bodyJson().doesNotHavePath("$.items[2].lastEmail");
            assertThat(result).bodyText().doesNotContain("failure");
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
                    new HistoryActor(CALLER_ID, "Anna Petrova"),
                    null);
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
            given(administration.reissueLink(OTHER_ID, false)).willThrow(new NotFoundException());

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
            given(administration.reissueLink(INVITATION_ID, false)).willReturn(issued());

            MvcTestResult result = send(HttpMethod.POST, "/api/v1/invitations/" + INVITATION_ID + "/link", "", admin());

            assertThat(result).hasStatusOk();
            assertThat(result).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
            assertThat(result).bodyJson().extractingPath("$.link").isEqualTo("http://localhost:4200/invite/" + TOKEN);
            assertThat(result).bodyJson().extractingPath("$.invitation.id").isEqualTo(INVITATION_ID.toString());
        }

        // AC-3: with the flag, without a body and with an empty object.
        @Test
        void reissueWithTheEmailFlag() {
            given(administration.reissueLink(any(), anyBoolean())).willReturn(issued());
            String path = "/api/v1/invitations/" + INVITATION_ID + "/link";

            send(HttpMethod.POST, path, "{\"sendEmail\": true}", admin());
            send(HttpMethod.POST, path, "{\"sendEmail\": false}", admin());
            send(HttpMethod.POST, path, "{}", admin());
            send(HttpMethod.POST, path, "", admin());

            then(administration).should().reissueLink(INVITATION_ID, true);
            then(administration).should(times(3)).reissueLink(INVITATION_ID, false);
        }

        // AC-3
        @Test
        void reissueResponseCarriesTheQueuedEmail() {
            given(administration.reissueLink(INVITATION_ID, true))
                    .willReturn(new IssuedInvitation(
                            pending(queued()), URI.create("http://localhost:4200/invite/" + TOKEN)));

            MvcTestResult result = send(
                    HttpMethod.POST,
                    "/api/v1/invitations/" + INVITATION_ID + "/link",
                    "{\"sendEmail\": true}",
                    admin());

            assertThat(result).hasStatusOk();
            assertThat(result).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.invitation.lastEmail.status")
                    .isEqualTo("QUEUED");
        }

        // AC-1, AC-6
        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.InvitationsControllerTest#reissueConflicts")
        void reissueConflictsAreProblemsWithTheirType(URI type) {
            given(administration.reissueLink(INVITATION_ID, true)).willThrow(new ConflictException(type, "Conflict."));

            assertProblem(
                    send(
                            HttpMethod.POST,
                            "/api/v1/invitations/" + INVITATION_ID + "/link",
                            "{\"sendEmail\": true}",
                            admin()),
                    HttpStatus.CONFLICT,
                    type);
        }

        @Test
        void nonBooleanFlagIsAValidationProblem() {
            assertValidationProblem(
                    send(
                            HttpMethod.POST,
                            "/api/v1/invitations/" + INVITATION_ID + "/link",
                            "{\"sendEmail\": \"yes\"}",
                            admin()),
                    "sendEmail");
        }

        @Test
        void nonUuidIdIsAValidationProblem() {
            assertValidationProblem(send(HttpMethod.POST, "/api/v1/invitations/nope/revoke", "", admin()), "id");
            assertValidationProblem(send(HttpMethod.POST, "/api/v1/invitations/nope/link", "", admin()), "id");
        }
    }

    @Nested
    class GetLink {

        static final String PATH = "/api/v1/invitations/" + INVITATION_ID + "/link";

        // AC-1, AC-4
        @Test
        void returnsTheLinkWithoutCaching() {
            given(administration.link(INVITATION_ID))
                    .willReturn(new PendingInvitationLink(
                            URI.create("http://localhost:4200/invite/" + TOKEN), "ivan@example.com", EXPIRES_AT));

            MvcTestResult result = send(HttpMethod.GET, PATH, "", admin());

            assertThat(result).hasStatusOk();
            assertThat(result).headers().hasValue(HttpHeaders.CACHE_CONTROL, "no-store");
            assertThat(result).bodyJson().isStrictlyEqualTo("""
                    {
                      "link": "http://localhost:4200/invite/%s",
                      "email": "ivan@example.com",
                      "expiresAt": "2026-10-19T09:00:30Z"
                    }
                    """.formatted(TOKEN));
        }

        // AC-2
        @Test
        void unknownInvitationIsNotFound() {
            given(administration.link(INVITATION_ID)).willThrow(new NotFoundException());

            assertProblem(send(HttpMethod.GET, PATH, "", admin()), HttpStatus.NOT_FOUND, Problems.NOT_FOUND);
        }

        // AC-2
        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.InvitationsControllerTest#unavailableLinkTypes")
        void closedOrExpiredInvitationIsAConflict(URI type) {
            given(administration.link(INVITATION_ID)).willThrow(new ConflictException(type, "Unavailable."));

            MvcTestResult result = send(HttpMethod.GET, PATH, "", admin());

            assertProblem(result, HttpStatus.CONFLICT, type);
            assertThat(result).bodyText().doesNotContain(TOKEN);
        }

        @Test
        void nonUuidIdIsAValidationProblem() {
            assertValidationProblem(send(HttpMethod.GET, "/api/v1/invitations/nope/link", "", admin()), "id");
        }
    }

    static Stream<URI> reissueConflicts() {
        return Stream.of(InvitationProblems.INVITATION_CLOSED, InvitationProblems.EMAIL_UNAVAILABLE);
    }

    @Nested
    class SendEmail {

        static final String PATH = "/api/v1/invitations/" + INVITATION_ID + "/email";

        // AC-4: 202 with the invitation, which carries the queued email; no link, no token.
        @Test
        void answersAcceptedWithTheInvitation() {
            given(administration.sendEmail(INVITATION_ID)).willReturn(pending(queued()));

            MvcTestResult result = send(HttpMethod.POST, PATH, "", admin());

            assertThat(result).hasStatus(HttpStatus.ACCEPTED);
            assertThat(result).bodyText().doesNotContain(TOKEN).doesNotContain("link");
            assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(INVITATION_ID.toString());
            assertThat(result).bodyJson().extractingPath("$.lastEmail.status").isEqualTo("QUEUED");
            assertThat(result)
                    .bodyJson()
                    .extractingPath("$.lastEmail.requestedBy.name")
                    .isEqualTo("Anna Petrova");
            assertThat(result).bodyJson().doesNotHavePath("$.lastEmail.finishedAt");
        }

        // AC-4
        @Test
        void unknownInvitationIsNotFound() {
            given(administration.sendEmail(INVITATION_ID)).willThrow(new NotFoundException());

            assertProblem(send(HttpMethod.POST, PATH, "", admin()), HttpStatus.NOT_FOUND, Problems.NOT_FOUND);
        }

        // AC-4, AC-5, AC-1
        @ParameterizedTest
        @MethodSource("com.mentorhub.users.internal.web.InvitationsControllerTest#sendEmailConflicts")
        void conflictsAreProblemsWithTheirType(URI type) {
            given(administration.sendEmail(INVITATION_ID)).willThrow(new ConflictException(type, "Conflict."));

            assertProblem(send(HttpMethod.POST, PATH, "", admin()), HttpStatus.CONFLICT, type);
        }

        @Test
        void nonUuidIdIsAValidationProblem() {
            assertValidationProblem(send(HttpMethod.POST, "/api/v1/invitations/nope/email", "", admin()), "id");
        }
    }

    static Stream<URI> sendEmailConflicts() {
        return Stream.of(
                InvitationProblems.INVITATION_CLOSED,
                InvitationProblems.INVITATION_EXPIRED,
                InvitationProblems.EMAIL_QUEUED,
                InvitationProblems.EMAIL_UNAVAILABLE);
    }

    static Stream<URI> unavailableLinkTypes() {
        return Stream.of(InvitationProblems.INVITATION_CLOSED, InvitationProblems.INVITATION_EXPIRED);
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
