---
name: explore
description: Turn a vague idea, or a choice of approach that needs more than the first step, into items in docs/roadmap.md with the user, on the branch /change started, without writing requirements. Start it yourself only after the user agreed, as at the path gate of /change.
argument-hint: "<idea or topic>"
---

Explore "$ARGUMENTS" with the user, as stage 1 of the **Stages** section of `docs/process.md` describes.

1. Check the branch: an exploration runs on `explore/<topic>`, with its notes in `docs/explorations/<topic>.md`, both created by `/change`. Without them, stop and go to `/change`. If the notes hold more than what the user confirmed there, continue from them.
2. Read `docs/roadmap.md`, the index of `docs/requirements/` and the capabilities the idea touches.
3. Work it out one topic at a time: propose a concrete shape, give your recommendation, and ask only what the user has to decide. Keep the notes current as you go, the user's decisions in their words, so a new session can continue from them.
4. Look for more than one way to reach the goal, and compare them before settling on one: this is the stage for alternatives.
5. Suggest a throwaway experiment in a separate branch only when reading cannot answer a question, and run it only when the user wants it. Bring back findings, not code.
6. Write the result into `docs/roadmap.md`: one line of scope for each item that names the goal rather than a way to reach it, what is out of scope, the capabilities it touches, and the user's product decisions, including a way to reach the goal the user leans toward. Split a big idea into several items. Write only what the user proposed or approved: offer your own suggestions, and add one only with the user's word. Only the user promotes an idea or reorders items.
7. Delete the notes. Then one of two, as the user prefers: `/pr` for the roadmap, and later `/change` for an item they want to start; or, when the exploration was started to settle the approach of one change and the user wants to go on with it now, `/change` on this branch, which renames it.

Write no requirements, contract or code here.
