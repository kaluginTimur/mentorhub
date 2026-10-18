package com.mentorhub.users.internal.invitation;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.Caller;
import com.mentorhub.users.internal.domain.InvitationStatus;
import com.mentorhub.users.internal.domain.StoredRoles;
import com.mentorhub.users.internal.domain.UserChange;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.persistence.InvitationRepository;
import com.mentorhub.users.internal.persistence.LinkedUser;
import com.mentorhub.users.internal.persistence.StoredInvitation;
import com.mentorhub.users.internal.persistence.StoredUser;
import com.mentorhub.users.internal.persistence.UserAuditTrail;
import com.mentorhub.users.internal.persistence.UserRepository;
import com.mentorhub.users.internal.security.CurrentCaller;
import com.mentorhub.users.internal.service.MeDetails;
import com.mentorhub.users.internal.service.MeService;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.NotFoundException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The link side of invitations (spec invitations, AC-8 to AC-17): preview for anyone holding a valid link, and
 * acceptance for any signed-in caller in any access status. Neither needs a permission: the token is the
 * authorization, and acceptance also requires the token's verified email to equal the invitation's.
 *
 * <p>Every invalid token answers the same {@link InvitationProblems#INVITATION_INVALID}, so the answer reveals nothing
 * about the invitation. Lock order: the invitation row, then the user row. Acceptance removes roles only from a
 * disabled user, who is not an active administrator, so it needs no administrator lock.
 */
@Service
public class InvitationAcceptanceService {

    private static final Logger log = LoggerFactory.getLogger(InvitationAcceptanceService.class);

    private final CurrentCaller currentCaller;
    private final InvitationRepository invitations;
    private final UserRepository users;
    private final StoredRoles storedRoles;
    private final UserAuditTrail userAuditTrail;
    private final MeService meService;

    InvitationAcceptanceService(
            CurrentCaller currentCaller,
            InvitationRepository invitations,
            UserRepository users,
            StoredRoles storedRoles,
            UserAuditTrail userAuditTrail,
            MeService meService) {
        this.currentCaller = currentCaller;
        this.invitations = invitations;
        this.users = users;
        this.storedRoles = storedRoles;
        this.userAuditTrail = userAuditTrail;
        this.meService = meService;
    }

    /** AC-8, AC-9. @throws NotFoundException {@link InvitationProblems#INVITATION_INVALID} unless pending */
    @Transactional(readOnly = true)
    public PreviewedInvitation preview(String token) {
        StoredInvitation invitation = InvitationTokens.hashIfWellFormed(token)
                .flatMap(invitations::findByTokenHash)
                .filter(found -> found.status() == InvitationStatus.PENDING)
                .orElseThrow(InvitationAcceptanceService::invalid);
        return new PreviewedInvitation(invitation.email(), invitation.expiresAt());
    }

    /**
     * AC-10 to AC-16, in one transaction. Accepting again an invitation already accepted by the user the caller's
     * identity is linked to changes nothing (AC-14).
     *
     * @return the caller as stored after acceptance
     * @throws NotFoundException {@link InvitationProblems#INVITATION_INVALID}
     * @throws ConflictException {@link InvitationProblems#INVITATION_EMAIL_MISMATCH} or
     *     {@link InvitationProblems#IDENTITY_LINKED_ELSEWHERE}
     */
    @Transactional
    public MeDetails accept(String token) {
        Caller caller = currentCaller.caller();
        StoredInvitation invitation = InvitationTokens.hashIfWellFormed(token)
                .flatMap(invitations::lockByTokenHash)
                .orElseThrow(InvitationAcceptanceService::invalid);
        // Read after the invitation lock, not taken from the authentication: a parallel first acceptance may have
        // linked the identity since the request was authenticated.
        Optional<LinkedUser> linked = users.findLinkedUser(caller.issuer(), caller.subject());
        if (invitation.status() == InvitationStatus.ACCEPTED
                && linked.isPresent()
                && linked.get().userId().equals(invitation.acceptedUserId())) {
            return meService.forLinkedUser(linked.get().userId());
        }
        if (invitation.status() != InvitationStatus.PENDING) {
            throw invalid();
        }
        String email = caller.email();
        if (!caller.emailVerified() || email == null || !email.equalsIgnoreCase(invitation.email())) {
            throw new ConflictException(
                    InvitationProblems.INVITATION_EMAIL_MISMATCH,
                    "Sign in with the verified email the invitation was sent to.");
        }
        Optional<UUID> userWithEmail = users.findUserIdByEmail(invitation.email());
        if (linked.isPresent()
                && !userWithEmail.map(linked.get().userId()::equals).orElse(false)) {
            throw linkedElsewhere(linked.get().userId(), userWithEmail.orElse(null));
        }

        boolean userCreated = users.insertUserIfAbsent(invitation.email(), caller.givenName(), caller.familyName()) > 0;
        UUID userId = users.findUserIdByEmail(invitation.email()).orElseThrow();
        users.lockUser(userId);
        boolean identityLinked = users.linkIdentityIfAbsent(userId, caller.issuer(), caller.subject()) > 0;
        UUID linkedUserId = users.findLinkedUser(caller.issuer(), caller.subject())
                .orElseThrow()
                .userId();
        if (!linkedUserId.equals(userId)) {
            // Linked by a concurrent transaction; the exception rolls back the user this one may have created.
            throw linkedElsewhere(linkedUserId, userId);
        }

        StoredUser user = users.findUser(userId).orElseThrow();
        Set<Role> invitedRoles = storedRoles.roles(invitation.roleNames());
        boolean enabled = false;
        Set<Role> removed = EnumSet.noneOf(Role.class);
        if (user.status() == UserStatus.DISABLED) {
            // A reactivated user gets exactly the invitation's known roles; an active one only gains roles.
            enabled = users.enable(userId) > 0;
            removed.addAll(storedRoles.roles(user.roleNames()));
            removed.removeAll(invitedRoles);
            users.removeRoles(userId, removed);
        }
        Set<Role> added = EnumSet.noneOf(Role.class);
        for (Role role : invitedRoles) {
            if (users.assignRoleIfAbsent(userId, role) > 0) {
                added.add(role);
            }
        }
        invitations.markAccepted(invitation.id(), userId);
        userAuditTrail.record(
                userId,
                userId,
                new UserChange.InvitationAccepted(
                        invitation.id(),
                        invitation.createdBy(),
                        userCreated,
                        identityLinked,
                        enabled,
                        names(added),
                        names(removed)));
        return meService.forLinkedUser(userId);
    }

    private static NotFoundException invalid() {
        return new NotFoundException(
                InvitationProblems.INVITATION_INVALID, "This invitation link is invalid or has expired.");
    }

    private static ConflictException linkedElsewhere(UUID linkedUserId, @Nullable UUID userId) {
        if (userId == null) {
            log.warn(
                    "Invitation acceptance refused: the identity is linked to user {}, and no user has the invited"
                            + " email",
                    linkedUserId);
        } else {
            log.warn(
                    "Invitation acceptance refused: the identity is linked to user {}, not to user {} with the"
                            + " invited email",
                    linkedUserId,
                    userId);
        }
        return new ConflictException(
                InvitationProblems.IDENTITY_LINKED_ELSEWHERE,
                "This account already belongs to another MentorHub user.");
    }

    private static List<String> names(Set<Role> roles) {
        return roles.stream().map(Role::name).toList();
    }
}
