package com.mentorhub.users.internal.persistence;

import com.mentorhub.audit.AuditEntry;
import com.mentorhub.audit.AuditLog;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.InvitationAuditAction;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The audit entries about invitations (subject type {@link InvitationAuditAction#SUBJECT_TYPE}); writing joins the
 * caller's transaction. The token never goes into the details.
 */
@Component
public class InvitationAuditTrail {

    private final AuditLog auditLog;

    InvitationAuditTrail(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    /** @param roles in enum order */
    public void created(UUID actorUserId, UUID invitationId, String email, Collection<Role> roles) {
        record(
                actorUserId,
                invitationId,
                InvitationAuditAction.INVITATION_CREATED,
                Map.of("email", email, "roles", roles.stream().map(Role::name).toList()));
    }

    public void linkReissued(UUID actorUserId, UUID invitationId, Instant expiresAt) {
        record(
                actorUserId,
                invitationId,
                InvitationAuditAction.INVITATION_LINK_REISSUED,
                Map.of("expiresAt", expiresAt.toString()));
    }

    public void revoked(UUID actorUserId, UUID invitationId) {
        record(actorUserId, invitationId, InvitationAuditAction.INVITATION_REVOKED, Map.of());
    }

    private void record(UUID actorUserId, UUID invitationId, InvitationAuditAction action, Map<String, ?> details) {
        auditLog.record(
                new AuditEntry(actorUserId, action.name(), InvitationAuditAction.SUBJECT_TYPE, invitationId, details));
    }
}
