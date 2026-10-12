# MentorHub

An internal platform for the mentors of a training center (SWE, QA, DevOps tracks): student and cohort tracking, notes and feedback, assignments with automatic checking, meeting scheduling.

## Map

- `api/openapi.yaml`: the API contract, **the source of truth** (ADR 0002).
- `backend/`, `frontend/`: stack, conventions, commands and verification are in `docs/development/backend.md` and `docs/development/frontend.md`. Read the guide before working in its folder. How documentation is kept consistent with the code: `docs/development/docs.md`.
- `infra/compose.yaml`: local environment.
- `docs/adr/`: architecture decisions. **Read before planning changes.**
- `docs/architecture/invariants.md`: domain model, authorization and visibility rules.
- `docs/features/`: feature specs (scenarios per role, acceptance criteria, UI, design).
- `.claude/`: the Claude Code workflow (agents, skills, hooks); see `.claude/README.md`.

## Agent workflow (Claude Code)

- The main session designs and coordinates: features are specified with `/spec` and implemented with `/feature`, which delegates code to the `backend-dev` and `frontend-dev` subagents. Quick fixes may be done directly.
- Finish work with `/review` and `/verify`.

## Rules

- **Contract first.** An API change starts in `api/openapi.yaml`; the backend implements the generated interfaces and the frontend uses the generated client. Never edit generated code.
- Before planning a feature, read the related ADRs, `docs/architecture/invariants.md` and the spec. Record significant new architectural decisions as an ADR.
- A feature is a vertical slice: contract, migration, backend, frontend, tests.
- Never edit an applied database migration; add a new one.
- Work is done when the verification in `docs/development/` passes for every area it touches.
- Security: domain data references `users.id`, never the token `sub`; access to student data is checked at the resource level; note visibility holds on every read path. Details in `docs/architecture/invariants.md`.
- Ask before destructive or hard-to-reverse actions, even on local resources: deleting data, resetting databases or Docker volumes, rewriting git history, pushing.
- When you suggest a destructive command for the user to run, say what it will destroy (for example, that `docker compose down -v` deletes the dev database volume).
- All project artifacts (code, docs, UI text, commit messages) are in English.
- Comment sparingly: only non-obvious infrastructure or algorithmic points. No comments that restate the code.
- Commit messages and pull request titles and descriptions are plain English text: no trailers (`Co-Authored-By` and the like), no "Generated with" lines and no mention of AI agents or models.
