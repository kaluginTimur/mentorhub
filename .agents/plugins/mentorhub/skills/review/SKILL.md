---
name: review
description: Independent review of current changes, or of changed requirements, by the reviewer subagent.
---

Run an independent review using the `reviewer` subagent.

## Setup

If the `reviewer` subagent is not yet registered in this session:

1. Read `.claude/agents/reviewer.md` and strip its YAML frontmatter.
2. Register `reviewer` with `define_subagent` per `.agents/plugins/mentorhub/rules/AGENTS.md` (`EnableWriteTools: true`, `EnableSubagentTools: false`, `EnableMCPTools: false`), appending the Antigravity tool mappings (`read_file`, `run_command` read-only only; never edit files) and noting that `backend/AGENTS.md` and `frontend/AGENTS.md` point to the area guides on demand.

## Target

Interpret the requested review target (`<target>`):

- empty: uncommitted changes (`git diff HEAD` plus untracked files);
- a git ref (branch, tag or commit): `git diff <ref>...HEAD` plus uncommitted changes;
- a path under `docs/plans/`: on a `feature/` branch it is a plan, so requirements mode (`requirements` and that plan's path); on any other branch it is a brief, so `git diff main...HEAD` plus uncommitted changes, with that brief;
- anything else: a focus or file list to narrow the review of uncommitted changes.

## Run

Invoke `reviewer` via `invoke_subagent` (`TypeName: "reviewer"`, `Model: "pro"` or `"inherit"`) with the interpreted target, instructing it to follow its checklist and return only the report. Relay the report to the user.
