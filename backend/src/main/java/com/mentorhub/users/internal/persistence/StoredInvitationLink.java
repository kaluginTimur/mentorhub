package com.mentorhub.users.internal.persistence;

import com.mentorhub.users.internal.domain.InvitationStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * The token of one invitation with what its link view needs. The status is derived by the query that read it, with the
 * database's {@code now()}.
 */
public record StoredInvitationLink(UUID id, String token, String email, InvitationStatus status, Instant expiresAt) {}
