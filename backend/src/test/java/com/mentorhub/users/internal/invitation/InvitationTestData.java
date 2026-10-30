package com.mentorhub.users.internal.invitation;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Direct database access for invitation tests; they run against committed data. */
final class InvitationTestData {

    record AuditRow(
            String action, @Nullable UUID actor, String subjectType, UUID subjectId, Map<String, Object> details) {}

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    InvitationTestData(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    void expire(UUID invitationId) {
        jdbc.sql("update invitations set expires_at = now() - interval '1 minute' where id = :id")
                .param("id", invitationId)
                .update();
    }

    void markAccepted(UUID invitationId, UUID userId) {
        jdbc.sql("update invitations set accepted_at = now(), accepted_user_id = :userId where id = :id")
                .param("id", invitationId)
                .param("userId", userId)
                .update();
    }

    void markRevoked(UUID invitationId, UUID userId) {
        jdbc.sql("update invitations set revoked_at = now(), revoked_by = :userId where id = :id")
                .param("id", invitationId)
                .param("userId", userId)
                .update();
    }

    void addStoredRole(UUID invitationId, String role) {
        jdbc.sql("insert into roles (name) values (:role) on conflict do nothing")
                .param("role", role)
                .update();
        jdbc.sql("insert into invitation_roles (invitation_id, role) values (:id, :role)")
                .param("id", invitationId)
                .param("role", role)
                .update();
    }

    String storedToken(UUID invitationId) {
        return jdbc.sql("select token from invitations where id = :id")
                .param("id", invitationId)
                .query(String.class)
                .single();
    }

    String allAuditDetails() {
        return String.join(
                "\n",
                jdbc.sql("select details::text from audit_log")
                        .query(String.class)
                        .list());
    }

    List<AuditRow> auditEntries(UUID subjectId) {
        return jdbc.sql("""
                        select action, actor_user_id, subject_type, subject_id, details::text as details
                        from audit_log where subject_id = :subject
                        order by occurred_at, id
                        """)
                .param("subject", subjectId)
                .query((rs, rowNum) -> new AuditRow(
                        rs.getString("action"),
                        rs.getObject("actor_user_id", UUID.class),
                        rs.getString("subject_type"),
                        rs.getObject("subject_id", UUID.class),
                        jsonMapper.readValue(rs.getString("details"), new TypeReference<Map<String, Object>>() {})))
                .list();
    }

    int count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Integer.class).single();
    }
}
