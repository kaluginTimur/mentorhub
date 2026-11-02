package com.mentorhub.users.internal.security;

import static com.mentorhub.support.StartupFailures.assertStartupFailsNaming;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

@ExtendWith(OutputCaptureExtension.class)
class SecurityPropertiesTests {

    @Test
    @DisplayName("IDN-2.1 Missing trusted issuers stop the application")
    void missingTrustedIssuersFailStartup(CapturedOutput output) {
        assertStartupFailsNaming(output, SecurityPropertiesOnly.class, "mentorhub.security.trustedIssuers");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    static class SecurityPropertiesOnly {}
}
