package com.mentorhub.enrollments.internal.service;

import java.util.UUID;

/** A cohort with its name as it is now. */
public record NamedCohort(UUID id, String name) {}
