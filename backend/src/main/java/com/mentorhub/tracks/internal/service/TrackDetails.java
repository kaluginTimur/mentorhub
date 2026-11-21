package com.mentorhub.tracks.internal.service;

import com.mentorhub.tracks.internal.domain.TrackStatus;
import com.mentorhub.users.DirectoryUser;
import java.util.List;
import java.util.UUID;

/**
 * A track with its coordinators and its mentors, each sorted as {@link com.mentorhub.users.UserDirectory} sorts users.
 *
 * @param canEnroll the current user may enroll students on the track and name its mentors, whatever its status (ENR-1)
 */
public record TrackDetails(
        UUID id,
        String code,
        String name,
        TrackStatus status,
        List<DirectoryUser> coordinators,
        List<DirectoryUser> mentors,
        boolean canEnroll) {

    public TrackDetails {
        coordinators = List.copyOf(coordinators);
        mentors = List.copyOf(mentors);
    }
}
