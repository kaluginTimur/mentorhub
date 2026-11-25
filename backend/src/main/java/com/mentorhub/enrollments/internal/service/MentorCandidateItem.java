package com.mentorhub.enrollments.internal.service;

import com.mentorhub.users.DirectoryUser;

/**
 * A candidate for the mentors of an enrollment or of a cohort, with what they lead on the track.
 *
 * @param students the number of active enrollments on the track the user leads (ENR-15)
 * @param cohorts the number of cohorts of the track the user is a mentor of
 */
public record MentorCandidateItem(DirectoryUser user, int students, int cohorts) {}
