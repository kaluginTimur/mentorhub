package com.mentorhub.users.internal.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param trustedIssuers the issuers whose tokens are accepted; a token of any other issuer is rejected with 401.
 *     Required: startup fails without at least one
 * @param bootstrapAdminEmail the administrator bootstrap address (ADR 0005); empty disables the rule
 */
@ConfigurationProperties("mentorhub.security")
@Validated
record SecurityProperties(
        @NotEmpty @Valid List<TrustedIssuer> trustedIssuers,
        @Nullable String bootstrapAdminEmail) {

    /**
     * @param audience a value the token's {@code aud} claim must contain
     * @param jwkSetUri when set, keys are loaded from it instead of through OIDC discovery
     */
    record TrustedIssuer(
            @NotBlank String issuerUri,
            @NotBlank String audience,
            @Nullable String jwkSetUri) {}
}
