package com.mentorhub.mail.internal.delivery;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.InvalidConfigurationPropertyValueException;

/** Validates {@code mentorhub.mail.from}: exactly one address with an optional display name, parsed strictly. */
final class SenderAddress {

    static final String PROPERTY = "mentorhub.mail.from";

    private static final String EXPECTED =
            "Must be exactly one email address with an optional display name, such as 'MentorHub <no-reply@example.org>'.";

    private SenderAddress() {}

    static InternetAddress parse(@Nullable String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidConfigurationPropertyValueException(
                    PROPERTY, value, "Required when spring.mail.host is set. " + EXPECTED);
        }
        InternetAddress address;
        try {
            // Strict parsing; the constructor also rejects a list of several addresses.
            address = new InternetAddress(value, true);
        } catch (AddressException e) {
            throw new InvalidConfigurationPropertyValueException(PROPERTY, value, EXPECTED);
        }
        if (address.isGroup()) {
            throw new InvalidConfigurationPropertyValueException(PROPERTY, value, EXPECTED);
        }
        return address;
    }
}
