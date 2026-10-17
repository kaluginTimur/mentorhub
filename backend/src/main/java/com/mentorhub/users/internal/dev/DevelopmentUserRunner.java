package com.mentorhub.users.internal.dev;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.persistence.LinkedUser;
import com.mentorhub.users.internal.persistence.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates or updates the development users on start: identity link, status and exactly the configured roles.
 * Idempotent. Profile data is set only when a user is created, as everywhere else.
 */
class DevelopmentUserRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevelopmentUserRunner.class);

    private final DevelopmentUsersProperties properties;
    private final UserRepository users;
    private final TransactionTemplate transactions;

    DevelopmentUserRunner(
            DevelopmentUsersProperties properties, UserRepository users, TransactionTemplate transactions) {
        this.properties = properties;
        this.users = users;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        for (DevelopmentUsersProperties.DevelopmentUser user : properties.users()) {
            transactions.executeWithoutResult(status -> apply(user));
        }
        log.info("Development users are ready: {}", properties.users().size());
    }

    private void apply(DevelopmentUsersProperties.DevelopmentUser user) {
        users.insertUserIfAbsent(user.email(), user.firstName(), user.lastName(), user.status());
        UUID userId = users.findUserIdByEmail(user.email()).orElseThrow();
        users.setStatus(userId, user.status());
        users.linkIdentityIfAbsent(userId, properties.issuerUri(), user.subject());
        LinkedUser linked =
                users.findLinkedUser(properties.issuerUri(), user.subject()).orElseThrow();
        if (!linked.userId().equals(userId)) {
            log.warn(
                    "Development user {}: the identity is linked to another user ({}) and is left unchanged",
                    user.email(),
                    linked.email());
        }
        users.removeRolesOtherThan(userId, user.roles());
        for (Role role : user.roles()) {
            users.assignRoleIfAbsent(userId, role);
        }
    }
}
