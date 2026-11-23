package com.mentorhub.enrollments.internal.service;

import com.mentorhub.enrollments.internal.EnrollmentProblems;
import com.mentorhub.tracks.TrackDirectory;
import com.mentorhub.users.CurrentUser;
import com.mentorhub.users.Permission;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.FieldViolation;
import com.mentorhub.web.ValidationException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;

/** The checks the enrollments and the cohorts of a track share. */
final class EnrollmentRules {

    static final int MAX_QUERY_LENGTH = 100;

    private EnrollmentRules() {}

    /** The controller admits only callers with one of the two permissions (ENR-1). */
    static void checkRelation(TrackDirectory tracks, CurrentUser caller, UUID trackId) {
        if (!caller.has(Permission.ENROLLMENTS_MANAGE) && !tracks.isCoordinator(trackId, caller.id())) {
            throw new AccessDeniedException("Not a coordinator of the track");
        }
    }

    /** @return the trimmed search, or {@code null} for none */
    static @Nullable String searchText(@Nullable String q) {
        if (q == null) {
            return null;
        }
        String text = q.strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.codePointCount(0, text.length()) > MAX_QUERY_LENGTH) {
            throw new ValidationException(
                    List.of(new FieldViolation("q", "must be at most " + MAX_QUERY_LENGTH + " characters")));
        }
        return text;
    }

    static ConflictException trackArchived(String detail) {
        return new ConflictException(EnrollmentProblems.TRACK_ARCHIVED, detail);
    }
}
