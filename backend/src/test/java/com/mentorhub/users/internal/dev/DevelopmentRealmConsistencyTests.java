package com.mentorhub.users.internal.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.users.internal.security.TestIssuer;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("IDN-8.1 A new account confirms its email and an email is registered once")
    void realmRequiresEmailVerificationAndRefusesDuplicateEmails() {
        JsonNode realm = realm();

        assertThat(realm.path("registrationAllowed").asBoolean()).isTrue();
        assertThat(realm.path("verifyEmail").asBoolean()).isTrue();
        assertThat(realm.path("duplicateEmailsAllowed").asBoolean()).isFalse();
        assertThat(realm.path("registrationEmailAsUsername").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("IDN-8.2 A token for the client carries the API audience")
    void webClientAccessTokenCarriesTheApiAudience() {
        JsonNode client = realm().path("clients")
                .valueStream()
                .filter(candidate ->
                        "mentorhub-web".equals(candidate.path("clientId").asString()))
                .findFirst()
                .orElseThrow();

        assertThat(client.path("protocolMappers").valueStream()).anySatisfy(mapper -> {
            assertThat(mapper.path("protocolMapper").asString()).isEqualTo("oidc-audience-mapper");
            JsonNode config = mapper.path("config");
            assertThat(config.path("included.custom.audience").asString()).isEqualTo(TestIssuer.AUDIENCE);
            assertThat(config.path("access.token.claim").asString()).isEqualTo("true");
        });
    }

    private static JsonNode realm() {
        return JsonMapper.builder().build().readTree(REALM.toFile());
    }

    private static Map<String, String> realmEmailsById() {
        return realm().path("users")
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
