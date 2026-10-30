package com.mentorhub.users.internal.invitation;

import java.util.UUID;

/**
 * An invitation email request was stored (spec invitation-emails, Events). Internal to {@code users}; the event
 * publication registry makes it durable. Carries no address, token or link, since the registry stores the event.
 */
public record InvitationEmailRequested(UUID emailRequestId) {}
