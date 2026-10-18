package com.mentorhub.users.internal.service;

import com.mentorhub.users.CurrentUserProvider;
import com.mentorhub.users.internal.persistence.StoredUser;
import com.mentorhub.users.internal.persistence.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caller's own profile (S8, AC-17). Any active user may change it, with or without roles; it only ever changes the
 * caller's own user, never an id from the request.
 */
@Service
public class ProfileService {

    private final CurrentUserProvider currentUser;
    private final UserRepository users;
    private final ProfileRules rules;
    private final ProfileWriter writer;

    ProfileService(CurrentUserProvider currentUser, UserRepository users, ProfileRules rules, ProfileWriter writer) {
        this.currentUser = currentUser;
        this.users = users;
        this.rules = rules;
        this.writer = writer;
    }

    /**
     * Stores the caller's first name, last name and phone. The email never changes. Read the result with
     * {@link MeService#me()}.
     *
     * @throws com.mentorhub.web.ValidationException with one error per invalid field; nothing is stored
     */
    @Transactional
    public void updateOwnProfile(ProfileInput input) {
        UUID userId = currentUser.currentUser().id();
        ValidProfile profile = rules.validate(input);
        if (!users.lockUser(userId)) {
            throw new IllegalStateException("The active caller has no stored user");
        }
        StoredUser current = users.findUser(userId).orElseThrow();
        writer.write(userId, current, profile);
    }
}
