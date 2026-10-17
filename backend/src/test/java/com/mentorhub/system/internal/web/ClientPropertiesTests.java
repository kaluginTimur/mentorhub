package com.mentorhub.system.internal.web;

import static com.mentorhub.support.StartupFailures.assertStartupFailsNaming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;

@ExtendWith(OutputCaptureExtension.class)
class ClientPropertiesTests {

    // AC-26
    @Test
    void missingClientConfigurationFailsStartup(CapturedOutput output) {
        assertStartupFailsNaming(
                output,
                ClientPropertiesOnly.class,
                "mentorhub.security.client.issuerUri",
                "mentorhub.security.client.clientId");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ClientProperties.class)
    static class ClientPropertiesOnly {}
}
