# Documentation

How project knowledge is kept consistent with the code. Paths in this document are relative to the repository root.

## Where knowledge lives

| Place | Contents |
|---|---|
| `docs/constitution.md` | The rules every change must keep, for people and agents alike |
| `AGENTS.md` | Map and working rules for any AI client. Claude Code loads it and the constitution into every session through `CLAUDE.md`, so both stay short |
| `docs/roadmap.md` | What is planned: stages, feature order, ideas, preliminary product decisions |
| `docs/development/` | One guide per area (`backend.md`, `frontend.md`, this file): stack, conventions, commands, verification, pitfalls |
| `docs/architecture/invariants.md` | Identity, authorization, audit |
| `docs/architecture/decisions/`, `docs/features/` | Architecture decisions, feature specs and their progress logs (`<feature>.progress.md`: step statuses, decisions at checkpoints, step commits) |
| `.claude/` | The Claude Code workflow: agents and skills describe roles and processes and point to the documents above; hooks and settings enforce rules (see `.claude/README.md`) |

## Conventions

The consistency check relies on these:

- Refer to repository files in backticks by their path from the repository root, e.g. `backend/pom.xml`. Markdown links may be relative to the document.
- Placeholders go in angle brackets or start with `$` (`docs/features/<feature>.md`); they are not checked.
- A file that does not exist yet is mentioned on a line that says so: "if it exists", "when it exists", "once it exists" or "(planned)". A directory reference (ending with `/`) may point to a directory that does not exist yet, as long as its parent does.
- Agents and skills refer to document sections by their bold name: "the **Verification** section of `docs/development/backend.md`". Every guide in `docs/development/` has a Verification section.
- Commands in `bash` blocks of the Verification and Checking an API sections must match an allow rule in `.claude/settings.json`, so agents can run them without prompts.

## Keeping decisions and specs current

Until the first release, documents describe the current state rather than a history of changes; git keeps the history.

- **ADRs are living.** A changed decision is edited in its ADR, with a dated note in the Status line (`revised <YYYY-MM-DD>: <what changed>`). No superseding ADRs before the first release.
- **A spec's Requirements are living:** scenarios, acceptance criteria, access matrix, visibility, audit, out of scope. When a feature's behavior changes, its spec is updated in the same change as the code, with a line under the header: `Revised <YYYY-MM-DD>: <what changed> (<feature or PR>)`. A new feature that changes another feature's behavior lists the affected criteria in its own spec and edits them in the other spec.
- **A spec's Design is a snapshot** of how the feature was implemented. It may drift from the code; the code, `api/openapi.yaml` and the guides in `docs/development/` are the truth for how things work now. A changed ADR does not trigger rewriting the Design sections of implemented specs.

## Requirements

Living requirements describe behavior, one file per capability in the flat folder `docs/requirements/`. The index `docs/requirements/README.md` (planned) is a table that links each capability file and gives its prefix:

```markdown
| Capability | Prefix |
|---|---|
| [Invitations](invitations.md) | INV |
```

A capability file has `## Purpose` and `## Requirements`, usually followed by `## Known limitations` and `## Out of scope`:

```markdown
### INV-1 Create an invitation

The system SHALL create an invitation for a new email (decision 0003).

#### INV-1.1 An administrator invites a new person

- WHEN an administrator submits a new email
- THEN an invitation is created

#### INV-1.2 The invitation email looks right (manual)

- WHEN the invited person opens the email
- THEN the link and the expiry date are readable
```

- A requirement has a statement with SHALL or MUST and at least one scenario. A scenario has `- WHEN` and `- THEN` lines; `- GIVEN` and `- AND` are optional. A scenario checked by hand ends its title with `(manual)`.
- The next number is the highest in the file plus one, for requirements and scenarios alike.
- References are written `(INV-1)`, `(INV-1.1)` and `(decision 0003)`, and must resolve. Only these forms are checked: "decisions 0003 and 0004" is not.
- A test cites one scenario: its name starts with the scenario id. In JUnit that is `@DisplayName("INV-1.1 ...")` on the test method, in Vitest `it('INV-1.1 ...', ...)`. Tests without an id, such as unit tests of an algorithm or end-to-end journeys, are allowed and do not count as coverage. Skipped tests do not count either. Tests skipped conditionally do count, because that is known only when they run: `@EnabledIf`, `@DisabledIf` and similar annotations, `it.skipIf` and `it.runIf`.

## Verification

```bash
node scripts/check-docs.mjs
node --test scripts/requirements.test.mjs
node scripts/requirements.mjs validate
```

`scripts/requirements.mjs validate` fails (exit code 1) on requirements and an index that break the **Requirements** format, on duplicate ids and on references that do not resolve. `scripts/requirements.mjs trace` also fails on a scenario without a test and on a test that cites a missing scenario.

`scripts/check-docs.mjs` fails (exit code 1) on broken references:
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
- `.githooks/pre-commit` runs `scripts/requirements.mjs validate` and the check on every commit. The check covers all changes since the last checked commit plus the staged ones, and blocks the commit on broken references. `.githooks/post-commit` then records the new commit as checked, but only if pre-commit checked exactly this commit; a `git commit --no-verify` leaves the marker where it was, so the next check still covers it.
- Enable the hooks once per clone: `git config core.hooksPath .githooks`. Git never enables repository hooks by itself.
- To accept the current state without committing: `node scripts/check-docs.mjs --mark`.
- The check follows references only. Whether a statement in a document is still true is part of the review (`/review`).
