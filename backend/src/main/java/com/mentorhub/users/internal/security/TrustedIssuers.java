package com.mentorhub.users.internal.security;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.web.client.RestOperations;

/**
 * Resolves the authentication manager for a token's issuer: {@code null} (401) for an untrusted issuer. Decoders are
 * created on the first token of each issuer, so the application starts while the identity provider is unavailable.
 */
final class TrustedIssuers implements AuthenticationManagerResolver<String> {

    private final Map<String, SecurityProperties.TrustedIssuer> trusted;
    private final RestOperations rest;
    private final Converter<Jwt, ? extends AbstractAuthenticationToken> converter;
    private final Map<String, AuthenticationManager> managers = new ConcurrentHashMap<>();

    /** @param rest the client for OIDC discovery and JWK set requests; it carries the timeouts */
    TrustedIssuers(
            List<SecurityProperties.TrustedIssuer> trusted,
            RestOperations rest,
            Converter<Jwt, ? extends AbstractAuthenticationToken> converter) {
        this.trusted = trusted.stream()
                .collect(
                        Collectors.toUnmodifiableMap(SecurityProperties.TrustedIssuer::issuerUri, Function.identity()));
        this.rest = rest;
        this.converter = converter;
    }

    @Override
    public @Nullable AuthenticationManager resolve(String issuer) {
        SecurityProperties.TrustedIssuer config = trusted.get(issuer);
        if (config == null) {
            return null;
        }
        return managers.computeIfAbsent(issuer, ignored -> manager(config));
    }

    private AuthenticationManager manager(SecurityProperties.TrustedIssuer config) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder(config));
        provider.setJwtAuthenticationConverter(converter);
        return provider::authenticate;
    }

    private NimbusJwtDecoder decoder(SecurityProperties.TrustedIssuer config) {
        NimbusJwtDecoder decoder;
        try {
            // Both builders fetch through rest: discovery as well as the JWK set.
            decoder = (config.jwkSetUri() != null
                            ? NimbusJwtDecoder.withJwkSetUri(config.jwkSetUri())
                            : NimbusJwtDecoder.withIssuerLocation(config.issuerUri()))
                    .restOperations(rest)
                    .build();
        } catch (RuntimeException e) {
            throw new AuthenticationServiceException(
                    "Cannot load the configuration of issuer " + config.issuerUri(), e);
        }
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(config.issuerUri()),
                new JwtClaimValidator<@Nullable List<String>>(
                        JwtClaimNames.AUD, audience -> audience != null && audience.contains(config.audience()))));
        return decoder;
    }
}
