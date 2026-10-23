package com.mentorhub.users.internal.invitation;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Resubmits event publications whose processing failed unexpectedly, without waiting for a restart (spec
 * invitation-emails, AC-13). The age is far longer than one send can take (the mail timeouts), so a send still in
 * progress is not started twice. Assumes a single application instance (spec, Asynchronous delivery).
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
        publications.resubmitIncompletePublicationsOlderThan(STALE_AFTER);
    }
}
