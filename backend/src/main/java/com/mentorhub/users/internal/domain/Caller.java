package com.mentorhub.users.internal.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The caller of the current request: the access status and, for a linked user, {@code users.id} as resolved at
 * authentication, plus the token claims that accepting an invitation needs. Domain data references {@code userId},
 * never {@code subject}.
 *
 * @param emailVerified {@code true} only when the token says {@code email_verified = true}
 */
public record Caller(
        AccessStatus status,
        @Nullable UUID userId,
        String issuer,
        String subject,
        @Nullable String email,
        boolean emailVerified,
        @Nullable String givenName,
        @Nullable String familyName) {}
