package com.mentorhub.users.internal.invitation;

import com.mentorhub.mail.MailDelivery;
import com.mentorhub.users.CurrentUserProvider;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.InvitationEmailReason;
import com.mentorhub.users.internal.domain.StoredRoles;
import com.mentorhub.users.internal.persistence.InvitationAuditTrail;
import com.mentorhub.users.internal.persistence.InvitationEmailRepository;
import com.mentorhub.users.internal.persistence.InvitationRepository;
import com.mentorhub.users.internal.persistence.InvitationSearch;
import com.mentorhub.users.internal.persistence.StoredInvitation;
import com.mentorhub.users.internal.persistence.StoredInvitationEmail;
import com.mentorhub.users.internal.persistence.StoredInvitationLink;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Managing invitations (spec invitations, AC-1 to AC-7; spec invitation-links): only holders of
 * {@link Permission#USERS_INVITE}, who see every invitation and the link of a pending one, so the only resource-level
 * rule is that an unknown id is {@link NotFoundException}. Revocation, new links and email requests lock the invitation
 * row, which serializes them with acceptance (AC-15).
 *
 * <p>Email requests (spec invitation-emails, AC-2 to AC-7): stored, audited and published as
 * {@link InvitationEmailRequested} in the changing transaction; {@link InvitationEmailSender} sends them after the
 * commit. Whether email is enabled is checked after the invitation's own errors (AC-6), so a refused request rolls back
 * the whole change.
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
    private final InvitationEmailRepository emails;
    private final UserRepository users;
    private final StoredRoles storedRoles;
    private final InvitationAuditTrail auditTrail;
    private final InvitationProperties properties;
    private final Validator validator;
    private final MailDelivery mail;
    private final ApplicationEventPublisher events;

    InvitationAdministrationService(
            CurrentUserProvider currentUser,
            InvitationRepository invitations,
            InvitationEmailRepository emails,
            UserRepository users,
            StoredRoles storedRoles,
            InvitationAuditTrail auditTrail,
            InvitationProperties properties,
            Validator validator,
            MailDelivery mail,
            ApplicationEventPublisher events) {
        this.currentUser = currentUser;
        this.invitations = invitations;
        this.emails = emails;
        this.users = users;
        this.storedRoles = storedRoles;
        this.auditTrail = auditTrail;
        this.properties = properties;
        this.validator = validator;
        this.mail = mail;
        this.events = events;
    }

    /**
     * AC-1 to AC-3, AC-15; spec invitation-emails, AC-2.
     *
     * @param email trimmed, stored as entered after trimming, compared ignoring case
     * @param roles as requested; a {@code null} item is a violation reported together with the email's
     * @param sendEmail whether to email the link to the invited address
     * @throws ValidationException for {@code email} and {@code roles}
     * @throws ConflictException {@link InvitationProblems#USER_ACTIVE} with {@code userId},
     *     {@link InvitationProblems#INVITATION_EXISTS} with {@code invitationId}, or
     *     {@link InvitationProblems#EMAIL_UNAVAILABLE}
     */
    @RequirePermission(Permission.USERS_INVITE)
    @Transactional
    public IssuedInvitation create(
            @Nullable String email, Collection<? extends @Nullable Role> roles, boolean sendEmail) {
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
        UUID invitationId = insert(validEmail, token, callerId, invitedRoles);
        auditTrail.created(callerId, invitationId, validEmail, invitedRoles);
        if (sendEmail) {
            // After the insert, which decides invitation-exists (AC-6); the exception rolls the insert back.
            requireEmailEnabled();
            requestEmail(invitationId, InvitationEmailReason.CREATED, callerId);
        }
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
     * AC-7: a new token replaces the old one and the expiry becomes now plus the link lifetime. Spec
     * invitation-emails, AC-3: with {@code sendEmail}, an email with the new link is requested, unless one is still
     * queued; that one builds its link when it is sent, so it carries the new link.
     *
     * @throws NotFoundException for an unknown id
     * @throws ConflictException {@link InvitationProblems#INVITATION_CLOSED} or
     *     {@link InvitationProblems#EMAIL_UNAVAILABLE}
     */
    @RequirePermission(Permission.USERS_INVITE)
    @Transactional
    public IssuedInvitation reissueLink(UUID invitationId, boolean sendEmail) {
        UUID callerId = currentUser.currentUser().id();
        lockOpen(invitationId);
        if (sendEmail) {
            requireEmailEnabled();
        }
        String token = InvitationTokens.newToken();
        invitations.replaceToken(invitationId, token, properties.linkLifetime());
        StoredInvitation invitation = load(invitationId);
        auditTrail.linkReissued(callerId, invitationId, invitation.expiresAt());
        // The invitation row is locked, so no other request can queue an email in between; checking first keeps the
        // unique index from aborting this transaction.
        if (sendEmail && emails.findQueued(invitationId).isEmpty()) {
            requestEmail(invitationId, InvitationEmailReason.LINK_REISSUED, callerId);
        }
        return new IssuedInvitation(details(invitation), properties.link(token));
    }

    /**
     * Spec invitation-links, AC-1, AC-2, AC-7: the current link of a pending invitation, for any holder of the
     * permission. Read-only and not audited. Not locked: a concurrent new link may make the returned link stale (the
     * spec's accepted edge case).
     *
     * @throws NotFoundException for an unknown id
     * @throws ConflictException {@link InvitationProblems#INVITATION_CLOSED} or
     *     {@link InvitationProblems#INVITATION_EXPIRED}
     */
    @RequirePermission(Permission.USERS_INVITE)
    @Transactional(readOnly = true)
    public PendingInvitationLink link(UUID invitationId) {
        StoredInvitationLink stored = invitations.findLink(invitationId).orElseThrow(NotFoundException::new);
        return switch (stored.status()) {
            case PENDING ->
                new PendingInvitationLink(properties.link(stored.token()), stored.email(), stored.expiresAt());
            case EXPIRED -> throw expired();
            case ACCEPTED, REVOKED -> throw closed();
        };
    }

    /**
     * Spec invitation-emails, AC-4 to AC-6: requests an email with the current link of a pending invitation; the token
     * and the expiry stay unchanged. Any holder of the permission may send it, not only the inviter.
     *
     * @throws NotFoundException for an unknown id
     * @throws ConflictException {@link InvitationProblems#INVITATION_CLOSED}, {@link InvitationProblems#INVITATION_EXPIRED},
     *     {@link InvitationProblems#EMAIL_UNAVAILABLE} or {@link InvitationProblems#EMAIL_QUEUED}, in this order
     */
    @RequirePermission(Permission.USERS_INVITE)
    @Transactional
    public InvitationDetails sendEmail(UUID invitationId) {
        UUID callerId = currentUser.currentUser().id();
        StoredInvitation invitation = invitations.lock(invitationId).orElseThrow(NotFoundException::new);
        switch (invitation.status()) {
            case PENDING -> {}
            case EXPIRED -> throw expired();
            case ACCEPTED, REVOKED -> throw closed();
        }
        requireEmailEnabled();
        if (emails.findQueued(invitationId).isPresent()) {
            throw emailQueued();
        }
        try {
            requestEmail(invitationId, InvitationEmailReason.RESENT, callerId);
        } catch (DuplicateKeyException e) {
            // Not expected while the invitation row is locked; the aborted transaction can only roll back.
            throw emailQueued();
        }
        return details(load(invitationId));
    }

    private void requireEmailEnabled() {
        if (!mail.isEnabled()) {
            throw new ConflictException(InvitationProblems.EMAIL_UNAVAILABLE, "Sending email is not available.");
        }
    }

    /** Stores, audits and publishes a request; the invitation has no queued request. */
    private void requestEmail(UUID invitationId, InvitationEmailReason reason, UUID callerId) {
        UUID requestId = emails.insertQueued(invitationId, reason, callerId);
        auditTrail.emailRequested(callerId, invitationId, reason);
        events.publishEvent(new InvitationEmailRequested(requestId));
    }

    private static ConflictException emailQueued() {
        return new ConflictException(
                InvitationProblems.EMAIL_QUEUED, "An email for this invitation is already queued.");
    }

    private static ConflictException expired() {
        return new ConflictException(
                InvitationProblems.INVITATION_EXPIRED, "This invitation has expired. Get a new link.");
    }

    private UUID insert(String email, String token, UUID callerId, Set<Role> roles) {
        for (int attempt = 1; ; attempt++) {
            Optional<UUID> inserted =
                    invitations.insertIfNoOpenInvitation(email, token, callerId, properties.linkLifetime(), roles);
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
            throw closed();
        }
    }

    private static ConflictException closed() {
        return new ConflictException(
                InvitationProblems.INVITATION_CLOSED, "This invitation is already accepted or revoked.");
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
        Map<UUID, StoredInvitationEmail> lastEmails =
                emails.findLatest(found.stream().map(StoredInvitation::id).toList());
        Map<UUID, UserName> names = users.findNames(Stream.concat(
                        found.stream()
                                .flatMap(invitation -> Stream.of(
                                        invitation.createdBy(), invitation.acceptedUserId(), invitation.revokedBy())),
                        lastEmails.values().stream().map(StoredInvitationEmail::requestedBy))
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
                        nullableActor(invitation.revokedBy(), names),
                        lastEmail(lastEmails.get(invitation.id()), names)))
                .toList();
    }

    private static @Nullable InvitationEmailDetails lastEmail(
            @Nullable StoredInvitationEmail email, Map<UUID, UserName> names) {
        return email == null
                ? null
                : new InvitationEmailDetails(
                        email.status(), email.requestedAt(), actor(email.requestedBy(), names), email.finishedAt());
    }

    private List<Role> knownRoles(Collection<String> roleNames) {
        return List.copyOf(storedRoles.roles(roleNames));
    }

    private static @Nullable HistoryActor nullableActor(@Nullable UUID userId, Map<UUID, UserName> names) {
        return userId == null ? null : actor(userId, names);
    }

    private static HistoryActor actor(UUID userId, Map<UUID, UserName> names) {
        UserName user = names.get(userId);
        // The invitation's and its emails' user columns reference users, which are never deleted.
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
