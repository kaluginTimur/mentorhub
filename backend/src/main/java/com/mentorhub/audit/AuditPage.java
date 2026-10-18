package com.mentorhub.audit;

import java.util.List;

/** One page of a subject's entries, newest first, and the number of the subject's entries on all pages. */
public record AuditPage(List<RecordedAuditEntry> items, long totalItems) {

    public AuditPage {
        items = List.copyOf(items);
    }
}
