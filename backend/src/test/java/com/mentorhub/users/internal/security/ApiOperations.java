package com.mentorhub.users.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

/** The operations of the API contract, and the operations the application serves, for tests over every operation. */
final class ApiOperations {

    private static final List<String> HTTP_METHODS =
            List.of("get", "put", "post", "delete", "patch", "head", "options");

    private ApiOperations() {}

    /**
     * An operation of the contract. {@code uuidPathParameters} are the names of its path parameters declared as
     * {@code format: uuid}; {@code hasRequestBody} tells whether it declares a request body.
     */
    record Operation(
            String method,
            String path,
            String operationId,
            boolean isPublic,
            boolean hasRequestBody,
            List<String> uuidPathParameters) {

        String key() {
            return method + " " + path;
        }

        String name() {
            return operationId + " (" + key() + ")";
        }

        /** The path with a random UUID for every path parameter. */
        String uri() {
            return path.replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        }

        /** The path with {@code value} for {@code parameter} and a random UUID for every other path parameter. */
        String uri(String parameter, String value) {
            return path.replace("{" + parameter + "}", value)
                    .replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        }
    }

    @SuppressWarnings("unchecked")
    static List<Operation> fromContract() {
        Map<String, Object> contract;
        try (Reader reader = Files.newBufferedReader(Path.of("..", "api", "openapi.yaml"))) {
            contract = new Yaml().load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat((List<Object>) contract.get("security"))
                .as("the contract requires a token by default")
                .isNotEmpty();
        Map<String, Object> components = (Map<String, Object>) contract.getOrDefault("components", Map.of());
        Map<String, Object> sharedParameters = (Map<String, Object>) components.getOrDefault("parameters", Map.of());
        List<Operation> operations = new ArrayList<>();
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) contract.get("paths");
        paths.forEach((path, item) -> item.forEach((method, value) -> {
            if (HTTP_METHODS.contains(method)) {
                Map<String, Object> operation = (Map<String, Object>) value;
                List<Map<String, Object>> parameters = new ArrayList<>();
                parameters.addAll((List<Map<String, Object>>) item.getOrDefault("parameters", List.of()));
                parameters.addAll((List<Map<String, Object>>) operation.getOrDefault("parameters", List.of()));
                List<String> uuidPathParameters = parameters.stream()
                        .map(parameter -> resolve(parameter, sharedParameters))
                        .filter(parameter -> "path".equals(parameter.get("in")))
                        .filter(parameter -> "uuid"
                                .equals(((Map<String, Object>) parameter.getOrDefault("schema", Map.of()))
                                        .get("format")))
                        .map(parameter -> (String) parameter.get("name"))
                        .toList();
                Object security = operation.get("security");
                operations.add(new Operation(
                        method.toUpperCase(),
                        path,
                        (String) operation.get("operationId"),
                        security instanceof List<?> list && list.isEmpty(),
                        operation.containsKey("requestBody"),
                        uuidPathParameters));
            }
        }));
        return operations;
    }

    /** {@code METHOD path} of each handler under {@code /api/v1/}, without the test probe. */
    static Set<String> served(RequestMappingHandlerMapping handlerMapping) {
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
    private static Map<String, Object> resolve(Map<String, Object> parameter, Map<String, Object> sharedParameters) {
        Object ref = parameter.get("$ref");
        if (ref == null) {
            return parameter;
        }
        String prefix = "#/components/parameters/";
        assertThat((String) ref).as("parameter reference").startsWith(prefix);
        Map<String, Object> resolved =
                (Map<String, Object>) sharedParameters.get(((String) ref).substring(prefix.length()));
        assertThat(resolved).as("parameter " + ref).isNotNull();
        return resolved;
    }
}
