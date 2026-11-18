package com.mentorhub.enrollments.internal;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Direct database setup of enrollments for tests that run against committed data. {@code TestUsers.deleteAll} removes
 * the rows. Nothing is audited, as an enrollment made by the application would be.
 */
public final class TestEnrollments {

    private final JdbcClient jdbc;

    public TestEnrollments(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID enroll(UUID studentId, UUID trackId, UUID enrolledBy, LocalDate startDate) {
        return jdbc.sql("insert into enrollments (student_id, track_id, start_date, enrolled_by)"
                        + " values (:student, :track, :startDate, :enrolledBy) returning id")
                .param("student", studentId)
                .param("track", trackId)
                .param("startDate", startDate)
                .param("enrolledBy", enrolledBy)
                .query(UUID.class)
                .single();
    }

    public void addMentor(UUID enrollmentId, UUID mentorId, UUID assignedBy) {
        jdbc.sql("insert into enrollment_mentors (enrollment_id, mentor_id, assigned_by)"
                        + " values (:enrollment, :mentor, :assignedBy)")
                .param("enrollment", enrollmentId)
                .param("mentor", mentorId)
                .param("assignedBy", assignedBy)
                .update();
    }

    public void removeMentor(UUID enrollmentId, UUID mentorId) {
        jdbc.sql("delete from enrollment_mentors where enrollment_id = :enrollment and mentor_id = :mentor")
                .param("enrollment", enrollmentId)
                .param("mentor", mentorId)
                .update();
    }
}
