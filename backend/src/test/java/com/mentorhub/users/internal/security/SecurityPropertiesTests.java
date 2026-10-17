package com.mentorhub.users.internal.security;

import static com.mentorhub.support.StartupFailures.assertStartupFailsNaming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

@ExtendWith(OutputCaptureExtension.class)
class SecurityPropertiesTests {

    // AC-26
    @Test
    void missingTrustedIssuersFailStartup(CapturedOutput output) {
        assertStartupFailsNaming(output, SecurityPropertiesOnly.class, "mentorhub.security.trustedIssuers");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    static class SecurityPropertiesOnly {}
}
