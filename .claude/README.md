# Claude Code workflow

How this repository is set up for working with Claude Code: who does what, the commands, the guardrails, and what a new developer needs. The process itself, whatever the client, is in `docs/process.md`; this file maps it to Claude Code.

## Roles

The main session is the designer and the coordinator. Code and tests are written by subagents, each with its own context and a fixed job. Their definitions are in `agents/`. Subagents cannot ask you questions directly: they stop with a report, the main session shows it to you and passes your answer back to the same agent. The `permissionMode: acceptEdits` of `backend-dev`, `frontend-dev` and `test-engineer` applies only in Manual mode; in `auto` mode the classifier reviews subagent actions like any others and ignores that field.

## Flow

```
/explore <idea>     optional: the idea becomes items in docs/roadmap.md, no requirements
/spec <change>      main session + you: branch feature/<change>, plan docs/plans/<change>.md, edits in docs/requirements/
                    reviewer checks the changed requirements
                    ── requirements gate: you read them in full ──
/feature <change>   plan and design; backend-dev: contract and migration
                    ── plan gate: you read the plan and the diff ──
  test-engineer     red tests with ids → commit of requirements, contract, plan and tests, if you agree
  backend-dev       tasks of the plan, tests go green ── you check the backend ──
  frontend-dev      tasks of the plan, in parallel unless the plan orders them ── you look at the screens ──
                    the list of details from both → you decide
  test-engineer     tests of details, in a fresh context (skipped for small changes)
  reviewer on the whole change → you decide what to fix → one fix round → re-review
  verifier → summary, proposed commit message → you accept
  finish: durable "how" to decisions and guides; the plan and the roadmap item are deleted
/pr                 description from the changes and the final report → you approve → push, pull request

At any time:
/decision <title>   record or change an architecture decision
/usage-review       compare the recorded token usage of changes
```

Before every stop for you, `/spec` and `/feature` write a handover into the plan (**Stopping for the owner** in `docs/process.md`) and record the usage, so you can answer in the same session or run `/clear` first and then the command the handover names. Do that rather than `/compact`, and when Claude Code offers to resume an old session from a summary, start fresh from the plan instead.

You review diffs in your IDE; nothing is committed or pushed unless you agree. Do not switch branches while agents are running: background agents write to the same working tree.

Bugs, refactoring and small changes need no plan (**Choosing a path** in `docs/process.md`). Do quick fixes directly in the main session; for larger ones, ask the main session to brief `backend-dev` or `frontend-dev`. Finish with `/review` and `/verify`, and `/pr` when the change should go through a pull request.

## Guardrails

Hooks run in the main session and in every subagent. If a blocked action is really needed, run it yourself with the `!` prefix in the prompt: Claude Code runs that line for you, outside the model's tools, and the model has no way to type into your prompt. Inside the model's Bash tool a leading `!` is ordinary shell negation (`! cmd` still runs `cmd`), and hooks and deny rules still apply to it.

An action passes these layers in order:

1. **Hooks and deny/ask rules**, in every permission mode. They are deterministic but check the command text, so they stop mistakes, not a deliberate workaround: a wrapped command (`bash -c '...'`, a subprocess call) is still caught by the hook because the destructive text is in it, an obfuscated one (a decoded script piped into `sh`) is not.
2. **Allow rules** run without a further check, and so do reads; in `auto` mode, also file edits inside the repository except protected paths such as `.git` and `.claude`.
3. **Everything else** is decided by the permission mode:
   - `auto`, the default for interactive sessions since Claude Code 2.1.283: a separate classifier model reviews the action. By default it blocks discarding uncommitted work, force pushes, running downloaded or decoded code and similar actions. After 3 blocks in a row or 20 in a session it pauses and Claude Code asks you instead. This is a model's judgment, not a rule.
   - `default` (Manual): Claude Code asks you.
   - `bypassPermissions`: nothing checks it.

What must hold regardless of mode belongs in a hook or a deny/ask rule.

| Hook | Blocks or reports |
|---|---|
| `guard-files.mjs` | Edits to generated code (`frontend/src/app/core/api/`, `backend/target/`) and to migrations in main |
| `guard-bash.mjs` | What a prefix rule cannot express: `docker compose down -v`, `docker system prune --volumes`, `flyway clean`, destructive SQL in any case, `git reset --hard`, force push, `git clean -f`, discarding all changes, `git pull --rebase`, amend, deleting branches (flags in any position), mutating migrations in main |
| `check-frontend.mjs` | Reports `provideApiConfiguration('/')` and literal colors or fonts in components |

A hook is added only for an error that is silent (build and tests miss it), irreversible, or a project policy. Local machine quirks and compiler errors are not hook material.

Permissions in `settings.json`: the verification commands run without prompts, so the commands in the `bash` blocks of the guides' Verification and Checking an API sections must each match an allow rule, and a changed command changes its rule too (`scripts/check-docs.mjs` fails otherwise); `git commit`, `git push` and `docker compose down` always ask; reading `.env` files is denied. Destructive commands with a fixed prefix (`docker volume rm`/`prune`, `dropdb`, `git rebase`, `git filter-branch`, `git stash drop`/`clear`, `git reflog expire`, `git update-ref -d`) are denied there too: they need no code and hold even when a hook cannot run. Prefix rules only match the command as written, so everything that depends on flags or content stays in `guard-bash.mjs`.

## Cost and context

Every tool call re-sends the agent's whole context; prompt caching makes the re-read cheap (about 0.1× the input price) but not free, and an expired cache is written again at full size. The rules below come from measuring the first full `/feature` run (identity foundation), where `backend-dev` took about half of the Opus usage, the coordinator a third and the reviewers the rest.

| Rule | Why |
|---|---|
| `backend-dev` and `frontend-dev` use the 1-hour cache (`experimental.cacheTtl` in their definitions) | Subagents default to a 5-minute cache, and every review pause (7–38 minutes) expired it: each resume rewrote the agent's whole history, up to about 390k tokens. That was 38% of `backend-dev`'s cost; replayed with the 1-hour cache, it costs 29% less. |
| The other agents keep the 5-minute cache | They run once without pauses; the 1-hour cache only raises their write price (about +20% for `reviewer`). |
| A context budget of about 250k for `backend-dev`, `frontend-dev` and `test-engineer`: a `PostToolUse` hook in their definitions (`.claude/hooks/context-budget.mjs`) tells the agent to finish its step and stop with a PARTIAL report and a handover, and a fresh agent continues. Between tasks, a fresh agent above about 150k; one task per brief | About half of a long-running agent's context is earlier reasoning; every call re-reads it. A check between briefs alone did not hold: in the sample center, T4 and T6 in one brief took one agent to 194k. The hook acts within the agent's turn, after each tool result, but cannot interrupt a running tool; agents follow it only because their definitions say it is legitimate, since a message after a tool result otherwise reads as an injection. |
| Contract and migration are one checkpoint | Two small steps, one pause and one resume instead of two. |
| A handover in the plan before every stop for you, `/clear` instead of `/compact`, idle compaction off (`settings.json`) | The coordinator re-read an average 178k context on 143 calls, and a review that outlasts the cache writes the whole history again. The plan restores a new session exactly; a compaction summary is lossy, and idle compaction would compact during a long review. |
| All review findings at once, one fix round, re-reviews on Sonnet limited to the previous findings and the fix diff | Each full review is 70–200k tokens on Opus; the frontend took four. |
| Area guides are not read twice | `backend/CLAUDE.md` and `frontend/CLAUDE.md` already load them. |
| `/spec` proposes splitting large features | A large task's own work, such as identity foundation's step 3 (+140k), is not reduced by any rule above. |
| In a cloud session, `/feature` checks an agent's transcript folder before continuing it, and starts a fresh one otherwise | After a `/clear` there, an agent started meanwhile and later continued ran as `general-purpose`: without its model and 1-hour cache (invitation-emails: `frontend-dev` on Opus, two whole-cache rewrites). |
| Subagents without a tool to start agents (cloud sessions) ask their `api-lookup` questions in the report | The coordinator asks `api-lookup`; probing APIs by compiling costs the implementation agent's context. |
| Manual browser checks run on Haiku, and `/pr` does not watch a green pull request | A clear test case needs no stronger model (invitation-emails AC-19 ran on inherited Opus, 0.42M); every pull request event wakes the session and re-reads its whole context. |
| `/spec` records its own `spec` phase | Otherwise its work lands in the next phase's record (invitation-emails: about 1M). |

### Measuring a session

```bash
node .claude/scripts/agent-usage.mjs [<session-id>]
```

It prints, per agent: the model, the effort its requests ran at (subagents may differ from the session: identity-foundation ran its Sonnet agents at `medium` in a `high` session), calls, average and last context size, cache reads and writes (5-minute and 1-hour), a weighted total (input-token equivalents at API price ratios) and the rebuilds after expired caches with their share of the cost. Without an id it reads the latest session of this repository, including sessions in its worktrees; a `<session>.jsonl` path works too. Transcripts stay on the machine that ran the session (under `~/.claude/projects/`, or `CLAUDE_CONFIG_DIR`) and Claude Code deletes them after 30 days by default, so what matters is recorded. Subagent transcripts do not record output tokens reliably, so the weighted totals leave output out; the main session's output is printed and recorded on its own (`output`), since effort mostly changes output and thinking. `/usage` shows the main session's cache hit ratio.

### Recording usage

Before every stop for you, `/spec` and `/feature` record the session in the repository:

```bash
node .claude/scripts/agent-usage.mjs --record <change> <phase>
```

It appends one line per agent and the change's size (inserted lines against the default branch, scenarios added or rewritten under `docs/requirements/`; the old features have acceptance criteria as `acs`) to `usage/<change>.jsonl` and prints one line per session it recorded. Claude Code can continue a conversation in a new session id without a restart; the script follows these continuations and records the whole chain once, so run it without a session id. In a cloud session `/clear` keeps the session id, so one session can hold several phases: a record covers only the API calls after the session's latest record of another phase, and replaces an earlier record of the same phase (as `/pr` does for `review`). The local session files a cloud `/clear` leaves behind, whose calls the cloud transcript repeats, are read with it, since subagents started meanwhile can be only there. `--until <time>` ends the window earlier, to rebuild a missed record. It refuses a chain none of whose agents in the window is in the Agents table of the plan, one that ended before another recorded session of the feature, or (without `--until`) a phase recorded again after a later phase; `--force` overrides after checking. The phases are `spec` (recorded by `/spec`), `plan`, `implementation` and `review`; the old features have `contract` instead of `plan`. A phase can span sessions, with one record per session. An agent spawned by another agent has its own line with `spawnedBy`, the top-level agent it works for, so its cost can be rolled up to that agent. `inspections` counts an agent's tool calls into dependency artifacts (`javap`, `unzip -l`, `jar tf`, `~/.m2`, `node_modules`), the work implementation agents leave to `api-lookup`. It reads the Agents table from the plan in the working tree or its history, so it also works after the plan is deleted. The record goes in with the next commit. `/usage-review` compares the records with the baseline.

### Baseline: identity foundation

One session, before these rules; record in `usage/identity-foundation.jsonl` (phase `all`). 134 files, 6,651 inserted lines, 30 acceptance criteria. Opus agents ran at `high` effort, Sonnet agents at `medium`; the coordinator wrote 123k output tokens (862 per call), not part of the weighted totals.

| Agent | Model | Calls | Avg context | Weighted | Rebuild share |
|---|---|---|---|---|---|
| coordinator | Opus | 143 | 178k | 3.43M | 14% |
| `backend-dev` | Opus | 135 | 239k | 5.66M | 38% |
| `reviewer`, 5 runs | Opus | 162 | 47–120k | 1.97M | 0% |
| `frontend-dev` | Sonnet | 53 | 113k | 1.52M | 54% |
| `verifier`, 2 runs | Sonnet | 10 | 10k | 0.04M | 0% |

Opus 11.07M in total: about 1.7k per inserted line and 370k per acceptance criterion.

## Setup for a new developer

- Enable the git hooks: `git config core.hooksPath .githooks`.
- Start Claude Code in the repository root and accept the workspace trust dialog once. Until the folder is trusted, Claude Code ignores the allow rules in `settings.json`: in `auto` mode the classifier then reviews even the verification commands, and in Manual mode or `-p` runs agents stall on approvals.
- Requirements from `README.md` (JDK 25, Docker, Node via nvm). The hooks need `node` on `PATH`.
- Make the default Node satisfy `frontend/.nvmrc` (`nvm alias default 24`): Claude Code starts the Angular CLI MCP server from `.mcp.json` with the default Node, and it needs ^24.15.
- Personal overrides go into `settings.local.json` (gitignored).

## Changing the workflow

- Agents and skills describe roles, processes, checkpoints and report formats, and point to `docs/` for the stack: when the stack changes, update `docs/development/`. Hooks and `settings.json` enforce what must hold.
- Test a changed agent or skill in a headless run from a clone, for example `claude -p "/verify all"` or `claude -p "<question>" --agent api-lookup`. Keep the clone outside `~/.claude`, which Claude Code treats as protected, and remember that an untrusted clone ignores the allow rules.
- Test a hook by piping a JSON event into it: `echo '{"tool_name":"Bash","tool_input":{"command":"..."},"cwd":"..."}' | node .claude/hooks/guard-bash.mjs`.
