package com.mentorhub.users;

import java.util.Set;
import java.util.UUID;

/** The active user of the current request: {@code users.id}, never the token subject, and effective permissions. */
public record CurrentUser(UUID id, Set<Permission> permissions) {

    public CurrentUser {
        permissions = Set.copyOf(permissions);
    }

    public boolean has(Permission permission) {
        return permissions.contains(permission);
    }
}
