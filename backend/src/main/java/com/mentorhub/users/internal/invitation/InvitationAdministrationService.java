package com.mentorhub.users.internal.invitation;

import com.mentorhub.users.CurrentUserProvider;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.StoredRoles;
import com.mentorhub.users.internal.persistence.InvitationAuditTrail;
import com.mentorhub.users.internal.persistence.InvitationRepository;
import com.mentorhub.users.internal.persistence.InvitationSearch;
import com.mentorhub.users.internal.persistence.StoredInvitation;
import com.mentorhub.users.internal.persistence.UserName;
import com.mentorhub.users.internal.persistence.UserRepository;
import com.mentorhub.users.internal.service.HistoryActor;
import com.mentorhub.users.internal.service.ResultPage;
import com.mentorhub.users.internal.service.TextInput;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.FieldViolation;
import com.mentorhub.web.NotFoundException;
import com.mentorhub.web.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Managing invitations (spec invitations, AC-1 to AC-7): only holders of {@link Permission#USERS_INVITE}, who see every
 * invitation, so the only resource-level rule is that an unknown id is {@link NotFoundException}. Revocation and new
 * links lock the invitation row, which serializes them with acceptance (AC-15).
 */
@Service
public class InvitationAdministrationService {

    static final int MAX_EMAIL_LENGTH = 254;
    /** The maximum email length, so a search for any valid email works (AC-4). */
    static final int MAX_QUERY_LENGTH = MAX_EMAIL_LENGTH;

    static final int MAX_PAGE_SIZE = 100;

    // An open invitation that blocks the insert can close before it is read back; then the insert is tried again.
    private static final int CREATE_ATTEMPTS = 3;

    private final CurrentUserProvider currentUser;
    private final InvitationRepository invitations;
    private final UserRepository users;
    private final StoredRoles storedRoles;
    private final InvitationAuditTrail auditTrail;
    private final InvitationProperties properties;
    private final Validator validator;

    InvitationAdministrationService(
            CurrentUserProvider currentUser,
            InvitationRepository invitations,
            UserRepository users,
            StoredRoles storedRoles,
            InvitationAuditTrail auditTrail,
            InvitationProperties properties,
            Validator validator) {
        this.currentUser = currentUser;
        this.invitations = invitations;
        this.users = users;
        this.storedRoles = storedRoles;
        this.auditTrail = auditTrail;
        this.properties = properties;
        this.validator = validator;
    }

    /**
     * AC-1 to AC-3, AC-15.
     *
     * @param email trimmed, stored as entered after trimming, compared ignoring case
     * @param roles as requested; a {@code null} item is a violation reported together with the email's
     * @throws ValidationException for {@code email} and {@code roles}
     * @throws ConflictException {@link InvitationProblems#USER_ACTIVE} with {@code userId}, or
     *     {@link InvitationProblems#INVITATION_EXISTS} with {@code invitationId}
     */
    @RequirePermission(Permission.USERS_INVITE)
    @Transactional
    public IssuedInvitation create(@Nullable String email, Collection<? extends @Nullable Role> roles) {
        List<FieldViolation> errors = new ArrayList<>();
        String validEmail = validEmail(email, errors);
        if (roles.stream().anyMatch(Objects::isNull)) {
            errors.add(new FieldViolation("roles", "must not contain null"));
        }
        if (validEmail == null || !errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        Set<Role> invitedRoles = EnumSet.noneOf(Role.class);
        roles.forEach(role -> invitedRoles.add(Objects.requireNonNull(role)));
        UUID callerId = currentUser.currentUser().id();
        Optional<UUID> activeUser = users.findActiveUserIdByEmail(validEmail);
        if (activeUser.isPresent()) {
            throw new ConflictException(
                    InvitationProblems.USER_ACTIVE,
                    "This person already has access.",
                    Map.of("userId", activeUser.get()));
        }
        String token = InvitationTokens.newToken();
        UUID invitationId = insert(validEmail, InvitationTokens.hash(token), callerId, invitedRoles);
        auditTrail.created(callerId, invitationId, validEmail, invitedRoles);
        return new IssuedInvitation(details(load(invitationId)), properties.link(token));
    }

    /** AC-4. @throws ValidationException for {@code q}, {@code page} or {@code size} */
    @RequirePermission(Permission.USERS_INVITE)
    @Transactional(readOnly = true)
    public ResultPage<InvitationDetails> list(InvitationQuery query) {
        List<FieldViolation> errors = new ArrayList<>();
        String text = TextInput.trimToNull(query.text());
        if (text != null && TextInput.length(text) > MAX_QUERY_LENGTH) {
            errors.add(new FieldViolation("q", "must be at most " + MAX_QUERY_LENGTH + " characters"));
        }
        if (query.page() < 0) {
            errors.add(new FieldViolation("page", "must be greater than or equal to 0"));
        }
        if (query.size() < 1 || query.size() > MAX_PAGE_SIZE) {
            errors.add(new FieldViolation("size", "must be between 1 and " + MAX_PAGE_SIZE));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        InvitationSearch search = new InvitationSearch(text, query.status());
        List<StoredInvitation> found = invitations.search(search, query.size(), (long) query.page() * query.size());
        return new ResultPage<>(details(found), query.page(), query.size(), invitations.count(search));
    }

    /**
     * AC-6.
     *
     * @throws NotFoundException for an unknown id
     * @throws ConflictException {@link InvitationProblems#INVITATION_CLOSED}
     */
    @RequirePermission(Permission.USERS_INVITE)
    @Transactional
    public InvitationDetails revoke(UUID invitationId) {
        UUID callerId = currentUser.currentUser().id();
        lockOpen(invitationId);
        invitations.markRevoked(invitationId, callerId);
        auditTrail.revoked(callerId, invitationId);
        return details(load(invitationId));
    }

    /**
     * AC-7: a new token replaces the old one and the expiry becomes now plus the link lifetime.
     *
     * @throws NotFoundException for an unknown id
     * @throws ConflictException {@link InvitationProblems#INVITATION_CLOSED}
     */
    @RequirePermission(Permission.USERS_INVITE)
    @Transactional
    public IssuedInvitation reissueLink(UUID invitationId) {
        UUID callerId = currentUser.currentUser().id();
        lockOpen(invitationId);
        String token = InvitationTokens.newToken();
        invitations.replaceToken(invitationId, InvitationTokens.hash(token), properties.linkLifetime());
        StoredInvitation invitation = load(invitationId);
        auditTrail.linkReissued(callerId, invitationId, invitation.expiresAt());
        return new IssuedInvitation(details(invitation), properties.link(token));
    }

    private UUID insert(String email, String tokenHash, UUID callerId, Set<Role> roles) {
        for (int attempt = 1; ; attempt++) {
            Optional<UUID> inserted =
                    invitations.insertIfNoOpenInvitation(email, tokenHash, callerId, properties.linkLifetime(), roles);
            if (inserted.isPresent()) {
                return inserted.get();
            }
            Optional<UUID> open = invitations.findOpenIdByEmail(email);
            if (open.isPresent()) {
                throw new ConflictException(
                        InvitationProblems.INVITATION_EXISTS,
                        "There is already an invitation for this email.",
                        Map.of("invitationId", open.get()));
            }
            if (attempt == CREATE_ATTEMPTS) {
                throw new IllegalStateException("Could not insert an invitation for an email without open ones");
            }
        }
    }

    private void lockOpen(UUID invitationId) {
        StoredInvitation invitation = invitations.lock(invitationId).orElseThrow(NotFoundException::new);
        if (!invitation.status().isOpen()) {
            throw new ConflictException(
                    InvitationProblems.INVITATION_CLOSED, "This invitation is already accepted or revoked.");
        }
    }

    private StoredInvitation load(UUID invitationId) {
        return invitations.find(invitationId).orElseThrow();
    }

    /** The trimmed email, or {@code null} after adding its first violation to {@code errors}. */
    private @Nullable String validEmail(@Nullable String email, List<FieldViolation> errors) {
        String trimmed = TextInput.trimToNull(email);
        if (trimmed == null) {
            errors.add(new FieldViolation("email", "must not be blank"));
            return null;
        }
        if (TextInput.length(trimmed) > MAX_EMAIL_LENGTH) {
            errors.add(new FieldViolation("email", "must be at most " + MAX_EMAIL_LENGTH + " characters"));
            return null;
        }
        if (!validator.validateValue(EmailValue.class, "value", trimmed).isEmpty()) {
            errors.add(new FieldViolation("email", "must be a well-formed email address"));
            return null;
        }
        return trimmed;
    }

    private InvitationDetails details(StoredInvitation invitation) {
        return details(List.of(invitation)).getFirst();
    }

    private List<InvitationDetails> details(List<StoredInvitation> found) {
        Map<UUID, UserName> names = users.findNames(found.stream()
                .flatMap(invitation ->
                        Stream.of(invitation.createdBy(), invitation.acceptedUserId(), invitation.revokedBy()))
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return found.stream()
                .map(invitation -> new InvitationDetails(
                        invitation.id(),
                        invitation.email(),
                        knownRoles(invitation.roleNames()),
                        invitation.status(),
                        invitation.createdAt(),
                        invitation.expiresAt(),
                        actor(invitation.createdBy(), names),
                        invitation.acceptedAt(),
                        nullableActor(invitation.acceptedUserId(), names),
                        invitation.revokedAt(),
                        nullableActor(invitation.revokedBy(), names)))
                .toList();
    }

    private List<Role> knownRoles(Collection<String> roleNames) {
        return List.copyOf(storedRoles.roles(roleNames));
    }

    private static @Nullable HistoryActor nullableActor(@Nullable UUID userId, Map<UUID, UserName> names) {
        return userId == null ? null : actor(userId, names);
    }

    private static HistoryActor actor(UUID userId, Map<UUID, UserName> names) {
        UserName user = names.get(userId);
        // The invitation's user columns reference users, which are never deleted.
        if (user == null) {
            throw new IllegalStateException("Invitation user " + userId + " is not a stored user");
        }
        return HistoryActor.of(user);
    }

    /** Bean validation's {@code @Email} rules, applied to a single value. */
    private static final class EmailValue {

        @Email
        private @Nullable String value;
    }
}
