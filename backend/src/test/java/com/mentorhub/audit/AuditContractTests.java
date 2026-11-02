package com.mentorhub.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** The API contract offers no view of audit entries across subjects. */
class AuditContractTests {

    @Test
    @DisplayName("AUD-6.1 There is no global audit view")
    @SuppressWarnings("unchecked")
    void noOperationReturnsEntriesAcrossSubjects() throws IOException {
        Map<String, Object> contract;
        try (Reader reader = Files.newBufferedReader(Path.of("..", "api", "openapi.yaml"))) {
            contract = new Yaml().load(reader);
        }

        Map<String, Object> paths = (Map<String, Object>) contract.get("paths");

        assertThat(paths.keySet()).noneMatch(path -> path.toLowerCase().contains("audit"));
        assertThat(paths.keySet())
                .filteredOn(path -> path.endsWith("/history"))
                .allMatch(path -> path.matches("/api/v1/[a-z-]+/\\{id}/history"));
    }
}
