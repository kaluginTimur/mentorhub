package com.mentorhub.users;

import java.util.Objects;
import java.util.UUID;

/** A cohort as a history names it: its id, and its name at the moment of the change. */
public record HistoryCohort(UUID id, String name) {

    public HistoryCohort {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }
}
