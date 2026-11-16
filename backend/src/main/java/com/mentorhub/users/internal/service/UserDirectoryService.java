package com.mentorhub.users.internal.service;

import com.mentorhub.users.DirectoryPage;
import com.mentorhub.users.DirectoryUser;
import com.mentorhub.users.Role;
import com.mentorhub.users.UserContact;
import com.mentorhub.users.UserDirectory;
import com.mentorhub.users.internal.domain.StoredRoles;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.persistence.StoredUserSummary;
import com.mentorhub.users.internal.persistence.UserName;
import com.mentorhub.users.internal.persistence.UserRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserDirectoryService implements UserDirectory {

    private static final Comparator<DirectoryUser> ORDER = Comparator.comparing(
                    (DirectoryUser user) -> user.name().toLowerCase(Locale.ROOT))
            .thenComparing(user -> user.email().toLowerCase(Locale.ROOT))
            .thenComparing(DirectoryUser::id);

    private final UserRepository users;
    private final StoredRoles storedRoles;

    UserDirectoryService(UserRepository users, StoredRoles storedRoles) {
        this.users = users;
        this.storedRoles = storedRoles;
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryUser> findUsers(Collection<UUID> userIds) {
        return sorted(users.findSummaries(userIds));
    }

    @Override
    @Transactional(readOnly = true)
    public DirectoryPage findUsers(Collection<UUID> userIds, int page, int size) {
        if (userIds.isEmpty()) {
            return new DirectoryPage(List.of(), 0);
        }
        return new DirectoryPage(
                users.findSummaryPage(userIds, size, offset(page, size)).stream()
                        .map(this::directoryUser)
                        .toList(),
                users.countUsers(userIds));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DirectoryUser> findActiveUsersWithRole(Role role) {
        return sorted(users.findActiveUsersWithRole(role));
    }

    @Override
    @Transactional(readOnly = true)
    public DirectoryPage findActiveUsersWithRole(
            Role role, @Nullable String text, Collection<UUID> excludedUserIds, int page, int size) {
        return new DirectoryPage(
                users.findActiveWithRolePage(role, text, excludedUserIds, size, offset(page, size)).stream()
                        .map(this::directoryUser)
                        .toList(),
                users.countActiveWithRole(role, text, excludedUserIds));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveWithRole(UUID userId, Role role) {
        return users.isActiveWithRole(userId, role);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserContact> findActiveContactsWithRole(Collection<UUID> userIds, Role role) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return users.findActiveContactsWithRole(userIds, role).stream()
                .map(user -> new UserContact(
                        user.id(),
                        name(user.id(), user.email(), user.firstName(), user.lastName()),
                        user.email(),
                        user.phone()))
                .toList();
    }

    private List<DirectoryUser> sorted(List<StoredUserSummary> stored) {
        return stored.stream().map(this::directoryUser).sorted(ORDER).toList();
    }

    private DirectoryUser directoryUser(StoredUserSummary user) {
        return new DirectoryUser(
                user.id(),
                name(user.id(), user.email(), user.firstName(), user.lastName()),
                user.email(),
                user.status() == UserStatus.DISABLED,
                storedRoles.roles(user.roleNames()));
    }

    private static String name(UUID id, String email, @Nullable String firstName, @Nullable String lastName) {
        return HistoryActor.of(new UserName(id, email, firstName, lastName)).name();
    }

    private static long offset(int page, int size) {
        return (long) page * size;
    }
}
