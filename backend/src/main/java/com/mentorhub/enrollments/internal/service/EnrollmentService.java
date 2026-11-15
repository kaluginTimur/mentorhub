package com.mentorhub.enrollments.internal.service;

import com.mentorhub.enrollments.internal.EnrollmentProblems;
import com.mentorhub.enrollments.internal.persistence.EnrollmentRepository;
import com.mentorhub.enrollments.internal.persistence.StoredEnrollment;
import com.mentorhub.tracks.TrackDirectory;
import com.mentorhub.tracks.TrackEnrollmentHistory;
import com.mentorhub.tracks.TrackSummary;
import com.mentorhub.users.CurrentUser;
import com.mentorhub.users.CurrentUserProvider;
import com.mentorhub.users.DirectoryPage;
import com.mentorhub.users.DirectoryUser;
import com.mentorhub.users.HistoryTrack;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.UserDirectory;
import com.mentorhub.users.UserEnrollmentHistory;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.FieldViolation;
import com.mentorhub.web.NotFoundException;
import com.mentorhub.web.ValidationException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enrollments (ENR). The controller checks the permission; this service checks, in order, the request, the track (404),
 * the relation for a caller who may enroll only on the tracks they coordinate (403), then the conflicts (409).
 *
 * <p>Enrolling locks the track's row first ({@link TrackDirectory#lockForEnrollment}), so enrolling on a track and
 * archiving it run one after another (TRK-5.6); no other lock is taken. One active enrollment per student is decided by
 * the unique index the insert relies on (ENR-2.6).
 */
@Service
public class EnrollmentService {

    static final int MAX_QUERY_LENGTH = 100;

    private final CurrentUserProvider currentUser;
    private final EnrollmentRepository enrollments;
    private final TrackDirectory tracks;
    private final TrackEnrollmentHistory trackHistory;
    private final UserDirectory users;
    private final UserEnrollmentHistory userHistory;

    EnrollmentService(
            CurrentUserProvider currentUser,
            EnrollmentRepository enrollments,
            TrackDirectory tracks,
            TrackEnrollmentHistory trackHistory,
            UserDirectory users,
            UserEnrollmentHistory userHistory) {
        this.currentUser = currentUser;
        this.enrollments = enrollments;
        this.tracks = tracks;
        this.trackHistory = trackHistory;
        this.users = users;
        this.userHistory = userHistory;
    }

    /** The enrollments of an active or an archived track, sorted by the student's name, then email, then id. */
    @Transactional(readOnly = true)
    public EnrollmentPage<TrackEnrollmentItem> listOfTrack(UUID trackId, int page, int size) {
        if (tracks.find(trackId).isEmpty()) {
            throw new NotFoundException();
        }
        Map<UUID, StoredEnrollment> byStudent = enrollments.findByTrack(trackId).stream()
                .collect(Collectors.toMap(StoredEnrollment::studentId, Function.identity()));
        DirectoryPage students = users.findUsers(byStudent.keySet(), page, size);
        List<TrackEnrollmentItem> items = students.items().stream()
                .map(student -> item(byStudent.get(student.id()), student))
                .toList();
        return new EnrollmentPage<>(items, page, size, students.totalItems());
    }

    /**
     * The active users with the student role and no active enrollment.
     *
     * @param q searched in the email, first and last name after trimming; blank means no search
     * @throws ValidationException for {@code q}
     * @throws AccessDeniedException when the caller may enroll only on tracks they coordinate, and does not coordinate
     *     this one
     * @throws ConflictException {@link EnrollmentProblems#TRACK_ARCHIVED}
     */
    @Transactional(readOnly = true)
    public EnrollmentPage<DirectoryUser> candidates(UUID trackId, @Nullable String q, int page, int size) {
        String text = searchText(q);
        TrackSummary track = tracks.find(trackId).orElseThrow(NotFoundException::new);
        checkRelation(currentUser.currentUser(), trackId);
        if (track.archived()) {
            throw trackArchived();
        }
        DirectoryPage candidates =
                users.findActiveUsersWithRole(Role.STUDENT, text, enrollments.findActiveStudentIds(), page, size);
        return new EnrollmentPage<>(candidates.items(), page, size, candidates.totalItems());
    }

    /**
     * Enrolls the student on the track from the start date, recorded in the track's and the student's history.
     *
     * @throws AccessDeniedException when the caller may enroll only on tracks they coordinate, and does not coordinate
     *     this one
     * @throws ConflictException {@link EnrollmentProblems#TRACK_ARCHIVED}, {@link EnrollmentProblems#STUDENT_NOT_ELIGIBLE}
     *     or {@link EnrollmentProblems#STUDENT_ALREADY_ENROLLED}, in this order
     */
    @Transactional
    public TrackEnrollmentItem enroll(UUID trackId, UUID studentId, LocalDate startDate) {
        CurrentUser caller = currentUser.currentUser();
        TrackSummary track = tracks.lockForEnrollment(trackId).orElseThrow(NotFoundException::new);
        checkRelation(caller, trackId);
        if (track.archived()) {
            throw trackArchived();
        }
        if (!users.isActiveWithRole(studentId, Role.STUDENT)) {
            throw new ConflictException(
                    EnrollmentProblems.STUDENT_NOT_ELIGIBLE,
                    "Only an active user with the student role can be enrolled.");
        }
        StoredEnrollment enrollment = enrollments
                .insertIfNoActive(studentId, trackId, startDate, caller.id())
                .orElseThrow(() -> new ConflictException(
                        EnrollmentProblems.STUDENT_ALREADY_ENROLLED, "The student already has an active enrollment."));
        trackHistory.recordStudentEnrolled(caller.id(), trackId, studentId, startDate);
        userHistory.recordEnrolled(
                caller.id(), studentId, new HistoryTrack(track.id(), track.code(), track.name()), startDate);
        DirectoryUser student = users.findUsers(List.of(studentId)).getFirst();
        return item(enrollment, student);
    }

    /** The caller's own enrollments, newest first, with the coordinators to contact. */
    @Transactional(readOnly = true)
    public List<OwnEnrollment> mine() {
        List<StoredEnrollment> own =
                enrollments.findByStudent(currentUser.currentUser().id());
        if (own.isEmpty()) {
            return List.of();
        }
        Map<UUID, TrackSummary> trackById =
                tracks.findTracks(own.stream().map(StoredEnrollment::trackId).toList()).stream()
                        .collect(Collectors.toMap(TrackSummary::id, Function.identity()));
        return own.stream()
                .map(enrollment -> new OwnEnrollment(
                        enrollment.id(),
                        track(trackById, enrollment.trackId()),
                        enrollment.startDate(),
                        enrollment.status(),
                        users.findActiveContactsWithRole(
                                tracks.coordinatorIds(enrollment.trackId()), Role.COORDINATOR)))
                .toList();
    }

    /** The controller admits only callers with one of the two permissions (ENR-1). */
    private void checkRelation(CurrentUser caller, UUID trackId) {
        if (!caller.has(Permission.ENROLLMENTS_MANAGE) && !tracks.isCoordinator(trackId, caller.id())) {
            throw new AccessDeniedException("Not a coordinator of the track");
        }
    }

    private static @Nullable String searchText(@Nullable String q) {
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

    // An enrollment references its track and its student, neither of which is ever deleted.
    private static TrackEnrollmentItem item(@Nullable StoredEnrollment enrollment, DirectoryUser student) {
        if (enrollment == null) {
            throw new IllegalStateException("No enrollment of student " + student.id());
        }
        return new TrackEnrollmentItem(enrollment.id(), student, enrollment.startDate(), enrollment.status());
    }

    private static TrackSummary track(Map<UUID, TrackSummary> trackById, UUID trackId) {
        TrackSummary track = trackById.get(trackId);
        if (track == null) {
            throw new IllegalStateException("Enrollment track " + trackId + " is not a stored track");
        }
        return track;
    }

    private static ConflictException trackArchived() {
        return new ConflictException(
                EnrollmentProblems.TRACK_ARCHIVED, "The track is archived; restore it to enroll students.");
    }
}
