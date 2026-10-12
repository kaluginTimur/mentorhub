# <Feature title>

Status: draft | approved | implemented
Stage: <roadmap stage from docs/architecture/invariants.md>
Related: <ADRs, other specs>

## 1. Requirements

### Goal

<The problem for mentors, coordinators or students, and the outcome. Two or three sentences.>

### Roles and scenarios

#### <Role>

- S1. <As a role, I do X to achieve Y.>

### Acceptance criteria

- AC-1 (S1): Given <state>, when <action>, then <observable result>.

### Access matrix

| Action | admin | coordinator | mentor | student | observer |
|---|---|---|---|---|---|
| <action> | ✓ | own track | assigned enrollment | own data | ✗ |

<Conditions are explicit: which cohort, which track, current or past assignment.>

### Visibility

| Data | Who sees it |
|---|---|
| <data item> | <roles and conditions> |

### Audit

| Event | Recorded data |
|---|---|
| <state change> | <actor, subject, before/after> |

### Out of scope

- <What this feature deliberately does not do.>

## 2. UI

### <Screen name>

- Route: `<path>`; roles: <who opens it>.
- Purpose: <one sentence>.
- Content and actions: <what is shown, what can be done>.
- States: loading, empty, error, <per-role differences>.
- Patterns: <page patterns from docs/ui/guidelines.md>.

```
<text wireframe>
```

## 3. Design

### Modules

<Which modules change or appear, and what each owns.>

### Data model

<Tables, key columns, constraints and relations; what the migration adds.>

### API

| Method and path | Purpose | Roles | Errors |
|---|---|---|---|
| `GET /api/v1/<path>` | <purpose> | <roles> | <404, 403, ...> |

### Authorization

<How the access matrix and visibility are enforced: which checks, on which paths.>

### Events

<Events published to or consumed from other modules.>

### Decisions

- <Decision and reason; link the ADR when one is needed.>

## 4. Open questions

- Q1. <Question>: <options and recommendation>; decided by <who>.
