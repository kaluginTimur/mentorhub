package com.mentorhub.tracks.internal.web;

import com.mentorhub.api.model.CoordinatorCandidate;
import com.mentorhub.api.model.CoordinatorCandidateList;
import com.mentorhub.api.model.HistoryActor;
import com.mentorhub.api.model.Track;
import com.mentorhub.api.model.TrackChange;
import com.mentorhub.api.model.TrackCoordinator;
import com.mentorhub.api.model.TrackField;
import com.mentorhub.api.model.TrackHistoryAction;
import com.mentorhub.api.model.TrackHistoryEntry;
import com.mentorhub.api.model.TrackHistoryPage;
import com.mentorhub.api.model.TrackList;
import com.mentorhub.api.model.TrackStatus;
import com.mentorhub.api.model.TrackStatusFilter;
import com.mentorhub.tracks.internal.service.NamedUser;
import com.mentorhub.tracks.internal.service.TrackDetails;
import com.mentorhub.tracks.internal.service.TrackHistory;
import com.mentorhub.tracks.internal.service.TrackHistoryItem;
import com.mentorhub.users.DirectoryUser;
import com.mentorhub.users.Role;
import java.time.ZoneOffset;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Maps between the generated contract types and the {@code tracks} service types; enums map by constant name. */
final class TracksMapper {

    private TracksMapper() {}

    /** {@code null} for {@code ALL}. */
    static com.mentorhub.tracks.internal.domain.@Nullable TrackStatus status(TrackStatusFilter filter) {
        return filter == TrackStatusFilter.ALL
                ? null
                : com.mentorhub.tracks.internal.domain.TrackStatus.valueOf(filter.name());
    }

    static TrackList trackList(List<TrackDetails> tracks) {
        return new TrackList(tracks.stream().map(TracksMapper::track).toList());
    }

    static Track track(TrackDetails track) {
        return new Track(
                track.id(),
                track.code(),
                track.name(),
                TrackStatus.valueOf(track.status().name()),
                track.coordinators().stream()
                        .map(user -> new TrackCoordinator(
                                user.id(),
                                user.name(),
                                user.email(),
                                user.disabled(),
                                user.roles().contains(Role.COORDINATOR)))
                        .toList(),
                // Placeholder until T3 of track-mentors reads the mentors of the track.
                List.of(),
                track.canEnroll());
    }

    static CoordinatorCandidateList candidates(List<DirectoryUser> users) {
        return new CoordinatorCandidateList(users.stream()
                .map(user -> new CoordinatorCandidate(user.id(), user.name(), user.email()))
                .toList());
    }

    static TrackHistoryPage historyPage(TrackHistory history) {
        return new TrackHistoryPage(
                history.items().stream().map(TracksMapper::historyEntry).toList(),
                history.page(),
                history.size(),
                history.totalItems());
    }

    private static TrackHistoryEntry historyEntry(TrackHistoryItem item) {
        com.mentorhub.tracks.internal.domain.TrackChange change = item.change();
        TrackHistoryEntry dto = new TrackHistoryEntry(
                item.id(),
                item.occurredAt().atOffset(ZoneOffset.UTC),
                TrackHistoryAction.valueOf(change.action().name()),
                historyActor(item.actor()));
        // The generated list starts empty; null leaves it out of the JSON when it does not apply.
        dto.setChanges(null);
        switch (change) {
            case com.mentorhub.tracks.internal.domain.TrackChange.Created(var code, var name) -> {
                dto.setCode(code);
                dto.setName(name);
            }
            case com.mentorhub.tracks.internal.domain.TrackChange.Updated(var changes) ->
                dto.setChanges(changes.stream()
                        .map(fieldChange -> new TrackChange(
                                TrackField.valueOf(fieldChange.field().name()),
                                fieldChange.oldValue(),
                                fieldChange.newValue()))
                        .toList());
            case com.mentorhub.tracks.internal.domain.TrackChange.Archived _,
                    com.mentorhub.tracks.internal.domain.TrackChange.Restored _ -> {}
            case com.mentorhub.tracks.internal.domain.TrackChange.CoordinatorAdded _,
                    com.mentorhub.tracks.internal.domain.TrackChange.CoordinatorRemoved _,
                    com.mentorhub.tracks.internal.domain.TrackChange.MentorAdded _,
                    com.mentorhub.tracks.internal.domain.TrackChange.MentorRemoved _ ->
                dto.setUser(historyActor(item.user()));
            case com.mentorhub.tracks.internal.domain.TrackChange.StudentEnrolled(var _, var startDate) -> {
                dto.setUser(historyActor(item.user()));
                dto.setStartDate(startDate);
            }
            case com.mentorhub.tracks.internal.domain.TrackChange.EnrollmentMentorAssigned _,
                    com.mentorhub.tracks.internal.domain.TrackChange.EnrollmentMentorRemoved _ -> {
                dto.setUser(historyActor(item.user()));
                dto.setMentor(historyActor(item.mentor()));
            }
        }
        return dto;
    }

    private static @Nullable HistoryActor historyActor(@Nullable NamedUser user) {
        return user == null ? null : new HistoryActor(user.id(), user.name());
    }
}
