package com.mentorhub.users.internal.invitation;

import java.time.Instant;

/** What anyone holding a valid link sees. */
public record PreviewedInvitation(String email, Instant expiresAt) {}
