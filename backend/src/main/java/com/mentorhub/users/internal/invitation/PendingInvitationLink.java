package com.mentorhub.users.internal.invitation;

import java.net.URI;
import java.time.Instant;

/** The link of a pending invitation (spec invitation-links, AC-1). */
public record PendingInvitationLink(URI link, String email, Instant expiresAt) {}
