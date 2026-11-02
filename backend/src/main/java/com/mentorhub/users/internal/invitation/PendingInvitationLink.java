package com.mentorhub.users.internal.invitation;

import java.net.URI;
import java.time.Instant;

/** The link of a pending invitation. */
public record PendingInvitationLink(URI link, String email, Instant expiresAt) {}
