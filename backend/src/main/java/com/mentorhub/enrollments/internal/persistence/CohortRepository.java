package com.mentorhub.enrollments.internal.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The cohorts of tracks. Callers that change cohorts or the cohorts of enrollments hold the lock of the track first
 * ({@link com.mentorhub.tracks.TrackDirectory#lockForEnrollment}); this repository takes no lock of its own.
 */
@Repository
public class CohortRepository {

    private static final String COLUMNS = "c.id, c.track_id, c.name, c.start_date";

    private final JdbcClient jdbc;

    CohortRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public StoredCohort insert(UUID trackId, String name, LocalDate startDate) {
        return jdbc.sql("insert into cohorts as c (track_id, name, start_date) values (:trackId, :name, :startDate)"
                        + " returning " + COLUMNS)
                .param("trackId", trackId)
                .param("name", name)
                .param("startDate", startDate)
                .query(CohortRepository::storedCohort)
                .single();
    }

    /** The cohort, if it is a cohort of the track. */
    public Optional<StoredCohort> findOnTrack(UUID trackId, UUID cohortId) {
        return jdbc.sql("select " + COLUMNS + " from cohorts c where c.id = :id and c.track_id = :trackId")
                .param("id", cohortId)
                .param("trackId", trackId)
                .query(CohortRepository::storedCohort)
                .optional();
    }

    /** The cohorts with the given ids; ids no cohort has are left out. */
    public Map<UUID, StoredCohort> findAll(Collection<UUID> cohortIds) {
        if (cohortIds.isEmpty()) {
            return Map.of();
        }
        return jdbc
                .sql("select " + COLUMNS + " from cohorts c where c.id = any(:ids)")
                .param("ids", UuidArray.of(cohortIds))
                .query(CohortRepository::storedCohort)
                .list()
                .stream()
                .collect(Collectors.toMap(StoredCohort::id, Function.identity()));
    }

    /**
     * Every cohort of the track with its number of active enrollments, newest start date first, then by name ignoring
     * case, then by id.
     */
    public List<CountedCohort> findByTrack(UUID trackId) {
        return jdbc.sql("""
                        select %s,
                               (select count(*) from enrollments e where e.cohort_id = c.id and e.status = 'ACTIVE')
                                   as enrollment_count
                        from cohorts c
                        where c.track_id = :trackId
                        order by c.start_date desc, lower(c.name), c.id
                        """.formatted(COLUMNS))
                .param("trackId", trackId)
                .query((rs, rowNum) -> new CountedCohort(storedCohort(rs, rowNum), rs.getInt("enrollment_count")))
                .list();
    }

    /** The number of active enrollments in the cohort. */
    public int countActive(UUID cohortId) {
        return jdbc.sql("select count(*) from enrollments where cohort_id = :id and status = 'ACTIVE'")
                .param("id", cohortId)
                .query(Integer.class)
                .single();
    }

    /** Whether an enrollment, in any status, is in the cohort. */
    public boolean hasEnrollments(UUID cohortId) {
        return jdbc.sql("select exists (select 1 from enrollments where cohort_id = :id)")
                .param("id", cohortId)
                .query(Boolean.class)
                .single();
    }

    public void delete(UUID cohortId) {
        jdbc.sql("delete from cohorts where id = :id").param("id", cohortId).update();
    }

    /**
     * Adds the mentor to the cohort, unless they are one: the primary key decides, parallel inserts included (COH-9.4).
     *
     * @return whether a row was inserted
     */
    public boolean insertMentor(UUID cohortId, UUID mentorId, UUID assignedBy) {
        return jdbc.sql("""
                        insert into cohort_mentors (cohort_id, mentor_id, assigned_by)
                        values (:cohortId, :mentorId, :assignedBy)
                        on conflict do nothing""")
                        .param("cohortId", cohortId)
                        .param("mentorId", mentorId)
                        .param("assignedBy", assignedBy)
                        .update()
                > 0;
    }

    /** @return whether a row was deleted */
    public boolean deleteMentor(UUID cohortId, UUID mentorId) {
        return jdbc.sql("delete from cohort_mentors where cohort_id = :cohortId and mentor_id = :mentorId")
                        .param("cohortId", cohortId)
                        .param("mentorId", mentorId)
                        .update()
                > 0;
    }

    /**
     * Removes every mentor of the cohort.
     *
     * @return the ids of the mentors removed
     */
    public List<UUID> deleteMentors(UUID cohortId) {
        return jdbc.sql("delete from cohort_mentors where cohort_id = :cohortId returning mentor_id")
                .param("cohortId", cohortId)
                .query((rs, rowNum) -> rs.getObject("mentor_id", UUID.class))
                .list();
    }

    /** The mentors of each of the cohorts; a cohort without one is left out. */
    public Map<UUID, Set<UUID>> mentorIds(Collection<UUID> cohortIds) {
        if (cohortIds.isEmpty()) {
            return Map.of();
        }
        record Row(UUID cohortId, UUID mentorId) {}
        return jdbc
                .sql("select cohort_id, mentor_id from cohort_mentors where cohort_id = any(:ids)")
                .param("ids", UuidArray.of(cohortIds))
                .query((rs, rowNum) ->
                        new Row(rs.getObject("cohort_id", UUID.class), rs.getObject("mentor_id", UUID.class)))
                .list()
                .stream()
                .collect(Collectors.groupingBy(Row::cohortId, Collectors.mapping(Row::mentorId, Collectors.toSet())));
    }

    /** Whether the user is a mentor of a cohort of the track. */
    public boolean isMentorOnTrack(UUID trackId, UUID mentorId) {
        return jdbc.sql("""
                        select exists (
                            select 1 from cohort_mentors m
                            join cohorts c on c.id = m.cohort_id
                            where c.track_id = :trackId and m.mentor_id = :mentorId
                        )
                        """)
                .param("trackId", trackId)
                .param("mentorId", mentorId)
                .query(Boolean.class)
                .single();
    }

    /** The number of cohorts of the track each user is a mentor of; a user who is a mentor of none is left out. */
    public Map<UUID, Integer> countMentoredCohorts(UUID trackId) {
        record Row(UUID mentorId, int count) {}
        return jdbc
                .sql("""
                        select m.mentor_id, count(*) as cohorts
                        from cohort_mentors m
                        join cohorts c on c.id = m.cohort_id
                        where c.track_id = :trackId
                        group by m.mentor_id""")
                .param("trackId", trackId)
                .query((rs, rowNum) -> new Row(rs.getObject("mentor_id", UUID.class), rs.getInt("cohorts")))
                .list()
                .stream()
                .collect(Collectors.toMap(Row::mentorId, Row::count));
    }

    private static StoredCohort storedCohort(ResultSet rs, int rowNum) throws SQLException {
        return new StoredCohort(
                rs.getObject("id", UUID.class),
                rs.getObject("track_id", UUID.class),
                rs.getString("name"),
                rs.getObject("start_date", LocalDate.class));
    }
}
