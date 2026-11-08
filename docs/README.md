# Documentation map

Which document holds what, for people and agents alike. Before writing something down, find its row: what a document does not hold goes where its row says. When two documents disagree, the one with the authority decides, and the other is fixed.

| Document | Holds | Does not hold (goes to) | Authority for |
|---|---|---|---|
| `docs/constitution.md` | The rules every change keeps, each with its reason | Rules of the domain (requirements), of one area (its guide), of how an agent works (`AGENTS.md`); common knowledge about tools and frameworks; the mechanics of a decision (the decision) | Its rules: no other document relaxes them |
| `docs/process.md` | How a change goes from an idea to main: stages, gates, roles, rules for tests | One client's commands and agents (its configuration folder, such as `.claude/`) | The process |
| `docs/roadmap.md` | The future: stages, planned items, ideas, preliminary product decisions | What is implemented (requirements, decisions); bugs (GitHub issues) | Nothing: items are intentions |
| `docs/requirements/` | What the system does, as users and API clients see it | How it is built; UI detail, exact strings, timings, field limits (code) | The behavior they describe |
| `api/openapi.yaml` | The API: operations, schemas, error types | Behavior beyond the shape of a call (requirements) | The API |
| `docs/architecture/decisions/` | Cross-cutting, hard-to-reverse choices that had real alternatives, with the reasons and the rejected alternatives | Conventions (guides), implementation details (code) | Architecture |
| `docs/development/<area>.md` | What anyone changing code in the area needs before opening it: conventions, rules, pitfalls, commands, the verification | How one feature or component is built (comments in its code); behavior (requirements) | How work in the area is done |
| `docs/ui/guidelines.md` | Page patterns and conventions shared by screens | The behavior of one screen (requirements, or the code) | How screens look and are laid out |
| `docs/plans/<change>.md` | One change in progress: design, tasks, the owner's decisions, the handover | Anything meant to last: it is deleted before the pull request, after stage 8 of `docs/process.md` moves what lasts | That change, until it merges |
| `docs/explorations/<topic>.md` | The notes of one exploration in progress | Anything meant to last: it is deleted once its roadmap items exist | Nothing |
| The code, its comments and tests | How the system is built, and every detail no document above describes | | Everything the rows above do not cover |
| `README.md` | Getting started for a person: setup, running, local accounts, checks | Rules of the project (constitution, guides) | |
| `AGENTS.md` | How an AI agent works in this repository | Rules of the project (constitution), the documentation (this map) | |

The constitution, the process, the requirements and the decisions name no AI client, its commands or its agents: they hold for a team without one. A plan or a roadmap item may name them when it is about one. A guide may name the project's agents and skills, since the process runs through them and a developer with another client carries them over, but no client's configuration (settings, permission rules, hooks): that stays in the client's folder, such as `.claude/`.

A detail that turns out to matter moves into the requirements. The format of requirements and when one has a scenario are in the **Requirements** section of `docs/development/docs.md`; when a choice is a decision, in the index `docs/architecture/decisions/README.md`.

## Guides

A guide is read in full by everyone who works in its area, agents on every run, so it grows only when the area gains a rule.

- **The test:** would someone working on an unrelated change in this area need it? If only someone changing that one component needs it, it is a comment in that component's code.
- A feature's behavior is in its requirements, and how it is built is in its code. A guide retells neither: no section about one feature, its classes, its algorithm or how its tests compare.
- What a feature adds to a guide is a rule for later changes, in a few lines. Example: how a module publishes asynchronous work and what its listener must guarantee, not how invitation emails avoid being sent twice.
- A guide gives the rule and where to look, not a copy of what the code, the contract or a configuration file lists: problem types, constructors, settings and their defaults, the cases a handler covers. Such a list goes stale with the next change, and the source is one search away.
- Name a file or a class only where the rule needs it. Each named file makes `scripts/check-docs.mjs` remind about the guide whenever that file changes.
- A plan does not order a guide section about its change; its tasks name only the rule an area gains, if any.
- **A passage added to a guide, or widened, needs the project owner's agreement,** and comes to the owner with four answers:
  1. Which later change, unrelated to this one, would run into it?
  2. What mistake does it prevent?
  3. Where else could it live (the code or a comment, the contract, a requirement, a decision, `README.md`), and why is that not enough?
  4. Did someone actually stumble on it in this change? Without that, the passage is a guess that someone will.
- Removing, shortening and mechanical edits (a renamed file, a changed command) need no answers: a guide must stay cheap to clean.
