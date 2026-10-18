package com.mentorhub.users.internal.web;

import com.mentorhub.api.model.AccessStatus;
import com.mentorhub.api.model.BootstrapChanges;
import com.mentorhub.api.model.DisableReason;
import com.mentorhub.api.model.HistoryAction;
import com.mentorhub.api.model.HistoryActor;
import com.mentorhub.api.model.HistoryEntry;
import com.mentorhub.api.model.HistoryPage;
import com.mentorhub.api.model.Me;
import com.mentorhub.api.model.MeUser;
import com.mentorhub.api.model.Permission;
import com.mentorhub.api.model.ProfileChange;
import com.mentorhub.api.model.ProfileField;
import com.mentorhub.api.model.Role;
import com.mentorhub.api.model.RoleFilter;
import com.mentorhub.api.model.UserDetails;
import com.mentorhub.api.model.UserPage;
import com.mentorhub.api.model.UserStatus;
import com.mentorhub.api.model.UserSummary;
import com.mentorhub.users.internal.domain.UserChange;
import com.mentorhub.users.internal.service.ManagedUser;
import com.mentorhub.users.internal.service.MeDetails;
import com.mentorhub.users.internal.service.ResultPage;
import com.mentorhub.users.internal.service.UserHistoryEntry;
import com.mentorhub.users.internal.service.UserListEntry;
import com.mentorhub.users.internal.service.UserProfile;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps between the generated contract types and the {@code users} service types; enums map by constant name. */
final class UsersMapper {

    private UsersMapper() {}

    static Me me(MeDetails details) {
        Me me = new Me(AccessStatus.valueOf(details.status().name()));
        UserProfile user = details.user();
        if (user != null) {
            me.setUser(new MeUser(
                    user.id(),
                    user.email(),
                    user.firstName(),
                    user.lastName(),
                    user.phone(),
                    roles(user.roles()),
                    user.permissions().stream()
                            .map(permission -> Permission.valueOf(permission.name()))
                            .toList()));
        }
        return me;
    }

    static UserPage userPage(ResultPage<UserListEntry> page) {
        return new UserPage(
                page.items().stream().map(UsersMapper::userSummary).toList(),
                page.page(),
                page.size(),
                page.totalItems());
    }

    static UserDetails userDetails(ManagedUser user) {
        UserDetails details = new UserDetails(
                user.id(),
                user.email(),
                user.firstName(),
                user.lastName(),
                user.phone(),
                UserStatus.valueOf(user.status().name()),
                roles(user.roles()),
                dateTime(user.createdAt()));
        details.setDisabledReason(disableReason(user.disabledReason()));
        details.setDisabledComment(user.disabledComment());
        return details;
    }

    static HistoryPage historyPage(ResultPage<UserHistoryEntry> page) {
        return new HistoryPage(
                page.items().stream().map(UsersMapper::historyEntry).toList(),
                page.page(),
                page.size(),
                page.totalItems());
    }

    static com.mentorhub.users.internal.domain.@Nullable UserStatus status(@Nullable UserStatus status) {
        return status == null ? null : com.mentorhub.users.internal.domain.UserStatus.valueOf(status.name());
    }

    static com.mentorhub.users.internal.domain.@Nullable RoleFilter roleFilter(@Nullable RoleFilter role) {
        if (role == null) {
            return null;
        }
        if (role == RoleFilter.NONE) {
            return new com.mentorhub.users.internal.domain.RoleFilter.WithoutRoles();
        }
        return new com.mentorhub.users.internal.domain.RoleFilter.WithRole(
                com.mentorhub.users.Role.valueOf(role.name()));
    }

    static com.mentorhub.users.internal.domain.DisableReason disableReason(DisableReason reason) {
        return com.mentorhub.users.internal.domain.DisableReason.valueOf(reason.name());
    }

    static com.mentorhub.users.Role role(Role role) {
        return com.mentorhub.users.Role.valueOf(role.name());
    }

    private static UserSummary userSummary(UserListEntry user) {
        return new UserSummary(
                user.id(),
                user.email(),
                user.firstName(),
                user.lastName(),
                UserStatus.valueOf(user.status().name()),
                roles(user.roles()));
    }

    private static HistoryEntry historyEntry(UserHistoryEntry entry) {
        UserChange change = entry.change();
        HistoryEntry dto = new HistoryEntry(
                entry.id(),
                dateTime(entry.occurredAt()),
                HistoryAction.valueOf(change.action().name()),
                historyActor(entry.actor()));
        // The generated lists start empty; null leaves the details that do not apply out of the JSON.
        dto.setRolesAdded(null);
        dto.setRolesRemoved(null);
        dto.setChanges(null);
        switch (change) {
            case UserChange.Disabled(var reason, var comment) -> {
                dto.setReason(disableReason(reason));
                dto.setComment(comment);
            }
            case UserChange.Enabled(var reason, var comment) -> {
                dto.setReason(disableReason(reason));
                dto.setComment(comment);
            }
            case UserChange.RolesChanged(var added, var removed) -> {
                dto.setRolesAdded(roleNames(added));
                dto.setRolesRemoved(roleNames(removed));
            }
            case UserChange.ProfileUpdated(var changes) ->
                dto.setChanges(changes.stream()
                        .map(fieldChange -> new ProfileChange(
                                ProfileField.valueOf(fieldChange.field().name()),
                                fieldChange.oldValue(),
                                fieldChange.newValue()))
                        .toList());
            case UserChange.BootstrappedAdmin bootstrap ->
                dto.setBootstrap(new BootstrapChanges(
                        bootstrap.userCreated(),
                        bootstrap.identityLinked(),
                        bootstrap.enabled(),
                        bootstrap.adminAdded()));
        }
        return dto;
    }

    private static @Nullable HistoryActor historyActor(
            com.mentorhub.users.internal.service.@Nullable HistoryActor actor) {
        return actor == null ? null : new HistoryActor(actor.id(), actor.name());
    }

    private static @Nullable DisableReason disableReason(
            com.mentorhub.users.internal.domain.@Nullable DisableReason reason) {
        return reason == null ? null : DisableReason.valueOf(reason.name());
    }

    private static List<Role> roles(Collection<com.mentorhub.users.Role> roles) {
        return roles.stream().map(role -> Role.valueOf(role.name())).toList();
    }

    /** Role names in history entries are already reduced to the ones the code knows. */
    private static List<Role> roleNames(List<String> names) {
        return names.stream().map(Role::valueOf).toList();
    }

    private static OffsetDateTime dateTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
