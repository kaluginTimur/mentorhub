package com.mentorhub.users.internal.persistence;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.InvitationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Invitations and their roles (spec invitations, Data model). Times come from the database's {@code now()}, so the
 * derived status, the expiry and the creation time use one clock. Rows are never deleted.
 */
@Repository
public class InvitationRepository {

    /** The status derivation of the spec; every read and the status filter use this one expression. */
    private static final String STATUS = """
            case when i.accepted_at is not null then 'ACCEPTED'
                 when i.revoked_at is not null then 'REVOKED'
                 when i.expires_at <= now() then 'EXPIRED'
                 else 'PENDING' end""";

    private static final String COLUMNS = """
            select i.id, i.email, %s as status, i.created_at, i.created_by, i.expires_at,
                   i.accepted_at, i.accepted_user_id, i.revoked_at, i.revoked_by
            from invitations i
            """.formatted(STATUS);

    private record InvitationRoleRow(UUID invitationId, String role) {}

    private final JdbcClient jdbc;

    InvitationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts an open invitation expiring {@code lifetime} after now, with its roles. The partial unique index on open
     * invitations decides between parallel inserts for one email: the later one waits for the earlier to commit.
     *
     * @return the new id, or empty when an open invitation for the email (ignoring case) exists
     */
    public Optional<UUID> insertIfNoOpenInvitation(
            String email, String token, UUID createdBy, Duration lifetime, Collection<Role> roles) {
        Optional<UUID> id = jdbc.sql("""
                        insert into invitations (email, token, created_by, expires_at)
                        values (:email, :token, :createdBy, now() + :lifetimeMillis * interval '1 millisecond')
                        on conflict ((lower(email))) where accepted_at is null and revoked_at is null do nothing
                        returning id
                        """)
                .param("email", email)
                .param("token", token)
                .param("createdBy", createdBy)
                .param("lifetimeMillis", lifetime.toMillis())
                .query(UUID.class)
                .optional();
        id.ifPresent(invitationId -> roles.forEach(
                role -> jdbc.sql("insert into invitation_roles (invitation_id, role) values (:invitationId, :role)")
                        .param("invitationId", invitationId)
                        .param("role", role.name())
                        .update()));
        return id;
    }

    public Optional<UUID> findOpenIdByEmail(String email) {
        return jdbc.sql("""
                        select id from invitations
                        where lower(email) = lower(:email) and accepted_at is null and revoked_at is null
                        """).param("email", email).query(UUID.class).optional();
    }

    public Optional<StoredInvitation> find(UUID id) {
        return withRoles(jdbc.sql(COLUMNS + "where i.id = :id")
                .param("id", id)
                .query(InvitationRepository::invitation)
                .optional());
    }

    public Optional<StoredInvitation> findByToken(String token) {
        return withRoles(jdbc.sql(COLUMNS + "where i.token = :token")
                .param("token", token)
                .query(InvitationRepository::invitation)
                .optional());
    }

    /**
     * Locks the invitation's row until the end of the transaction and reads it, so acceptance, revocation and new
     * links of one invitation are serialized. {@code for no key update}, not {@code for update}:
     * {@code invitation_roles} references {@code invitations}, and {@code for update} would conflict with the
     * {@code for key share} that referencing inserts take.
     */
    public Optional<StoredInvitation> lock(UUID id) {
        return withRoles(jdbc.sql(COLUMNS + "where i.id = :id for no key update")
                .param("id", id)
                .query(InvitationRepository::invitation)
                .optional());
    }

    /**
     * Like {@link #lock(UUID)}, by token. A concurrent new link that replaced the token leaves nothing to find once it
     * commits.
     */
    public Optional<StoredInvitation> lockByToken(String token) {
        return withRoles(jdbc.sql(COLUMNS + "where i.token = :token for no key update")
                .param("token", token)
                .query(InvitationRepository::invitation)
                .optional());
    }

    /** Replaces the token and sets the expiry to {@code lifetime} after now. */
    public void replaceToken(UUID id, String token, Duration lifetime) {
        jdbc.sql("""
                        update invitations
                        set token = :token, expires_at = now() + :lifetimeMillis * interval '1 millisecond'
                        where id = :id
                        """)
                .param("id", id)
                .param("token", token)
                .param("lifetimeMillis", lifetime.toMillis())
                .update();
    }

    public void markRevoked(UUID id, UUID revokedBy) {
        jdbc.sql("update invitations set revoked_at = now(), revoked_by = :revokedBy where id = :id")
                .param("id", id)
                .param("revokedBy", revokedBy)
                .update();
    }

    public void markAccepted(UUID id, UUID userId) {
        jdbc.sql("update invitations set accepted_at = now(), accepted_user_id = :userId where id = :id")
                .param("id", id)
                .param("userId", userId)
                .update();
    }

    /** A page of invitations, newest first, then by id descending. */
    public List<StoredInvitation> search(InvitationSearch search, int limit, long offset) {
        Map<String, Object> params = new HashMap<>();
        String where = where(search, params);
        params.put("limit", limit);
        params.put("offset", offset);
        List<StoredInvitation> invitations = jdbc.sql(COLUMNS + where + """
                        order by i.created_at desc, i.id desc
                        limit :limit offset :offset
                        """)
                .params(params)
                .query(InvitationRepository::invitation)
                .list();
        if (invitations.isEmpty()) {
            return invitations;
        }
        Map<UUID, List<String>> roles =
                roleNames(invitations.stream().map(StoredInvitation::id).toList());
        return invitations.stream()
                .map(invitation -> invitation.withRoleNames(roles.getOrDefault(invitation.id(), List.of())))
                .toList();
    }

    public long count(InvitationSearch search) {
        Map<String, Object> params = new HashMap<>();
        return jdbc.sql("select count(*) from invitations i\n" + where(search, params))
                .params(params)
                .query(Long.class)
                .single();
    }

    private Optional<StoredInvitation> withRoles(Optional<StoredInvitation> invitation) {
        return invitation.map(
                found -> found.withRoleNames(roleNames(List.of(found.id())).getOrDefault(found.id(), List.of())));
    }

    private Map<UUID, List<String>> roleNames(List<UUID> invitationIds) {
        return jdbc
                .sql("""
                        select invitation_id, role from invitation_roles
                        where invitation_id in (:ids)
                        order by role
                        """)
                .param("ids", invitationIds)
                .query((rs, rowNum) ->
                        new InvitationRoleRow(rs.getObject("invitation_id", UUID.class), rs.getString("role")))
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        InvitationRoleRow::invitationId,
                        Collectors.mapping(InvitationRoleRow::role, Collectors.toList())));
    }

    private static StoredInvitation invitation(ResultSet rs, int rowNum) throws SQLException {
        return new StoredInvitation(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                InvitationStatus.valueOf(rs.getString("status")),
                instant(rs, "created_at"),
                rs.getObject("created_by", UUID.class),
                instant(rs, "expires_at"),
                nullableInstant(rs, "accepted_at"),
                rs.getObject("accepted_user_id", UUID.class),
                nullableInstant(rs, "revoked_at"),
                rs.getObject("revoked_by", UUID.class),
                List.of());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static @Nullable Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static String where(InvitationSearch search, Map<String, Object> params) {
        List<String> conditions = new ArrayList<>();
        if (search.text() != null) {
            conditions.add("i.email ilike :pattern escape '\\'");
            params.put("pattern", LikePatterns.contains(search.text()));
        }
        if (search.status() != null) {
            conditions.add("(" + STATUS + ") = :status");
            params.put("status", search.status().name());
        }
        return conditions.isEmpty() ? "" : "where " + String.join("\n  and ", conditions) + "\n";
    }
}
