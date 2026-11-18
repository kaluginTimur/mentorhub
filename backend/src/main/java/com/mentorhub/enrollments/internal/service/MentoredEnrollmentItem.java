package com.mentorhub.enrollments.internal.service;

import com.mentorhub.enrollments.internal.domain.EnrollmentStatus;
import com.mentorhub.tracks.TrackSummary;
import com.mentorhub.users.DirectoryUser;
import java.time.LocalDate;
import java.util.UUID;

/** An enrollment the caller is a mentor of, with the student as they are now. */
public record MentoredEnrollmentItem(
        UUID id, DirectoryUser student, TrackSummary track, LocalDate startDate, EnrollmentStatus status) {}
