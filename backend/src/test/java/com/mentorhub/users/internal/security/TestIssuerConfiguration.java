package com.mentorhub.users.internal.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;

/**
 * Trusts {@link TestIssuer}, {@link TestIssuer#UNREACHABLE_ISSUER} and {@link TestIssuer#HANGING_ISSUER} (with 1 s
 * issuer timeouts), sets {@link TestIssuer#BOOTSTRAP_EMAIL} as the administrator bootstrap address and, in
 * the dev profile, links the development users to identities of {@link TestIssuer}.
 */
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
            registry.add("mentorhub.security.trusted-issuers[1].issuer-uri", () -> TestIssuer.UNREACHABLE_ISSUER);
            registry.add("mentorhub.security.trusted-issuers[1].audience", () -> TestIssuer.AUDIENCE);
            registry.add("mentorhub.security.trusted-issuers[1].jwk-set-uri", () -> TestIssuer.UNREACHABLE_JWK_SET_URI);
            registry.add("mentorhub.security.trusted-issuers[2].issuer-uri", () -> TestIssuer.HANGING_ISSUER);
            registry.add("mentorhub.security.trusted-issuers[2].audience", () -> TestIssuer.AUDIENCE);
            registry.add("mentorhub.security.trusted-issuers[2].jwk-set-uri", issuer::hangingJwkSetUri);
            // Shorter than the defaults, so the hanging issuer fails fast in tests.
            registry.add("mentorhub.security.issuer-connect-timeout", () -> "1s");
            registry.add("mentorhub.security.issuer-read-timeout", () -> "1s");
            registry.add("mentorhub.security.bootstrap-admin-email", () -> TestIssuer.BOOTSTRAP_EMAIL);
            // In dev-profile tests, the development users are linked to identities of this issuer.
            registry.add("mentorhub.development-users.issuer-uri", () -> TestIssuer.ISSUER);
        };
    }
}
