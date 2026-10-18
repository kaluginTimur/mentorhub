package com.mentorhub.users.internal.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.users.internal.domain.DisableReason;
import com.mentorhub.users.internal.domain.UserStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DevelopmentUsersPropertiesTests {

    static ValidatorFactory factory;
    static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest
    @CsvSource(
            nullValues = "null",
            value = {
                "ACTIVE, null, null, true",
                "DISABLED, GRADUATED, null, true",
                "DISABLED, OTHER, Development user, true",
                "DISABLED, null, null, false", // a disabled user needs a reason
                "DISABLED, OTHER, null, false", // OTHER needs a comment
                "ACTIVE, SECURITY, null, false", // an active user has no reason
                "ACTIVE, null, Comment, false"
            })
    void disabledReasonMatchesTheStatus(
            UserStatus status, @Nullable DisableReason reason, @Nullable String comment, boolean valid) {
        var user = new DevelopmentUsersProperties.DevelopmentUser(
                "subject", "user@mentorhub.local", null, null, status, reason, comment, List.of());

        assertThat(validator.validate(user).isEmpty()).isEqualTo(valid);
    }
}
