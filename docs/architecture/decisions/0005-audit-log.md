# 0005. Audit log

## Context

Audited changes need a record: disabling and enabling users, role changes, profile edits and the administrator bootstrap today; enrollments, notes, feedback and assignments later. Several forces shape the log:

- An entry that can go missing defeats its purpose. "Who gave this person access?" must have an answer for every change, including changes made shortly before a crash.
- Every module writes entries, so the log must not create dependencies between modules (decision 0001).
- The log can repeat what it records: a note's text, a student's grade. A global log view would become a second read path for that data.
- Entries must not be rewritten afterwards, also not by a bug in our own code.

## Decision

- **A dedicated module** `com.mentorhub.audit` owns the `audit_log` table. Its public API is `AuditLog.record(AuditEntry)` and a query by subject. It depends on no other module.
- **Synchronous, in the changing transaction.** `record` joins the caller's transaction (`Propagation.MANDATORY`) and fails without one. A change and its entry commit or roll back together. Entries are not written from application events.
- **Entry shape:** time, actor (`users.id`, or none for a change made by the system), action, subject (type and id), and details as JSON.
  - The action and the subject type are strings owned by the module that writes them (for example `USER_DISABLED`, subject type `USER`). `audit` does not enumerate them, so a new action needs no change in `audit`.
  - The details are what a reader needs to understand the change (a reason, added and removed roles, old and new values of changed fields). Content that has its own visibility rules (a note's body) is not copied into the details; the entry references the subject instead.
- **Append-only in the database.** A trigger rejects `UPDATE` and `DELETE` on `audit_log`. The application has no code path that changes an entry.
- **Referential integrity:** `actor_user_id` references `users` (users are never deleted). Foreign keys between tables of different modules are allowed: Spring Modulith verifies code dependencies, not the schema, and a foreign key only couples the schemas (a module extracted later would have to replace it). `subject_id` has no foreign key because it is polymorphic: depending on `subject_type` it points at different tables, and a foreign key references exactly one. The subject's module writes the entry in the same transaction as the change, so the subject exists.
- **No global read endpoint.** The log is read only through per-subject views that the subject's module serves under the subject's own access rules: a user's history behind `USERS_READ` in `users`, later an enrollment's history in its own module with the enrollment's access checks. Each view maps the actions it knows into a typed contract and skips actions it does not know, with a warning, instead of failing (as decision 0004 does for unknown roles). A new action shown in a history extends that contract in the same change.

## Why

- A change without its entry cannot be committed, and a stored entry cannot be changed or deleted by the application.
- Every module that writes audited changes depends on `audit`'s public API; `audit` depends on nothing.
- Cost: a slow audit insert slows the change. The table has one insert per audited change, which is negligible at MentorHub's scale.
- A global audit view, retention and export are not possible without a later decision on their visibility rules.

## Rejected alternatives

- **Entries from application events** (`@ApplicationModuleListener` with the Modulith event publication registry). Decouples writers further, but an entry can be delayed or, after a failed listener, lost or replayed; the guarantee would rest on the registry's retries. Events stay the tool for reactions between modules.
- **Database triggers or change data capture** on domain tables. Captures every row change, but not the actor or the intent ("disabled because graduated"), and ties the log to the table layout.
- **Hibernate Envers or a similar entity history.** The existing persistence code uses JdbcClient, not JPA entities, and Envers records row versions, not actions with reasons.
- **One typed table per module.** Strong typing, but every module would reimplement storage, ordering and paging, and a later cross-module view (for administrators) would need a union over all of them.
- **A global audit screen now.** Useful for administrators, but every module's details would need a visibility filter in one place that knows every module. Deferred until someone defines who may see which actions.
