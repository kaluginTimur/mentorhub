/**
 * The append-only audit log (ADR 0006). Public API: {@link AuditLog}, {@link AuditEntry}, {@link RecordedAuditEntry}
 * and {@link AuditPage}. Depends on no other module; actions and subject types are strings owned by the writing module.
 */
@NullMarked
package com.mentorhub.audit;

import org.jspecify.annotations.NullMarked;
