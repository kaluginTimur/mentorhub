# Claude Code workflow

How this repository is set up for working with Claude Code: who does what, the commands, the guardrails, and what a new developer needs.

## Roles

The main session designs and coordinates. Feature code is written by subagents, each with its own context and a fixed job.

| Agent | Job | Model |
|---|---|---|
| `backend-dev` | Implements backend work one step at a time and stops with a checkpoint report after each step | opus |
| `frontend-dev` | Builds a screen prototype for visual approval, then finishes the frontend on its own until verification is green | sonnet |
| `reviewer` | Independent review of a diff or a spec against the project rules; never edits | opus |
| `verifier` | Runs the verification and reports pass/fail; never fixes | sonnet |
| `api-lookup` | Checks that a class, method or export exists in the exact dependency versions the project uses | haiku |

Definitions are in `agents/`. Subagents cannot ask you questions directly: they stop with a report, the main session shows it to you and passes your answer back to the same agent. The `permissionMode: acceptEdits` of `backend-dev` and `frontend-dev` applies only in Manual mode; in `auto` mode the classifier reviews subagent actions like any others and ignores that field.

## Feature flow

```
/spec <feature>     main session + you: docs/features/<feature>.md (requirements, UI, design)
                    reviewer checks the spec; you approve it
/feature <feature>  progress log docs/features/<feature>.progress.md, branch feature/<feature>
  backend-dev   1 contract + 2 migration → you approve (→ commit, if you agree)
  ── phase done: /clear, then /feature <feature> ──
  frontend-dev  A prototype on mock data → you approve the screens (skipped until mock mode exists)
                B finishes on its own → reviewer → you decide what to fix → one fix round → re-review
  backend-dev   3 domain, service, authorization → you approve      (in parallel with the frontend)
                4 API layer and tests → you approve
  ── phase done: /clear, then /feature <feature> ──
  reviewer on the whole feature → you decide what to fix → one fix round → re-review
  verifier → summary, AC coverage, proposed commit message
  on acceptance: the spec's Design section is synced with the final contract and schema, status → implemented
/pr                 description from the spec and the final report → you approve → push, pull request
```

`/feature` works on a branch `feature/<feature>` and keeps a progress log next to the spec: step statuses, your decisions at each checkpoint, open questions and step commits. When you resume in a new session, the log, not the conversation or `git diff`, tells where the work stopped; if the log is on another branch, `/feature` says so instead of starting over. You review each backend step's diff in your IDE; after you approve a step, it offers to commit it. Nothing is committed or pushed unless you agree. Do not switch branches while agents are running: background agents write to the same working tree.

Each phase of a feature runs in its own session. When a phase is approved and committed, `/feature` tells you to run `/clear` and then `/feature <feature>`: the new session continues from the log. Do that rather than `/compact`, and when Claude Code offers to resume an old feature session from a summary, start fresh from the log instead; a summary may drop details the log keeps.

## Commands

| Command | What it does |
|---|---|
| `/spec <feature>` | Work out a feature spec with Claude |
| `/feature <feature> [step]` | Implement an approved spec; resumes from the progress log, `step` overrides where to continue (`contract`, `migration`, `domain`, `api`, `frontend`, `review`, `verify`) |
| `/review [git-ref \| spec path \| focus]` | Independent review of current changes, or of a spec |
| `/verify [backend\|frontend\|docs\|all] [git-ref]` | Run the verification and get a pass/fail table; `docs` with a ref lists documents to recheck since that ref |
| `/adr <title>` | Record an architecture decision in `docs/adr/` |
| `/pr [base-branch]` | Open a GitHub pull request: checks, a generated description for your approval, push and `gh pr create` |
| `/usage-review [feature ...]` | Temporary: compare the recorded token usage of features with the baseline, check that each optimization took effect, propose further ones split into quality-neutral and quality-risking |

Bugs, refactoring and small changes need no spec. Do quick fixes directly in the main session; for larger ones, ask the main session to brief `backend-dev` or `frontend-dev`. Finish with `/review` and `/verify`, and `/pr` when the change should go through a pull request.

## Where knowledge lives

| Place | Contents |
|---|---|
| `AGENTS.md` | Map and project-wide rules, shared with other AI clients; `CLAUDE.md` only imports it. Loaded into every session, so keep it short |
| `docs/development/<area>.md` | Stack, conventions, commands, verification, pitfalls; loaded when Claude works in `backend/` or `frontend/` (through the one-line `CLAUDE.md` there) |
| `docs/architecture/invariants.md` | Domain model, authorization, visibility |
| `docs/adr/`, `docs/features/` | Decisions and feature specs |
| `docs/development/docs.md` | How references between documents, artifacts and code are kept consistent |
| `agents/`, `skills/` | Roles, processes, checkpoints and report formats only. No commands, versions or class names: they point to the documents above |
| `hooks/`, `settings.json` | Enforcement, the only place with concrete paths and commands |
| `scripts/`, `usage/` | Tooling for the workflow itself and the recorded token usage per feature |

When the stack changes, update `docs/development/`; agents and skills stay as they are. `scripts/check-docs.mjs` runs on every commit through the git hooks in `.githooks/`, in `/verify docs` and in reviews: it blocks broken references (paths, sections agents rely on, Verification commands without an allow rule, paths the hooks protect) and lists documents that refer to changed files.

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
| `guard-files.mjs` | Edits to generated code (`frontend/src/app/core/api/`, `backend/target/`) and to migrations that are committed or recorded in the dev database |
| `guard-bash.mjs` | What a prefix rule cannot express: `docker compose down -v`, `docker system prune --volumes`, `flyway clean`, destructive SQL in any case, `git reset --hard`, force push, `git clean -f`, discarding all changes, `git pull --rebase`, amend, deleting branches (flags in any position), mutating applied migrations |
| `check-frontend.mjs` | Reports `provideApiConfiguration('/')` and literal colors or fonts in components |

A hook is added only for an error that is silent (build and tests miss it), irreversible, or a project policy. Local machine quirks and compiler errors are not hook material.

Permissions in `settings.json`: the verification commands run without prompts; `git commit`, `git push` and `docker compose down` always ask; reading `.env` files is denied. Destructive commands with a fixed prefix (`docker volume rm`/`prune`, `dropdb`, `git rebase`, `git filter-branch`, `git stash drop`/`clear`, `git reflog expire`, `git update-ref -d`) are denied there too: they need no code and hold even when a hook cannot run. Prefix rules only match the command as written, so everything that depends on flags or content stays in `guard-bash.mjs`.

## Cost and context

Every tool call re-sends the agent's whole context; prompt caching makes the re-read cheap (about 0.1× the input price) but not free, and an expired cache is written again at full size. The rules below come from measuring the first full `/feature` run (identity foundation), where `backend-dev` took about half of the Opus usage, the coordinator a third and the reviewers the rest.

| Rule | Why |
|---|---|
| `backend-dev` and `frontend-dev` use the 1-hour cache (`experimental.cacheTtl` in their definitions) | Subagents default to a 5-minute cache, and every review pause (7–38 minutes) expired it: each resume rewrote the agent's whole history, up to about 390k tokens. That was 38% of `backend-dev`'s cost; replayed with the 1-hour cache, it costs 29% less. |
| The other agents keep the 5-minute cache | They run once without pauses; the 1-hour cache only raises their write price (about +20% for `reviewer`). |
| A fresh `backend-dev` between steps once its context passes about 150k | About half of a long-running agent's context is earlier reasoning; every call re-reads it. The checkpoint report's "Notes for the next step" and the log carry what the next step needs. |
| Contract and migration are one checkpoint | Two small steps, one pause and one resume instead of two. |
| One session per phase, `/clear` instead of `/compact`, idle compaction off (`settings.json`) | The coordinator re-read an average 178k context on 143 calls. The log restores a new session exactly; a compaction summary is lossy, and idle compaction would compact during a long review. |
| All review findings at once, one fix round, re-reviews on Sonnet limited to the previous findings and the fix diff | Each full review is 70–200k tokens on Opus; the frontend took four. |
| Area guides are not read twice | `backend/CLAUDE.md` and `frontend/CLAUDE.md` already load them. |
| `/spec` proposes splitting large features | A step's own work, such as identity foundation's step 3 (+140k), is not reduced by any rule above. |
| In a cloud session, `/feature` checks an agent's transcript folder before continuing it, and starts a fresh one otherwise | After a `/clear` there, an agent started meanwhile and later continued ran as `general-purpose`: without its model and 1-hour cache (invitation-emails: `frontend-dev` on Opus, two whole-cache rewrites). |
| Subagents without a tool to start agents (cloud sessions) ask their `api-lookup` questions in the report | The coordinator asks `api-lookup`; probing APIs by compiling costs the implementation agent's context. |
| Manual browser checks run on Haiku, and `/pr` does not watch a green pull request | A clear test case needs no stronger model (invitation-emails AC-19 ran on inherited Opus, 0.42M); every pull request event wakes the session and re-reads its whole context. |
| `/spec` records its own `spec` phase | Otherwise the spec's work lands in the feature's `contract` record (invitation-emails: about 1M). |

### Measuring a session

```bash
node .claude/scripts/agent-usage.mjs [<session-id>]
```

It prints, per agent: the model, the effort its requests ran at (subagents may differ from the session: identity-foundation ran its Sonnet agents at `medium` in a `high` session), calls, average and last context size, cache reads and writes (5-minute and 1-hour), a weighted total (input-token equivalents at API price ratios) and the rebuilds after expired caches with their share of the cost. Without an id it reads the latest session of this repository, including sessions in its worktrees; a `<session>.jsonl` path works too. Transcripts stay on the machine that ran the session (under `~/.claude/projects/`, or `CLAUDE_CONFIG_DIR`) and Claude Code deletes them after 30 days by default, so what matters is recorded. Subagent transcripts do not record output tokens reliably, so the weighted totals leave output out; the main session's output is printed and recorded on its own (`output`), since effort mostly changes output and thinking. `/usage` shows the main session's cache hit ratio.

### Recording usage

At the end of each phase of a feature, `/feature` records the session in the repository:

```bash
node .claude/scripts/agent-usage.mjs --record <feature> <phase>
```

It appends one line per agent and the feature's size (inserted lines against the default branch, acceptance criteria in the spec) to `usage/<feature>.jsonl` and prints one line per session it recorded. Claude Code can continue a conversation in a new session id without a restart; the script follows these continuations and records the whole chain once, so run it without a session id. In a cloud session `/clear` keeps the session id, so one session can hold several phases: a record covers only the API calls after the session's latest record of another phase, and replaces an earlier record of the same phase (as `/pr` does for `review`). The local session files a cloud `/clear` leaves behind, whose calls the cloud transcript repeats, are read with it, since subagents started meanwhile can be only there. `--until <time>` ends the window earlier, to rebuild a missed record. It refuses a chain none of whose agents in the window is in the progress log's Agents table, one that ended before another recorded session of the feature, or (without `--until`) a phase recorded again after a later phase; `--force` overrides after checking. The phases are `spec` (recorded by `/spec`), `contract`, `implementation` and `review`. An agent spawned by another agent has its own line with `spawnedBy`, the top-level agent it works for, so its cost can be rolled up to that agent. `inspections` counts an agent's tool calls into dependency artifacts (`javap`, `unzip -l`, `jar tf`, `~/.m2`, `node_modules`), the work implementation agents leave to `api-lookup`. It reads the Agents table from the progress log committed on the feature branch, so the log is committed first and the record follows in its own commit. `/usage-review` compares the records with the baseline.

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

## Other AI clients

Everything in `.claude/` (agents, skills, hooks, permissions, MCP config) is written for Claude Code. What other clients share without copying:

- `AGENTS.md`: the project rules; Cursor, OpenCode and Codex read it directly;
- `docs/`: stack, conventions, verification, invariants, decisions and specs;
- the git hooks in `.githooks/` and `scripts/check-docs.mjs`.

To bring the workflow to another client, port the processes from `agents/` and `skills/` to that client's mechanisms (subagents, commands, hooks). They are short descriptions of roles and steps; the knowledge they rely on stays in `docs/` and `AGENTS.md`.

## Changing the workflow

- Test a changed agent or skill in a headless run from a clone, for example `claude -p "/verify all"` or `claude -p "<question>" --agent api-lookup`. Keep the clone outside `~/.claude`, which Claude Code treats as protected, and remember that an untrusted clone ignores the allow rules.
- Test a hook by piping a JSON event into it: `echo '{"tool_name":"Bash","tool_input":{"command":"..."},"cwd":"..."}' | node .claude/hooks/guard-bash.mjs`.
