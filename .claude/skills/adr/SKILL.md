---
name: adr
description: Record an architecture decision as a new ADR in docs/adr/, worked out with the user.
argument-hint: "<decision title>"
disable-model-invocation: true
---

Record the architecture decision "$ARGUMENTS" as a new ADR.

1. Read every ADR in `docs/adr/` and `docs/architecture/invariants.md`. If the decision changes or replaces an existing ADR, say which one; the old ADR gets a status line `superseded by <number>` instead of being rewritten.
2. The number is the highest existing number plus one, four digits. The file name is `<number>-<short-kebab-title>.md`.
3. Work out the content with the user. Propose a draft and ask only what the user has to decide:
   - **Context**: the forces and constraints that make a decision necessary;
   - **Decision**: what is decided, concretely enough to check code against it;
   - **Consequences**: what becomes easier, what becomes harder, what follow-up work it creates.
4. Write it in the format of the existing ADRs, in English:

```
# <number>. <Title>

Status: proposed, <YYYY-MM-DD>

## Context

## Decision

## Consequences
```

5. The status changes to `accepted` only when the user accepts the decision. If the decision changes a rule that `AGENTS.md`, `docs/architecture/invariants.md` or `docs/development/` describes, update those documents too.
