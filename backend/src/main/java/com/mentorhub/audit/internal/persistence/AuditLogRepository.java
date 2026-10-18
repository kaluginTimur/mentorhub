package com.mentorhub.audit.internal.persistence;

import com.mentorhub.audit.AuditEntry;
import com.mentorhub.audit.RecordedAuditEntry;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class AuditLogRepository {

    private static final TypeReference<Map<String, @Nullable Object>> DETAILS = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    AuditLogRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    public void insert(AuditEntry entry) {
        jdbc.sql("""
                        insert into audit_log (actor_user_id, action, subject_type, subject_id, details)
                        values (:actor, :action, :subjectType, :subjectId, cast(:details as jsonb))
                        """)
                .param("actor", entry.actorUserId(), Types.OTHER)
                .param("action", entry.action())
                .param("subjectType", entry.subjectType())
                .param("subjectId", entry.subjectId())
                .param("details", jsonMapper.writeValueAsString(entry.details()))
                .update();
    }

    public List<RecordedAuditEntry> findBySubject(String subjectType, UUID subjectId, int limit, long offset) {
        return jdbc.sql("""
                        select id, occurred_at, actor_user_id, action, subject_type, subject_id, details::text as details
                        from audit_log
                        where subject_type = :subjectType and subject_id = :subjectId
                        order by occurred_at desc, id desc
                        limit :limit offset :offset
                        """)
                .param("subjectType", subjectType)
                .param("subjectId", subjectId)
                .param("limit", limit)
                .param("offset", offset)
                .query((rs, rowNum) -> new RecordedAuditEntry(
                        rs.getObject("id", UUID.class),
                        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                        rs.getObject("actor_user_id", UUID.class),
                        rs.getString("action"),
                        rs.getString("subject_type"),
                        rs.getObject("subject_id", UUID.class),
                        jsonMapper.readValue(rs.getString("details"), DETAILS)))
                .list();
    }

    public long countBySubject(String subjectType, UUID subjectId) {
        return jdbc.sql("select count(*) from audit_log where subject_type = :subjectType and subject_id = :subjectId")
                .param("subjectType", subjectType)
                .param("subjectId", subjectId)
                .query(Long.class)
                .single();
    }
}
