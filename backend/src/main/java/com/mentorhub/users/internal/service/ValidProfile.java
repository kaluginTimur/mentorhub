package com.mentorhub.users.internal.service;

import org.jspecify.annotations.Nullable;

/** A profile after {@link ProfileRules}: trimmed names, blank as {@code null}, and the phone in E.164. */
record ValidProfile(
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String phone) {}
