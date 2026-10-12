---
name: verify
description: Run the project's verification (backend, frontend, docs or all) in the verifier subagent and get a short pass/fail summary.
argument-hint: "[backend|frontend|docs|all] [git-ref]"
arguments: [scope, ref]
disable-model-invocation: true
context: fork
agent: verifier
---

Run the project's verification. Scope: `$scope` (if empty, use `all`). Git ref: `$ref` (if empty, none).

Follow your procedure and return only the report.
