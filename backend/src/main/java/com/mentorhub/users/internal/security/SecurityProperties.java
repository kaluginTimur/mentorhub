package com.mentorhub.users.internal.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param trustedIssuers the issuers whose tokens are accepted; a token of any other issuer is rejected with 401.
 *     Required: startup fails without at least one
 * @param bootstrapAdminEmail the administrator bootstrap address (ADR 0005); empty disables the rule
 * @param issuerConnectTimeout connect timeout of OIDC discovery and JWK set requests to an issuer
 * @param issuerReadTimeout read timeout of those requests: a hung identity provider fails the request with 500 after
 *     it instead of holding the request thread
 */
@ConfigurationProperties("mentorhub.security")
@Validated
record SecurityProperties(
        @NotEmpty @Valid List<TrustedIssuer> trustedIssuers,
        @Nullable String bootstrapAdminEmail,
        @DefaultValue("2s") Duration issuerConnectTimeout,
        @DefaultValue("5s") Duration issuerReadTimeout) {

    /**
     * @param audience a value the token's {@code aud} claim must contain
     * @param jwkSetUri when set, keys are loaded from it instead of through OIDC discovery
     */
    record TrustedIssuer(
            @NotBlank String issuerUri,
            @NotBlank String audience,
            @Nullable String jwkSetUri) {}
}
