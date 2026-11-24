package com.mentorhub.enrollments.internal.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static StoredCohort storedCohort(ResultSet rs, int rowNum) throws SQLException {
        return new StoredCohort(
                rs.getObject("id", UUID.class),
                rs.getObject("track_id", UUID.class),
                rs.getString("name"),
                rs.getObject("start_date", LocalDate.class));
    }
}
