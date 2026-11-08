package com.mentorhub.users.internal.invitation;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.mail.MailMessage;
import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** The text of the invitation email. */
class InvitationEmailTextTests {

    /** The expected rendering of an expiry, written independently of the production formatter's pattern. */
    static String utc(Instant instant) {
        return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
                        .withZone(ZoneOffset.UTC)
                        .format(instant)
                + " UTC";
    }

    @Test
    void showsTheExpiryInUtcWithoutALeadingZeroInTheDay() {
        MailMessage message = InvitationEmailText.message(
                "ivan@example.com", URI.create("https://app.test/invite/abc"), Instant.parse("2026-11-05T23:30:00Z"));

        assertThat(message.text()).contains("The link expires on 5 Nov 2026, 23:30 UTC.");
    }
}
