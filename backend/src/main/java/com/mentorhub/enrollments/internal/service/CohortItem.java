package com.mentorhub.enrollments.internal.service;

import com.mentorhub.users.DirectoryUser;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param enrollmentCount the number of active enrollments in the cohort
 * @param mentors the mentors of the cohort, in any status and with any roles, sorted by name, then email, then id
 */
public record CohortItem(UUID id, String name, LocalDate startDate, int enrollmentCount, List<DirectoryUser> mentors) {

    public CohortItem {
        mentors = List.copyOf(mentors);
    }
}
