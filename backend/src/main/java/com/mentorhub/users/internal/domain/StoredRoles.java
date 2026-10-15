package com.mentorhub.users.internal.domain;

import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps stored role names to {@link Role}. A name the code does not know (for example after a release rollback) grants
 * nothing and never fails a request (ADR 0004).
 */
@Component
public class StoredRoles {

    private static final Logger log = LoggerFactory.getLogger(StoredRoles.class);
    private static final Map<String, Role> ROLES_BY_NAME =
            Arrays.stream(Role.values()).collect(Collectors.toUnmodifiableMap(Role::name, Function.identity()));

    private final Set<String> reportedUnknownNames = ConcurrentHashMap.newKeySet();

    public Set<Role> roles(Collection<String> names) {
        EnumSet<Role> roles = EnumSet.noneOf(Role.class);
        for (String name : names) {
            Role role = ROLES_BY_NAME.get(name);
            if (role != null) {
                roles.add(role);
            } else if (reportedUnknownNames.add(name)) {
                log.warn("Ignoring stored role '{}' unknown to this version of the application", name);
            }
        }
        return roles;
    }

    public Set<Permission> permissions(Collection<String> names) {
        EnumSet<Permission> permissions = EnumSet.noneOf(Permission.class);
        roles(names).forEach(role -> permissions.addAll(role.permissions()));
        return permissions;
    }
}
