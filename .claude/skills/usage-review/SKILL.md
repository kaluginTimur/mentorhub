---
name: usage-review
description: Temporary. Analyse the recorded token usage of features against the identity foundation baseline, check that each workflow optimization took effect, and propose further optimizations, separating those that are quality-neutral from those that could harm the quality of the workflow.
argument-hint: "[feature ...]"
disable-model-invocation: true
---

Temporary skill: it exists to evaluate the workflow optimizations of 2026-10 over the next 2 to 4 features after identity foundation. When its exit condition below is met, it is deleted.

Analyse the usage of the features in `$ARGUMENTS`, or of every feature with a record in `.claude/usage/` when empty. Identity foundation is the baseline: it ran before the optimizations, in one session.

## Inputs

- `.claude/usage/<feature>.jsonl`: one line per agent per recorded session (`type: agent`) and the feature's size at that point (`type: size`), as the **Recording usage** section of `.claude/README.md` describes. The latest `size` line of a feature is its size.
- `docs/features/<feature>.progress.md` on the branch where it lives (`git show <ref>:<path>` when it is not on the current branch): decisions, handoff notes, agents and review rounds. This is the quality evidence.
- The rules and their reasons: the **Cost and context** section of `.claude/README.md`.

If a feature has no record for one of its phases, say so; never estimate the missing part.

## 1. Did each technique take effect

For every feature after the baseline, check and report yes, no or no data:

| Technique | Evidence |
|---|---|
| 1-hour cache on `backend-dev` and `frontend-dev` | their `write1h` is most of their writes |
| No cache rebuilds after review pauses | their `rebuilds` is 0, or each rebuild matches a pause the log explains |
| Fresh `backend-dev` above about 150k | no `backend-dev` record's `lastContext` is far above it; the log's Agents table shows the new agents |
| One session per phase | one record per phase, each with its own session |
| Cheap re-reviews | re-review `reviewer` records run on Sonnet and are much smaller than full reviews |
| Step 4 on Sonnet (experiment) | the step 4 `backend-dev` record runs on Sonnet; compare its feedback rounds and the final review's findings in controllers, mapping and error rendering with user-administration (one cosmetic round, none) |
| One fix round per review | the log shows one fix round after each review |
| API checks delegated to `api-lookup` | `backend-dev` and `frontend-dev` records have `inspections` 0, and there are `api-lookup` records with their `spawnedBy`; the reports' "API lookups" sections in the log or the transcripts name what was asked. Records made before `inspections` existed (all of identity-foundation, user-administration and invitations) lack it |

A technique that did not take effect is the first finding: fix the mechanism before judging its effect.

## 2. Trend against the baseline

Per feature, in one table with the baseline as the first row:
- the effort each agent type ran at (`effort` in the records);
- Opus and Sonnet weighted totals, and Opus weighted per inserted line and per acceptance criterion;
- the coordinator's output tokens per call (`output` on the `main` records; subagent output is not recorded);
- per agent type: calls, average context per call, rebuild share (rebuild tokens at their write price over the weighted total). A record with `spawnedBy` is a nested agent (such as `api-lookup` started by `backend-dev`): count its cost toward the `spawnedBy` agent type and name it next to that type's figures;
- the share of each agent type in the Opus total.

Average context per call and rebuild share barely depend on feature size; compare totals only through the per-line and per-AC figures. Effort changes thinking and output, and through thinking kept in an agent's context also calls and context size, so it confounds every metric: compare features at the same effort, or name the difference next to each figure it may explain and never attribute that part to a workflow technique. Records made before effort was recorded were backfilled from the transcripts: identity-foundation ran Opus agents at `high` and Sonnet agents at `medium`, user-administration everything at `medium`. One feature is an observation, not a trend: call something a trend only when at least two features after the baseline agree, and give the spread.

## 3. Quality signals

From each feature's progress log, against the baseline log:
- feedback rounds per backend checkpoint, and what they were about;
- questions in a fresh agent's report that an earlier decision or handoff note already answered (a sign that the handoff is thin);
- review findings by severity at the first frontend review and at the final review, and the number of fix rounds;
- deviations from the spec and the reasons;
- what the user said about control or comfort with merged checkpoints, if anything.

## 4. Proposals

Find the largest remaining costs (agent type, phase, rebuilds, calls) and propose changes for them, in two separate lists:

- **Quality-neutral:** changes that alter how context is stored, carried or re-read, but not what an agent knows, decides or checks. Examples: cache lifetime, session and agent boundaries backed by the log, duplicate reads, the size of tool output, the model for mechanical checks.
- **Quality-risking:** changes that reduce what an agent knows, how hard it thinks or how much is checked. Examples: fewer review passes, more merged checkpoints, lower effort, a cheaper model for an implementation agent, shorter briefs, a different fresh-agent threshold. For each one, give the expected saving, the quality signal from section 3 that would show harm, and what the data so far says about it. These are the user's decisions.

Also name any existing rule whose cost the data does not justify, or whose quality signal shows harm, with the evidence, and propose to relax or revert it.

Every proposal names the file it would change (an agent, a skill, the template, `settings.json`) and the expected effect in the same units as section 2. Do not change any file: this skill only reports.

## Report

```
## Usage review: <features>

### Techniques in effect
<table from section 1>

### Trend
<table from section 2>, then 2-4 lines on what it shows

### Quality
<the signals from section 3, per feature, compared with the baseline>

### Proposals
Quality-neutral:
1. <change>: <file>; <expected effect>; <evidence>
Quality-risking (your decision):
1. <change>: <file>; <expected saving>; <harm signal>; <evidence so far>

### Exit condition
<met or not, and why>
```

## Exit condition

The evaluation ends when four features after the baseline are recorded, or earlier when three agree on every metric in section 2 within about 20%. Then propose: move the final trend table and the conclusions into the **Cost and context** section of `.claude/README.md`, delete this skill and its row in the README's command table, and keep recording usage. Recording is permanent; this skill is not.
