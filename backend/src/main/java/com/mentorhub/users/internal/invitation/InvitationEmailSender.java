package com.mentorhub.users.internal.invitation;

import com.mentorhub.mail.MailDelivery;
import com.mentorhub.mail.MailDeliveryException;
import com.mentorhub.mail.MailMessage;
import com.mentorhub.users.internal.domain.InvitationEmailFailure;
import com.mentorhub.users.internal.domain.InvitationEmailStatus;
import com.mentorhub.users.internal.domain.InvitationStatus;
import com.mentorhub.users.internal.persistence.InvitationEmailRepository;
import com.mentorhub.users.internal.persistence.InvitationRepository;
import com.mentorhub.users.internal.persistence.StoredInvitationEmail;
import com.mentorhub.users.internal.persistence.StoredInvitationLink;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends stored invitation email requests after the requesting transaction commits, on the async executor (spec
 * invitation-emails, AC-8 to AC-15). Three steps, so that no transaction or connection is held while the mail server is
 * contacted: a short transaction reads the request and the invitation, the send runs outside any transaction, and a
 * second short transaction records the outcome.
 *
 * <p>A mail failure is recorded as {@link InvitationEmailStatus#FAILED} and completes the publication, so it is not
 * retried (AC-11). Any other exception leaves the publication incomplete for {@link InvitationEmailResubmission} or the
 * next start (AC-12, AC-13); a request that is no longer queued is never sent again.
 */
@Component
class InvitationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(InvitationEmailSender.class);

    static final String DISABLED_FAILURE = "Email is not enabled: no mail server is configured";

    private record Delivery(MailMessage message, String token, URI link) {}

    private final InvitationEmailRepository emails;
    private final InvitationRepository invitations;
    private final InvitationProperties properties;
    private final MailDelivery mail;
    private final TransactionTemplate transaction;

    InvitationEmailSender(
            InvitationEmailRepository emails,
            InvitationRepository invitations,
            InvitationProperties properties,
            MailDelivery mail,
            PlatformTransactionManager transactionManager) {
        this.emails = emails;
        this.invitations = invitations;
        this.properties = properties;
        this.mail = mail;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    // NOT_SUPPORTED instead of the annotation's REQUIRES_NEW: the steps open their own short transactions. Public, so
    // the async and transaction proxies apply.
    @ApplicationModuleListener(propagation = Propagation.NOT_SUPPORTED)
    public void on(InvitationEmailRequested event) {
        Delivery delivery = transaction.execute(status -> prepare(event));
        if (delivery == null) {
            return;
        }
        try {
            mail.send(delivery.message());
        } catch (MailDeliveryException e) {
            InvitationEmailFailure failure = InvitationEmailFailure.of(
                    String.valueOf(e.getMessage()),
                    delivery.token(),
                    delivery.link().toString());
            // Neither the exception nor the message: only the redacted reason (AC-15).
            log.warn("Invitation email {} failed: {}", event.emailRequestId(), failure.text());
            transaction.executeWithoutResult(status -> emails.markFailed(event.emailRequestId(), failure));
            return;
        }
        transaction.executeWithoutResult(status -> emails.markSent(event.emailRequestId()));
    }

    /** The message to send, or {@code null} when the request is done without sending. */
    private @Nullable Delivery prepare(InvitationEmailRequested event) {
        StoredInvitationEmail request = emails.lock(event.emailRequestId()).orElse(null);
        if (request == null || request.status() != InvitationEmailStatus.QUEUED) {
            return null;
        }
        StoredInvitationLink invitation =
                invitations.findLink(request.invitationId()).orElseThrow();
        if (invitation.status() != InvitationStatus.PENDING) {
            emails.markSkipped(request.id());
            return null;
        }
        if (!mail.isEnabled()) {
            // A request stored while email was enabled, replayed after a restart without a mail server.
            log.warn("Invitation email {} failed: {}", request.id(), DISABLED_FAILURE);
            emails.markFailed(request.id(), InvitationEmailFailure.of(DISABLED_FAILURE));
            return null;
        }
        URI link = properties.link(invitation.token());
        return new Delivery(
                InvitationEmailText.message(invitation.email(), link, invitation.expiresAt()),
                invitation.token(),
                link);
    }
}
