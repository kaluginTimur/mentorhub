package com.mentorhub.users.internal.web;

import com.mentorhub.api.model.CreatedInvitation;
import com.mentorhub.api.model.HistoryActor;
import com.mentorhub.api.model.Invitation;
import com.mentorhub.api.model.InvitationLink;
import com.mentorhub.api.model.InvitationPage;
import com.mentorhub.api.model.InvitationPreview;
import com.mentorhub.api.model.InvitationStatus;
import com.mentorhub.api.model.Role;
import com.mentorhub.users.internal.invitation.InvitationDetails;
import com.mentorhub.users.internal.invitation.IssuedInvitation;
import com.mentorhub.users.internal.invitation.PendingInvitationLink;
import com.mentorhub.users.internal.invitation.PreviewedInvitation;
import com.mentorhub.users.internal.service.ResultPage;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;

/** Maps between the generated contract types and the invitation service types; enums map by constant name. */
final class InvitationsMapper {

    private InvitationsMapper() {}

    static CreatedInvitation created(IssuedInvitation issued) {
        return new CreatedInvitation(invitation(issued.invitation()), issued.link());
    }

    static Invitation invitation(InvitationDetails details) {
        Invitation invitation = new Invitation(
                details.id(),
                details.email(),
                details.roles().stream().map(role -> Role.valueOf(role.name())).toList(),
                InvitationStatus.valueOf(details.status().name()),
                dateTime(details.createdAt()),
                dateTime(details.expiresAt()),
                actor(details.createdBy()));
        invitation.setAcceptedAt(details.acceptedAt() == null ? null : dateTime(details.acceptedAt()));
        invitation.setAcceptedUser(details.acceptedUser() == null ? null : actor(details.acceptedUser()));
        invitation.setRevokedAt(details.revokedAt() == null ? null : dateTime(details.revokedAt()));
        invitation.setRevokedBy(details.revokedBy() == null ? null : actor(details.revokedBy()));
        return invitation;
    }

    static InvitationPage invitationPage(ResultPage<InvitationDetails> page) {
        return new InvitationPage(
                page.items().stream().map(InvitationsMapper::invitation).toList(),
                page.page(),
                page.size(),
                page.totalItems());
    }

    static InvitationLink link(PendingInvitationLink link) {
        return new InvitationLink(link.link(), link.email(), dateTime(link.expiresAt()));
    }

    static InvitationPreview preview(PreviewedInvitation preview) {
        return new InvitationPreview(preview.email(), dateTime(preview.expiresAt()));
    }

    static com.mentorhub.users.internal.domain.@Nullable InvitationStatus status(@Nullable InvitationStatus status) {
        return status == null ? null : com.mentorhub.users.internal.domain.InvitationStatus.valueOf(status.name());
    }

    private static HistoryActor actor(com.mentorhub.users.internal.service.HistoryActor actor) {
        return new HistoryActor(actor.id(), actor.name());
    }

    private static OffsetDateTime dateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
