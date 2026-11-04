---
name: usage-review
description: Analyse the recorded token usage of changes against the identity foundation baseline and the last features of the old process, check that each workflow rule took effect, and propose further optimizations, separating those that are quality-neutral from those that could harm the quality of the workflow.
argument-hint: "[change ...]"
disable-model-invocation: true
---

This skill measures what the workflow costs. It first evaluated the optimizations of 2026-10, and now measures the spec-anchored process of `docs/process.md`.

Analyse the usage of the changes in `$ARGUMENTS`, or of every change with a record in `.claude/usage/` when empty. Identity foundation is the baseline, in one session and before the optimizations. Identity foundation, user administration, invitations, invitation links and invitation emails ran the old process: spec, then fixed backend steps, with the size in acceptance criteria (`acs`). A later change has `scenarios` instead, so compare per inserted line across the two, and treat per-scenario and per-criterion figures as different units.

## Inputs

- `.claude/usage/<change>.jsonl`: one line per agent per recorded session (`type: agent`) and the change's size at that point (`type: size`), as the **Recording usage** section of `.claude/README.md` describes. The latest `size` line of a change is its size.
- The plan's last version in git (`git log -p -- docs/plans/<change>.md`, since the plan is deleted before the pull request): decisions, handovers, agents and review rounds. This is the quality evidence. For the old features it is `docs/features/<feature>.progress.md` in the history.
- The rules and their reasons: the **Cost and context** section of `.claude/README.md`.

If a feature has no record for one of its phases, say so; never estimate the missing part.

## 1. Did each technique take effect

For every feature after the baseline, check and report yes, no or no data:

| Technique | Evidence |
|---|---|
| 1-hour cache on `backend-dev` and `frontend-dev` | their `write1h` is most of their writes |
| No cache rebuilds after review pauses | their `rebuilds` is 0, or each rebuild matches a pause the plan explains |
| Fresh `backend-dev` above about 150k | no `backend-dev` record's `lastContext` is far above it; the plan's Agents table shows the new agents |
| Continuing from a handover | a stop for the user followed by a long pause shows a new session, not a rebuild of the coordinator; the new session's first calls have a small context |
| `test-engineer` model | Opus on Tracking core: its share of the total and the rework its tests caused, compared with what Sonnet would cost at the same calls |
| Cheap re-reviews | re-review `reviewer` records run on Sonnet and are much smaller than full reviews |
| One fix round per review | the plan shows one fix round after each review |
| API checks delegated to `api-lookup` | `backend-dev` and `frontend-dev` records have `inspections` 0, and there are `api-lookup` records with their `spawnedBy`; the reports' "API lookups" sections in the plan or the transcripts name what was asked. Records made before `inspections` existed (all of identity-foundation, user-administration and invitations) lack it |

A technique that did not take effect is the first finding: fix the mechanism before judging its effect.

## 2. Trend against the baseline

Per feature, in one table with the baseline as the first row:
- the effort each agent type ran at (`effort` in the records);
- Opus and Sonnet weighted totals, and Opus weighted per inserted line, and per scenario or acceptance criterion;
- the coordinator's output tokens per call (`output` on the `main` records; subagent output is not recorded);
- per agent type: calls, average context per call, rebuild share (rebuild tokens at their write price over the weighted total). A record with `spawnedBy` is a nested agent (such as `api-lookup` started by `backend-dev`): count its cost toward the `spawnedBy` agent type and name it next to that type's figures;
- the share of each agent type in the Opus total.

Average context per call and rebuild share barely depend on feature size; compare totals only through the per-line and per-scenario figures. Effort changes thinking and output, and through thinking kept in an agent's context also calls and context size, so it confounds every metric: compare features at the same effort, or name the difference next to each figure it may explain and never attribute that part to a workflow technique. Records made before effort was recorded were backfilled from the transcripts: identity-foundation ran Opus agents at `high` and Sonnet agents at `medium`, user-administration everything at `medium`. One feature is an observation, not a trend: call something a trend only when at least two features after the baseline agree, and give the spread.

## 3. Quality signals

From each change's plan, against the baseline log:
- feedback rounds per checkpoint, and what they were about;
- questions in a fresh agent's report that an earlier decision or handover already answered (a sign that the handover is thin);
- review findings by severity at the final review, and the number of fix rounds;
- deviations from the plan and the reasons;
- how many items of the list of details the user rejected, and whether they became requirements (often means requirements are too thin);
- real bugs found by the second test pass (`docs/process.md` removes the pass if it finds none on Tracking core and the next feature);
- wrong tests the developers reported, and tests with ids changed after the red commit;
- scenarios that got in the way of a change and caught nothing;
- what the user said about control with fewer checkpoints, if anything.

## 4. Proposals

Find the largest remaining costs (agent type, phase, rebuilds, calls) and propose changes for them, in two separate lists:

- **Quality-neutral:** changes that alter how context is stored, carried or re-read, but not what an agent knows, decides or checks. Examples: cache lifetime, session and agent boundaries backed by the log, duplicate reads, the size of tool output, the model for mechanical checks.
- **Quality-risking:** changes that reduce what an agent knows, how hard it thinks or how much is checked. Examples: fewer review passes, fewer checkpoints, lower effort, a cheaper model for an implementation agent, shorter briefs, a different fresh-agent threshold. For each one, give the expected saving, the quality signal from section 3 that would show harm, and what the data so far says about it. These are the user's decisions.

Also name any existing rule whose cost the data does not justify, or whose quality signal shows harm, with the evidence, and propose to relax or revert it.

Every proposal names the file it would change (an agent, a skill, the template, `settings.json`) and the expected effect in the same units as section 2. Do not change any file: this skill only reports.

## Report

```
## Usage review: <changes>

### Techniques in effect
<table from section 1>

### Trend
<table from section 2>, then 2-4 lines on what it shows

### Quality
<the signals from section 3, per change, compared with the baseline>

### Proposals
Quality-neutral:
1. <change>: <file>; <expected effect>; <evidence>
Quality-risking (your decision):
1. <change>: <file>; <expected saving>; <harm signal>; <evidence so far>
```

A conclusion that has held over several changes belongs in the **Cost and context** section of `.claude/README.md`: propose moving it there. The user decides when this skill has done its job.
