# Documentation

How project knowledge is kept consistent with the code. Paths in this document are relative to the repository root.

## Where knowledge lives

| Place | Contents |
|---|---|
| `AGENTS.md` | Map and project-wide rules for any AI client; Claude Code loads it into every session through the one-line `CLAUDE.md`, so it stays short |
| `docs/development/` | One guide per area (`backend.md`, `frontend.md`, this file): stack, conventions, commands, verification, pitfalls |
| `docs/architecture/invariants.md` | Domain model, authorization, visibility |
| `docs/adr/`, `docs/features/` | Architecture decisions, feature specs and their progress logs (`<feature>.progress.md`: step statuses, decisions at checkpoints, step commits) |
| `.claude/` | The Claude Code workflow: agents and skills describe roles and processes and point to the documents above; hooks and settings enforce rules (see `.claude/README.md`) |

## Conventions

The consistency check relies on these:

- Refer to repository files in backticks by their path from the repository root, e.g. `backend/pom.xml`. Markdown links may be relative to the document.
- Placeholders go in angle brackets or start with `$` (`docs/features/<feature>.md`); they are not checked.
- A file that does not exist yet is mentioned on a line that says so: "if it exists", "when it exists", "once it exists" or "(planned)". A directory reference (ending with `/`) may point to a directory that does not exist yet, as long as its parent does.
- Agents and skills refer to document sections by their bold name: "the **Verification** section of `docs/development/backend.md`". Every guide in `docs/development/` has a Verification section.
- Commands in `bash` blocks of the Verification and Checking an API sections must match an allow rule in `.claude/settings.json`, so agents can run them without prompts.

## Verification

```bash
node scripts/check-docs.mjs
```

It fails (exit code 1) on broken references:
- a path in `AGENTS.md`, `CLAUDE.md`, `README.md`, `docs/` or `.claude/` that does not exist and is not gitignored;
- an `@` import in an `AGENTS.md` or `CLAUDE.md` that does not resolve;
- a section that an agent or skill refers to but no guide has;
- a Verification or Checking an API command that matches no allow rule;
- a repository path in `.claude/hooks/` that no longer exists.

It also lists the code and config files changed since the last checked commit (including uncommitted changes) that documents refer to, with the referring lines. These reminders do not fail the check; read the listed places and update them if the change made them wrong. To look at a specific range instead:

```bash
node scripts/check-docs.mjs --since <ref>
```

## Change tracking

- The last checked commit is stored locally in `.git/docs-check-base` (not versioned).
- `.githooks/pre-commit` runs the check on every commit, covering all changes since the last checked commit plus the staged ones, and blocks the commit on broken references. `.githooks/post-commit` then records the new commit as checked, but only if pre-commit checked exactly this commit; a `git commit --no-verify` leaves the marker where it was, so the next check still covers it.
- Enable the hooks once per clone: `git config core.hooksPath .githooks`. Git never enables repository hooks by itself.
- To accept the current state without committing: `node scripts/check-docs.mjs --mark`.
- The check follows references only. Whether a statement in a document is still true is part of the review (`/review`).
