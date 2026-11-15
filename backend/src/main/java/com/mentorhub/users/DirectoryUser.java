package com.mentorhub.users;

import java.util.Set;
import java.util.UUID;

/**
 * A user as another module shows them.
 *
 * @param name first and last name joined by a space, or the email when both are missing
 * @param roles the user's roles that this version knows; a stored role it does not know is left out (decision 0004)
 */
public record DirectoryUser(UUID id, String name, String email, boolean disabled, Set<Role> roles) {

    public DirectoryUser {
        roles = Set.copyOf(roles);
    }
}
