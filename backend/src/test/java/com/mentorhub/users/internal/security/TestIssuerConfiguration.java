package com.mentorhub.users.internal.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/** Trusts {@link TestIssuer} and sets {@link TestIssuer#BOOTSTRAP_EMAIL} as the administrator bootstrap address. */
@TestConfiguration(proxyBeanMethods = false)
public class TestIssuerConfiguration {

    @Bean(destroyMethod = "close")
    TestIssuer testIssuer() {
        return new TestIssuer();
    }

    @Bean
    DynamicPropertyRegistrar testIssuerProperties(TestIssuer issuer) {
        return registry -> {
            registry.add("mentorhub.security.trusted-issuers[0].issuer-uri", () -> TestIssuer.ISSUER);
            registry.add("mentorhub.security.trusted-issuers[0].audience", () -> TestIssuer.AUDIENCE);
            registry.add("mentorhub.security.trusted-issuers[0].jwk-set-uri", issuer::jwkSetUri);
            registry.add("mentorhub.security.bootstrap-admin-email", () -> TestIssuer.BOOTSTRAP_EMAIL);
        };
    }
}
