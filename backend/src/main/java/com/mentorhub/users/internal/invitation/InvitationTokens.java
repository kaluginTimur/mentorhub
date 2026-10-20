package com.mentorhub.users.internal.invitation;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Invitation tokens: 32 random bytes, base64url without padding (43 characters). The token is stored and looked up
 * as is.
 */
final class InvitationTokens {

    private static final int TOKEN_BYTES = 32;
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private InvitationTokens() {}

    static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The token to look up, or empty for a token that cannot exist, so it needs no query. */
    static Optional<String> ifWellFormed(String token) {
        return FORMAT.matcher(token).matches() ? Optional.of(token) : Optional.empty();
    }
}
