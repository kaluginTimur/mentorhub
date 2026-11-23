package com.mentorhub.enrollments.internal.service;

import java.time.LocalDate;
import java.util.UUID;

/** @param enrollmentCount the number of active enrollments in the cohort */
public record CohortItem(UUID id, String name, LocalDate startDate, int enrollmentCount) {}
