package com.mentorhub.users.internal.bootstrap;

import org.jspecify.annotations.Nullable;

/** The only token claims the application reads besides {@code aud}, which the decoder validates. */
public record SignInClaims(
        String issuer,
        String subject,
        @Nullable String email,
        boolean emailVerified,
        @Nullable String givenName,
        @Nullable String familyName) {}
