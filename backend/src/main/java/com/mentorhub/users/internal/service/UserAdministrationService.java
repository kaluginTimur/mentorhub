package com.mentorhub.users.internal.service;

import com.mentorhub.users.CurrentUserProvider;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.DisableReason;
import com.mentorhub.users.internal.domain.StoredRoles;
import com.mentorhub.users.internal.domain.UserChange;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.persistence.RecordedUserChange;
import com.mentorhub.users.internal.persistence.StoredUser;
import com.mentorhub.users.internal.persistence.UserAuditTrail;
import com.mentorhub.users.internal.persistence.UserChangePage;
import com.mentorhub.users.internal.persistence.UserName;
import com.mentorhub.users.internal.persistence.UserRepository;
import com.mentorhub.users.internal.persistence.UserSearch;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.FieldViolation;
import com.mentorhub.web.NotFoundException;
import com.mentorhub.web.ValidationException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration (spec user-administration): the users list, a user, its history, and the audited changes with
 * the lockout rules. Administrators see every user, so the only resource-level rule is that an unknown id is
 * {@link NotFoundException}.
 *
 * <p>Lock order, the same everywhere to rule out deadlocks (AC-15): the administrator lock first, then the user row.
 * Every change that can remove an active administrator takes the administrator lock before it counts the remaining
 * ones; other changes take only the row lock.
 */
@Service
public class UserAdministrationService {

    static final int MAX_QUERY_LENGTH = 100;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_COMMENT_LENGTH = 500;

    private final CurrentUserProvider currentUser;
    private final UserRepository users;
    private final StoredRoles storedRoles;
    private final UserAuditTrail auditTrail;
    private final ProfileRules profileRules;
    private final ProfileWriter profileWriter;

    UserAdministrationService(
            CurrentUserProvider currentUser,
            UserRepository users,
            StoredRoles storedRoles,
            UserAuditTrail auditTrail,
            ProfileRules profileRules,
            ProfileWriter profileWriter) {
        this.currentUser = currentUser;
        this.users = users;
        this.storedRoles = storedRoles;
        this.auditTrail = auditTrail;
        this.profileRules = profileRules;
        this.profileWriter = profileWriter;
    }

    /** AC-1, AC-2. @throws ValidationException for {@code q}, {@code page} or {@code size} */
    @RequirePermission(Permission.USERS_READ)
    @Transactional(readOnly = true)
    public ResultPage<UserListEntry> list(UserQuery query) {
        List<FieldViolation> errors = new ArrayList<>();
        String text = TextInput.trimToNull(query.text());
        if (text != null && TextInput.length(text) > MAX_QUERY_LENGTH) {
            errors.add(new FieldViolation("q", "must be at most " + MAX_QUERY_LENGTH + " characters"));
        }
        validatePage(query.page(), query.size(), errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        UserSearch search = new UserSearch(text, query.status(), query.role());
        List<UserListEntry> items = users.search(search, query.size(), (long) query.page() * query.size()).stream()
                .map(user -> new UserListEntry(
                        user.id(),
                        user.email(),
                        user.firstName(),
                        user.lastName(),
                        user.status(),
                        knownRoles(user.roleNames())))
                .toList();
        return new ResultPage<>(items, query.page(), query.size(), users.count(search));
    }

    /** AC-4. */
    @RequirePermission(Permission.USERS_READ)
    @Transactional(readOnly = true)
    public ManagedUser get(UUID userId) {
        return managedUser(users.findUser(userId).orElseThrow(NotFoundException::new));
    }

    /** AC-19: newest first; entries with an action the code does not know are left out. */
    @RequirePermission(Permission.USERS_READ)
    @Transactional(readOnly = true)
    public ResultPage<UserHistoryEntry> history(UUID userId, int page, int size) {
        List<FieldViolation> errors = new ArrayList<>();
        validatePage(page, size, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        if (!users.exists(userId)) {
            throw new NotFoundException();
        }
        UserChangePage changes = auditTrail.find(userId, page, size);
        Map<UUID, UserName> actors = users.findNames(changes.items().stream()
                .map(RecordedUserChange::actorUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        List<UserHistoryEntry> items = changes.items().stream()
                .map(change -> new UserHistoryEntry(
                        change.id(),
                        change.occurredAt(),
                        actor(change.actorUserId(), actors),
                        withKnownRoles(change.change())))
                .toList();
        return new ResultPage<>(items, page, size, changes.totalItems());
    }

    /**
     * AC-6, AC-7, AC-9, AC-13 to AC-15. Disabling a disabled user changes nothing, including its reason and comment.
     *
     * @param comment trimmed; blank is none; at most 500 characters; required for {@link DisableReason#OTHER}
     * @throws ValidationException for {@code comment}
     * @throws ConflictException {@link LockoutProblems#SELF_LOCKOUT} or {@link LockoutProblems#LAST_ADMIN}
     */
    @RequirePermission(Permission.USERS_MANAGE)
    @Transactional
    public ManagedUser disable(UUID userId, DisableReason reason, @Nullable String comment) {
        String validComment = disableComment(reason, comment);
        UUID callerId = currentUser.currentUser().id();
        users.lockAdministrators();
        StoredUser user = lockAndLoad(userId);
        if (user.status() == UserStatus.DISABLED) {
            return managedUser(user);
        }
        if (userId.equals(callerId)) {
            throw selfLockout();
        }
        if (knownRoles(user.roleNames()).contains(Role.ADMIN) && !otherActiveAdministratorExists(userId)) {
            throw lastAdmin();
        }
        users.disable(userId, reason, validComment);
        auditTrail.record(callerId, userId, new UserChange.Disabled(reason, validComment));
        return reload(userId);
    }

    /** AC-8, AC-9: clears the reason and comment; roles are unchanged. Enabling an active user changes nothing. */
    @RequirePermission(Permission.USERS_MANAGE)
    @Transactional
    public ManagedUser enable(UUID userId) {
        UUID callerId = currentUser.currentUser().id();
        StoredUser user = lockAndLoad(userId);
        DisableReason reason = user.disabledReason();
        if (user.status() == UserStatus.ACTIVE || reason == null) {
            return managedUser(user);
        }
        users.enable(userId);
        auditTrail.record(callerId, userId, new UserChange.Enabled(reason, user.disabledComment()));
        return reload(userId);
    }

    /**
     * AC-10 to AC-15: the known stored roles become exactly {@code roles}; stored roles the code does not know stay
     * untouched and are never reported.
     *
     * @throws ConflictException {@link LockoutProblems#SELF_LOCKOUT} or {@link LockoutProblems#LAST_ADMIN}
     */
    @RequirePermission(Permission.USERS_MANAGE)
    @Transactional
    public ManagedUser setRoles(UUID userId, Set<Role> roles) {
        Set<Role> requested = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
        UUID callerId = currentUser.currentUser().id();
        // Only a set without ADMIN can remove an administrator; known from the request before anything is read.
        if (!requested.contains(Role.ADMIN)) {
            users.lockAdministrators();
        }
        StoredUser user = lockAndLoad(userId);
        Set<Role> current = storedRoles.roles(user.roleNames());
        Set<Role> added = EnumSet.copyOf(requested);
        added.removeAll(current);
        Set<Role> removed = EnumSet.noneOf(Role.class);
        removed.addAll(current);
        removed.removeAll(requested);
        if (added.isEmpty() && removed.isEmpty()) {
            return managedUser(user);
        }
        if (removed.contains(Role.ADMIN)) {
            if (userId.equals(callerId)) {
                throw selfLockout();
            }
            if (user.status() == UserStatus.ACTIVE && !otherActiveAdministratorExists(userId)) {
                throw lastAdmin();
            }
        }
        users.removeRoles(userId, removed);
        added.forEach(role -> users.assignRoleIfAbsent(userId, role));
        auditTrail.record(callerId, userId, new UserChange.RolesChanged(names(added), names(removed)));
        return reload(userId);
    }

    /**
     * AC-16, AC-18: another user's profile (or the caller's own, by id). Saving unchanged values writes no entry.
     *
     * @throws ValidationException with one error per invalid field; nothing is stored
     */
    @RequirePermission(Permission.USERS_MANAGE)
    @Transactional
    public ManagedUser updateProfile(UUID userId, ProfileInput input) {
        ValidProfile profile = profileRules.validate(input);
        UUID callerId = currentUser.currentUser().id();
        StoredUser user = lockAndLoad(userId);
        profileWriter.write(callerId, user, profile);
        return reload(userId);
    }

    private StoredUser lockAndLoad(UUID userId) {
        if (!users.lockUser(userId)) {
            throw new NotFoundException();
        }
        return users.findUser(userId).orElseThrow();
    }

    private ManagedUser reload(UUID userId) {
        return managedUser(users.findUser(userId).orElseThrow());
    }

    private boolean otherActiveAdministratorExists(UUID userId) {
        return users.otherActiveUserWithRoleExists(Role.ADMIN, userId);
    }

    private ManagedUser managedUser(StoredUser user) {
        boolean disabled = user.status() == UserStatus.DISABLED;
        return new ManagedUser(
                user.id(),
                user.email(),
                user.firstName(),
                user.lastName(),
                user.phone(),
                user.status(),
                disabled ? user.disabledReason() : null,
                disabled ? user.disabledComment() : null,
                knownRoles(user.roleNames()),
                user.createdAt());
    }

    private List<Role> knownRoles(List<String> roleNames) {
        return List.copyOf(storedRoles.roles(roleNames));
    }

    private UserChange withKnownRoles(UserChange change) {
        if (change instanceof UserChange.RolesChanged(List<String> rolesAdded, List<String> rolesRemoved)) {
            return new UserChange.RolesChanged(
                    names(storedRoles.roles(rolesAdded)), names(storedRoles.roles(rolesRemoved)));
        }
        return change;
    }

    private static @Nullable HistoryActor actor(@Nullable UUID actorUserId, Map<UUID, UserName> names) {
        if (actorUserId == null) {
            return null;
        }
        UserName user = names.get(actorUserId);
        // actor_user_id references users, which are never deleted
        if (user == null) {
            throw new IllegalStateException("Audit actor " + actorUserId + " is not a stored user");
        }
        String name = String.join(
                " ",
                Stream.of(user.firstName(), user.lastName())
                        .filter(Objects::nonNull)
                        .toList());
        return new HistoryActor(user.id(), name.isEmpty() ? user.email() : name);
    }

    private static @Nullable String disableComment(DisableReason reason, @Nullable String comment) {
        String trimmed = TextInput.trimToNull(comment);
        if (trimmed == null && reason == DisableReason.OTHER) {
            throw ValidationException.of("comment", "must not be blank when the reason is OTHER");
        }
        if (trimmed != null && TextInput.length(trimmed) > MAX_COMMENT_LENGTH) {
            throw ValidationException.of("comment", "must be at most " + MAX_COMMENT_LENGTH + " characters");
        }
        return trimmed;
    }

    private static void validatePage(int page, int size, List<FieldViolation> errors) {
        if (page < 0) {
            errors.add(new FieldViolation("page", "must be greater than or equal to 0"));
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            errors.add(new FieldViolation("size", "must be between 1 and " + MAX_PAGE_SIZE));
        }
    }

    private static List<String> names(Set<Role> roles) {
        return roles.stream().map(Role::name).toList();
    }

    private static ConflictException selfLockout() {
        return new ConflictException(LockoutProblems.SELF_LOCKOUT, "You can't lock yourself out.");
    }

    private static ConflictException lastAdmin() {
        return new ConflictException(
                LockoutProblems.LAST_ADMIN, "MentorHub must keep at least one active administrator.");
    }
}
