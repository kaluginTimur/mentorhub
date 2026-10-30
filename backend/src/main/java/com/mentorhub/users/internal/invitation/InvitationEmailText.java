package com.mentorhub.users.internal.invitation;

import com.mentorhub.mail.MailMessage;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The invitation email (spec invitation-emails, AC-14): plain text in English, impersonal, with the link alone on its
 * line so no mail client takes punctuation into it.
 */
final class InvitationEmailText {

    static final String SUBJECT = "You're invited to MentorHub";

    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern(
                    "d MMM uuuu, HH:mm 'UTC'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private InvitationEmailText() {}

    static MailMessage message(String email, URI link, Instant expiresAt) {
        String text = """
                You're invited to MentorHub, the training center's mentoring platform.

                Open this link and sign in or create an account with %s:

                %s

                The link expires on %s.

                If you didn't expect this invitation, ignore this email.
                """.formatted(email, link, EXPIRY.format(expiresAt));
        return new MailMessage(email, SUBJECT, text);
    }
}
