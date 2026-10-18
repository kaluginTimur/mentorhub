package com.mentorhub.users.internal.persistence;

import com.mentorhub.users.internal.domain.UserChange;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A stored audit entry about a user; {@code actorUserId} is {@code null} for a change made by the system. */
public record RecordedUserChange(
        UUID id, Instant occurredAt, @Nullable UUID actorUserId, UserChange change) {}
