package com.mentorhub.users.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.web.FieldViolation;
import com.mentorhub.web.ValidationException;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

// AC-18
class ProfileRulesTests {

    private final ProfileRules rules = new ProfileRules(new UsersProperties("KZ"));

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "8 701 123 45 67      | +77011234567", // national format with the default region
                "+7 701 123 45 67     | +77011234567",
                "  +7 (701) 123-45-67 | +77011234567",
                "87011234567          | +77011234567",
                "+1 650 253 0000      | +16502530000", // another region needs +
                "+77011234567         | +77011234567"
            })
    void storesValidPhonesInE164(String input, String stored) {
        assertThat(rules.validate(new ProfileInput(null, null, input)).phone()).isEqualTo(stored);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "12345",
                "not a phone",
                "+7 701 123",
                "+7 701 123 45 67 89",
                "+77011234567 ext. 12", // E.164 has no extension
                "650 253 0000" // a US number without + is read as KZ
            })
    void rejectsInvalidPhones(String input) {
        assertThatThrownBy(() -> rules.validate(new ProfileInput(null, null, input)))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        exception -> assertThat(exception.errors())
                                .containsExactly(new FieldViolation("phone", "must be a valid phone number")));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n"})
    void blankValuesAreStoredAsNull(@Nullable String blank) {
        ValidProfile profile = rules.validate(new ProfileInput(blank, blank, blank));

        assertThat(profile).isEqualTo(new ValidProfile(null, null, null));
    }

    @Test
    void trimsNames() {
        ValidProfile profile = rules.validate(new ProfileInput("  Anna ", "\tPetrova\n", null));

        assertThat(profile).isEqualTo(new ValidProfile("Anna", "Petrova", null));
    }

    @Test
    void namesMayHave100CharactersAfterTrimming() {
        String name = "a".repeat(100);
        String emoji = "😀".repeat(100); // 100 code points, 200 UTF-16 units

        ValidProfile profile = rules.validate(new ProfileInput("  " + name + "  ", emoji, null));

        assertThat(profile.firstName()).isEqualTo(name);
        assertThat(profile.lastName()).isEqualTo(emoji);
    }

    @Test
    void reportsEveryInvalidField() {
        String tooLong = "a".repeat(101);

        assertThatThrownBy(() -> rules.validate(new ProfileInput(tooLong, tooLong, "12345")))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        exception -> assertThat(exception.errors())
                                .containsExactly(
                                        new FieldViolation("firstName", "must be at most 100 characters"),
                                        new FieldViolation("lastName", "must be at most 100 characters"),
                                        new FieldViolation("phone", "must be a valid phone number")));
    }

    @Test
    void defaultRegionMustBeSupported() {
        assertThat(new UsersProperties(" kz ").phoneDefaultRegion()).isEqualTo("KZ");
        assertThatThrownBy(() -> new UsersProperties("XX"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mentorhub.users.phone-default-region");
    }
}
