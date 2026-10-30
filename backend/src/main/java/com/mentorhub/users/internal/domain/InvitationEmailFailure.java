package com.mentorhub.users.internal.domain;

/**
 * The stored reason of a {@link InvitationEmailStatus#FAILED} email: one line of 1 to {@value #MAX_LENGTH} characters,
 * as the {@code failure} check of {@code invitation_emails} requires. Build it with {@link #of(String, String...)}, which
 * removes the token and the link (AC-15); the database cannot check that.
 *
 * @param text the reason, without control characters
 */
public record InvitationEmailFailure(String text) {

    public static final int MAX_LENGTH = 500;

    static final String REDACTED = "[redacted]";

    private static final String UNKNOWN = "Unknown mail failure";

    public InvitationEmailFailure {
        int length = text.codePointCount(0, text.length());
        if (length < 1 || length > MAX_LENGTH || !text.equals(oneLine(text))) {
            throw new IllegalArgumentException("A failure must be one line of 1 to " + MAX_LENGTH + " characters");
        }
    }

    /**
     * @param description what failed, for example a mail exception's message
     * @param secrets values that must not be stored, such as the token; each occurrence is replaced before the text is
     *     shortened, so no part of a secret remains
     */
    public static InvitationEmailFailure of(String description, String... secrets) {
        String text = description;
        for (String secret : secrets) {
            if (!secret.isEmpty()) {
                text = text.replace(secret, REDACTED);
            }
        }
        text = oneLine(text);
        if (text.isEmpty()) {
            text = UNKNOWN;
        }
        if (text.codePointCount(0, text.length()) > MAX_LENGTH) {
            text = text.substring(0, text.offsetByCodePoints(0, MAX_LENGTH)).strip();
        }
        return new InvitationEmailFailure(text);
    }

    // PostgreSQL text cannot store NUL; SMTP replies contain line breaks.
    private static String oneLine(String text) {
        return text.replaceAll("[\\p{Cntrl}\\s]+", " ").strip();
    }
}
