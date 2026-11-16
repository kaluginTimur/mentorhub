package com.mentorhub.users;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * How to reach a user.
 *
 * @param name first and last name joined by a space, or the email when both are missing
 * @param phone E.164, or {@code null} when the user has none
 */
public record UserContact(
        UUID id, String name, String email, @Nullable String phone) {}
