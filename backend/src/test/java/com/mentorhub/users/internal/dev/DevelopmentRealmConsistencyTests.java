package com.mentorhub.users.internal.dev;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The development users of {@code application-dev.yaml} must match the realm users by id and email. */
class DevelopmentRealmConsistencyTests {

    private static final Path REALM = Path.of("../infra/keycloak/mentorhub-realm.json");

    @Test
    void everyDevelopmentUserIsARealmUserWithTheSameIdAndEmail() {
        Map<String, String> realmEmailsById = realmEmailsById();
        DevelopmentUsersProperties properties = developmentUsers();

        assertThat(properties.users())
                .isNotEmpty()
                .allSatisfy(user ->
                        assertThat(realmEmailsById).as(user.email()).containsEntry(user.subject(), user.email()));
    }

    @Test
    void theStrangerExistsOnlyInTheRealm() {
        Map<String, String> realmEmailsById = realmEmailsById();
        var configuredIds = developmentUsers().users().stream()
                .map(DevelopmentUsersProperties.DevelopmentUser::subject)
                .collect(Collectors.toSet());

        assertThat(realmEmailsById).containsValue("stranger@mentorhub.local");
        assertThat(realmEmailsById.entrySet())
                .filteredOn(entry -> !configuredIds.contains(entry.getKey()))
                .extracting(Map.Entry::getValue)
                .containsExactly("stranger@mentorhub.local");
    }

    private static Map<String, String> realmEmailsById() {
        JsonNode realm = JsonMapper.builder().build().readTree(REALM.toFile());
        return realm.path("users")
                .valueStream()
                .collect(Collectors.toMap(
                        user -> user.path("id").asString(),
                        user -> user.path("email").asString(),
                        (first, second) -> first,
                        java.util.LinkedHashMap::new));
    }

    private static DevelopmentUsersProperties developmentUsers() {
        var environment = new StandardEnvironment();
        environment.setActiveProfiles("dev");
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        return Binder.get(environment)
                .bind("mentorhub.development-users", DevelopmentUsersProperties.class)
                .get();
    }
}
