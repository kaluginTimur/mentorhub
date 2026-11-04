---
name: explore
description: Turn a vague idea into items in docs/roadmap.md with the user, without writing requirements. Optional first stage of a change.
argument-hint: "<idea or topic>"
disable-model-invocation: true
---

Explore "$ARGUMENTS" with the user, as stage 1 of the **Stages** section of `docs/process.md` describes.

1. Read `docs/roadmap.md`, the index of `docs/requirements/` and the capabilities the idea touches.
2. Work it out one topic at a time: propose a concrete shape, give your recommendation, and ask only what the user has to decide. If the discussion grows, keep notes in `docs/explorations/<topic>.md`, so a new session can continue from them.
3. Suggest a throwaway experiment in a separate branch only when reading cannot answer a question, and run it only when the user wants it. Bring back findings, not code.
4. Write the result into `docs/roadmap.md`: one line of scope for each item, what is out of scope, the capabilities it touches, and product decisions marked "user". Split a big idea into several items. Only the user promotes an idea or reorders items; your own suggestions go to Ideas, marked as yours.
5. Delete the notes. Tell the user the next step: `/spec <change>` for an item they want to start.

Write no requirements, contract or code here.
