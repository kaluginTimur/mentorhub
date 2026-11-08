# Requirements

Living requirements: what the system does, one file per capability. The format is in the **Requirements** section of `docs/development/docs.md`.

The prefix of a capability is the stable key of its ids. The order below is logical: foundations first, then the features built on them.

| Capability | Prefix | Summary |
|---|---|---|
| [Identity](identity.md) | IDN | Who may enter: validated tokens, linked identities, active users, the administrator bootstrap, the current user |
| [Authorization](authorization.md) | PERM | Roles and their permissions, and how a refused action responds |
| [Platform](platform.md) | PLT | What every API and client relies on: error responses, paging, client configuration |
| [Audit](audit.md) | AUD | What is recorded, how the record is protected, how it is read, and the catalog of actions |
| [User administration](user-administration.md) | USR | Viewing and changing users, their profiles and roles |
| [Invitations](invitations.md) | INV | Inviting a person, invitation links, accepting an invitation |
| [Invitation emails](invitation-emails.md) | IEM | Sending invitations by email and tracking their delivery |
