# Claude Code workflow

How this repository is set up for working with Claude Code: who does what, the commands, the guardrails, and what a new developer needs.

## Roles

The main session designs and coordinates. Feature code is written by subagents, each with a fresh context and a fixed job.

| Agent | Job | Model |
|---|---|---|
| `backend-dev` | Implements backend work one step at a time and stops with a checkpoint report after each step | opus |
| `frontend-dev` | Builds a screen prototype for visual approval, then finishes the frontend on its own until verification is green | sonnet |
| `reviewer` | Independent review of a diff or a spec against the project rules; never edits | opus |
| `verifier` | Runs the verification and reports pass/fail; never fixes | sonnet |
| `api-lookup` | Checks that a class, method or export exists in the exact dependency versions the project uses | haiku |

Definitions are in `agents/`. Subagents cannot ask you questions directly: they stop with a report, the main session shows it to you and passes your answer back to the same agent.

## Feature flow

```
/spec <feature>     main session + you: docs/features/<feature>.md (requirements, UI, design)
                    reviewer checks the spec; you approve it
/feature <feature>
  backend-dev   1 contract → you approve
  frontend-dev  A prototype on mock data → you approve the screens (skipped until mock mode exists)
                B finishes on its own → reviewer → fixes
  backend-dev   2 migration → you approve
                3 domain, service, authorization → you approve
                4 API layer and tests → you approve
  reviewer on the whole feature → you decide what to fix
  verifier → summary, AC coverage, proposed commit message
  on acceptance: the spec's Design section is synced with the final contract and schema, status → implemented
```

You review each backend step's diff in your IDE. Nothing is committed unless you ask.

## Commands

| Command | What it does |
|---|---|
| `/spec <feature>` | Work out a feature spec with Claude |
| `/feature <feature> [step]` | Implement an approved spec; `step` resumes after an interruption (`contract`, `migration`, `domain`, `api`, `frontend`, `review`, `verify`) |
| `/review [git-ref \| spec path \| focus]` | Independent review of current changes, or of a spec |
| `/verify [backend\|frontend\|docs\|all] [git-ref]` | Run the verification and get a pass/fail table; `docs` with a ref lists documents to recheck since that ref |
| `/adr <title>` | Record an architecture decision in `docs/adr/` |

Bugs, refactoring and small changes need no spec. Do quick fixes directly in the main session; for larger ones, ask the main session to brief `backend-dev` or `frontend-dev`. Finish with `/review` and `/verify`.

## Where knowledge lives

| Place | Contents |
|---|---|
| `CLAUDE.md` | Map and project-wide rules; loaded into every session, so keep it short |
| `docs/development/<area>.md` | Stack, conventions, commands, verification, pitfalls; loaded when Claude works in `backend/` or `frontend/` (through the one-line `CLAUDE.md` there) |
| `docs/architecture/invariants.md` | Domain model, authorization, visibility |
| `docs/adr/`, `docs/features/` | Decisions and feature specs |
| `docs/development/docs.md` | How references between documents, artifacts and code are kept consistent |
| `agents/`, `skills/` | Roles, processes, checkpoints and report formats only. No commands, versions or class names: they point to the documents above |
| `hooks/`, `settings.json` | Enforcement, the only place with concrete paths and commands |

When the stack changes, update `docs/development/`; agents and skills stay as they are. `scripts/check-docs.mjs` runs on every commit through the git hooks in `.githooks/`, in `/verify docs` and in reviews: it blocks broken references (paths, sections agents rely on, Verification commands without an allow rule, paths the hooks protect) and lists documents that refer to changed files.

## Guardrails

Hooks run in the main session and in every subagent. A blocked action can only be run by you, manually, with the `!` prefix in the prompt.

| Hook | Blocks or reports |
|---|---|
| `guard-files.mjs` | Edits to generated code (`frontend/src/app/core/api/`, `backend/target/`) and to migrations that are committed or recorded in the dev database |
| `guard-bash.mjs` | Removing Docker volumes, `docker compose down -v`, destructive SQL, `flyway clean`, `git reset --hard`, force push, `git clean -f`, discarding all changes, rebase, amend, deleting branches or stashes, mutating applied migrations, commit messages with trailers or AI attributions |
| `check-frontend.mjs` | Reports `provideApiConfiguration('/')` and literal colors or fonts in components |

A hook is added only for an error that is silent (build and tests miss it), irreversible, or a project policy. Local machine quirks and compiler errors are not hook material.

Permissions in `settings.json`: the verification commands run without prompts; `git commit`, `git push` and `docker compose down` always ask; reading `.env` files is denied.

## Setup for a new developer

- Enable the git hooks: `git config core.hooksPath .githooks`.
- Start Claude Code in the repository root and accept the workspace trust dialog once. Until the folder is trusted, Claude Code ignores the allow rules in `settings.json`, and agents stall on approvals.
- Requirements from `README.md` (JDK 25, Docker, Node via nvm). The hooks need `node` on `PATH`.
- Make the default Node satisfy `frontend/.nvmrc` (`nvm alias default 24`): Claude Code starts the Angular CLI MCP server from `.mcp.json` with the default Node, and it needs ^24.15.
- Personal overrides go into `settings.local.json` (gitignored).

## Changing the workflow

- Test a changed agent or skill in a headless run from a clone, for example `claude -p "/verify all"` or `claude -p "<question>" --agent api-lookup`. Keep the clone outside `~/.claude`, which Claude Code treats as protected, and remember that an untrusted clone ignores the allow rules.
- Test a hook by piping a JSON event into it: `echo '{"tool_name":"Bash","tool_input":{"command":"..."},"cwd":"..."}' | node .claude/hooks/guard-bash.mjs`.
