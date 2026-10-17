package com.mentorhub.users.internal.persistence;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import java.sql.Types;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    // One row per stored role of the user; folded into a UserAccess.
    private record AccessRow(
            UUID userId, String status, @Nullable String role) {}

    // One row per stored role of the user; folded into a StoredProfile.
    private record ProfileRow(
            UUID id,
            String email,
            @Nullable String firstName,
            @Nullable String lastName,
            @Nullable String role) {}

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
                        select u.id, u.email, u.first_name, u.last_name, ur.role
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
                        rs.getString("role")))
                .list();
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        ProfileRow first = rows.getFirst();
        List<String> roleNames =
                rows.stream().map(ProfileRow::role).filter(Objects::nonNull).toList();
        return Optional.of(
                new StoredProfile(first.id(), first.email(), first.firstName(), first.lastName(), roleNames));
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

    /** @return the number of inserted rows: 0 when a user with this email (ignoring case) exists */
    public int insertUserIfAbsent(
            String email, @Nullable String firstName, @Nullable String lastName, UserStatus status) {
        return jdbc.sql("""
                        insert into users (email, first_name, last_name, status)
                        values (:email, :firstName, :lastName, :status)
                        on conflict ((lower(email))) do nothing
                        """)
                .param("email", email)
                .param("firstName", firstName, Types.VARCHAR)
                .param("lastName", lastName, Types.VARCHAR)
                .param("status", status.name())
                .update();
    }

    public Optional<UUID> findUserIdByEmail(String email) {
        return jdbc.sql("select id from users where lower(email) = lower(:email)")
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

    /** @return the number of updated rows: 0 when the user already has the status */
    // TODO(user-administration step 3): replace with enable(userId) and disable(userId, reason, comment).
    public int setStatus(UUID userId, UserStatus status) {
        return jdbc.sql("""
                        update users
                        set status = :status,
                            disabled_reason = case when :status = 'DISABLED' then 'OTHER' end,
                            disabled_comment = case when :status = 'DISABLED' then 'Development user' end,
                            updated_at = now()
                        where id = :id and status <> :status
                        """).param("id", userId).param("status", status.name()).update();
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

    /** @return the number of inserted rows: 0 when the user already has the role */
    public int assignRoleIfAbsent(UUID userId, Role role) {
        return jdbc.sql("insert into user_roles (user_id, role) values (:userId, :role) on conflict do nothing")
                .param("userId", userId)
                .param("role", role.name())
                .update();
    }
}
