package com.mentorhub.users.internal.persistence;

import com.mentorhub.users.internal.domain.InvitationEmailFailure;
import com.mentorhub.users.internal.domain.InvitationEmailReason;
import com.mentorhub.users.internal.domain.InvitationEmailStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Invitation email requests (spec invitation-emails, Data model). A row stores no link or token. Times come from the
 * database's {@code now()}. Rows are never deleted.
 */
@Repository
public class InvitationEmailRepository {

    private static final String COLUMNS = """
            select e.id, e.invitation_id, e.reason, e.status, e.requested_at, e.requested_by, e.finished_at
            from invitation_emails e
            """;

    private final JdbcClient jdbc;

    InvitationEmailRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts a {@link InvitationEmailStatus#QUEUED} request at now.
     *
     * @return the request's id
     * @throws DuplicateKeyException when the invitation already has a queued request ({@code invitation_emails_queued_uq});
     *     a parallel insert waits for the other transaction and fails if it commits. The failed statement aborts the
     *     transaction, so the caller can only roll back.
     */
    public UUID insertQueued(UUID invitationId, InvitationEmailReason reason, UUID requestedBy) {
        return jdbc.sql("""
                        insert into invitation_emails (invitation_id, reason, requested_by)
                        values (:invitationId, :reason, :requestedBy)
                        returning id
                        """)
                .param("invitationId", invitationId)
                .param("reason", reason.name())
                .param("requestedBy", requestedBy)
                .query(UUID.class)
                .single();
    }

    /** The invitation's queued request; there is at most one. */
    public Optional<StoredInvitationEmail> findQueued(UUID invitationId) {
        return jdbc.sql(COLUMNS + "where e.invitation_id = :invitationId and e.status = 'QUEUED'")
                .param("invitationId", invitationId)
                .query(InvitationEmailRepository::email)
                .optional();
    }

    /**
     * Locks the request's row until the end of the transaction and reads it, so two deliveries of one request are
     * serialized. {@code for no key update}, like the other row locks of this module.
     */
    public Optional<StoredInvitationEmail> lock(UUID id) {
        return jdbc.sql(COLUMNS + "where e.id = :id for no key update")
                .param("id", id)
                .query(InvitationEmailRepository::email)
                .optional();
    }

    /**
     * The latest request of each of the invitations that have one, by request time, then by id descending (the order of
     * {@code invitation_emails_latest_idx}).
     */
    public Map<UUID, StoredInvitationEmail> findLatest(Collection<UUID> invitationIds) {
        if (invitationIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                        select distinct on (e.invitation_id)
                               e.id, e.invitation_id, e.reason, e.status, e.requested_at, e.requested_by, e.finished_at
                        from invitation_emails e
                        where e.invitation_id in (:ids)
                        order by e.invitation_id, e.requested_at desc, e.id desc
                        """).param("ids", invitationIds).query(InvitationEmailRepository::email).list().stream()
                .collect(Collectors.toMap(StoredInvitationEmail::invitationId, Function.identity()));
    }

    /** @return whether the request was still queued and is now sent */
    public boolean markSent(UUID id) {
        return finish(id, InvitationEmailStatus.SENT, null);
    }

    /** @return whether the request was still queued and is now skipped */
    public boolean markSkipped(UUID id) {
        return finish(id, InvitationEmailStatus.SKIPPED, null);
    }

    /** @return whether the request was still queued and is now failed */
    public boolean markFailed(UUID id, InvitationEmailFailure failure) {
        return finish(id, InvitationEmailStatus.FAILED, failure.text());
    }

    /** Only a queued request changes, so a request that already has an outcome keeps it. */
    private boolean finish(UUID id, InvitationEmailStatus status, @Nullable String failure) {
        return jdbc.sql("""
                                update invitation_emails
                                set status = :status, finished_at = now(), failure = :failure
                                where id = :id and status = 'QUEUED'
                                """)
                        .param("id", id)
                        .param("status", status.name())
                        .param("failure", failure, Types.VARCHAR)
                        .update()
                == 1;
    }

    private static StoredInvitationEmail email(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime finishedAt = rs.getObject("finished_at", OffsetDateTime.class);
        return new StoredInvitationEmail(
                rs.getObject("id", UUID.class),
                rs.getObject("invitation_id", UUID.class),
                InvitationEmailReason.valueOf(rs.getString("reason")),
                InvitationEmailStatus.valueOf(rs.getString("status")),
                rs.getObject("requested_at", OffsetDateTime.class).toInstant(),
                rs.getObject("requested_by", UUID.class),
                finishedAt == null ? null : finishedAt.toInstant());
    }
}
