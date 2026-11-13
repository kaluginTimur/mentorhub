---
name: explore
description: >-
  Use this skill to turn a vague idea or an unsettled approach choice into items
  in docs/roadmap.md on an explore/ branch, without writing requirements.
  Activate only after the user confirmed the Explore path at the path gate of
  the change skill.
---

Explore the requested topic with the user, as stage 1 of the **Stages** section of `docs/process.md` describes.

1. Check the branch: an exploration runs on `explore/<topic>`, with its notes in `docs/explorations/<topic>.md`, both created by the `change` skill. Without them, stop and go to the `change` skill. If the notes hold more than what the user confirmed there, continue from them.
2. Read `docs/roadmap.md`, the index `docs/requirements/README.md`, and the capabilities the idea touches (`read_file`).
3. Work it out one topic at a time: propose a concrete shape, give your recommendation, and ask only what the user has to decide. Keep the notes current as you go (`edit_file`), with the user's decisions in their words, so a new session can continue from them.
4. Look for more than one way to reach the goal, and compare them before settling on one: this is the stage for alternatives.
5. Suggest a throwaway experiment in a separate branch only when reading cannot answer a question, and run it only when the user wants it. Bring back findings, not code.
6. Write the result into `docs/roadmap.md`: one line of scope for each item that names the goal rather than a way to reach it, what is out of scope, the capabilities it touches, and the user's product decisions, including a way to reach the goal the user leans toward. Split a big idea into several items. Write only what the user proposed or approved: offer your own suggestions, and add one only with the user's word. Only the user promotes an idea or reorders items.
7. Delete the notes. Then one of two, as the user prefers: use the `pr` skill for the roadmap, and later the `change` skill for an item they want to start; or, when the exploration was started to settle the approach of one change and the user wants to go on with it now, use the `change` skill on this branch, which renames it.

Write no requirements, contract or code here.
