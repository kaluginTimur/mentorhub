package com.mentorhub.users.internal.web;

import com.mentorhub.api.InvitationsApi;
import com.mentorhub.api.model.CreatedInvitation;
import com.mentorhub.api.model.Invitation;
import com.mentorhub.api.model.InvitationCreate;
import com.mentorhub.api.model.InvitationLink;
import com.mentorhub.api.model.InvitationLinkReissue;
import com.mentorhub.api.model.InvitationPage;
import com.mentorhub.api.model.InvitationPreview;
import com.mentorhub.api.model.InvitationStatus;
import com.mentorhub.api.model.InvitationToken;
import com.mentorhub.api.model.Me;
import com.mentorhub.users.NoPermissionRequired;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import com.mentorhub.users.internal.invitation.InvitationAcceptanceService;
import com.mentorhub.users.internal.invitation.InvitationAdministrationService;
import com.mentorhub.users.internal.invitation.InvitationQuery;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code Invitations} tag: managing invitations, and the public preview and the acceptance of a link. Parameter
 * names equal the request parameter names, because method validation reports violations by Java parameter name.
 */
@RestController
class InvitationsController implements InvitationsApi {

    private final InvitationAdministrationService administration;
    private final InvitationAcceptanceService acceptance;
    private final HttpServletResponse response;

    InvitationsController(
            InvitationAdministrationService administration,
            InvitationAcceptanceService acceptance,
            HttpServletResponse response) {
        this.administration = administration;
        this.acceptance = acceptance;
        this.response = response;
    }

    /** The generated methods return DTOs, so the header of a response that carries a token is set here. */
    private void noStore() {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @Override
    @RequirePermission(Permission.USERS_INVITE)
    public CreatedInvitation createInvitation(InvitationCreate invitationCreate) {
        // Bean validation checks the set, not its items: `[null]` binds to a set containing null, which the service
        // reports together with the email's violations.
        List<com.mentorhub.users.@Nullable Role> roles = invitationCreate.getRoles().stream()
                .map(role -> role == null ? null : UsersMapper.role(role))
                .toList();
        CreatedInvitation created = InvitationsMapper.created(administration.create(
                invitationCreate.getEmail(), roles, Boolean.TRUE.equals(invitationCreate.getSendEmail())));
        noStore();
        return created;
    }

    @Override
    @RequirePermission(Permission.USERS_INVITE)
    public InvitationPage listInvitations(
            @Nullable String q, @Nullable InvitationStatus status, Integer page, Integer size) {
        return InvitationsMapper.invitationPage(
                administration.list(new InvitationQuery(q, InvitationsMapper.status(status), page, size)));
    }

    @Override
    @RequirePermission(Permission.USERS_INVITE)
    public Invitation revokeInvitation(UUID id) {
        return InvitationsMapper.invitation(administration.revoke(id));
    }

    @Override
    @RequirePermission(Permission.USERS_INVITE)
    public CreatedInvitation reissueInvitationLink(UUID id, @Nullable InvitationLinkReissue invitationLinkReissue) {
        boolean sendEmail = invitationLinkReissue != null && Boolean.TRUE.equals(invitationLinkReissue.getSendEmail());
        CreatedInvitation created = InvitationsMapper.created(administration.reissueLink(id, sendEmail));
        noStore();
        return created;
    }

    @Override
    @RequirePermission(Permission.USERS_INVITE)
    public InvitationLink getInvitationLink(UUID id) {
        InvitationLink link = InvitationsMapper.link(administration.link(id));
        noStore();
        return link;
    }

    @Override
    @RequirePermission(Permission.USERS_INVITE)
    public Invitation sendInvitationEmail(UUID id) {
        return InvitationsMapper.invitation(administration.sendEmail(id));
    }

    @Override
    @NoPermissionRequired
    public InvitationPreview previewInvitation(InvitationToken invitationToken) {
        return InvitationsMapper.preview(acceptance.preview(invitationToken.getToken()));
    }

    @Override
    @NoPermissionRequired
    public Me acceptInvitation(InvitationToken invitationToken) {
        return UsersMapper.me(acceptance.accept(invitationToken.getToken()));
    }
}
