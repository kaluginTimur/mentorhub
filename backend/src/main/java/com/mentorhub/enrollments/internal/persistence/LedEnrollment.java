package com.mentorhub.enrollments.internal.persistence;

/** @param direct the user leads it as a mentor of the enrollment itself, not only through its cohort */
public record LedEnrollment(StoredEnrollment enrollment, boolean direct) {}
