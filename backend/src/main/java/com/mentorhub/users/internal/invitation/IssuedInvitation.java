package com.mentorhub.users.internal.invitation;

import java.net.URI;

/** An invitation with its new link. */
public record IssuedInvitation(InvitationDetails invitation, URI link) {}
