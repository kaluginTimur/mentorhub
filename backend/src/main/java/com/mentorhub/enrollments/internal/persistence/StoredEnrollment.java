package com.mentorhub.enrollments.internal.persistence;

import com.mentorhub.enrollments.internal.domain.EnrollmentStatus;
import java.time.LocalDate;
import java.util.UUID;

public record StoredEnrollment(UUID id, UUID studentId, UUID trackId, LocalDate startDate, EnrollmentStatus status) {}
