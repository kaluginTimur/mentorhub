package com.mentorhub.users.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.support.EndpointTest;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.ApiOperations.Operation;
import com.mentorhub.web.Problems;
import java.util.List;
import java.util.Set;
import org.assertj.core.api.SoftAssertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.json.JsonMapper;

/**
 * Authentication and the gate hold for every operation the application serves under {@code /api/v1}: without a token
 * each answers 401, and a caller who is not invited or disabled is refused, except the contract's public operations
 * and the operations the requirements exempt from the gate. The mechanism itself is tested against the probe.
 */
@EndpointTest
class OperationGateTests {

    /** Reading the current user and accepting an invitation admit every signed-in caller (IDN-3, IDN-5, INV-11). */
    static final Set<String> GATE_EXEMPTIONS = Set.of("getMe", "acceptInvitation");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @BeforeEach
    void setUp() {
        TestUsers users = new TestUsers(jdbc);
        users.deleteAll();
        users.link(users.create("disabled@example.com", UserStatus.DISABLED), "disabled");
    }

    @Test
    void everyServedOperationIsBehindAuthenticationAndTheGateUnlessTheDocumentsExemptIt() {
        List<Operation> operations = ApiOperations.fromContract();
        assertThat(ApiOperations.served(handlerMapping))
                .as("operations the application serves under /api/v1 compared with the contract")
                .containsExactlyInAnyOrderElementsOf(
                        operations.stream().map(Operation::key).toList());
        assertThat(operations)
                .extracting(Operation::operationId)
                .as("the gate exemptions are contract operations that need a token")
                .containsAll(GATE_EXEMPTIONS);
        assertThat(operations)
                .filteredOn(operation -> GATE_EXEMPTIONS.contains(operation.operationId()))
                .noneMatch(Operation::isPublic);

        String notInvited = issuer.token("stranger").bearer();
        String disabled = issuer.token("disabled").bearer();
        String unauthorized = "401 " + Problems.UNAUTHORIZED;
        String notInvitedRefusal = "403 " + Problems.NOT_INVITED;
        String disabledRefusal = "403 " + Problems.ACCESS_ENDED;

        SoftAssertions softly = new SoftAssertions();
        for (Operation operation : operations) {
            String name = operation.name();
            String withoutToken = outcome(operation, null);
            String asNotInvited = outcome(operation, notInvited);
            String asDisabled = outcome(operation, disabled);

            if (operation.isPublic()) {
                softly.assertThat(withoutToken).as(name + " without a token").doesNotStartWith("401");
            } else {
                softly.assertThat(withoutToken).as(name + " without a token").isEqualTo(unauthorized);
            }
            if (operation.isPublic() || GATE_EXEMPTIONS.contains(operation.operationId())) {
                softly.assertThat(asNotInvited)
                        .as(name + " by a caller who is not invited")
                        .doesNotStartWith("401")
                        .isNotEqualTo(notInvitedRefusal);
                softly.assertThat(asDisabled)
                        .as(name + " by a disabled user")
                        .doesNotStartWith("401")
                        .isNotEqualTo(disabledRefusal);
            } else {
                softly.assertThat(asNotInvited)
                        .as(name + " by a caller who is not invited")
                        .isEqualTo(notInvitedRefusal);
                softly.assertThat(asDisabled).as(name + " by a disabled user").isEqualTo(disabledRefusal);
            }
        }
        softly.assertAll();
    }

    /** The status and, for a problem, its type. Path ids are random and there is no body. */
    private String outcome(Operation operation, @Nullable String authorization) {
        MockMvcRequestBuilder request =
                mvc.method(HttpMethod.valueOf(operation.method())).uri(operation.uri());
        if (operation.hasRequestBody()) {
            request = request.contentType(MediaType.APPLICATION_JSON);
        }
        if (authorization != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        MvcTestResult result = request.exchange();
        int status = result.getResponse().getStatus();
        String contentType = result.getResponse().getContentType();
        if (contentType == null
                || !MediaType.APPLICATION_PROBLEM_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))) {
            return String.valueOf(status);
        }
        return status + " "
                + jsonMapper
                        .readTree(result.getResponse().getContentAsByteArray())
                        .path("type")
                        .asString();
    }
}
