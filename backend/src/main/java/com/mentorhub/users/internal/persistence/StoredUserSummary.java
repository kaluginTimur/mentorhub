package com.mentorhub.users.internal.persistence;

import com.mentorhub.users.internal.domain.UserStatus;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A user in a list, with the names of its stored roles, including names the code may not know. */
public record StoredUserSummary(
        UUID id,
        String email,
        @Nullable String firstName,
        @Nullable String lastName,
        UserStatus status,
        List<String> roleNames) {

    public StoredUserSummary {
        roleNames = List.copyOf(roleNames);
    }
}
