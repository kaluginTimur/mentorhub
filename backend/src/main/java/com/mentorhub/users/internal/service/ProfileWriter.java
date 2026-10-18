package com.mentorhub.users.internal.service;

import com.mentorhub.users.internal.domain.ProfileField;
import com.mentorhub.users.internal.domain.UserChange;
import com.mentorhub.users.internal.persistence.StoredUser;
import com.mentorhub.users.internal.persistence.UserAuditTrail;
import com.mentorhub.users.internal.persistence.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/** Stores a validated profile and audits the changed fields (AC-16, AC-17); shared by both profile operations. */
@Component
class ProfileWriter {

    private final UserRepository users;
    private final UserAuditTrail auditTrail;

    ProfileWriter(UserRepository users, UserAuditTrail auditTrail) {
        this.users = users;
        this.auditTrail = auditTrail;
    }

    /** Call with the user's row locked. Writes nothing when no value changes. */
    void write(UUID actorUserId, StoredUser current, ValidProfile profile) {
        List<UserChange.FieldChange> changes = new ArrayList<>();
        addIfChanged(changes, ProfileField.FIRST_NAME, current.firstName(), profile.firstName());
        addIfChanged(changes, ProfileField.LAST_NAME, current.lastName(), profile.lastName());
        addIfChanged(changes, ProfileField.PHONE, current.phone(), profile.phone());
        if (changes.isEmpty()) {
            return;
        }
        users.updateProfile(current.id(), profile.firstName(), profile.lastName(), profile.phone());
        auditTrail.record(actorUserId, current.id(), new UserChange.ProfileUpdated(changes));
    }

    private static void addIfChanged(
            List<UserChange.FieldChange> changes,
            ProfileField field,
            @Nullable String oldValue,
            @Nullable String newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(new UserChange.FieldChange(field, oldValue, newValue));
        }
    }
}
