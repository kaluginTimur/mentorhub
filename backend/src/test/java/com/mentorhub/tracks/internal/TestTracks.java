package com.mentorhub.tracks.internal;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Direct database setup of tracks for tests that run against committed data. {@code TestUsers.deleteAll} removes the
 * rows.
 */
public final class TestTracks {

    private final JdbcClient jdbc;

    public TestTracks(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID create(String code, String name, String status) {
        return jdbc.sql("insert into tracks (code, name, status) values (:code, :name, :status) returning id")
                .param("code", code)
                .param("name", name)
                .param("status", status)
                .query(UUID.class)
                .single();
    }

    public UUID active(String code, String name) {
        return create(code, name, "ACTIVE");
    }

    public UUID archived(String code, String name) {
        return create(code, name, "ARCHIVED");
    }

    public void addCoordinator(UUID trackId, UUID userId) {
        jdbc.sql("insert into track_coordinators (track_id, user_id) values (:trackId, :userId)")
                .param("trackId", trackId)
                .param("userId", userId)
                .update();
    }
}
