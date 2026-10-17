package com.mentorhub.users.internal.service;

import com.mentorhub.users.internal.domain.AccessStatus;
import org.jspecify.annotations.Nullable;

/** The caller's access status; {@code user} is present only for an active user. */
public record MeDetails(AccessStatus status, @Nullable UserProfile user) {}
