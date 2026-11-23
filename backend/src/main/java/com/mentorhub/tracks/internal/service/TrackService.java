package com.mentorhub.tracks.internal.service;

import com.mentorhub.tracks.ActiveEnrollments;
import com.mentorhub.tracks.internal.TrackProblems;
import com.mentorhub.tracks.internal.domain.TrackChange;
import com.mentorhub.tracks.internal.domain.TrackField;
import com.mentorhub.tracks.internal.domain.TrackStatus;
import com.mentorhub.tracks.internal.persistence.StoredTrack;
import com.mentorhub.tracks.internal.persistence.TrackAuditTrail;
import com.mentorhub.tracks.internal.persistence.TrackChangePage;
import com.mentorhub.tracks.internal.persistence.TrackCoordinator;
import com.mentorhub.tracks.internal.persistence.TrackMentor;
import com.mentorhub.tracks.internal.persistence.TrackRepository;
import com.mentorhub.users.CoordinatorHistory;
import com.mentorhub.users.CurrentUser;
import com.mentorhub.users.CurrentUserProvider;
import com.mentorhub.users.DirectoryPage;
import com.mentorhub.users.DirectoryUser;
import com.mentorhub.users.HistoryTrack;
import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.TrackMentorHistory;
import com.mentorhub.users.UserDirectory;
import com.mentorhub.web.ConflictException;
import com.mentorhub.web.FieldViolation;
import com.mentorhub.web.NotFoundException;
import com.mentorhub.web.ValidationException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tracks, their coordinators (TRK) and their mentors (ENR-13). Callers hold the permission the controller checks; every
 * track is visible to them, so an unknown id is {@link NotFoundException}. The mentors of a track are managed only by a
 * caller who may enroll on it (ENR-1), checked after the track is found.
 *
 * <p>Every change locks the track's row first ({@link TrackRepository#lock}), then checks the relation and the status,
 * then the eligibility of a user not yet named or the enrollments of a mentor removed, then writes; so changes of one
 * track, enrollments on it, the assignment of mentors to them and its archiving run one after another (TRK-5.4, TRK-5.6,
 * ENR-13.7). The track's row is the only lock taken.
 */
@Service
public class TrackService {

    static final int MAX_CODE_LENGTH = 20;
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_QUERY_LENGTH = 100;

    private final CurrentUserProvider currentUser;
    private final TrackRepository tracks;
    private final TrackAuditTrail auditTrail;
    private final UserDirectory userDirectory;
    private final CoordinatorHistory coordinatorHistory;
    private final ActiveEnrollments activeEnrollments;
    private final TrackMentorHistory mentorHistory;

    TrackService(
            CurrentUserProvider currentUser,
            TrackRepository tracks,
            TrackAuditTrail auditTrail,
            UserDirectory userDirectory,
            CoordinatorHistory coordinatorHistory,
            ActiveEnrollments activeEnrollments,
            TrackMentorHistory mentorHistory) {
        this.currentUser = currentUser;
        this.tracks = tracks;
        this.auditTrail = auditTrail;
        this.userDirectory = userDirectory;
        this.coordinatorHistory = coordinatorHistory;
        this.activeEnrollments = activeEnrollments;
        this.mentorHistory = mentorHistory;
    }

    /** @param status {@code null} for every track */
    @Transactional(readOnly = true)
    public List<TrackDetails> list(@Nullable TrackStatus status) {
        return withMembers(tracks.findAll(status));
    }

    @Transactional(readOnly = true)
    public TrackDetails get(UUID trackId) {
        return details(tracks.find(trackId).orElseThrow(NotFoundException::new));
    }

    /**
     * @param code trimmed; 1 to {@value #MAX_CODE_LENGTH} characters
     * @param name trimmed; 1 to {@value #MAX_NAME_LENGTH} characters
     * @throws ValidationException for {@code code} or {@code name}
     */
    @Transactional
    public TrackDetails create(String code, String name) {
        TrackValues values = validate(code, name);
        StoredTrack track = tracks.insert(values.code(), values.name());
        auditTrail.record(actor(), track.id(), new TrackChange.Created(track.code(), track.name()));
        return details(track);
    }

    /**
     * Sets the code and the name independently; a value sent unchanged stays, and no change records nothing.
     *
     * @throws ValidationException for {@code code} or {@code name}
     * @throws ConflictException {@link TrackProblems#TRACK_ARCHIVED}
     */
    @Transactional
    public TrackDetails update(UUID trackId, String code, String name) {
        TrackValues values = validate(code, name);
        StoredTrack track = lockActive(trackId);
        List<TrackChange.FieldChange> changes = new ArrayList<>();
        if (!track.code().equals(values.code())) {
            changes.add(new TrackChange.FieldChange(TrackField.CODE, track.code(), values.code()));
        }
        if (!track.name().equals(values.name())) {
            changes.add(new TrackChange.FieldChange(TrackField.NAME, track.name(), values.name()));
        }
        if (changes.isEmpty()) {
            return details(track);
        }
        tracks.update(trackId, values.code(), values.name());
        auditTrail.record(actor(), trackId, new TrackChange.Updated(changes));
        return details(new StoredTrack(trackId, values.code(), values.name(), track.status()));
    }

    /**
     * Archiving an archived track changes and records nothing.
     *
     * @throws ConflictException {@link TrackProblems#TRACK_HAS_ACTIVE_ENROLLMENTS}
     */
    @Transactional
    public TrackDetails archive(UUID trackId) {
        return changeStatus(trackId, TrackStatus.ARCHIVED, new TrackChange.Archived());
    }

    /** Restoring an active track changes and records nothing. */
    @Transactional
    public TrackDetails restore(UUID trackId) {
        return changeStatus(trackId, TrackStatus.ACTIVE, new TrackChange.Restored());
    }

    /**
     * Newest first; entries with an action the code does not know are left out. The named or removed user, the enrolled
     * student and a mentor are shown with their current name.
     */
    @Transactional(readOnly = true)
    public TrackHistory history(UUID trackId, int page, int size) {
        if (tracks.find(trackId).isEmpty()) {
            throw new NotFoundException();
        }
        TrackChangePage changes = auditTrail.find(trackId, page, size);
        Map<UUID, NamedUser> names = names(changes.items().stream()
                .flatMap(change -> Stream.of(change.actorUserId(), namedUser(change.change()), mentor(change.change())))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        List<TrackHistoryItem> items = changes.items().stream()
                .map(change -> new TrackHistoryItem(
                        change.id(),
                        change.occurredAt(),
                        actorName(change.actorUserId(), names),
                        change.change(),
                        userName(namedUser(change.change()), names),
                        userName(mentor(change.change()), names)))
                .toList();
        return new TrackHistory(items, page, size, changes.totalItems());
    }

    /**
     * The active users with the coordinator role who are not coordinators of the track.
     *
     * @throws ConflictException {@link TrackProblems#TRACK_ARCHIVED}
     */
    @Transactional(readOnly = true)
    public List<DirectoryUser> candidates(UUID trackId) {
        StoredTrack track = tracks.find(trackId).orElseThrow(NotFoundException::new);
        if (track.status() == TrackStatus.ARCHIVED) {
            throw trackArchived();
        }
        Set<UUID> coordinators = coordinatorIds(List.of(trackId)).getOrDefault(trackId, Set.of());
        return userDirectory.findActiveUsersWithRole(Role.COORDINATOR).stream()
                .filter(user -> !coordinators.contains(user.id()))
                .toList();
    }

    /**
     * Names the user a coordinator of the track; naming a coordinator again changes and records nothing, even one no
     * longer eligible (TRK-6.3), so eligibility is checked only for a user not yet named.
     *
     * @throws ConflictException {@link TrackProblems#TRACK_ARCHIVED} or {@link TrackProblems#COORDINATOR_NOT_ELIGIBLE}
     */
    @Transactional
    public TrackDetails addCoordinator(UUID trackId, UUID userId) {
        StoredTrack track = lockActive(trackId);
        if (tracks.isCoordinator(trackId, userId)) {
            return details(track);
        }
        if (!userDirectory.isActiveWithRole(userId, Role.COORDINATOR)) {
            throw new ConflictException(
                    TrackProblems.COORDINATOR_NOT_ELIGIBLE,
                    "Only an active user with the coordinator role can be named a coordinator.");
        }
        if (tracks.addCoordinatorIfAbsent(trackId, userId) > 0) {
            UUID actor = actor();
            auditTrail.record(actor, trackId, new TrackChange.CoordinatorAdded(userId));
            coordinatorHistory.recordAdded(actor, userId, historyTrack(track));
        }
        return details(track);
    }

    /**
     * Removes the user as a coordinator of the track; removing a user who is not one, or an unknown id, changes and
     * records nothing.
     *
     * @throws ConflictException {@link TrackProblems#TRACK_ARCHIVED}
     */
    @Transactional
    public TrackDetails removeCoordinator(UUID trackId, UUID userId) {
        StoredTrack track = lockActive(trackId);
        if (tracks.removeCoordinator(trackId, userId) > 0) {
            UUID actor = actor();
            auditTrail.record(actor, trackId, new TrackChange.CoordinatorRemoved(userId));
            coordinatorHistory.recordRemoved(actor, userId, historyTrack(track));
        }
        return details(track);
    }

    /**
     * The active users with the mentor role who are not mentors of the track.
     *
     * @param q searched in the email, first and last name after trimming; blank means no search
     * @throws ValidationException for {@code q}
     * @throws AccessDeniedException when the caller may not enroll on the track (ENR-1)
     * @throws ConflictException {@link TrackProblems#TRACK_ARCHIVED}
     */
    @Transactional(readOnly = true)
    public DirectoryPage mentorCandidates(UUID trackId, @Nullable String q, int page, int size) {
        String text = searchText(q);
        StoredTrack track = tracks.find(trackId).orElseThrow(NotFoundException::new);
        checkMayEnroll(trackId);
        if (track.status() == TrackStatus.ARCHIVED) {
            throw trackArchived();
        }
        return userDirectory.findActiveUsersWithRole(Role.MENTOR, text, mentorIds(trackId), page, size);
    }

    /**
     * Names the user a mentor of the track; naming a mentor again changes and records nothing, even one no longer
     * eligible (ENR-13.3), so eligibility is checked only for a user not yet named.
     *
     * @throws AccessDeniedException when the caller may not enroll on the track (ENR-1)
     * @throws ConflictException {@link TrackProblems#TRACK_ARCHIVED} or {@link TrackProblems#MENTOR_NOT_ELIGIBLE}
     */
    @Transactional
    public TrackDetails addMentor(UUID trackId, UUID userId) {
        StoredTrack track = lockForMentors(trackId);
        if (tracks.isMentor(trackId, userId)) {
            return details(track);
        }
        if (!userDirectory.isActiveWithRole(userId, Role.MENTOR)) {
            throw new ConflictException(
                    TrackProblems.MENTOR_NOT_ELIGIBLE,
                    "Only an active user with the mentor role can be named a mentor.");
        }
        if (tracks.addMentorIfAbsent(trackId, userId) > 0) {
            UUID actor = actor();
            auditTrail.record(actor, trackId, new TrackChange.MentorAdded(userId));
            mentorHistory.recordAdded(actor, userId, historyTrack(track));
        }
        return details(track);
    }

    /**
     * Removes the user as a mentor of the track; removing a user who is not one, or an unknown id, changes and records
     * nothing.
     *
     * @throws AccessDeniedException when the caller may not enroll on the track (ENR-1)
     * @throws ConflictException {@link TrackProblems#TRACK_ARCHIVED} or {@link TrackProblems#MENTOR_LEADS_ENROLLMENTS}
     */
    @Transactional
    public TrackDetails removeMentor(UUID trackId, UUID userId) {
        StoredTrack track = lockForMentors(trackId);
        if (!tracks.isMentor(trackId, userId)) {
            return details(track);
        }
        if (activeEnrollments.ledOnTrack(trackId, userId)) {
            throw new ConflictException(
                    TrackProblems.MENTOR_LEADS_ENROLLMENTS,
                    "The mentor leads active enrollments on the track; remove them from those enrollments first.");
        }
        if (tracks.removeMentor(trackId, userId) > 0) {
            UUID actor = actor();
            auditTrail.record(actor, trackId, new TrackChange.MentorRemoved(userId));
            mentorHistory.recordRemoved(actor, userId, historyTrack(track));
        }
        return details(track);
    }

    private StoredTrack lockForMentors(UUID trackId) {
        StoredTrack track = tracks.lock(trackId).orElseThrow(NotFoundException::new);
        checkMayEnroll(trackId);
        if (track.status() == TrackStatus.ARCHIVED) {
            throw trackArchived();
        }
        return track;
    }

    /** The controller admits only callers with one of the two permissions (ENR-1). */
    private void checkMayEnroll(UUID trackId) {
        CurrentUser caller = currentUser.currentUser();
        if (!caller.has(Permission.ENROLLMENTS_MANAGE) && !tracks.isCoordinator(trackId, caller.id())) {
            throw new AccessDeniedException("Not a coordinator of the track");
        }
    }

    private Set<UUID> mentorIds(UUID trackId) {
        return tracks.findMentors(List.of(trackId)).stream()
                .map(TrackMentor::userId)
                .collect(Collectors.toSet());
    }

    private TrackDetails changeStatus(UUID trackId, TrackStatus status, TrackChange change) {
        StoredTrack track = tracks.lock(trackId).orElseThrow(NotFoundException::new);
        if (track.status() == status) {
            return details(track);
        }
        if (status == TrackStatus.ARCHIVED && activeEnrollments.existOnTrack(trackId)) {
            throw new ConflictException(
                    TrackProblems.TRACK_HAS_ACTIVE_ENROLLMENTS,
                    "The track has active enrollments; it can be archived once they end.");
        }
        tracks.updateStatus(trackId, status);
        auditTrail.record(actor(), trackId, change);
        return details(new StoredTrack(trackId, track.code(), track.name(), status));
    }

    private StoredTrack lockActive(UUID trackId) {
        StoredTrack track = tracks.lock(trackId).orElseThrow(NotFoundException::new);
        if (track.status() == TrackStatus.ARCHIVED) {
            throw trackArchived();
        }
        return track;
    }

    private TrackDetails details(StoredTrack track) {
        return withMembers(List.of(track)).getFirst();
    }

    private List<TrackDetails> withMembers(List<StoredTrack> stored) {
        CurrentUser caller = currentUser.currentUser();
        List<UUID> trackIds = stored.stream().map(StoredTrack::id).toList();
        Map<UUID, Set<UUID>> coordinatorIds = coordinatorIds(trackIds);
        Map<UUID, Set<UUID>> mentorIds = new HashMap<>();
        for (TrackMentor mentor : tracks.findMentors(trackIds)) {
            mentorIds.computeIfAbsent(mentor.trackId(), id -> new HashSet<>()).add(mentor.userId());
        }
        Map<UUID, Map<UUID, Integer>> students = activeEnrollments.countLed(trackIds);
        List<DirectoryUser> users =
                userDirectory.findUsers(Stream.concat(coordinatorIds.values().stream(), mentorIds.values().stream())
                        .flatMap(Set::stream)
                        .collect(Collectors.toSet()));
        return stored.stream()
                .map(track -> {
                    Set<UUID> coordinators = coordinatorIds.getOrDefault(track.id(), Set.of());
                    Set<UUID> mentors = mentorIds.getOrDefault(track.id(), Set.of());
                    return new TrackDetails(
                            track.id(),
                            track.code(),
                            track.name(),
                            track.status(),
                            users.stream()
                                    .filter(user -> coordinators.contains(user.id()))
                                    .toList(),
                            users.stream()
                                    .filter(user -> mentors.contains(user.id()))
                                    .toList(),
                            students.getOrDefault(track.id(), Map.of()),
                            canEnroll(caller, coordinators));
                })
                .toList();
    }

    private Map<UUID, Set<UUID>> coordinatorIds(Collection<UUID> trackIds) {
        Map<UUID, Set<UUID>> result = new HashMap<>();
        for (TrackCoordinator coordinator : tracks.findCoordinators(trackIds)) {
            result.computeIfAbsent(coordinator.trackId(), id -> new HashSet<>()).add(coordinator.userId());
        }
        return result;
    }

    private Map<UUID, NamedUser> names(Set<UUID> userIds) {
        return userDirectory.findUsers(userIds).stream()
                .map(user -> new NamedUser(user.id(), user.name()))
                .collect(Collectors.toMap(NamedUser::id, Function.identity()));
    }

    private UUID actor() {
        return currentUser.currentUser().id();
    }

    /** ENR-1: by the permission and the relation alone, whatever the status. */
    private static boolean canEnroll(CurrentUser caller, Set<UUID> coordinatorIds) {
        return caller.has(Permission.ENROLLMENTS_MANAGE)
                || (caller.has(Permission.ENROLLMENTS_MANAGE_COORDINATED) && coordinatorIds.contains(caller.id()));
    }

    private static @Nullable UUID namedUser(TrackChange change) {
        return switch (change) {
            case TrackChange.CoordinatorAdded(var userId) -> userId;
            case TrackChange.CoordinatorRemoved(var userId) -> userId;
            case TrackChange.MentorAdded(var userId) -> userId;
            case TrackChange.MentorRemoved(var userId) -> userId;
            case TrackChange.StudentEnrolled(var userId, var _) -> userId;
            case TrackChange.EnrollmentMentorAssigned(var userId, var _) -> userId;
            case TrackChange.EnrollmentMentorRemoved(var userId, var _) -> userId;
            case TrackChange.EnrollmentCohortChanged(var userId, var _, var _) -> userId;
            default -> null;
        };
    }

    private static @Nullable UUID mentor(TrackChange change) {
        return switch (change) {
            case TrackChange.EnrollmentMentorAssigned(var _, var mentorId) -> mentorId;
            case TrackChange.EnrollmentMentorRemoved(var _, var mentorId) -> mentorId;
            default -> null;
        };
    }

    private static @Nullable NamedUser actorName(@Nullable UUID actorUserId, Map<UUID, NamedUser> names) {
        if (actorUserId == null) {
            return null;
        }
        NamedUser user = names.get(actorUserId);
        // actor_user_id references users, which are never deleted
        if (user == null) {
            throw new IllegalStateException("Audit actor " + actorUserId + " is not a stored user");
        }
        return user;
    }

    private static @Nullable NamedUser userName(@Nullable UUID userId, Map<UUID, NamedUser> names) {
        return userId == null ? null : names.get(userId);
    }

    private static HistoryTrack historyTrack(StoredTrack track) {
        return new HistoryTrack(track.id(), track.code(), track.name());
    }

    private static ConflictException trackArchived() {
        return new ConflictException(TrackProblems.TRACK_ARCHIVED, "The track is archived; restore it to change it.");
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

    private static TrackValues validate(String code, String name) {
        List<FieldViolation> errors = new ArrayList<>();
        String validCode = code.strip();
        String validName = name.strip();
        checkLength("code", validCode, MAX_CODE_LENGTH, errors);
        checkLength("name", validName, MAX_NAME_LENGTH, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new TrackValues(validCode, validName);
    }

    private static void checkLength(String field, String value, int maxLength, List<FieldViolation> errors) {
        if (value.isEmpty()) {
            errors.add(new FieldViolation(field, "must not be blank"));
        } else if (value.codePointCount(0, value.length()) > maxLength) {
            errors.add(new FieldViolation(field, "must be at most " + maxLength + " characters"));
        }
    }

    private record TrackValues(String code, String name) {}
}
