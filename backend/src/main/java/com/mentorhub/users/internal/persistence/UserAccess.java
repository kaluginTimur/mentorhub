package com.mentorhub.users.internal.persistence;

import com.mentorhub.users.internal.domain.UserStatus;
import java.util.List;
import java.util.UUID;

/** The user linked to an identity, with the names of its stored roles, including names the code may not know. */
public record UserAccess(UUID userId, UserStatus status, List<String> roleNames) {}
