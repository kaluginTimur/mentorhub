package com.mentorhub.users.internal.service;

import com.mentorhub.users.internal.domain.AccessStatus;
import com.mentorhub.users.internal.domain.Caller;
import com.mentorhub.users.internal.domain.StoredRoles;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.persistence.StoredProfile;
import com.mentorhub.users.internal.persistence.UserRepository;
import com.mentorhub.users.internal.security.CurrentCaller;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The caller's own user. A caller that is not invited or disabled learns only its status. */
@Service
public class MeService {

    private final CurrentCaller currentCaller;
    private final UserRepository users;
    private final StoredRoles storedRoles;

    MeService(CurrentCaller currentCaller, UserRepository users, StoredRoles storedRoles) {
        this.currentCaller = currentCaller;
        this.users = users;
        this.storedRoles = storedRoles;
    }

    public MeDetails me() {
        Caller caller = currentCaller.caller();
        if (caller.status() != AccessStatus.ACTIVE || caller.userId() == null) {
            return new MeDetails(caller.status(), null);
        }
        return new MeDetails(AccessStatus.ACTIVE, profile(caller.userId()));
    }

    /**
     * The caller's details as stored now, for an operation that has just linked the caller to {@code userId}
     * (accepting an invitation): the caller's authentication still carries the status it had when the request
     * started.
     */
    public MeDetails forLinkedUser(UUID userId) {
        UserStatus status = users.findUser(userId)
                .orElseThrow(() -> new IllegalStateException("The caller's linked user is not stored"))
                .status();
        return switch (status) {
            case ACTIVE -> new MeDetails(AccessStatus.ACTIVE, profile(userId));
            case DISABLED -> new MeDetails(AccessStatus.DISABLED, null);
        };
    }

    private UserProfile profile(UUID userId) {
        StoredProfile profile = users.findProfile(userId)
                .orElseThrow(() -> new IllegalStateException("The active caller has no stored user"));
        return new UserProfile(
                profile.id(),
                profile.email(),
                profile.firstName(),
                profile.lastName(),
                profile.phone(),
                List.copyOf(storedRoles.roles(profile.roleNames())),
                List.copyOf(storedRoles.permissions(profile.roleNames())));
    }
}
