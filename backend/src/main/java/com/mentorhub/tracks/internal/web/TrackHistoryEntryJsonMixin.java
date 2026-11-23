package com.mentorhub.tracks.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mentorhub.api.model.CohortChange;
import com.mentorhub.api.model.CohortReference;
import com.mentorhub.api.model.HistoryActor;
import com.mentorhub.api.model.TrackChange;
import com.mentorhub.api.model.TrackHistoryEntry;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.jackson.JacksonMixin;

/**
 * Leaves the detail properties that do not apply to an entry's action out of the JSON. Per property, not per class:
 * the required {@code actor} must still be sent as {@code null} for a change made by the system.
 */
@JacksonMixin(TrackHistoryEntry.class)
abstract class TrackHistoryEntryJsonMixin {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable String getCode();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable String getName();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable List<TrackChange> getChanges();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable HistoryActor getUser();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable LocalDate getStartDate();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable HistoryActor getMentor();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable CohortReference getCohort();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable CohortChange getCohortChange();
}
