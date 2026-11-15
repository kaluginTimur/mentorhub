package com.mentorhub.tracks.internal;

import java.net.URI;

/** The 409 problem types of tracks. */
public final class TrackProblems {

    public static final URI TRACK_ARCHIVED = URI.create("urn:mentorhub:problem:track-archived");
    public static final URI COORDINATOR_NOT_ELIGIBLE = URI.create("urn:mentorhub:problem:coordinator-not-eligible");
    public static final URI TRACK_HAS_ACTIVE_ENROLLMENTS =
            URI.create("urn:mentorhub:problem:track-has-active-enrollments");

    private TrackProblems() {}
}
