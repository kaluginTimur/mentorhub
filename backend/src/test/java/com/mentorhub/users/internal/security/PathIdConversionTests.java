package com.mentorhub.users.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.support.EndpointTest;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.ApiOperations.Operation;
import com.mentorhub.web.Problems;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every path parameter the contract declares as a UUID rejects a value that is not one, naming the parameter, on
 * every operation. A declared request body is sent as {@code {}}. {@link OperationGateTests} checks that the served operations are the contract's; the mechanism is
 * tested against the probe.
 */
@EndpointTest
class PathIdConversionTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    JsonMapper jsonMapper;

    String holder;

    @BeforeEach
    void setUp() {
        TestUsers users = new TestUsers(jdbc);
        users.deleteAll();
        users.link(users.create("holder@example.com", UserStatus.ACTIVE, Role.values()), "holder");
        holder = issuer.token("holder").bearer();
    }

    @Test
    void everyUuidPathParameterRejectsAValueThatIsNotAUuid() {
        JsonNode me = json(mvc.get()
                .uri("/api/v1/me")
                .header(HttpHeaders.AUTHORIZATION, holder)
                .exchange());
        List<String> permissions = new ArrayList<>();
        for (JsonNode permission : me.path("user").path("permissions")) {
            permissions.add(permission.asString());
        }
        assertThat(permissions)
                .as("the caller holds every permission")
                .containsExactlyInAnyOrder(
                        Arrays.stream(Permission.values()).map(Enum::name).toArray(String[]::new));
        List<Operation> operations = ApiOperations.fromContract().stream()
                .filter(operation -> !operation.uuidPathParameters().isEmpty())
                .toList();
        assertThat(operations).as("operations with a UUID path parameter").isNotEmpty();

        String validationFailed = "400 " + Problems.VALIDATION_FAILED;
        SoftAssertions softly = new SoftAssertions();
        for (Operation operation : operations) {
            for (String parameter : operation.uuidPathParameters()) {
                MockMvcRequestBuilder request = mvc.method(HttpMethod.valueOf(operation.method()))
                        .uri(operation.uri(parameter, "not-a-uuid"))
                        .header(HttpHeaders.AUTHORIZATION, holder);
                if (operation.hasRequestBody()) {
                    request = request.contentType(MediaType.APPLICATION_JSON).content("{}");
                }
                MvcTestResult result = request.exchange();
                JsonNode body = json(result);

                softly.assertThat(result.getResponse().getStatus() + " "
                                + body.path("type").asString())
                        .as(operation.name() + " with " + parameter + " not a UUID")
                        .isEqualTo(validationFailed);
                softly.assertThat(errors(body))
                        .as(operation.name() + " errors")
                        .contains(parameter + ": must be a UUID");
            }
        }
        softly.assertAll();
    }

    private JsonNode json(MvcTestResult result) {
        byte[] content = result.getResponse().getContentAsByteArray();
        return jsonMapper.readTree(content.length == 0 ? "{}".getBytes(StandardCharsets.UTF_8) : content);
    }

    private static List<String> errors(JsonNode body) {
        List<String> errors = new ArrayList<>();
        for (JsonNode error : body.path("errors")) {
            errors.add(error.path("field").asString() + ": "
                    + error.path("message").asString());
        }
        return errors;
    }
}
