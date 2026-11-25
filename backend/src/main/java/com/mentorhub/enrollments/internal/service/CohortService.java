package com.mentorhub.enrollments.internal.service;

import com.mentorhub.enrollments.internal.EnrollmentProblems;
import com.mentorhub.enrollments.internal.domain.EnrollmentStatus;
import com.mentorhub.enrollments.internal.persistence.CohortRepository;
import com.mentorhub.enrollments.internal.persistence.CountedCohort;
import com.mentorhub.enrollments.internal.persistence.EnrollmentRepository;
import com.mentorhub.enrollments.internal.persistence.StoredCohort;
import com.mentorhub.enrollments.internal.persistence.StoredEnrollment;
import com.mentorhub.tracks.TrackDirectory;
import com.mentorhub.tracks.TrackEnrollmentHistory;
import com.mentorhub.tracks.TrackSummary;
import com.mentorhub.users.CurrentUser;
import com.mentorhub.users.CurrentUserProvider;
import com.mentorhub.users.DirectoryPage;
import com.mentorhub.users.DirectoryUser;
import com.mentorhub.users.HistoryCohort;
import com.mentorhub.users.HistoryTrack;
import com.mentorhub.users.Role;
import com.mentorhub.users.UserDirectory;
import com.mentorhub.users.UserEnrollmentHistory;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.FieldViolation;
import com.mentorhub.web.NotFoundException;
import com.mentorhub.web.ValidationException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cohorts (COH). The controller checks the permission; this service checks, in order, the request, the track and the
 * cohort (404, a cohort of another track included), the relation for a caller who may change only the tracks they
 * coordinate (403), then the conflicts (409), {@code track-archived} first.
 *
 * <p>Every change first locks the track's row ({@link TrackDirectory#lockForEnrollment}), the lock that archiving and
 * enrolling take too, and only then reads the cohort and the enrollments it checks. So changes of the cohorts of one
 * track, deletions and enrolling into a cohort included, the changes of the mentors of its cohorts and of the track, and
 * its archiving run one after another (COH-3.3, COH-4.2, COH-4.4, ENR-13.9, TRK-5.4); no other lock is taken.
 */
@Service
public class CohortService {

    static final int MAX_NAME_LENGTH = 100;

    private final CurrentUserProvider currentUser;
    private final CohortRepository cohorts;
    private final EnrollmentRepository enrollments;
    private final TrackDirectory tracks;
    private final TrackEnrollmentHistory trackHistory;
    private final UserDirectory users;
    private final UserEnrollmentHistory userHistory;
    private final MentorLoads loads;

    CohortService(
            CurrentUserProvider currentUser,
            CohortRepository cohorts,
            EnrollmentRepository enrollments,
            TrackDirectory tracks,
            TrackEnrollmentHistory trackHistory,
            UserDirectory users,
            UserEnrollmentHistory userHistory,
            MentorLoads loads) {
        this.loads = loads;
        this.currentUser = currentUser;
        this.cohorts = cohorts;
        this.enrollments = enrollments;
        this.tracks = tracks;
        this.trackHistory = trackHistory;
        this.users = users;
        this.userHistory = userHistory;
    }

    /**
     * The cohorts of an active or an archived track, newest start date first, then by name, then by id, each with its
     * mentors.
     */
    @Transactional(readOnly = true)
    public List<CohortItem> list(UUID trackId) {
        if (tracks.find(trackId).isEmpty()) {
            throw new NotFoundException();
        }
        List<CountedCohort> counted = cohorts.findByTrack(trackId);
        Map<UUID, Set<UUID>> mentorIds =
                cohorts.mentorIds(counted.stream().map(c -> c.cohort().id()).toList());
        Set<UUID> all = new HashSet<>();
        mentorIds.values().forEach(all::addAll);
        List<DirectoryUser> mentors = all.isEmpty() ? List.of() : users.findUsers(all);
        return counted.stream()
                .map(c -> {
                    StoredCohort cohort = c.cohort();
                    Set<UUID> ids = mentorIds.getOrDefault(cohort.id(), Set.of());
                    return new CohortItem(
                            cohort.id(),
                            cohort.name(),
                            cohort.startDate(),
                            c.enrollmentCount(),
                            mentors.stream()
                                    .filter(mentor -> ids.contains(mentor.id()))
                                    .toList());
                })
                .toList();
    }

    /**
     * Creates a cohort on the track with the enrollments given, moved from their cohorts if they are in one; the
     * creation is recorded first, then each change of an enrollment's cohort.
     *
     * @param name trimmed before it is checked and stored
     * @throws ValidationException for {@code name} and {@code enrollmentIds}
     * @throws AccessDeniedException when the caller may change only the tracks they coordinate, and does not coordinate
     *     this one
     * @throws ConflictException {@link EnrollmentProblems#TRACK_ARCHIVED} or
     *     {@link EnrollmentProblems#ENROLLMENT_NOT_ELIGIBLE}, in this order
     */
    @Transactional
    public CohortItem create(
            UUID trackId, String name, LocalDate startDate, Collection<? extends @Nullable UUID> enrollmentIds) {
        List<FieldViolation> errors = new ArrayList<>();
        String trimmed = name(name, errors);
        List<UUID> ids = ids("enrollmentIds", enrollmentIds, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        CurrentUser caller = currentUser.currentUser();
        TrackSummary track = tracks.lockForEnrollment(trackId).orElseThrow(NotFoundException::new);
        EnrollmentRules.checkRelation(tracks, caller, trackId);
        if (track.archived()) {
            throw trackArchived();
        }
        List<StoredEnrollment> members = eligible(trackId, ids);
        StoredCohort cohort = cohorts.insert(trackId, trimmed, startDate);
        trackHistory.recordCohortCreated(caller.id(), trackId, historyCohort(cohort), cohort.startDate());
        moveInto(caller, track, cohort, members);
        return item(cohort);
    }

    /**
     * Deletes a cohort that has no enrollment, with its mentors, each removal recorded in the track's and the mentor's
     * history before the deletion; its entries in the histories stay.
     *
     * @throws AccessDeniedException when the caller may change only the tracks they coordinate, and does not coordinate
     *     the track
     * @throws ConflictException {@link EnrollmentProblems#TRACK_ARCHIVED} or {@link EnrollmentProblems#COHORT_NOT_EMPTY},
     *     in this order
     */
    @Transactional
    public void delete(UUID trackId, UUID cohortId) {
        CurrentUser caller = currentUser.currentUser();
        TrackSummary track = tracks.lockForEnrollment(trackId).orElseThrow(NotFoundException::new);
        StoredCohort cohort = cohorts.findOnTrack(trackId, cohortId).orElseThrow(NotFoundException::new);
        EnrollmentRules.checkRelation(tracks, caller, trackId);
        if (track.archived()) {
            throw trackArchived();
        }
        if (cohorts.hasEnrollments(cohortId)) {
            throw new ConflictException(
                    EnrollmentProblems.COHORT_NOT_EMPTY, "The cohort has enrollments; take them out first.");
        }
        HistoryCohort named = historyCohort(cohort);
        for (UUID mentorId : cohorts.deleteMentors(cohortId)) {
            recordMentorRemoved(caller, track, named, mentorId);
        }
        cohorts.delete(cohortId);
        trackHistory.recordCohortDeleted(caller.id(), trackId, historyCohort(cohort), cohort.startDate());
    }

    /**
     * Adds the enrollments to the cohort, all or nothing: one in another cohort of the track is moved, one already in
     * this cohort or given twice changes nothing.
     *
     * @return the cohort after the change
     * @throws ValidationException for {@code enrollmentIds}
     * @throws AccessDeniedException when the caller may change only the tracks they coordinate, and does not coordinate
     *     the track
     * @throws ConflictException {@link EnrollmentProblems#TRACK_ARCHIVED} or
     *     {@link EnrollmentProblems#ENROLLMENT_NOT_ELIGIBLE}, in this order
     */
    @Transactional
    public CohortItem add(UUID trackId, UUID cohortId, Collection<? extends @Nullable UUID> enrollmentIds) {
        List<FieldViolation> errors = new ArrayList<>();
        List<UUID> ids = ids("enrollmentIds", enrollmentIds, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        CurrentUser caller = currentUser.currentUser();
        TrackSummary track = tracks.lockForEnrollment(trackId).orElseThrow(NotFoundException::new);
        StoredCohort cohort = cohorts.findOnTrack(trackId, cohortId).orElseThrow(NotFoundException::new);
        EnrollmentRules.checkRelation(tracks, caller, trackId);
        if (track.archived()) {
            throw trackArchived();
        }
        moveInto(caller, track, cohort, eligible(trackId, ids));
        return item(cohort);
    }

    /**
     * Takes the enrollment out of the cohort, leaving it in no cohort. An enrollment that is not in the cohort, or an id
     * no enrollment has, changes nothing.
     *
     * @throws AccessDeniedException when the caller may change only the tracks they coordinate, and does not coordinate
     *     the track
     * @throws ConflictException {@link EnrollmentProblems#TRACK_ARCHIVED}
     */
    @Transactional
    public void remove(UUID trackId, UUID cohortId, UUID enrollmentId) {
        CurrentUser caller = currentUser.currentUser();
        TrackSummary track = tracks.lockForEnrollment(trackId).orElseThrow(NotFoundException::new);
        StoredCohort cohort = cohorts.findOnTrack(trackId, cohortId).orElseThrow(NotFoundException::new);
        EnrollmentRules.checkRelation(tracks, caller, trackId);
        if (track.archived()) {
            throw trackArchived();
        }
        StoredEnrollment enrollment = enrollments.find(enrollmentId).orElse(null);
        if (enrollment == null || !cohortId.equals(enrollment.cohortId())) {
            return;
        }
        enrollments.setCohort(enrollmentId, null);
        recordChange(caller, track, enrollment.studentId(), historyCohort(cohort), null);
    }

    /**
     * The active enrollments of the track that are not in the cohort, sorted by the student's name, then email, then
     * id.
     *
     * @param cohortId the cohort the candidates are for; {@code null} for a new cohort
     * @param q searched in the student's email, first and last name after trimming; blank means no search
     * @param withoutCohort narrows to the enrollments in no cohort
     * @throws ValidationException for {@code q}
     * @throws AccessDeniedException when the caller may change only the tracks they coordinate, and does not coordinate
     *     the track
     * @throws ConflictException {@link EnrollmentProblems#TRACK_ARCHIVED}
     */
    @Transactional(readOnly = true)
    public EnrollmentPage<CohortCandidateItem> candidates(
            UUID trackId, @Nullable UUID cohortId, @Nullable String q, boolean withoutCohort, int page, int size) {
        String text = EnrollmentRules.searchText(q);
        TrackSummary track = tracks.find(trackId).orElseThrow(NotFoundException::new);
        if (cohortId != null && cohorts.findOnTrack(trackId, cohortId).isEmpty()) {
            throw new NotFoundException();
        }
        EnrollmentRules.checkRelation(tracks, currentUser.currentUser(), trackId);
        if (track.archived()) {
            throw trackArchived();
        }
        // Keyed by student: holds while a student has at most one active enrollment (ENR-2.6).
        Map<UUID, StoredEnrollment> byStudent =
                enrollments.findActiveByTrackOutside(trackId, cohortId, withoutCohort).stream()
                        .collect(Collectors.toMap(StoredEnrollment::studentId, Function.identity()));
        DirectoryPage students = users.findUsers(byStudent.keySet(), text, page, size);
        List<StoredEnrollment> onPage = students.items().stream()
                .map(student -> Objects.requireNonNull(byStudent.get(student.id())))
                .toList();
        Map<UUID, StoredCohort> cohortById = cohorts.findAll(onPage.stream()
                .map(StoredEnrollment::cohortId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<CohortCandidateItem> items = students.items().stream()
                .map(student -> {
                    StoredEnrollment enrollment = Objects.requireNonNull(byStudent.get(student.id()));
                    return new CohortCandidateItem(
                            enrollment.id(), student, namedCohort(cohortById, enrollment.cohortId()));
                })
                .toList();
        return new EnrollmentPage<>(items, page, size, students.totalItems());
    }

    /**
     * Assigns the users as mentors of the cohort, all or nothing: a user who is a mentor of the cohort, even one no
     * longer eligible, or given twice, changes nothing; each other must be an active user with the mentor role who is a
     * mentor of the track. Parallel assignments are decided by the primary key, and only the one that inserts records
     * (COH-9.4).
     *
     * @return the cohort after the change
     * @throws ValidationException for {@code userIds}
     * @throws AccessDeniedException when the caller may change only the tracks they coordinate, and does not coordinate
     *     the track
     * @throws ConflictException {@link EnrollmentProblems#TRACK_ARCHIVED} or {@link EnrollmentProblems#MENTOR_NOT_ELIGIBLE},
     *     in this order
     */
    @Transactional
    public CohortItem assignMentors(UUID trackId, UUID cohortId, Collection<? extends @Nullable UUID> userIds) {
        List<FieldViolation> errors = new ArrayList<>();
        List<UUID> ids = ids("userIds", userIds, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        CurrentUser caller = currentUser.currentUser();
        TrackSummary track = tracks.lockForEnrollment(trackId).orElseThrow(NotFoundException::new);
        StoredCohort cohort = cohorts.findOnTrack(trackId, cohortId).orElseThrow(NotFoundException::new);
        EnrollmentRules.checkRelation(tracks, caller, trackId);
        if (track.archived()) {
            throw trackArchived();
        }
        Set<UUID> current = cohorts.mentorIds(List.of(cohortId)).getOrDefault(cohortId, Set.of());
        List<UUID> added = new LinkedHashSet<>(ids)
                .stream().filter(id -> !current.contains(id)).toList();
        for (UUID userId : added) {
            if (!users.isActiveWithRole(userId, Role.MENTOR) || !tracks.isMentor(trackId, userId)) {
                throw new ConflictException(
                        EnrollmentProblems.MENTOR_NOT_ELIGIBLE,
                        "Only active users with the mentor role who are mentors of the track can be assigned.");
            }
        }
        HistoryCohort named = historyCohort(cohort);
        HistoryTrack historyTrack = historyTrack(track);
        for (UUID userId : added) {
            if (cohorts.insertMentor(cohortId, userId, caller.id())) {
                trackHistory.recordCohortMentorAssigned(caller.id(), trackId, named, userId);
                userHistory.recordCohortMentorAssigned(caller.id(), userId, named, historyTrack);
            }
        }
        return item(cohort);
    }

    /**
     * Removes the user as a mentor of the cohort; a user who is not one, or an id no user has, changes and records
     * nothing. A mentor of an enrollment in the cohort stays its mentor.
     *
     * @return the cohort after the change
     * @throws AccessDeniedException when the caller may change only the tracks they coordinate, and does not coordinate
     *     the track
     * @throws ConflictException {@link EnrollmentProblems#TRACK_ARCHIVED}
     */
    @Transactional
    public CohortItem removeMentor(UUID trackId, UUID cohortId, UUID userId) {
        CurrentUser caller = currentUser.currentUser();
        TrackSummary track = tracks.lockForEnrollment(trackId).orElseThrow(NotFoundException::new);
        StoredCohort cohort = cohorts.findOnTrack(trackId, cohortId).orElseThrow(NotFoundException::new);
        EnrollmentRules.checkRelation(tracks, caller, trackId);
        if (track.archived()) {
            throw trackArchived();
        }
        if (cohorts.deleteMentor(cohortId, userId)) {
            recordMentorRemoved(caller, track, historyCohort(cohort), userId);
        }
        return item(cohort);
    }

    /**
     * The active users with the mentor role who are mentors of the track and not mentors of the cohort, a mentor of an
     * enrollment in it included, each with what they lead on the track, sorted by name, then email, then id.
     *
     * @param q searched in the email, first and last name after trimming; blank means no search
     * @throws ValidationException for {@code q}
     * @throws AccessDeniedException when the caller may change only the tracks they coordinate, and does not coordinate
     *     the track
     * @throws ConflictException {@link EnrollmentProblems#TRACK_ARCHIVED}
     */
    @Transactional(readOnly = true)
    public EnrollmentPage<MentorCandidateItem> mentorCandidates(
            UUID trackId, UUID cohortId, @Nullable String q, int page, int size) {
        String text = EnrollmentRules.searchText(q);
        TrackSummary track = tracks.find(trackId).orElseThrow(NotFoundException::new);
        if (cohorts.findOnTrack(trackId, cohortId).isEmpty()) {
            throw new NotFoundException();
        }
        EnrollmentRules.checkRelation(tracks, currentUser.currentUser(), trackId);
        if (track.archived()) {
            throw trackArchived();
        }
        DirectoryPage candidates = users.findActiveUsersWithRoleAmong(
                tracks.mentorIds(trackId),
                Role.MENTOR,
                text,
                cohorts.mentorIds(List.of(cohortId)).getOrDefault(cohortId, Set.of()),
                page,
                size);
        return loads.page(trackId, candidates, page, size);
    }

    private void recordMentorRemoved(CurrentUser caller, TrackSummary track, HistoryCohort cohort, UUID mentorId) {
        trackHistory.recordCohortMentorRemoved(caller.id(), track.id(), cohort, mentorId);
        userHistory.recordCohortMentorRemoved(caller.id(), mentorId, cohort, historyTrack(track));
    }

    /** Each id once, in the order given; every one must be an active enrollment of the track. */
    private List<StoredEnrollment> eligible(UUID trackId, Collection<UUID> enrollmentIds) {
        Set<UUID> ids = new LinkedHashSet<>(enrollmentIds);
        Map<UUID, StoredEnrollment> found =
                enrollments.findAll(ids).stream().collect(Collectors.toMap(StoredEnrollment::id, Function.identity()));
        List<StoredEnrollment> eligible = ids.stream()
                .map(found::get)
                .filter(enrollment -> enrollment != null
                        && enrollment.trackId().equals(trackId)
                        && enrollment.status() == EnrollmentStatus.ACTIVE)
                .map(Objects::requireNonNull)
                .toList();
        if (eligible.size() != ids.size()) {
            throw new ConflictException(
                    EnrollmentProblems.ENROLLMENT_NOT_ELIGIBLE,
                    "Only the active enrollments of the track can be put into its cohorts.");
        }
        return eligible;
    }

    private void moveInto(CurrentUser caller, TrackSummary track, StoredCohort cohort, List<StoredEnrollment> members) {
        Map<UUID, StoredCohort> previous = cohorts.findAll(members.stream()
                .map(StoredEnrollment::cohortId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        for (StoredEnrollment enrollment : members) {
            UUID from = enrollment.cohortId();
            if (cohort.id().equals(from)) {
                continue;
            }
            enrollments.setCohort(enrollment.id(), cohort.id());
            recordChange(
                    caller,
                    track,
                    enrollment.studentId(),
                    from == null ? null : historyCohort(stored(previous, from)),
                    historyCohort(cohort));
        }
    }

    private void recordChange(
            CurrentUser caller,
            TrackSummary track,
            UUID studentId,
            @Nullable HistoryCohort from,
            @Nullable HistoryCohort to) {
        trackHistory.recordEnrollmentCohortChanged(caller.id(), track.id(), studentId, from, to);
        userHistory.recordCohortChanged(caller.id(), studentId, historyTrack(track), from, to);
    }

    private CohortItem item(StoredCohort cohort) {
        return new CohortItem(
                cohort.id(),
                cohort.name(),
                cohort.startDate(),
                cohorts.countActive(cohort.id()),
                users.findUsers(cohorts.mentorIds(List.of(cohort.id())).getOrDefault(cohort.id(), Set.of())));
    }

    private static HistoryTrack historyTrack(TrackSummary track) {
        return new HistoryTrack(track.id(), track.code(), track.name());
    }

    private static String name(String name, List<FieldViolation> errors) {
        String trimmed = name.strip();
        int length = trimmed.codePointCount(0, trimmed.length());
        if (length == 0) {
            errors.add(new FieldViolation("name", "must not be blank"));
        } else if (length > MAX_NAME_LENGTH) {
            errors.add(new FieldViolation("name", "must be at most " + MAX_NAME_LENGTH + " characters"));
        }
        return trimmed;
    }

    // Bean validation checks the list, not its items: `[null]` binds to a list containing null.
    private static List<UUID> ids(String field, Collection<? extends @Nullable UUID> ids, List<FieldViolation> errors) {
        List<UUID> result = new ArrayList<>(ids.size());
        for (@Nullable UUID id : ids) {
            if (id == null) {
                errors.add(new FieldViolation(field, "must not contain null"));
                return List.of();
            }
            result.add(id);
        }
        return List.copyOf(result);
    }

    private static HistoryCohort historyCohort(StoredCohort cohort) {
        return new HistoryCohort(cohort.id(), cohort.name());
    }

    // An enrollment's cohort is a stored cohort: the foreign key keeps it from being deleted.
    private static StoredCohort stored(Map<UUID, StoredCohort> cohortById, UUID cohortId) {
        StoredCohort cohort = cohortById.get(cohortId);
        if (cohort == null) {
            throw new IllegalStateException("Enrollment cohort " + cohortId + " is not a stored cohort");
        }
        return cohort;
    }

    private static @Nullable NamedCohort namedCohort(Map<UUID, StoredCohort> cohortById, @Nullable UUID cohortId) {
        if (cohortId == null) {
            return null;
        }
        StoredCohort cohort = stored(cohortById, cohortId);
        return new NamedCohort(cohort.id(), cohort.name());
    }

    private static ConflictException trackArchived() {
        return EnrollmentRules.trackArchived("The track is archived; restore it to change its cohorts.");
    }
}
