package com.mentorhub.users.internal.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * An audited change to a user; its components are the details of the audit entry (spec user-administration, Audit).
 */
public sealed interface UserChange {

    UserAuditAction action();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Disabled(DisableReason reason, @Nullable String comment) implements UserChange {
        public Disabled {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public UserAuditAction action() {
            return UserAuditAction.USER_DISABLED;
        }
    }

    /** The reason and comment the user had been disabled with. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Enabled(DisableReason reason, @Nullable String comment) implements UserChange {
        public Enabled {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public UserAuditAction action() {
            return UserAuditAction.USER_ENABLED;
        }
    }

    /** Role names as stored; a reader keeps only the roles it knows. */
    record RolesChanged(List<String> rolesAdded, List<String> rolesRemoved) implements UserChange {
        public RolesChanged {
            rolesAdded = List.copyOf(rolesAdded);
            rolesRemoved = List.copyOf(rolesRemoved);
        }

        @Override
        public UserAuditAction action() {
            return UserAuditAction.USER_ROLES_CHANGED;
        }
    }

    record ProfileUpdated(List<FieldChange> changes) implements UserChange {
        public ProfileUpdated {
            changes = List.copyOf(changes);
        }

        @Override
        public UserAuditAction action() {
            return UserAuditAction.USER_PROFILE_UPDATED;
        }
    }

    record FieldChange(
            ProfileField field,
            @Nullable String oldValue,
            @Nullable String newValue) {
        public FieldChange {
            Objects.requireNonNull(field, "field");
        }
    }

    /** What the administrator bootstrap (ADR 0005) did. */
    record BootstrappedAdmin(boolean userCreated, boolean identityLinked, boolean enabled, boolean adminAdded)
            implements UserChange {
        @Override
        public UserAuditAction action() {
            return UserAuditAction.USER_BOOTSTRAPPED_ADMIN;
        }

        public boolean changedAnything() {
            return userCreated || identityLinked || enabled || adminAdded;
        }
    }

    /**
     * What accepting an invitation did (spec invitations, AC-16). Role names as stored; a reader keeps only the roles
     * it knows.
     *
     * @param invitedBy {@code users.id} of the administrator who created the invitation
     * @param enabled the user was disabled and has been enabled
     */
    record InvitationAccepted(
            UUID invitationId,
            UUID invitedBy,
            boolean userCreated,
            boolean identityLinked,
            boolean enabled,
            List<String> rolesAdded,
            List<String> rolesRemoved)
            implements UserChange {
        public InvitationAccepted {
            Objects.requireNonNull(invitationId, "invitationId");
            Objects.requireNonNull(invitedBy, "invitedBy");
            rolesAdded = List.copyOf(rolesAdded);
            rolesRemoved = List.copyOf(rolesRemoved);
        }

        @Override
        public UserAuditAction action() {
            return UserAuditAction.USER_INVITATION_ACCEPTED;
        }
    }
}
