package com.mentorhub.users.internal.bootstrap;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserChange;
import com.mentorhub.users.internal.persistence.LinkedUser;
import com.mentorhub.users.internal.persistence.UserAuditTrail;
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
 * administrator. Idempotent and safe under concurrent first requests: after the unlocked check finds no active
 * administrator, the administrator lock serializes the rule with other bootstraps and with lockout changes, and the
 * check runs again. Only a transaction that changed something logs the warning and writes
 * {@code USER_BOOTSTRAPPED_ADMIN} (AC-20).
 */
public class AdministratorBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdministratorBootstrap.class);

    private final UserRepository users;
    private final UserAuditTrail auditTrail;
    private final TransactionTemplate transactions;
    private final @Nullable String bootstrapEmail;

    /** @param bootstrapEmail the configured bootstrap address; empty or {@code null} disables the rule */
    public AdministratorBootstrap(
            UserRepository users,
            UserAuditTrail auditTrail,
            TransactionTemplate transactions,
            @Nullable String bootstrapEmail) {
        this.users = users;
        this.auditTrail = auditTrail;
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
        users.lockAdministrators();
        if (users.activeUserWithRoleExists(Role.ADMIN)) {
            return;
        }
        boolean userCreated = false;
        boolean identityLinked = false;
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
            userCreated = users.insertUserIfAbsent(email, claims.givenName(), claims.familyName()) > 0;
            userId = users.findUserIdByEmail(email).orElseThrow();
            identityLinked = users.linkIdentityIfAbsent(userId, claims.issuer(), claims.subject()) > 0;
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
        var changes = new UserChange.BootstrappedAdmin(
                userCreated,
                identityLinked,
                users.enable(userId) > 0,
                users.assignRoleIfAbsent(userId, Role.ADMIN) > 0);
        if (changes.changedAnything()) {
            log.warn("Administrator bootstrap applied to user {}", userId);
            auditTrail.record(null, userId, changes);
        }
    }
}
