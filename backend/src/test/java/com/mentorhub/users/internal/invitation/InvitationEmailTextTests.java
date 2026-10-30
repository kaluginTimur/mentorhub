package com.mentorhub.users.internal.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.mail.MailMessage;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** Spec invitation-emails, AC-14. */
class InvitationEmailTextTests {

    /** The expected rendering of an expiry, written independently of the production formatter's pattern. */
    static String utc(Instant instant) {
        return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
                        .withZone(ZoneOffset.UTC)
                        .format(instant)
                + " UTC";
    }

    @Test
    void rendersTheImpersonalPlainTextEmail() {
        MailMessage message = InvitationEmailText.message(
                "ivan@example.com", URI.create("https://app.test/invite/abc"), Instant.parse("2026-10-25T14:00:59Z"));

        assertThat(message.to()).isEqualTo("ivan@example.com");
        assertThat(message.subject()).isEqualTo("You're invited to MentorHub");
        assertThat(message.text()).isEqualTo("""
                You're invited to MentorHub, the training center's mentoring platform.

                Open this link and sign in or create an account with ivan@example.com:

                https://app.test/invite/abc

                The link expires on 25 Oct 2026, 14:00 UTC.

                If you didn't expect this invitation, ignore this email.
                """);
    }

    @Test
    void showsTheExpiryInUtcWithoutALeadingZeroInTheDay() {
        MailMessage message = InvitationEmailText.message(
                "ivan@example.com", URI.create("https://app.test/invite/abc"), Instant.parse("2026-11-05T23:30:00Z"));

        assertThat(message.text()).contains("The link expires on 5 Nov 2026, 23:30 UTC.");
    }
}
