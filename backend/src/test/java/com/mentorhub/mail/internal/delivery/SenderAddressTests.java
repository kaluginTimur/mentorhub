package com.mentorhub.mail.internal.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;

class SenderAddressTests {

    @ParameterizedTest
    @ValueSource(
            strings = {
                "no-reply@example.org",
                "MentorHub <no-reply@example.org>",
                "\"MentorHub, Training\" <no-reply@example.org>",
                "<no-reply@mentorhub.local>"
            })
    void acceptsOneAddressWithAnOptionalDisplayName(String from) {
        assertThat(SenderAddress.parse(from).getAddress()).isIn("no-reply@example.org", "no-reply@mentorhub.local");
    }

    // Spec invitation-emails, AC-1.
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {
                "  ",
                "MentorHub",
                "no-reply",
                "no-reply@",
                "a@example.org, b@example.org",
                "MentorHub <no-reply@example.org",
                "Team: a@example.org, b@example.org;",
                "no reply@example.org"
            })
    void rejectsAnythingElseNamingTheProperty(String from) {
        assertThatThrownBy(() -> SenderAddress.parse(from))
                .isInstanceOf(InvalidConfigurationPropertyValueException.class)
                .extracting(e -> ((InvalidConfigurationPropertyValueException) e).getName())
                .isEqualTo("mentorhub.mail.from");
    }
}
