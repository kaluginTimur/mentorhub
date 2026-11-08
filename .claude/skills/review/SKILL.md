---
name: review
description: Independent review of current changes, or of changed requirements, by the reviewer subagent.
argument-hint: "[git-ref | docs/plans/<change>.md | focus]"
disable-model-invocation: true
context: fork
agent: reviewer
---

Review target: `$ARGUMENTS`

Interpret the target:
- empty: uncommitted changes (`git diff HEAD` plus untracked files);
- a git ref (branch, tag or commit): `git diff <ref>...HEAD` plus uncommitted changes;
- a path under `docs/plans/`: requirements mode, with that plan;
- anything else: a focus or file list to narrow the review of uncommitted changes.

Follow your checklist and return only the report.
