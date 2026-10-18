package com.mentorhub.users.internal.persistence;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.DisableReason;
import com.mentorhub.users.internal.domain.RoleFilter;
import com.mentorhub.users.internal.domain.UserStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    /**
     * Key of the transaction-scoped advisory lock that serializes every change that can remove an active administrator
     * (spec user-administration, Authorization). Taken before any user row lock.
     */
    private static final long ADMINISTRATORS_LOCK = 7_301_946_532_015_100_001L;

    private static final List<String> KNOWN_ROLES =
            Arrays.stream(Role.values()).map(Role::name).toList();

    // One row per stored role of the user; folded into a UserAccess.
    private record AccessRow(
            UUID userId, String status, @Nullable String role) {}

    // One row per stored role of the user; folded into a StoredProfile.
    private record ProfileRow(
            UUID id,
            String email,
            @Nullable String firstName,
            @Nullable String lastName,
            @Nullable String phone,
            @Nullable String role) {}

    private record UserRoleRow(UUID userId, String role) {}

    private final JdbcClient jdbc;

    UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** The user linked to the identity, with the names of its stored roles, including names the code may not know. */
    public Optional<UserAccess> findAccess(String issuer, String subject) {
        List<AccessRow> rows = jdbc.sql("""
                        select u.id, u.status, ur.role
                        from user_identities i
                        join users u on u.id = i.user_id
                        left join user_roles ur on ur.user_id = u.id
                        where i.issuer = :issuer and i.subject = :subject
                        """)
                .param("issuer", issuer)
                .param("subject", subject)
                .query((rs, rowNum) ->
                        new AccessRow(rs.getObject("id", UUID.class), rs.getString("status"), rs.getString("role")))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        AccessRow first = rows.getFirst();
        List<String> roleNames =
                rows.stream().map(AccessRow::role).filter(Objects::nonNull).toList();
        return Optional.of(new UserAccess(first.userId(), UserStatus.valueOf(first.status()), roleNames));
    }

    public Optional<StoredProfile> findProfile(UUID userId) {
        List<ProfileRow> rows = jdbc.sql("""
                        select u.id, u.email, u.first_name, u.last_name, u.phone, ur.role
                        from users u
                        left join user_roles ur on ur.user_id = u.id
                        where u.id = :id
                        """)
                .param("id", userId)
                .query((rs, rowNum) -> new ProfileRow(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("phone"),
                        rs.getString("role")))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        ProfileRow first = rows.getFirst();
        List<String> roleNames =
                rows.stream().map(ProfileRow::role).filter(Objects::nonNull).toList();
        return Optional.of(new StoredProfile(
                first.id(), first.email(), first.firstName(), first.lastName(), first.phone(), roleNames));
    }

    public Optional<StoredUser> findUser(UUID userId) {
        List<String> roleNames = roleNames(userId);
        return jdbc.sql("""
                        select id, email, first_name, last_name, phone, status, disabled_reason, disabled_comment,
                               created_at
                        from users
                        where id = :id
                        """)
                .param("id", userId)
                .query((rs, rowNum) -> storedUser(rs, roleNames))
                .optional();
    }

    public boolean exists(UUID userId) {
        return jdbc.sql("select exists (select 1 from users where id = :id)")
                .param("id", userId)
                .query(Boolean.class)
                .single();
    }

    /**
     * Locks the user's row until the end of the transaction, so changes to one user are serialized. {@code for no key
     * update}, not {@code for update}: the foreign keys to {@code users} (such as the audit entry's actor) take
     * {@code for key share}, which {@code for update} blocks, so two administrators changing each other would deadlock.
     *
     * @return {@code false} when the user does not exist
     */
    public boolean lockUser(UUID userId) {
        return jdbc.sql("select id from users where id = :id for no key update")
                .param("id", userId)
                .query(UUID.class)
                .optional()
                .isPresent();
    }

    /**
     * Takes the administrator lock until the end of the transaction. Lock order, to rule out deadlocks: this lock
     * first, then user rows.
     */
    public void lockAdministrators() {
        jdbc.sql("select pg_advisory_xact_lock(:key)")
                .param("key", ADMINISTRATORS_LOCK)
                .query((rs, rowNum) -> rowNum)
                .single();
    }

    /** A page of users sorted by last name, first name and email ignoring case, missing names last, then id. */
    public List<StoredUserSummary> search(UserSearch search, int limit, long offset) {
        Map<String, Object> params = new HashMap<>();
        String where = where(search, params);
        params.put("limit", limit);
        params.put("offset", offset);
        List<StoredUserSummary> users = jdbc.sql("""
                        select u.id, u.email, u.first_name, u.last_name, u.status
                        from users u
                        """ + where + """
                        order by lower(u.last_name) asc nulls last, lower(u.first_name) asc nulls last,
                                 lower(u.email), u.id
                        limit :limit offset :offset
                        """)
                .params(params)
                .query((rs, rowNum) -> new StoredUserSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        UserStatus.valueOf(rs.getString("status")),
                        List.of()))
                .list();
        if (users.isEmpty()) {
            return users;
        }
        Map<UUID, List<String>> roles = jdbc
                .sql("select user_id, role from user_roles where user_id in (:ids) order by role")
                .param("ids", users.stream().map(StoredUserSummary::id).toList())
                .query((rs, rowNum) -> new UserRoleRow(rs.getObject("user_id", UUID.class), rs.getString("role")))
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        UserRoleRow::userId, Collectors.mapping(UserRoleRow::role, Collectors.toList())));
        return users.stream()
                .map(user -> new StoredUserSummary(
                        user.id(),
                        user.email(),
                        user.firstName(),
                        user.lastName(),
                        user.status(),
                        roles.getOrDefault(user.id(), List.of())))
                .toList();
    }

    public long count(UserSearch search) {
        Map<String, Object> params = new HashMap<>();
        return jdbc.sql("select count(*) from users u\n" + where(search, params))
                .params(params)
                .query(Long.class)
                .single();
    }

    public Map<UUID, UserName> findNames(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return jdbc
                .sql("select id, email, first_name, last_name from users where id in (:ids)")
                .param("ids", List.copyOf(userIds))
                .query((rs, rowNum) -> new UserName(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("first_name"),
                        rs.getString("last_name")))
                .list()
                .stream()
                .collect(Collectors.toMap(UserName::id, Function.identity()));
    }

    public Optional<LinkedUser> findLinkedUser(String issuer, String subject) {
        return jdbc.sql("""
                        select u.id, u.email
                        from user_identities i
                        join users u on u.id = i.user_id
                        where i.issuer = :issuer and i.subject = :subject
                        """)
                .param("issuer", issuer)
                .param("subject", subject)
                .query((rs, rowNum) -> new LinkedUser(rs.getObject("id", UUID.class), rs.getString("email")))
                .optional();
    }

    public boolean activeUserWithRoleExists(Role role) {
        return jdbc.sql("""
                        select exists (
                            select 1 from user_roles ur
                            join users u on u.id = ur.user_id
                            where ur.role = :role and u.status = 'ACTIVE'
                        )
                        """).param("role", role.name()).query(Boolean.class).single();
    }

    public boolean otherActiveUserWithRoleExists(Role role, UUID excludedUserId) {
        return jdbc.sql("""
                        select exists (
                            select 1 from user_roles ur
                            join users u on u.id = ur.user_id
                            where ur.role = :role and u.status = 'ACTIVE' and u.id <> :excluded
                        )
                        """)
                .param("role", role.name())
                .param("excluded", excludedUserId)
                .query(Boolean.class)
                .single();
    }

    /**
     * Inserts an active user; a disabled one is created active and then {@link #disable disabled}, so the status is
     * always written together with its reason.
     *
     * @return the number of inserted rows: 0 when a user with this email (ignoring case) exists
     */
    public int insertUserIfAbsent(String email, @Nullable String firstName, @Nullable String lastName) {
        return jdbc.sql("""
                        insert into users (email, first_name, last_name, status)
                        values (:email, :firstName, :lastName, 'ACTIVE')
                        on conflict ((lower(email))) do nothing
                        """)
                .param("email", email)
                .param("firstName", firstName, Types.VARCHAR)
                .param("lastName", lastName, Types.VARCHAR)
                .update();
    }

    public Optional<UUID> findUserIdByEmail(String email) {
        return jdbc.sql("select id from users where lower(email) = lower(:email)")
                .param("email", email)
                .query(UUID.class)
                .optional();
    }

    public Optional<UUID> findActiveUserIdByEmail(String email) {
        return jdbc.sql("select id from users where lower(email) = lower(:email) and status = 'ACTIVE'")
                .param("email", email)
                .query(UUID.class)
                .optional();
    }

    /** @return the number of inserted rows: 0 when the identity is already linked to any user */
    public int linkIdentityIfAbsent(UUID userId, String issuer, String subject) {
        return jdbc.sql("""
                        insert into user_identities (user_id, issuer, subject)
                        values (:userId, :issuer, :subject)
                        on conflict (issuer, subject) do nothing
                        """)
                .param("userId", userId)
                .param("issuer", issuer)
                .param("subject", subject)
                .update();
    }

    /**
     * Sets the user active and clears the disable reason and comment.
     *
     * @return the number of updated rows: 0 when the user is already active
     */
    public int enable(UUID userId) {
        return jdbc.sql("""
                        update users
                        set status = 'ACTIVE', disabled_reason = null, disabled_comment = null, updated_at = now()
                        where id = :id and status <> 'ACTIVE'
                        """).param("id", userId).update();
    }

    /**
     * Disables an active user with the reason and comment; a disabled user keeps its reason and comment.
     *
     * @return the number of updated rows: 0 when the user is already disabled
     */
    public int disable(UUID userId, DisableReason reason, @Nullable String comment) {
        return jdbc.sql("""
                        update users
                        set status = 'DISABLED', disabled_reason = :reason, disabled_comment = :comment,
                            updated_at = now()
                        where id = :id and status <> 'DISABLED'
                        """)
                .param("id", userId)
                .param("reason", reason.name())
                .param("comment", comment, Types.VARCHAR)
                .update();
    }

    public void updateProfile(
            UUID userId, @Nullable String firstName, @Nullable String lastName, @Nullable String phone) {
        jdbc.sql("""
                        update users
                        set first_name = :firstName, last_name = :lastName, phone = :phone, updated_at = now()
                        where id = :id
                        """)
                .param("id", userId)
                .param("firstName", firstName, Types.VARCHAR)
                .param("lastName", lastName, Types.VARCHAR)
                .param("phone", phone, Types.VARCHAR)
                .update();
    }

    /** Removes every stored role assignment of the user except {@code kept}, including roles the code does not know. */
    public int removeRolesOtherThan(UUID userId, Collection<Role> kept) {
        if (kept.isEmpty()) {
            return jdbc.sql("delete from user_roles where user_id = :userId")
                    .param("userId", userId)
                    .update();
        }
        return jdbc.sql("delete from user_roles where user_id = :userId and role not in (:kept)")
                .param("userId", userId)
                .param("kept", kept.stream().map(Role::name).toList())
                .update();
    }

    /** Removes exactly the given roles; other stored roles, including roles the code does not know, stay. */
    public int removeRoles(UUID userId, Collection<Role> roles) {
        if (roles.isEmpty()) {
            return 0;
        }
        return jdbc.sql("delete from user_roles where user_id = :userId and role in (:roles)")
                .param("userId", userId)
                .param("roles", roles.stream().map(Role::name).toList())
                .update();
    }

    /** @return the number of inserted rows: 0 when the user already has the role */
    public int assignRoleIfAbsent(UUID userId, Role role) {
        return jdbc.sql("insert into user_roles (user_id, role) values (:userId, :role) on conflict do nothing")
                .param("userId", userId)
                .param("role", role.name())
                .update();
    }

    private List<String> roleNames(UUID userId) {
        return jdbc.sql("select role from user_roles where user_id = :userId order by role")
                .param("userId", userId)
                .query(String.class)
                .list();
    }

    private static StoredUser storedUser(ResultSet rs, List<String> roleNames) throws SQLException {
        String reason = rs.getString("disabled_reason");
        return new StoredUser(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("phone"),
                UserStatus.valueOf(rs.getString("status")),
                reason == null ? null : DisableReason.valueOf(reason),
                rs.getString("disabled_comment"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                roleNames);
    }

    private static String where(UserSearch search, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();
        if (search.text() != null) {
            conditions.add("""
                    (u.email ilike :pattern escape '\\' or u.first_name ilike :pattern escape '\\'
                     or u.last_name ilike :pattern escape '\\')""");
            params.put("pattern", LikePatterns.contains(search.text()));
        }
        if (search.status() != null) {
            conditions.add("u.status = :status");
            params.put("status", search.status().name());
        }
        switch (search.role()) {
            case RoleFilter.WithRole(Role role) -> {
                conditions.add("exists (select 1 from user_roles ur where ur.user_id = u.id and ur.role = :role)");
                params.put("role", role.name());
            }
            case RoleFilter.WithoutRoles() -> {
                conditions.add(
                        "not exists (select 1 from user_roles ur where ur.user_id = u.id and ur.role in (:known))");
                params.put("known", KNOWN_ROLES);
            }
            case null -> {}
        }
        return conditions.isEmpty() ? "" : "where " + String.join("\n  and ", conditions) + "\n";
    }
}
