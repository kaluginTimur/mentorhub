# 0006. Audit log

Status: accepted, 2026-10-18

## Context

`docs/architecture/invariants.md` requires an AuditLog entry for every state change a spec marks as audited. The first audited changes come with user administration (`docs/features/user-administration.md`): disabling and enabling users, role changes, profile edits and the administrator bootstrap. The tracking core will add enrollments, notes and feedback, and assignments will follow.

Several forces shape the log:

- An entry that can go missing defeats its purpose. "Who gave this person access?" must have an answer for every change, including changes made shortly before a crash.
- Every module writes entries, so the log must not create dependencies between modules (ADR 0001).
- The log can repeat what it records: a note's text, a student's grade. A global log view would become a second read path for that data, and note visibility must hold on every read path, audit views included.
- Entries must not be rewritten afterwards, also not by a bug in our own code.

## Decision

- **A dedicated module** `com.mentorhub.audit` owns the `audit_log` table. Its public API is `AuditLog.record(AuditEntry)` and a query by subject. It depends on no other module.
- **Synchronous, in the changing transaction.** `record` joins the caller's transaction (`Propagation.MANDATORY`) and fails without one. A change and its entry commit or roll back together. Entries are not written from application events: an event listener that fails or runs after the commit could lose the entry.
- **Entry shape:** time, actor (`users.id`, or none for a change made by the system), action, subject (type and id), and details as JSON.
  - The action and the subject type are strings owned by the module that writes them (for example `USER_DISABLED`, subject type `USER`). `audit` does not enumerate them, so a new action needs no change in `audit`.
  - The details are what a reader needs to understand the change (a reason, added and removed roles, old and new values of changed fields). Content that has its own visibility rules (a note's body) is not copied into the details; the entry references the subject instead.
- **Append-only in the database.** A trigger rejects `UPDATE` and `DELETE` on `audit_log`. The application has no code path that changes an entry.
- **Referential integrity:** `actor_user_id` references `users` (users are never deleted). Foreign keys between tables of different modules are allowed: Spring Modulith verifies code dependencies, not the schema, and a foreign key only couples the schemas (a module extracted later would have to replace it). `subject_id` has no foreign key because it is polymorphic: depending on `subject_type` it points at different tables, and a foreign key references exactly one. The subject's module writes the entry in the same transaction as the change, so the subject exists.
- **No global read endpoint.** The log is read only through per-subject views that the subject's module serves under the subject's own access rules: a user's history behind `USERS_READ` in `users`, later an enrollment's history in its own module with the enrollment's access checks. Each view maps the actions it knows into a typed contract and skips actions it does not know, with a warning, instead of failing (as ADR 0004 does for unknown roles).

Alternatives considered:

- **Entries from application events** (`@ApplicationModuleListener` with the Modulith event publication registry). Decouples writers further, but an entry can be delayed or, after a failed listener, lost or replayed; the guarantee would rest on the registry's retries. Rejected for audit; events stay the tool for reactions between modules.
- **Database triggers or change data capture** on domain tables. Captures every row change, but not the actor or the intent ("disabled because graduated"), and ties the log to the table layout.
- **Hibernate Envers or a similar entity history.** The existing persistence code uses JdbcClient, not JPA entities, and Envers records row versions, not actions with reasons.
- **One typed table per module.** Strong typing, but every module would reimplement storage, ordering and paging, and a later cross-module view (for administrators) would need a union over all of them.
- **A global audit screen now.** Useful for administrators, but every module's details would need a visibility filter in one place that knows every module. Deferred until a spec defines who may see which actions.

## Consequences

- A change without its entry cannot be committed, and a stored entry cannot be changed or deleted by the application.
- Every module that writes audited changes depends on `audit`'s public API; `audit` depends on nothing.
- Writing an entry is part of the change's transaction, so a slow audit insert slows the change. The table has one insert per audited change, which is negligible at MentorHub's scale.
- Each module that shows history defines its own typed contract for its actions and keeps it in step with what it writes. A new action shown in a history extends that contract in the same change.
- A global audit view, retention and export are not possible without a later decision on their visibility rules. Personal data in audit details falls under the postponed personal data rules.
- `docs/architecture/invariants.md` records the rules: audited changes are written in the same transaction; the log is append-only; it is read only through per-subject views that apply the subject's access rules.
