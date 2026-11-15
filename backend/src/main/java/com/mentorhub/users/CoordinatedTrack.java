package com.mentorhub.users;

import java.util.Objects;
import java.util.UUID;

/** A track as a user's history names it: its id, and its code and name at the moment of the change. */
public record CoordinatedTrack(UUID id, String code, String name) {

    public CoordinatedTrack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(name, "name");
    }
}
