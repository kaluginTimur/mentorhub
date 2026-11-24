package com.mentorhub.enrollments.internal.persistence;

import java.time.LocalDate;
import java.util.UUID;

public record StoredCohort(UUID id, UUID trackId, String name, LocalDate startDate) {}
