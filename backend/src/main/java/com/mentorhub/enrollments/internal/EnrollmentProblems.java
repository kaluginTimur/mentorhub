package com.mentorhub.enrollments.internal;

import java.net.URI;

/** The 409 problem types of enrollments and cohorts. */
public final class EnrollmentProblems {

    /** The same type as archived tracks refuse every change with. */
    public static final URI TRACK_ARCHIVED = URI.create("urn:mentorhub:problem:track-archived");

    public static final URI STUDENT_NOT_ELIGIBLE = URI.create("urn:mentorhub:problem:student-not-eligible");
    public static final URI STUDENT_ALREADY_ENROLLED = URI.create("urn:mentorhub:problem:student-already-enrolled");
    public static final URI MENTOR_NOT_ELIGIBLE = URI.create("urn:mentorhub:problem:mentor-not-eligible");
    public static final URI ENROLLMENT_NOT_ELIGIBLE = URI.create("urn:mentorhub:problem:enrollment-not-eligible");
    public static final URI COHORT_NOT_EMPTY = URI.create("urn:mentorhub:problem:cohort-not-empty");
    public static final URI COHORT_NOT_ELIGIBLE = URI.create("urn:mentorhub:problem:cohort-not-eligible");

    private EnrollmentProblems() {}
}
