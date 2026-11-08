# Documentation

How project knowledge is kept consistent with the code. Paths in this document are relative to the repository root.

## Conventions

The consistency check relies on these:

- Refer to repository files in backticks by their path from the repository root, e.g. `backend/pom.xml`. Markdown links may be relative to the document.
- Placeholders go in angle brackets or start with `$` (`docs/plans/<change>.md`); they are not checked.
- A file that does not exist yet is mentioned on a line that says so: "if it exists", "when it exists", "once it exists" or "(planned)". A directory reference (ending with `/`) may point to a directory that does not exist yet, as long as its parent does.
- Agents and skills refer to document sections by their bold name: "the **Verification** section of `docs/development/backend.md`". Every guide in `docs/development/` has a Verification section.

## Keeping decisions and requirements current

Until the first release, documents describe the current state rather than a history of changes; git keeps the history.

- **Decisions are living,** as `docs/architecture/decisions/README.md` says.
- **Requirements are living,** and the authority for the behavior they describe. They change in the same branch as the code (`docs/process.md`).

## Requirements

Living requirements describe behavior, one file per capability in the flat folder `docs/requirements/`. The index `docs/requirements/README.md` is a table that links each capability file and gives its prefix:

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

- A requirement has a statement with SHALL or MUST: the rule in one or two sentences, or a table, without retelling its scenarios. A scenario has `- WHEN` and `- THEN` lines; `- GIVEN` and `- AND` are optional, and it checks one behavior. A scenario checked by hand ends its title with `(manual)`.
- A rule about access, data, state transitions, security or irreversible actions, or one whose breach is hard to notice, has a scenario, however short the rule is. A requirement may go without scenarios only when a mistake is seen at once on the ordinary path and cheap to fix: a rare state, such as an error response, does not qualify, nor does a ban on showing what the user must not see.
- Hiding an action the server would refuse anyway is not such a ban and needs no scenario. An operational detail, such as a log warning, stays out of the requirements, in the code and a test without an id. So do exact strings, timings and field limits.
- A scenario refers to a table instead of retelling its rows. A table is for a catalog or for rules without scenarios: a rule with a scenario keeps its behavior in the scenario.
- The next number is the highest in the file plus one for a requirement, and the highest of its requirement plus one for a scenario. The number of a removed id may come back: `validate` and `trace` reject every reference and test that still cites a removed id. Gaps are fine.
- Two branches can take the same new id, since nothing hands numbers out centrally. `validate` catches it once both are merged, in the pre-commit hook and in CI on main, and the later change renumbers its ids and the tests that cite them.
- When a scenario is removed, so is its test if the behavior is gone or another test checks it; a test of behavior that stays and that nothing else checks loses the id and stays as a test of a detail.
- References are written `(INV-1)`, `(INV-1.1)` and `(decision 0003)`, and must resolve. A requirement that relies on an architecture decision cites it. Only these forms are checked: "decisions 0003 and 0004" is not.
- A test cites one scenario: its name starts with the scenario id. In JUnit that is `@DisplayName("INV-1.1 ...")` on the test method, in Vitest `it('INV-1.1 ...', ...)`. Tests without an id, such as unit tests of an algorithm or end-to-end journeys, are allowed and do not count as coverage. Skipped tests do not count either. Tests skipped conditionally do count, because that is known only when they run: `@EnabledIf`, `@DisabledIf` and similar annotations, `it.skipIf` and `it.runIf`.

To read one requirement or scenario without its file, and to find who cites a capability or an id:

```bash
node scripts/requirements.mjs show <id>
node scripts/requirements.mjs cited-by <prefix or id>
```

## Verification

```bash
node scripts/check-docs.mjs
node --test scripts/requirements.test.mjs
node scripts/requirements.mjs trace
```

`scripts/requirements.mjs trace` fails (exit code 1) on everything `validate` fails on, and on a scenario without a test and a test that cites a missing scenario. `scripts/requirements.mjs validate` alone fails on requirements and an index that are missing or break the **Requirements** format, on duplicate ids, on ids out of ascending order (gaps are allowed) and on references that do not resolve.

`scripts/check-docs.mjs` fails (exit code 1) on broken references:
- a path in `AGENTS.md`, `CLAUDE.md`, `README.md`, `docs/` or `.claude/` that does not exist and is not gitignored;
- an `@` import in an `AGENTS.md` or `CLAUDE.md` that does not resolve;
- a section that an agent or skill refers to but no guide has, and a section any document names with its file ("the **Guides** section of `docs/README.md`") that the file lacks;
- a Verification or Checking an API command that matches no allow rule;
- a repository path in `.claude/hooks/` that no longer exists.

It also lists the code and config files changed since the last checked commit (including uncommitted changes) that documents refer to, with the referring lines. These reminders do not fail the check; read the listed places and update them if the change made them wrong. To look at a specific range instead:

```bash
node scripts/check-docs.mjs --since <ref>
```

## Change tracking

- The last checked commit is stored locally in `.git/docs-check-base` (not versioned).
- `.githooks/pre-commit` runs `scripts/requirements.mjs trace` and the check on every commit. The check covers all changes since the last checked commit plus the staged ones, and blocks the commit on broken references. `.githooks/post-commit` then records the new commit as checked, but only if pre-commit checked exactly this commit; a `git commit --no-verify` leaves the marker where it was, so the next check still covers it.
- Enable the hooks once per clone: `git config core.hooksPath .githooks`. Git never enables repository hooks by itself.
- To accept the current state without committing: `node scripts/check-docs.mjs --mark`.
- The check follows references only. Whether a statement in a document is still true is part of the review (`/review`).
