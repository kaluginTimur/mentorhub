package com.mentorhub.users.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mentorhub.api.model.BootstrapChanges;
import com.mentorhub.api.model.DisableReason;
import com.mentorhub.api.model.HistoryEntry;
import com.mentorhub.api.model.ProfileChange;
import com.mentorhub.api.model.Role;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.jackson.JacksonMixin;

/**
 * Leaves the detail properties that do not apply to an entry's action out of the JSON. Per property, not per class:
 * the required {@code actor} must still be sent as {@code null} for a change made by the system.
 */
@JacksonMixin(HistoryEntry.class)
abstract class HistoryEntryJsonMixin {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable DisableReason getReason();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable String getComment();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable List<Role> getRolesAdded();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable List<Role> getRolesRemoved();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable List<ProfileChange> getChanges();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable BootstrapChanges getBootstrap();
}
