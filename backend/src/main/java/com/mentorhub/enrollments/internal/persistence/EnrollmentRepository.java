package com.mentorhub.enrollments.internal.persistence;

import com.mentorhub.enrollments.internal.domain.EnrollmentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

    public Optional<StoredEnrollment> find(UUID enrollmentId) {
        return jdbc.sql("select " + COLUMNS + " from enrollments where id = :id")
                .param("id", enrollmentId)
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

    /** Every enrollment of the track, in any status, the user is a mentor of now. */
    public List<StoredEnrollment> findByTrackAndMentor(UUID trackId, UUID mentorId) {
        return jdbc.sql("select " + COLUMNS + " from enrollments where track_id = :trackId"
                        + " and id in (select enrollment_id from enrollment_mentors where mentor_id = :mentorId)")
                .param("trackId", trackId)
                .param("mentorId", mentorId)
                .query(EnrollmentRepository::storedEnrollment)
                .list();
    }

    /**
     * The number of active enrollments each user is a mentor of, per track; a user who leads none on a track is left
     * out of it, and so is a track without any.
     */
    public Map<UUID, Map<UUID, Integer>> countActiveLed(Collection<UUID> trackIds) {
        if (trackIds.isEmpty()) {
            return Map.of();
        }
        record Row(UUID trackId, UUID mentorId, int count) {}
        return jdbc
                .sql("""
                        select e.track_id, m.mentor_id, count(*) as led
                        from enrollments e
                        join enrollment_mentors m on m.enrollment_id = e.id
                        where e.track_id in (:ids) and e.status = 'ACTIVE'
                        group by e.track_id, m.mentor_id""")
                .param("ids", trackIds)
                .query((rs, rowNum) -> new Row(
                        rs.getObject("track_id", UUID.class), rs.getObject("mentor_id", UUID.class), rs.getInt("led")))
                .list()
                .stream()
                .collect(Collectors.groupingBy(Row::trackId, Collectors.toMap(Row::mentorId, Row::count)));
    }

    /** Every enrollment of the student, in any status, newest first. */
    public List<StoredEnrollment> findByStudent(UUID studentId) {
        return jdbc.sql("select " + COLUMNS + " from enrollments where student_id = :studentId"
                        + " order by created_at desc, id")
                .param("studentId", studentId)
                .query(EnrollmentRepository::storedEnrollment)
                .list();
    }

    /** Every enrollment, in any status, the user is a mentor of now. */
    public List<StoredEnrollment> findByMentor(UUID mentorId) {
        return jdbc.sql("select " + COLUMNS + " from enrollments"
                        + " where id in (select enrollment_id from enrollment_mentors where mentor_id = :mentorId)")
                .param("mentorId", mentorId)
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

    public boolean existActiveLedOnTrack(UUID trackId, UUID mentorId) {
        return jdbc.sql("""
                        select exists (
                            select 1 from enrollments e
                            join enrollment_mentors m on m.enrollment_id = e.id
                            where e.track_id = :trackId and e.status = 'ACTIVE' and m.mentor_id = :mentorId
                        )
                        """)
                .param("trackId", trackId)
                .param("mentorId", mentorId)
                .query(Boolean.class)
                .single();
    }

    /**
     * Adds the mentor to the enrollment, unless they are one: the primary key decides, parallel inserts included
     * (ENR-9.4).
     *
     * @return whether a row was inserted
     */
    public boolean insertMentor(UUID enrollmentId, UUID mentorId, UUID assignedBy) {
        return jdbc.sql("""
                        insert into enrollment_mentors (enrollment_id, mentor_id, assigned_by)
                        values (:enrollmentId, :mentorId, :assignedBy)
                        on conflict do nothing""")
                        .param("enrollmentId", enrollmentId)
                        .param("mentorId", mentorId)
                        .param("assignedBy", assignedBy)
                        .update()
                > 0;
    }

    /** @return whether a row was deleted */
    public boolean deleteMentor(UUID enrollmentId, UUID mentorId) {
        return jdbc.sql("delete from enrollment_mentors where enrollment_id = :enrollmentId and mentor_id = :mentorId")
                        .param("enrollmentId", enrollmentId)
                        .param("mentorId", mentorId)
                        .update()
                > 0;
    }

    public boolean isMentor(UUID enrollmentId, UUID userId) {
        return jdbc.sql("select exists (select 1 from enrollment_mentors"
                        + " where enrollment_id = :enrollmentId and mentor_id = :userId)")
                .param("enrollmentId", enrollmentId)
                .param("userId", userId)
                .query(Boolean.class)
                .single();
    }

    /** The mentors of each of the enrollments; an enrollment without one is left out. */
    public Map<UUID, Set<UUID>> mentorIds(Collection<UUID> enrollmentIds) {
        if (enrollmentIds.isEmpty()) {
            return Map.of();
        }
        record Row(UUID enrollmentId, UUID mentorId) {}
        return jdbc
                .sql("select enrollment_id, mentor_id from enrollment_mentors where enrollment_id in (:ids)")
                .param("ids", enrollmentIds)
                .query((rs, rowNum) ->
                        new Row(rs.getObject("enrollment_id", UUID.class), rs.getObject("mentor_id", UUID.class)))
                .list()
                .stream()
                .collect(Collectors.groupingBy(
                        Row::enrollmentId, Collectors.mapping(Row::mentorId, Collectors.toSet())));
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
