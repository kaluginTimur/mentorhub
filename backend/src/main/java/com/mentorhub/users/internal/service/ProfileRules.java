package com.mentorhub.users.internal.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.mentorhub.web.FieldViolation;
import com.mentorhub.web.ValidationException;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Profile values (AC-18): names trimmed, blank as {@code null}, at most 100 characters; the phone optional, parsed
 * with the configured default region, valid, without an extension, and stored in E.164. The database has no check for
 * these rules (user decision), so every profile write goes through here.
 */
@Component
class ProfileRules {

    static final int MAX_NAME_LENGTH = 100;

    private final PhoneNumberUtil phoneNumbers = PhoneNumberUtil.getInstance();
    private final String defaultRegion;

    ProfileRules(UsersProperties properties) {
        this.defaultRegion = properties.phoneDefaultRegion();
    }

    /** @throws ValidationException with one error per invalid field */
    ValidProfile validate(ProfileInput input) {
        List<FieldViolation> errors = new ArrayList<>();
        String firstName = name("firstName", input.firstName(), errors);
        String lastName = name("lastName", input.lastName(), errors);
        String phone = phone(input.phone(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new ValidProfile(firstName, lastName, phone);
    }

    private static @Nullable String name(String field, @Nullable String value, List<FieldViolation> errors) {
        String name = TextInput.trimToNull(value);
        if (name != null && TextInput.length(name) > MAX_NAME_LENGTH) {
            errors.add(new FieldViolation(field, "must be at most " + MAX_NAME_LENGTH + " characters"));
        }
        return name;
    }

    private @Nullable String phone(@Nullable String value, List<FieldViolation> errors) {
        String text = TextInput.trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            PhoneNumber number = phoneNumbers.parse(text, defaultRegion);
            if (phoneNumbers.isValidNumber(number) && !number.hasExtension()) {
                return phoneNumbers.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
            }
        } catch (NumberParseException exception) {
            // reported below
        }
        errors.add(new FieldViolation("phone", "must be a valid phone number"));
        return null;
    }
}
