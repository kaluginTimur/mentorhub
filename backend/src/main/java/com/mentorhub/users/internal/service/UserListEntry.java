package com.mentorhub.users.internal.service;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A user in the users list; roles are the known ones, in enum order. */
public record UserListEntry(
        UUID id,
        String email,
        @Nullable String firstName,
        @Nullable String lastName,
        UserStatus status,
        List<Role> roles) {

    public UserListEntry {
        roles = List.copyOf(roles);
    }
}
