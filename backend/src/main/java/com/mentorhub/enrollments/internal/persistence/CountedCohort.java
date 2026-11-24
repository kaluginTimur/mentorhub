package com.mentorhub.enrollments.internal.persistence;

/** @param enrollmentCount the number of active enrollments in the cohort */
public record CountedCohort(StoredCohort cohort, int enrollmentCount) {}
