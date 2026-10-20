package com.mentorhub.users.internal.invitation;

import java.net.URI;

/** An invitation with its new link (AC-1, AC-7). */
public record IssuedInvitation(InvitationDetails invitation, URI link) {}
