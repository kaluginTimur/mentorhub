package com.mentorhub.users.internal.persistence;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record StoredContact(
        UUID id,
        String email,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String phone) {}
