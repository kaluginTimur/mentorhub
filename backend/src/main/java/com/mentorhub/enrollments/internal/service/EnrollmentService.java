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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enrollments (ENR). The controller checks the permission; this service checks, in order, the request, the track or the
 * enrollment (404), the relation for a caller who may enroll only on the tracks they coordinate (403), then the
 * conflicts (409). The mentors of an enrollment are managed under the same relation to the enrollment's track.
 *
 * <p>Enrolling and assigning a mentor of an enrollment lock the track's row first
 * ({@link TrackDirectory#lockForEnrollment}), so enrolling on a track and archiving it, or assigning a mentor and
 * removing them from the track, run one after another (TRK-5.6, ENR-13.7); no other lock is taken. One active enrollment per student is decided by
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

    /**
     * The enrollments of an active or an archived track, sorted by the student's name, then email, then id.
     *
     * @param mentorId narrows to the enrollments the user is a mentor of (ENR-15); {@code null} for all
     */
    @Transactional(readOnly = true)
    public EnrollmentPage<TrackEnrollmentItem> listOfTrack(UUID trackId, @Nullable UUID mentorId, int page, int size) {
        if (tracks.find(trackId).isEmpty()) {
            throw new NotFoundException();
        }
        List<StoredEnrollment> ofTrack = mentorId == null
                ? enrollments.findByTrack(trackId)
                : enrollments.findByTrackAndMentor(trackId, mentorId);
        // Keyed by student over every status: holds only while a student has at most one enrollment on a
        // track. Once an enrollment can end and the student return, toMap throws on the duplicate key.
        Map<UUID, StoredEnrollment> byStudent =
                ofTrack.stream().collect(Collectors.toMap(StoredEnrollment::studentId, Function.identity()));
        DirectoryPage students = users.findUsers(byStudent.keySet(), page, size);
        List<StoredEnrollment> onPage = students.items().stream()
                .map(student -> stored(byStudent.get(student.id()), student))
                .toList();
        Map<UUID, Set<UUID>> mentorIds =
                enrollments.mentorIds(onPage.stream().map(StoredEnrollment::id).toList());
        List<DirectoryUser> mentors =
                users.findUsers(mentorIds.values().stream().flatMap(Set::stream).collect(Collectors.toSet()));
        List<TrackEnrollmentItem> items = new ArrayList<>();
        for (int i = 0; i < onPage.size(); i++) {
            StoredEnrollment enrollment = onPage.get(i);
            items.add(item(
                    enrollment,
                    students.items().get(i),
                    mentorsOf(mentors, mentorIds.getOrDefault(enrollment.id(), Set.of()))));
        }
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
        return item(enrollment, student, List.of());
    }

    /**
     * The active users with the mentor role who are mentors of the enrollment's track and not mentors of the
     * enrollment.
     *
     * @param q searched in the email, first and last name after trimming; blank means no search
     * @throws ValidationException for {@code q}
     * @throws AccessDeniedException when the caller may manage only the tracks they coordinate, and does not coordinate
     *     the enrollment's track
     */
    @Transactional(readOnly = true)
    public EnrollmentPage<DirectoryUser> mentorCandidates(UUID enrollmentId, @Nullable String q, int page, int size) {
        String text = searchText(q);
        StoredEnrollment enrollment = enrollments.find(enrollmentId).orElseThrow(NotFoundException::new);
        checkRelation(currentUser.currentUser(), enrollment.trackId());
        DirectoryPage candidates = users.findActiveUsersWithRoleAmong(
                tracks.mentorIds(enrollment.trackId()),
                Role.MENTOR,
                text,
                enrollments.mentorIds(List.of(enrollmentId)).getOrDefault(enrollmentId, Set.of()),
                page,
                size);
        return new EnrollmentPage<>(candidates.items(), page, size, candidates.totalItems());
    }

    /**
     * Assigns a mentor of the enrollment's track as a mentor of the enrollment, recorded in the track's, the student's
     * and the mentor's history. A mentor of the enrollment is not checked again and changes nothing; parallel assignments are decided by the
     * primary key, and only the one that inserts records (ENR-9.4).
     *
     * @return the enrollment after the change
     * @throws AccessDeniedException when the caller may manage only the tracks they coordinate, and does not coordinate
     *     the enrollment's track
     * @throws ConflictException {@link EnrollmentProblems#MENTOR_NOT_ELIGIBLE}
     */
    @Transactional
    public TrackEnrollmentItem assignMentor(UUID enrollmentId, UUID userId) {
        CurrentUser caller = currentUser.currentUser();
        StoredEnrollment enrollment = enrollments.find(enrollmentId).orElseThrow(NotFoundException::new);
        // An enrollment never changes its track, which is never deleted.
        tracks.lockForEnrollment(enrollment.trackId());
        checkRelation(caller, enrollment.trackId());
        if (!enrollments.isMentor(enrollmentId, userId)) {
            if (!users.isActiveWithRole(userId, Role.MENTOR) || !tracks.isMentor(enrollment.trackId(), userId)) {
                throw new ConflictException(
                        EnrollmentProblems.MENTOR_NOT_ELIGIBLE,
                        "Only an active user with the mentor role who is a mentor of the track can be assigned.");
            }
            if (enrollments.insertMentor(enrollmentId, userId, caller.id())) {
                trackHistory.recordMentorAssigned(caller.id(), enrollment.trackId(), enrollment.studentId(), userId);
                userHistory.recordMentorAssigned(
                        caller.id(), enrollment.studentId(), userId, historyTrack(enrollment.trackId()));
            }
        }
        return item(enrollment);
    }

    /**
     * Removes the mentor from the enrollment, recorded in the track's, the student's and the mentor's history. A user
     * who is not a mentor of the enrollment, or an id no user has, changes nothing.
     *
     * @return the enrollment after the change
     * @throws AccessDeniedException when the caller may manage only the tracks they coordinate, and does not coordinate
     *     the enrollment's track
     */
    @Transactional
    public TrackEnrollmentItem removeMentor(UUID enrollmentId, UUID userId) {
        CurrentUser caller = currentUser.currentUser();
        StoredEnrollment enrollment = enrollments.find(enrollmentId).orElseThrow(NotFoundException::new);
        checkRelation(caller, enrollment.trackId());
        if (enrollments.deleteMentor(enrollmentId, userId)) {
            trackHistory.recordMentorRemoved(caller.id(), enrollment.trackId(), enrollment.studentId(), userId);
            userHistory.recordMentorRemoved(
                    caller.id(), enrollment.studentId(), userId, historyTrack(enrollment.trackId()));
        }
        return item(enrollment);
    }

    /** The caller's own enrollments, newest first, with the coordinators and the mentors to contact. */
    @Transactional(readOnly = true)
    public List<OwnEnrollment> mine() {
        List<StoredEnrollment> own =
                enrollments.findByStudent(currentUser.currentUser().id());
        if (own.isEmpty()) {
            return List.of();
        }
        Map<UUID, TrackSummary> trackById = tracksOf(own);
        Map<UUID, Set<UUID>> mentorIds =
                enrollments.mentorIds(own.stream().map(StoredEnrollment::id).toList());
        return own.stream()
                .map(enrollment -> new OwnEnrollment(
                        enrollment.id(),
                        track(trackById, enrollment.trackId()),
                        enrollment.startDate(),
                        enrollment.status(),
                        users.findActiveContactsWithRole(tracks.coordinatorIds(enrollment.trackId()), Role.COORDINATOR),
                        users.findActiveContactsWithRole(
                                mentorIds.getOrDefault(enrollment.id(), Set.of()), Role.MENTOR)))
                .toList();
    }

    /**
     * The enrollments the caller is a mentor of now, in any status, sorted by the student's name, then email, then id.
     * The controller checks the permission; the relation is the filter itself (ENR-11).
     */
    @Transactional(readOnly = true)
    public EnrollmentPage<MentoredEnrollmentItem> mentored(int page, int size) {
        // Keyed by student: holds only while a student has at most one enrollment. Once an enrollment can end and
        // the student return, toMap throws on the duplicate key.
        Map<UUID, StoredEnrollment> byStudent = enrollments
                .findByMentor(currentUser.currentUser().id())
                .stream()
                .collect(Collectors.toMap(StoredEnrollment::studentId, Function.identity()));
        DirectoryPage students = users.findUsers(byStudent.keySet(), page, size);
        List<StoredEnrollment> onPage = students.items().stream()
                .map(student -> stored(byStudent.get(student.id()), student))
                .toList();
        Map<UUID, TrackSummary> trackById = tracksOf(onPage);
        List<MentoredEnrollmentItem> items = new ArrayList<>();
        for (int i = 0; i < onPage.size(); i++) {
            StoredEnrollment enrollment = onPage.get(i);
            items.add(new MentoredEnrollmentItem(
                    enrollment.id(),
                    students.items().get(i),
                    track(trackById, enrollment.trackId()),
                    enrollment.startDate(),
                    enrollment.status()));
        }
        return new EnrollmentPage<>(items, page, size, students.totalItems());
    }

    private Map<UUID, TrackSummary> tracksOf(List<StoredEnrollment> list) {
        return tracks.findTracks(list.stream().map(StoredEnrollment::trackId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(TrackSummary::id, Function.identity()));
    }

    private TrackEnrollmentItem item(StoredEnrollment enrollment) {
        DirectoryUser student = users.findUsers(List.of(enrollment.studentId())).getFirst();
        return item(
                enrollment,
                student,
                users.findUsers(
                        enrollments.mentorIds(List.of(enrollment.id())).getOrDefault(enrollment.id(), Set.of())));
    }

    // An enrollment references its track, which is never deleted.
    private HistoryTrack historyTrack(UUID trackId) {
        TrackSummary track = tracks.find(trackId)
                .orElseThrow(() -> new IllegalStateException("Enrollment track " + trackId + " is not a stored track"));
        return new HistoryTrack(track.id(), track.code(), track.name());
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
    private static StoredEnrollment stored(@Nullable StoredEnrollment enrollment, DirectoryUser student) {
        if (enrollment == null) {
            throw new IllegalStateException("No enrollment of student " + student.id());
        }
        return enrollment;
    }

    private static TrackEnrollmentItem item(
            StoredEnrollment enrollment, DirectoryUser student, List<DirectoryUser> mentors) {
        return new TrackEnrollmentItem(enrollment.id(), student, enrollment.startDate(), enrollment.status(), mentors);
    }

    /** Keeps the directory's order: by name, then email, then id. */
    private static List<DirectoryUser> mentorsOf(List<DirectoryUser> mentors, Set<UUID> ids) {
        return mentors.stream().filter(mentor -> ids.contains(mentor.id())).toList();
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
