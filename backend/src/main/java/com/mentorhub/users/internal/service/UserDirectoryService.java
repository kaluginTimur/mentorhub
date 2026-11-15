package com.mentorhub.users.internal.service;

import com.mentorhub.users.DirectoryUser;
import com.mentorhub.users.Role;
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
    public List<DirectoryUser> findActiveUsersWithRole(Role role) {
        return sorted(users.findActiveUsersWithRole(role));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActiveWithRole(UUID userId, Role role) {
        return users.isActiveWithRole(userId, role);
    }

    private List<DirectoryUser> sorted(List<StoredUserSummary> stored) {
        return stored.stream()
                .map(user -> new DirectoryUser(
                        user.id(),
                        HistoryActor.of(new UserName(user.id(), user.email(), user.firstName(), user.lastName()))
                                .name(),
                        user.email(),
                        user.status() == UserStatus.DISABLED,
                        storedRoles.roles(user.roleNames())))
                .sorted(ORDER)
                .toList();
    }
}
