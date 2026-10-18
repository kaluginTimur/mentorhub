package com.mentorhub.users.internal.persistence;

import com.mentorhub.users.internal.domain.DisableReason;
import com.mentorhub.users.internal.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A stored user with the names of its stored roles, including names the code may not know. */
public record StoredUser(
        UUID id,
        String email,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String phone,
        UserStatus status,
        @Nullable DisableReason disabledReason,
        @Nullable String disabledComment,
        Instant createdAt,
        List<String> roleNames) {

    public StoredUser {
        roleNames = List.copyOf(roleNames);
    }
}
