---
name: decision
description: Record or change an architecture decision in docs/architecture/decisions/, worked out with the user.
argument-hint: "<decision title>"
disable-model-invocation: true
---

Record the architecture decision "$ARGUMENTS".

1. Read the index `docs/architecture/decisions/README.md` and the decisions it names as related. If the decision does not meet the threshold there, say so and suggest where it belongs instead: a guide, a requirement or the code.
2. If it changes an existing decision, edit that decision in place, as the index says.
3. For a new one, the number is the highest existing number plus one, four digits, and the file name is `<number>-<short-kebab-title>.md`.
4. Work out the content with the user. Propose a draft and ask only what the user has to decide:
   - **Context:** the forces and constraints that make a decision necessary;
   - **Decision:** what is decided, concretely enough to check code against it;
   - **Why:** the reasons, including what it costs;
   - **Rejected alternatives:** what else was considered, and why not.
5. Write it in English, in the format of the existing decisions, with the sections in this order. Add its line to the index.

A rule the decision introduces for the whole project is only a proposal: the constitution is amended by the user. If the decision changes what `AGENTS.md`, `docs/requirements/` or `docs/development/` says, update those documents too.
