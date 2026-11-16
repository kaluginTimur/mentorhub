package com.mentorhub.enrollments.internal.service;

import com.mentorhub.enrollments.internal.domain.EnrollmentStatus;
import com.mentorhub.users.DirectoryUser;
import java.time.LocalDate;
import java.util.UUID;

/** An enrollment as the enrollments of its track show it, with the student as they are now. */
public record TrackEnrollmentItem(UUID id, DirectoryUser student, LocalDate startDate, EnrollmentStatus status) {}
