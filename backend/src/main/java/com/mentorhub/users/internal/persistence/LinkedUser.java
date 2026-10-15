package com.mentorhub.users.internal.persistence;

import java.util.UUID;

/** The user an identity is linked to. */
public record LinkedUser(UUID userId, String email) {}
