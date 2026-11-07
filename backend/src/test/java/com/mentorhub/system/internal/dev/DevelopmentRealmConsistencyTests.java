package com.mentorhub.system.internal.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.users.internal.security.TestIssuer;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The local Keycloak realm: one account per persona of DEV-1, and the settings identity relies on. */
class DevelopmentRealmConsistencyTests {

    private static final Path REALM = Personas.REALM;

    @Test
    void theRealmHasAnAccountForEveryPersonaAndNoOther() {
        assertThat(Personas.realmEmailsById().keySet()).containsExactlyInAnyOrderElementsOf(Personas.ALL_SUBJECTS);
    }

    // Only Not invited has no user whose email could be compared; the others are compared with the sample center data.
    @Test
    void theAccountOfThePersonaNotInvitedIsTheStranger() {
        assertThat(Personas.realmEmailsById()).containsEntry(Personas.NOT_INVITED, "stranger@mentorhub.local");
    }

    @Test
    @DisplayName("IDN-8.1 A new account confirms its email")
    void realmRequiresEmailVerification() {
        JsonNode realm = realm();

        assertThat(realm.path("registrationAllowed").asBoolean()).isTrue();
        assertThat(realm.path("verifyEmail").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("IDN-8.3 An email is registered once")
    void realmRefusesDuplicateEmails() {
        JsonNode realm = realm();

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
}
