package com.mentorhub.tracks.internal.persistence;

import com.mentorhub.tracks.internal.domain.TrackStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class TrackRepository {

    private final JdbcClient jdbc;

    TrackRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Inserts an active track. */
    public StoredTrack insert(String code, String name) {
        return jdbc.sql("""
                        insert into tracks (code, name, status) values (:code, :name, 'ACTIVE')
                        returning id, code, name, status
                        """)
                .param("code", code)
                .param("name", name)
                .query(TrackRepository::storedTrack)
                .single();
    }

    public Optional<StoredTrack> find(UUID trackId) {
        return jdbc.sql("select id, code, name, status from tracks where id = :id")
                .param("id", trackId)
                .query(TrackRepository::storedTrack)
                .optional();
    }

    /** The tracks with the given ids; ids no track has are left out. */
    public List<StoredTrack> findAll(Collection<UUID> trackIds) {
        if (trackIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select id, code, name, status from tracks where id in (:ids)")
                .param("ids", List.copyOf(trackIds))
                .query(TrackRepository::storedTrack)
                .list();
    }

    /**
     * Locks the track's row until the end of the transaction, so the changes of one track, its archiving included, are
     * serialized. {@code for no key update}, not {@code for update}: inserting into {@code track_coordinators} or
     * {@code enrollments} takes {@code for key share} on the track, which {@code for update} blocks.
     */
    public Optional<StoredTrack> lock(UUID trackId) {
        return jdbc.sql("select id, code, name, status from tracks where id = :id for no key update")
                .param("id", trackId)
                .query(TrackRepository::storedTrack)
                .optional();
    }

    /**
     * The tracks of the status, or every track for {@code null}, sorted by code and then by name ignoring case.
     */
    public List<StoredTrack> findAll(@Nullable TrackStatus status) {
        String where = status == null ? "" : "where status = :status\n";
        JdbcClient.StatementSpec statement = jdbc.sql(
                "select id, code, name, status from tracks\n" + where + "order by lower(code), lower(name), id");
        if (status != null) {
            statement = statement.param("status", status.name());
        }
        return statement.query(TrackRepository::storedTrack).list();
    }

    public void update(UUID trackId, String code, String name) {
        jdbc.sql("update tracks set code = :code, name = :name where id = :id")
                .param("id", trackId)
                .param("code", code)
                .param("name", name)
                .update();
    }

    public void updateStatus(UUID trackId, TrackStatus status) {
        jdbc.sql("update tracks set status = :status where id = :id")
                .param("id", trackId)
                .param("status", status.name())
                .update();
    }

    public List<TrackCoordinator> findCoordinators(Collection<UUID> trackIds) {
        if (trackIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("select track_id, user_id from track_coordinators where track_id in (:ids)")
                .param("ids", List.copyOf(trackIds))
                .query((rs, rowNum) ->
                        new TrackCoordinator(rs.getObject("track_id", UUID.class), rs.getObject("user_id", UUID.class)))
                .list();
    }

    public boolean isCoordinator(UUID trackId, UUID userId) {
        return jdbc.sql(
                        "select exists (select 1 from track_coordinators where track_id = :trackId and user_id = :userId)")
                .param("trackId", trackId)
                .param("userId", userId)
                .query(Boolean.class)
                .single();
    }

    /** Returns the number of inserted rows: 0 when the user is already a coordinator of the track. */
    public int addCoordinatorIfAbsent(UUID trackId, UUID userId) {
        return jdbc.sql("""
                        insert into track_coordinators (track_id, user_id) values (:trackId, :userId)
                        on conflict do nothing
                        """).param("trackId", trackId).param("userId", userId).update();
    }

    /** Returns the number of deleted rows: 0 when the user is not a coordinator of the track. */
    public int removeCoordinator(UUID trackId, UUID userId) {
        return jdbc.sql("delete from track_coordinators where track_id = :trackId and user_id = :userId")
                .param("trackId", trackId)
                .param("userId", userId)
                .update();
    }

    private static StoredTrack storedTrack(ResultSet rs, int rowNum) throws SQLException {
        return new StoredTrack(
                rs.getObject("id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                TrackStatus.valueOf(rs.getString("status")));
    }
}
