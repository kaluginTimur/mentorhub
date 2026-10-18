package com.mentorhub.users.internal.persistence;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** What is needed to show a user's name. */
public record UserName(
        UUID id,
        String email,
        @Nullable String firstName,
        @Nullable String lastName) {}
