package com.mentorhub.users.internal.web;

import com.mentorhub.api.InvitationsApi;
import com.mentorhub.api.model.CreatedInvitation;
import com.mentorhub.api.model.Invitation;
import com.mentorhub.api.model.InvitationCreate;
import com.mentorhub.api.model.InvitationPage;
import com.mentorhub.api.model.InvitationPreview;
import com.mentorhub.api.model.InvitationStatus;
import com.mentorhub.api.model.InvitationToken;
import com.mentorhub.api.model.Me;
import com.mentorhub.api.model.Role;
import com.mentorhub.users.NoPermissionRequired;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import com.mentorhub.users.internal.invitation.InvitationAcceptanceService;
import com.mentorhub.users.internal.invitation.InvitationAdministrationService;
import com.mentorhub.users.internal.invitation.InvitationQuery;
import com.mentorhub.web.ValidationException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code Invitations} tag: managing invitations, and the public preview and the acceptance of a link. Parameter
 * names equal the request parameter names, because method validation reports violations by Java parameter name.
 */
@RestController
class InvitationsController implements InvitationsApi {

    private final InvitationAdministrationService administration;
    private final InvitationAcceptanceService acceptance;

    InvitationsController(InvitationAdministrationService administration, InvitationAcceptanceService acceptance) {
        this.administration = administration;
        this.acceptance = acceptance;
    }

    @Override
    @RequirePermission(Permission.USERS_INVITE)
    public CreatedInvitation createInvitation(InvitationCreate invitationCreate) {
        Set<Role> roles = invitationCreate.getRoles();
        // Bean validation checks the set, not its items: `[null]` binds to a set containing null.
        if (roles.stream().anyMatch(Objects::isNull)) {
            throw ValidationException.of("roles", "must not contain null");
        }
        return InvitationsMapper.created(administration.create(
                invitationCreate.getEmail(),
                roles.stream().map(UsersMapper::role).collect(Collectors.toUnmodifiableSet())));
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
    public CreatedInvitation reissueInvitationLink(UUID id) {
        return InvitationsMapper.created(administration.reissueLink(id));
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
