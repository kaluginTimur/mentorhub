package com.mentorhub.users.internal.service;

import org.jspecify.annotations.Nullable;

/** A whole profile as the client sent it, before trimming and validation. */
public record ProfileInput(
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String phone) {}
