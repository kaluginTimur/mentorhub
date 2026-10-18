package com.mentorhub.users.internal.service;

import com.mentorhub.users.internal.persistence.UserName;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/** @param name first and last name joined by a space, or the email when both are missing */
public record HistoryActor(UUID id, String name) {

    public static HistoryActor of(UserName user) {
        String name = String.join(
                " ",
                Stream.of(user.firstName(), user.lastName())
                        .filter(Objects::nonNull)
                        .toList());
        return new HistoryActor(user.id(), name.isEmpty() ? user.email() : name);
    }
}
