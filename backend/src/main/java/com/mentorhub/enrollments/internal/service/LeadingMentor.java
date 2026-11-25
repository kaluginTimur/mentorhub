package com.mentorhub.enrollments.internal.service;

import com.mentorhub.users.DirectoryUser;

/** @param throughCohort the user leads the enrollment only as a mentor of its cohort, not of the enrollment itself */
public record LeadingMentor(DirectoryUser user, boolean throughCohort) {}
