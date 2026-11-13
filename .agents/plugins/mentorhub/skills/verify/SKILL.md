---
name: verify
description: Run the project's verification (backend, frontend, docs or all) in the verifier subagent and get a short pass/fail summary.
---

Run the project's verification using the `verifier` subagent. Scope: `<scope>` (`backend`, `frontend`, `docs`, or `all`; if omitted, use `all`). Git ref: `<ref>` (if omitted, none).

## Setup

If the `verifier` subagent is not yet registered in this session:

1. Read `.claude/agents/verifier.md` and strip its YAML frontmatter.
2. Register `verifier` with `define_subagent` per `.agents/plugins/mentorhub/rules/AGENTS.md` (`EnableWriteTools: true`, `EnableSubagentTools: false`, `EnableMCPTools: false`), appending the Antigravity tool mappings (`read_file`, `run_command`; never edit files) and noting that `backend/AGENTS.md` and `frontend/AGENTS.md` point to the area guides on demand.

## Run

Invoke `verifier` via `invoke_subagent` (`TypeName: "verifier"`, `Model: "inherit"`), passing the scope (`<scope>`) and optional git ref (`<ref>`), and instructing it to follow its procedure and return only the report. Relay the report to the user.
