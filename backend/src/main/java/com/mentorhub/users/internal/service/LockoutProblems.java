package com.mentorhub.users.internal.service;

import java.net.URI;

/** The 409 problem types of the lockout rules. */
public final class LockoutProblems {

    public static final URI SELF_LOCKOUT = URI.create("urn:mentorhub:problem:self-lockout");
    public static final URI LAST_ADMIN = URI.create("urn:mentorhub:problem:last-admin");

    private LockoutProblems() {}
}
