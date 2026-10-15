package com.mentorhub.users.internal.bootstrap;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.persistence.LinkedUser;
import com.mentorhub.users.internal.persistence.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * ADR 0005: while no active user has the ADMIN role, the identity with the verified bootstrap email becomes an active
 * administrator. Idempotent and safe under concurrent first requests: every write tolerates a concurrent equal write,
 * and the warning is logged only by the transaction that changed rows.
 */
public class AdministratorBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdministratorBootstrap.class);

    private final UserRepository users;
    private final TransactionTemplate transactions;
    private final @Nullable String bootstrapEmail;

    /** @param bootstrapEmail the configured bootstrap address; empty or {@code null} disables the rule */
    public AdministratorBootstrap(
            UserRepository users, TransactionTemplate transactions, @Nullable String bootstrapEmail) {
        this.users = users;
        this.transactions = transactions;
        this.bootstrapEmail = StringUtils.hasText(bootstrapEmail) ? bootstrapEmail : null;
    }

    public void applyIfEligible(SignInClaims claims) {
        if (bootstrapEmail == null
                || !claims.emailVerified()
                || claims.email() == null
                || !claims.email().equalsIgnoreCase(bootstrapEmail)) {
            return;
        }
        transactions.executeWithoutResult(status -> apply(claims, claims.email(), status));
    }

    private void apply(SignInClaims claims, String email, TransactionStatus transaction) {
        if (users.activeUserWithRoleExists(Role.ADMIN)) {
            return;
        }
        int changedRows = 0;
        UUID userId;
        Optional<LinkedUser> linked = users.findLinkedUser(claims.issuer(), claims.subject());
        if (linked.isPresent()) {
            if (!linked.get().email().equalsIgnoreCase(email)) {
                log.warn(
                        "Administrator bootstrap refused: the identity is linked to user {}, whose email is not the"
                                + " bootstrap address",
                        linked.get().userId());
                return;
            }
            userId = linked.get().userId();
        } else {
            changedRows += users.insertActiveUserIfAbsent(email, claims.givenName(), claims.familyName());
            userId = users.findUserIdByEmail(email).orElseThrow();
            changedRows += users.linkIdentityIfAbsent(userId, claims.issuer(), claims.subject());
            UUID linkedUserId = users.findLinkedUser(claims.issuer(), claims.subject())
                    .orElseThrow()
                    .userId();
            if (!linkedUserId.equals(userId)) {
                // Linked to another user by a concurrent transaction: never move an identity.
                transaction.setRollbackOnly();
                log.warn(
                        "Administrator bootstrap refused: the identity is linked to user {}, whose email is not the"
                                + " bootstrap address",
                        linkedUserId);
                return;
            }
        }
        changedRows += users.activate(userId);
        changedRows += users.assignRoleIfAbsent(userId, Role.ADMIN);
        if (changedRows > 0) {
            log.warn("Administrator bootstrap applied to user {}", userId);
        }
    }
}
