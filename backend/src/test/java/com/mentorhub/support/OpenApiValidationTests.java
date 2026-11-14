package com.mentorhub.support;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

class OpenApiValidationTests {

    @Test
    void responseMatcherRejectsResponseMissingRequiredField() {
        MockHttpServletRequest request = request("GET", "/api/v1/system/info");
        MockHttpServletResponse response = jsonResponse(200, MediaType.APPLICATION_JSON_VALUE, """
                {"name": "MentorHub"}
                """);

        assertThatThrownBy(() -> OpenApiValidation.responseMatcher().match(mvcResult(request, response)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("version");
    }

    @Test
    void responseMatcherRejectsResponseViolatingEnumOrFormat() {
        MockHttpServletRequest formatRequest = request("GET", "/api/v1/system/client-config");
        MockHttpServletResponse formatResponse = jsonResponse(200, MediaType.APPLICATION_JSON_VALUE, """
                {
                    "issuer": "not-a-valid-uri",
                    "clientId": "test-client",
                    "scope": "openid",
                    "invitationEmails": true
                }
                """);

        assertThatThrownBy(() -> OpenApiValidation.responseMatcher().match(mvcResult(formatRequest, formatResponse)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("uri pattern");

        MockHttpServletRequest enumRequest = request("GET", "/api/v1/me");
        MockHttpServletResponse enumResponse = jsonResponse(200, MediaType.APPLICATION_JSON_VALUE, """
                {
                    "status": "INVALID_STATUS"
                }
                """);

        assertThatThrownBy(() -> OpenApiValidation.responseMatcher().match(mvcResult(enumRequest, enumResponse)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("enum");
    }

    @Test
    void responseMatcherRejectsAdditionalPropertyOnClosedSchema() {
        MockHttpServletRequest request = request("GET", "/api/v1/system/info");
        MockHttpServletResponse response = jsonResponse(200, MediaType.APPLICATION_JSON_VALUE, """
                {
                    "name": "MentorHub",
                    "version": "0.0.1",
                    "extraField": "forbidden"
                }
                """);

        assertThatThrownBy(() -> OpenApiValidation.responseMatcher().match(mvcResult(request, response)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("extraField");
    }

    @Test
    void responseMatcherAcceptsValidDtoResponse() {
        MockHttpServletRequest request = request("GET", "/api/v1/system/info");
        MockHttpServletResponse response = jsonResponse(200, MediaType.APPLICATION_JSON_VALUE, """
                {
                    "name": "MentorHub",
                    "version": "0.0.1"
                }
                """);

        assertThatNoException()
                .isThrownBy(() -> OpenApiValidation.responseMatcher().match(mvcResult(request, response)));
    }

    @Test
    void responseMatcherAcceptsProblemWithExtensionMembers() {
        MockHttpServletRequest request = request("GET", "/api/v1/me");
        MockHttpServletResponse response = jsonResponse(401, MediaType.APPLICATION_PROBLEM_JSON_VALUE, """
                {
                    "type": "urn:mentorhub:problem:unauthorized",
                    "title": "Unauthorized",
                    "status": 401,
                    "instance": "/api/v1/me",
                    "customExtension": "allowed"
                }
                """);

        assertThatNoException()
                .isThrownBy(() -> OpenApiValidation.responseMatcher().match(mvcResult(request, response)));
    }

    @Test
    void responseMatcherAcceptsValidationProblemWithErrorsAndExtensionMembers() {
        MockHttpServletRequest request = request("POST", "/api/v1/invitations");
        MockHttpServletResponse response = jsonResponse(400, MediaType.APPLICATION_PROBLEM_JSON_VALUE, """
                {
                    "type": "urn:mentorhub:problem:validation-failed",
                    "title": "Validation Failed",
                    "status": 400,
                    "instance": "/api/v1/invitations",
                    "errors": [
                        {"field": "email", "message": "must be valid"}
                    ],
                    "customExtension": 123
                }
                """);

        assertThatNoException()
                .isThrownBy(() -> OpenApiValidation.responseMatcher().match(mvcResult(request, response)));
    }

    @Test
    void responseMatcherAcceptsInvitationConflictProblemWithUserId() {
        MockHttpServletRequest request = request("POST", "/api/v1/invitations");
        MockHttpServletResponse response = jsonResponse(409, MediaType.APPLICATION_PROBLEM_JSON_VALUE, """
                {
                    "type": "urn:mentorhub:problem:user-active",
                    "title": "User is active",
                    "status": 409,
                    "instance": "/api/v1/invitations",
                    "userId": "a0000000-0000-0000-0000-000000000001",
                    "customExtension": "allowed"
                }
                """);

        assertThatNoException()
                .isThrownBy(() -> OpenApiValidation.responseMatcher().match(mvcResult(request, response)));
    }

    @Test
    void responseMatcherAcceptsInvitationConflictProblemWithInvitationId() {
        MockHttpServletRequest request = request("POST", "/api/v1/invitations");
        MockHttpServletResponse response = jsonResponse(409, MediaType.APPLICATION_PROBLEM_JSON_VALUE, """
                {
                    "type": "urn:mentorhub:problem:invitation-exists",
                    "title": "Invitation exists",
                    "status": 409,
                    "instance": "/api/v1/invitations",
                    "invitationId": "b0000000-0000-0000-0000-000000000002",
                    "customExtension": "allowed"
                }
                """);

        assertThatNoException()
                .isThrownBy(() -> OpenApiValidation.responseMatcher().match(mvcResult(request, response)));
    }

    @Test
    void requestMatcherRejectsMissingRequiredField() {
        MockHttpServletRequest request = jsonRequest("POST", "/api/v1/invitations", """
                {"roles": ["ADMIN"]}
                """);
        MockHttpServletResponse response = jsonResponse(400, MediaType.APPLICATION_PROBLEM_JSON_VALUE, """
                {
                    "type": "urn:mentorhub:problem:validation-failed",
                    "title": "Validation Failed",
                    "status": 400,
                    "errors": []
                }
                """);

        assertThatThrownBy(() -> OpenApiValidation.requestMatcher().match(mvcResult(request, response)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("email");
    }

    @Test
    void requestMatcherRejectsAdditionalPropertyOnClosedRequestSchema() {
        MockHttpServletRequest request = jsonRequest("POST", "/api/v1/invitations", """
                {
                    "email": "test@example.com",
                    "roles": ["ADMIN"],
                    "unexpectedProp": "rejected"
                }
                """);
        MockHttpServletResponse response = jsonResponse(400, MediaType.APPLICATION_PROBLEM_JSON_VALUE, """
                {
                    "type": "urn:mentorhub:problem:validation-failed",
                    "title": "Validation Failed",
                    "status": 400,
                    "errors": []
                }
                """);

        assertThatThrownBy(() -> OpenApiValidation.requestMatcher().match(mvcResult(request, response)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("unexpectedProp");
    }

    @Test
    void requestAndResponseMatcherAcceptsValidRequestAndResponse() {
        MockHttpServletRequest request = jsonRequest("POST", "/api/v1/invitations", """
                {
                    "email": "test@example.com",
                    "roles": ["ADMIN"]
                }
                """);
        MockHttpServletResponse response = jsonResponse(201, MediaType.APPLICATION_JSON_VALUE, """
                {
                    "invitation": {
                        "id": "c0000000-0000-0000-0000-000000000003",
                        "email": "test@example.com",
                        "roles": ["ADMIN"],
                        "status": "PENDING",
                        "createdAt": "2026-11-14T12:00:00Z",
                        "expiresAt": "2026-11-21T12:00:00Z",
                        "createdBy": {
                            "id": "00000000-0000-0000-0000-000000000001",
                            "name": "Anna Petrova"
                        }
                    },
                    "link": "http://localhost:4200/invite/token123"
                }
                """);

        assertThatNoException()
                .isThrownBy(() -> OpenApiValidation.requestAndResponseMatcher().match(mvcResult(request, response)));
    }

    @Test
    void responseMatcherAcceptsUnmappedEndpointWhenStatusIsNotFound() {
        MockHttpServletRequest request = request("GET", "/api/v1/no-such-endpoint");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(404);

        assertThatNoException()
                .isThrownBy(() -> OpenApiValidation.responseMatcher().match(mvcResult(request, response)));
    }

    @Test
    void responseMatcherRejectsUnmappedEndpointWhenStatusIsNotNotFound() {
        MockHttpServletRequest request = request("GET", "/api/v1/no-such-endpoint");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        assertThatThrownBy(() -> OpenApiValidation.responseMatcher().match(mvcResult(request, response)))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("No API path found");
    }

    @Test
    void responseMatcherSkipsNonApiPathsAndTestProbes() {
        MockHttpServletRequest nonApiRequest = request("GET", "/actuator/health");
        MockHttpServletResponse nonApiResponse = jsonResponse(200, MediaType.APPLICATION_JSON_VALUE, """
                {"status": "UP"}
                """);

        assertThatNoException()
                .isThrownBy(() -> OpenApiValidation.responseMatcher().match(mvcResult(nonApiRequest, nonApiResponse)));

        MockHttpServletRequest probeRequest = request("GET", "/api/v1/test/current-user");
        MockHttpServletResponse probeResponse = jsonResponse(200, MediaType.APPLICATION_JSON_VALUE, """
                {"probe": true}
                """);

        assertThatNoException()
                .isThrownBy(() -> OpenApiValidation.responseMatcher().match(mvcResult(probeRequest, probeResponse)));
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }

    private static MockHttpServletRequest jsonRequest(String method, String uri, String jsonBody) {
        MockHttpServletRequest request = request(method, uri);
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(jsonBody.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static MockHttpServletResponse jsonResponse(int status, String contentType, String jsonBody) {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        response.setContentType(contentType);
        try {
            response.getOutputStream().write(jsonBody.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    private static MvcResult mvcResult(MockHttpServletRequest request, MockHttpServletResponse response) {
        return (MvcResult) Proxy.newProxyInstance(
                MvcResult.class.getClassLoader(),
                new Class<?>[] {MvcResult.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRequest" -> request;
                    case "getResponse" -> response;
                    default -> null;
                });
    }
}
