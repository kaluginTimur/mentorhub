package com.mentorhub.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** The API contract offers no view of audit entries across subjects. */
class AuditContractTests {

    /** The schema of one audit entry as the API returns it. */
    private static final String ENTRY = "#/components/schemas/HistoryEntry";

    private static final Set<String> METHODS =
            Set.of("get", "put", "post", "delete", "patch", "options", "head", "trace");

    private static final String SUBJECT_HISTORY = "/api/v1/[a-z-]+/\\{id}/history";

    @Test
    @DisplayName("AUD-6.1 There is no global audit view")
    @SuppressWarnings("unchecked")
    void noOperationReturnsEntriesAcrossSubjects() throws IOException {
        Map<String, Object> contract;
        try (Reader reader = Files.newBufferedReader(Path.of("..", "api", "openapi.yaml"))) {
            contract = new Yaml().load(reader);
        }

        Map<String, Object> paths = (Map<String, Object>) contract.get("paths");

        // Every operation whose responses hold audit entries, directly or through referenced schemas.
        Map<String, String> returningEntries = new TreeMap<>();
        paths.forEach((path, item) -> ((Map<String, Object>) item).forEach((method, operation) -> {
            if (METHODS.contains(method)
                    && reaches(((Map<String, Object>) operation).get("responses"), contract, new HashSet<>())) {
                returningEntries.put(method.toUpperCase() + " " + path, path);
            }
        }));

        assertThat(returningEntries).as("operations returning audit entries").isNotEmpty();
        assertThat(returningEntries)
                .as("operations returning audit entries are the history of one subject")
                .allSatisfy((operation, path) -> assertThat(path).matches(SUBJECT_HISTORY));
        assertThat(paths.keySet()).noneMatch(path -> path.toLowerCase().contains("audit"));
        assertThat(paths.keySet())
                .filteredOn(path -> path.endsWith("/history"))
                .allMatch(path -> path.matches(SUBJECT_HISTORY));
    }

    /** Whether {@code node} refers to {@link #ENTRY}, following local {@code $ref}s through the contract. */
    @SuppressWarnings("unchecked")
    private static boolean reaches(Object node, Map<String, Object> contract, Set<String> visited) {
        if (node instanceof Map<?, ?> map) {
            if (map.get("$ref") instanceof String ref) {
                if (ref.equals(ENTRY)) {
                    return true;
                }
                if (visited.add(ref) && reaches(resolve(ref, contract), contract, visited)) {
                    return true;
                }
            }
            return ((Map<String, Object>) map).values().stream().anyMatch(value -> reaches(value, contract, visited));
        }
        if (node instanceof List<?> list) {
            return list.stream().anyMatch(value -> reaches(value, contract, visited));
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Object resolve(String ref, Map<String, Object> contract) {
        assertThat(ref).as("only local references are expected").startsWith("#/");
        Object node = contract;
        for (String name : ref.substring(2).split("/")) {
            node = ((Map<String, Object>) node).get(name);
        }
        assertThat(node).as("reference %s resolves", ref).isNotNull();
        return node;
    }
}
