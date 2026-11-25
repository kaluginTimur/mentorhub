package com.mentorhub.enrollments.internal;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Direct database setup of cohorts for tests that run against committed data. {@code TestUsers.deleteAll} removes the
 * rows. Nothing is audited, as a cohort made by the application would be.
 */
public final class TestCohorts {

    private final JdbcClient jdbc;

    public TestCohorts(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID create(UUID trackId, String name, LocalDate startDate) {
        return jdbc.sql("insert into cohorts (track_id, name, start_date) values (:track, :name, :startDate)"
                        + " returning id")
                .param("track", trackId)
                .param("name", name)
                .param("startDate", startDate)
                .query(UUID.class)
                .single();
    }

    /** Puts the enrollment into the cohort, which must be of the enrollment's track. */
    public void put(UUID enrollmentId, UUID cohortId) {
        jdbc.sql("update enrollments set cohort_id = :cohort where id = :enrollment")
                .param("cohort", cohortId)
                .param("enrollment", enrollmentId)
                .update();
    }

    /** Makes the user a mentor of the cohort; the track's mentors are not checked. */
    public void addMentor(UUID cohortId, UUID mentorId, UUID assignedBy) {
        jdbc.sql(
                        "insert into cohort_mentors (cohort_id, mentor_id, assigned_by) values (:cohort, :mentor, :assignedBy)")
                .param("cohort", cohortId)
                .param("mentor", mentorId)
                .param("assignedBy", assignedBy)
                .update();
    }
}
