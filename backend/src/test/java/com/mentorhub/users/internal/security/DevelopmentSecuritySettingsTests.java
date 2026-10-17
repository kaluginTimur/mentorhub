package com.mentorhub.users.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;

/**
 * The dev profile on the test classpath, as {@code TestMentorHubApplication} runs it: the settings of
 * {@code application-dev.yaml} replace the placeholder issuer of the test-wide {@code config/application.yaml}, so
 * sign-in through the local Keycloak works.
 */
class DevelopmentSecuritySettingsTests {

    @Test
    void devProfileTrustsOnlyTheLocalKeycloak() {
        var environment = new StandardEnvironment();
        environment.setActiveProfiles("dev");
        ConfigDataEnvironmentPostProcessor.applyTo(environment);

        SecurityProperties security = Binder.get(environment)
                .bind("mentorhub.security", SecurityProperties.class)
                .get();

        assertThat(security.trustedIssuers()).singleElement().satisfies(issuer -> {
            assertThat(issuer.issuerUri()).isEqualTo("http://localhost:8180/realms/mentorhub");
            assertThat(issuer.audience()).isEqualTo("mentorhub-api");
            assertThat(issuer.jwkSetUri()).isNull();
        });
        assertThat(security.bootstrapAdminEmail()).isEqualTo("admin@mentorhub.local");
        assertThat(environment.getProperty("mentorhub.security.client.issuer-uri"))
                .isEqualTo("http://localhost:8180/realms/mentorhub");
        assertThat(environment.getProperty("mentorhub.security.client.client-id"))
                .isEqualTo("mentorhub-web");
    }
}
