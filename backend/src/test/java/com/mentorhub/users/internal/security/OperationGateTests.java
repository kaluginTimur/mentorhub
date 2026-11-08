package com.mentorhub.users.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.web.Problems;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.assertj.core.api.SoftAssertions;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.json.JsonMapper;

/**
 * Authentication and the gate hold for every operation the application serves under {@code /api/v1}: without a token
 * each answers 401, and a caller who is not invited or disabled is refused, except the contract's public operations
 * and the operations the requirements exempt from the gate. The mechanism itself is tested against the probe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
class OperationGateTests {

    /** Reading the current user and accepting an invitation admit every signed-in caller (IDN-3, IDN-5, INV-11). */
    static final Set<String> GATE_EXEMPTIONS = Set.of("getMe", "acceptInvitation");

    static final List<String> HTTP_METHODS = List.of("get", "put", "post", "delete", "patch", "head", "options");

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

    record Operation(String method, String path, String operationId, boolean isPublic) {

        String key() {
            return method + " " + path;
        }

        String uri() {
            return path.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        }
    }

    @Test
    void everyServedOperationIsBehindAuthenticationAndTheGateUnlessTheDocumentsExemptIt() throws IOException {
        List<Operation> operations = contractOperations();
        assertThat(servedOperations())
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
            String name = operation.operationId() + " (" + operation.key() + ")";
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

    /** {@code METHOD path} of each handler under {@code /api/v1/}, without the test probe. */
    private Set<String> servedOperations() {
        return handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType() != PermissionProbeController.class)
                .flatMap(entry -> {
                    Set<RequestMethod> methods =
                            entry.getKey().getMethodsCondition().getMethods();
                    List<String> names = methods.isEmpty()
                            ? List.of("ANY")
                            : methods.stream().map(RequestMethod::name).toList();
                    return entry.getKey().getPatternValues().stream()
                            .filter(pattern -> pattern.startsWith("/api/v1/"))
                            .flatMap(pattern -> names.stream().map(method -> method + " " + pattern));
                })
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("unchecked")
    private static List<Operation> contractOperations() throws IOException {
        Map<String, Object> contract;
        try (Reader reader = Files.newBufferedReader(Path.of("..", "api", "openapi.yaml"))) {
            contract = new Yaml().load(reader);
        }
        assertThat((List<Object>) contract.get("security"))
                .as("the contract requires a token by default")
                .isNotEmpty();
        List<Operation> operations = new ArrayList<>();
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) contract.get("paths");
        paths.forEach((path, item) -> item.forEach((method, value) -> {
            if (HTTP_METHODS.contains(method)) {
                Map<String, Object> operation = (Map<String, Object>) value;
                Object security = operation.get("security");
                operations.add(new Operation(
                        method.toUpperCase(),
                        path,
                        (String) operation.get("operationId"),
                        security instanceof List<?> list && list.isEmpty()));
            }
        }));
        return operations;
    }
}
