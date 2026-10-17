package com.mentorhub.users.internal.persistence;

import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A user's stored profile with the names of its stored roles, including names the code may not know. */
public record StoredProfile(
        UUID id,
        String email,
        @Nullable String firstName,
        @Nullable String lastName,
        List<String> roleNames) {}
