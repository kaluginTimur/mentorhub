package com.mentorhub.users.internal.service;

import org.jspecify.annotations.Nullable;

/** Free-text input rules shared by the services: trimmed first, blank means none, length in code points. */
public final class TextInput {

    private TextInput() {}

    public static @Nullable String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** The length as PostgreSQL's {@code char_length} counts it. */
    public static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
