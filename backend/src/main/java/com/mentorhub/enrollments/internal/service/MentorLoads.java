package com.mentorhub.enrollments.internal.service;

import com.mentorhub.enrollments.internal.persistence.CohortRepository;
import com.mentorhub.enrollments.internal.persistence.EnrollmentRepository;
import com.mentorhub.users.DirectoryPage;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** What the candidates for the mentors of an enrollment or of a cohort lead on its track (ENR-10, COH-10). */
@Component
class MentorLoads {

    private final EnrollmentRepository enrollments;
    private final CohortRepository cohorts;

    MentorLoads(EnrollmentRepository enrollments, CohortRepository cohorts) {
        this.enrollments = enrollments;
        this.cohorts = cohorts;
    }

    EnrollmentPage<MentorCandidateItem> page(UUID trackId, DirectoryPage candidates, int page, int size) {
        Map<UUID, Integer> students =
                enrollments.countActiveLed(List.of(trackId)).getOrDefault(trackId, Map.of());
        Map<UUID, Integer> cohortCounts = cohorts.countMentoredCohorts(trackId);
        List<MentorCandidateItem> items = candidates.items().stream()
                .map(user -> new MentorCandidateItem(
                        user, students.getOrDefault(user.id(), 0), cohortCounts.getOrDefault(user.id(), 0)))
                .toList();
        return new EnrollmentPage<>(items, page, size, candidates.totalItems());
    }
}
