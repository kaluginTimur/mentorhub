package com.mentorhub.users.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Spec invitation-emails, AC-11 and AC-15: a short reason without the token. */
class InvitationEmailFailureTests {

    static final String TOKEN = "Xf3kq9WvT0bD2mJ7sL1pR8yZ4cN6hA5eG0uI3oK9wQe";
    static final String LINK = "https://app.test/invite/" + TOKEN;

    @Test
    void removesEveryOccurrenceOfTheSecrets() {
        InvitationEmailFailure failure =
                InvitationEmailFailure.of("554 rejected " + LINK + " and " + TOKEN, TOKEN, LINK);

        assertThat(failure.text())
                .doesNotContain(TOKEN)
                .isEqualTo("554 rejected https://app.test/invite/[redacted] and [redacted]");
    }

    @Test
    void joinsLinesAndRemovesControlCharacters() {
        assertThat(InvitationEmailFailure.of("  550 no such user\r\n\tsecond line\u0000end  ")
                        .text())
                .isEqualTo("550 no such user second line end");
    }

    @Test
    void redactsBeforeShorteningSoNoPartOfASecretRemains() {
        String description = "x".repeat(InvitationEmailFailure.MAX_LENGTH - 10) + TOKEN;

        InvitationEmailFailure failure = InvitationEmailFailure.of(description, TOKEN);

        assertThat(failure.text()).hasSize(InvitationEmailFailure.MAX_LENGTH).doesNotContain(TOKEN.substring(0, 5));
    }

    @Test
    void shortensByCharactersNotCodeUnits() {
        String description = "😀".repeat(InvitationEmailFailure.MAX_LENGTH + 1);

        String text = InvitationEmailFailure.of(description).text();

        assertThat(text.codePointCount(0, text.length())).isEqualTo(InvitationEmailFailure.MAX_LENGTH);
        assertThat(text).isEqualTo("😀".repeat(InvitationEmailFailure.MAX_LENGTH));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  \r\n "})
    void anEmptyDescriptionBecomesAGenericReason(String description) {
        assertThat(InvitationEmailFailure.of(description).text()).isEqualTo("Unknown mail failure");
    }

    @Test
    void anEmptySecretIsIgnored() {
        assertThat(InvitationEmailFailure.of("refused", "").text()).isEqualTo("refused");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " padded ", "two\nlines"})
    void theConstructorRejectsTextTheDatabaseWouldNotStoreAsIs(String text) {
        assertThatThrownBy(() -> new InvitationEmailFailure(text)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theConstructorRejectsTooLongText() {
        assertThatThrownBy(() -> new InvitationEmailFailure("x".repeat(InvitationEmailFailure.MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
