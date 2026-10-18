package com.mentorhub.users.internal.invitation;

import static com.mentorhub.support.StartupFailures.assertStartupFailsNaming;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

@ExtendWith(OutputCaptureExtension.class)
class InvitationPropertiesTests {

    // Spec, Configuration: mentorhub.app-url is required.
    @Test
    void missingAppUrlFailsStartup(CapturedOutput output) {
        assertStartupFailsNaming(output, InvitationPropertiesOnly.class, "mentorhub.appUrl");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://mentorhub.example", "https://mentorhub.example/", "https://mentorhub.example//"})
    void theLinkIsTheAppUrlWithTheInvitePath(String appUrl) {
        var properties =
                new InvitationProperties(URI.create(appUrl), new InvitationProperties.Invitations(Duration.ofDays(7)));

        assertThat(properties.link("Xf3kq9WvT0bD2mJ7sL1pR8yZ4cN6hA5eG0uI3oK9wQe"))
                .isEqualTo(URI.create("https://mentorhub.example/invite/Xf3kq9WvT0bD2mJ7sL1pR8yZ4cN6hA5eG0uI3oK9wQe"));
    }

    @Test
    void theLinkKeepsAPathOfTheAppUrl() {
        var properties = new InvitationProperties(
                URI.create("https://example.org/mentorhub/"), new InvitationProperties.Invitations(Duration.ofDays(7)));

        assertThat(properties.link("t")).isEqualTo(URI.create("https://example.org/mentorhub/invite/t"));
    }

    @Test
    void theLinkLifetimeMustBePositive() {
        assertThatThrownBy(() -> new InvitationProperties.Invitations(Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mentorhub.invitations.link-lifetime");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(InvitationProperties.class)
    static class InvitationPropertiesOnly {}
}
