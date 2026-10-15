package com.mentorhub.users.internal.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The caller of the current request: the access status and, for a linked user, {@code users.id}. */
public record Caller(AccessStatus status, @Nullable UUID userId) {}
