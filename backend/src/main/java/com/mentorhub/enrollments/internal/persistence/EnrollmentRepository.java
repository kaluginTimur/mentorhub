package com.mentorhub.enrollments.internal.persistence;

import com.mentorhub.enrollments.internal.domain.EnrollmentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EnrollmentRepository {

    private static final String COLUMNS = "id, student_id, track_id, start_date, status";

    private final JdbcClient jdbc;

    EnrollmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts an active enrollment, unless the student has one: the unique index of one active enrollment per student
     * decides, parallel inserts included (ENR-2.6).
     *
     * @return empty when the student already has an active enrollment
     */
    public Optional<StoredEnrollment> insertIfNoActive(
            UUID studentId, UUID trackId, LocalDate startDate, UUID enrolledBy) {
        return jdbc.sql("""
                        insert into enrollments (student_id, track_id, start_date, status, enrolled_by)
                        values (:studentId, :trackId, :startDate, 'ACTIVE', :enrolledBy)
                        on conflict do nothing
                        returning\s""" + COLUMNS)
                .param("studentId", studentId)
                .param("trackId", trackId)
                .param("startDate", startDate)
                .param("enrolledBy", enrolledBy)
                .query(EnrollmentRepository::storedEnrollment)
                .optional();
    }

    /** Every enrollment of the track, in any status. */
    public List<StoredEnrollment> findByTrack(UUID trackId) {
        return jdbc.sql("select " + COLUMNS + " from enrollments where track_id = :trackId")
                .param("trackId", trackId)
                .query(EnrollmentRepository::storedEnrollment)
                .list();
    }

    /** Every enrollment of the student, in any status, newest first. */
    public List<StoredEnrollment> findByStudent(UUID studentId) {
        return jdbc.sql("select " + COLUMNS + " from enrollments where student_id = :studentId"
                        + " order by created_at desc, id")
                .param("studentId", studentId)
                .query(EnrollmentRepository::storedEnrollment)
                .list();
    }

    /** The students who have an active enrollment, on any track. */
    public List<UUID> findActiveStudentIds() {
        return jdbc.sql("select student_id from enrollments where status = 'ACTIVE'")
                .query((rs, rowNum) -> rs.getObject("student_id", UUID.class))
                .list();
    }

    public boolean existActiveOnTrack(UUID trackId) {
        return jdbc.sql("select exists (select 1 from enrollments where track_id = :trackId and status = 'ACTIVE')")
                .param("trackId", trackId)
                .query(Boolean.class)
                .single();
    }

    private static StoredEnrollment storedEnrollment(ResultSet rs, int rowNum) throws SQLException {
        return new StoredEnrollment(
                rs.getObject("id", UUID.class),
                rs.getObject("student_id", UUID.class),
                rs.getObject("track_id", UUID.class),
                rs.getObject("start_date", LocalDate.class),
                EnrollmentStatus.valueOf(rs.getString("status")));
    }
}
