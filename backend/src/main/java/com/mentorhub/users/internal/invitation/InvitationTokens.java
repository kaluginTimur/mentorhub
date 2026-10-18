package com.mentorhub.users.internal.invitation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Invitation tokens: 32 random bytes, base64url without padding (43 characters). Only the SHA-256 hash of the token,
 * in lowercase hex, is stored and looked up.
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

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    /** The hash to look up, or empty for a token that cannot exist, so it needs no query. */
    static Optional<String> hashIfWellFormed(String token) {
        return FORMAT.matcher(token).matches() ? Optional.of(hash(token)) : Optional.empty();
    }
}
