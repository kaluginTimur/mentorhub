package com.mentorhub.users.internal.invitation;

import java.time.Instant;

/** What anyone holding a valid link sees (AC-8). */
public record PreviewedInvitation(String email, Instant expiresAt) {}
