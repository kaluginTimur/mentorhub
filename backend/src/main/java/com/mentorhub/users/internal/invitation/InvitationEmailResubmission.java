package com.mentorhub.users.internal.invitation;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Resubmits invitation email publications whose processing failed unexpectedly, without waiting for a restart (spec
 * invitation-emails, AC-13). Only {@link InvitationEmailRequested} publications are resubmitted: other listeners may
 * not tolerate a delivery while one is still running. The age counts from the publication, so a publication still
 * waiting in the executor or still sending is resubmitted too; {@link InvitationEmailSender} runs the deliveries of one
 * request one at a time, and a request that is no longer queued is not sent again. Assumes a single application
 * instance (spec, Asynchronous delivery).
 */
@Component
class InvitationEmailResubmission {

    static final Duration STALE_AFTER = Duration.ofMinutes(5);

    private final IncompleteEventPublications publications;

    InvitationEmailResubmission(IncompleteEventPublications publications) {
        this.publications = publications;
    }

    @Scheduled(initialDelay = 5, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    void resubmitStalePublications() {
        Instant staleBefore = Instant.now().minus(STALE_AFTER);
        publications.resubmitIncompletePublications(
                publication -> publication.getEvent() instanceof InvitationEmailRequested
                        && publication.getPublicationDate().isBefore(staleBefore));
    }
}
