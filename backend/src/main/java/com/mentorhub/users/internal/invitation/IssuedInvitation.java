package com.mentorhub.users.internal.invitation;

import java.net.URI;

/** An invitation with its new link; the only time the link is available (AC-1, AC-7). */
public record IssuedInvitation(InvitationDetails invitation, URI link) {}
