# MentorHub

An internal platform for the mentors of a training center (SWE, QA, DevOps tracks): student and cohort tracking, notes and feedback, assignments with automatic checking, meeting scheduling.

The rules every change must keep are in `docs/constitution.md`.

## Map

- `api/openapi.yaml`: the API contract, **the source of truth** (decision 0002).
- `backend/`, `frontend/`: stack, conventions, commands and verification are in `docs/development/backend.md` and `docs/development/frontend.md`. How documentation is kept consistent with the code: `docs/development/docs.md`.
- `infra/compose.yaml`: local environment.
- `docs/process.md`: how a change is made, from an idea to main. Read it before anything larger than a quick fix.
- `docs/roadmap.md`: what is planned: stages, feature order, ideas and preliminary product decisions.
- `docs/architecture/decisions/`: architecture decisions. Read the index and open the decisions a task touches.
- `docs/requirements/`: living requirements, what the system does, one file per capability. Read the index and open the capabilities a task touches.

## Rules

- Before planning a change, read the decisions and requirements it touches and its roadmap item, as **What to read** in `docs/process.md` says. Propose a new decision when a choice meets the threshold in the decisions index.
- When a change conflicts with the constitution, a decision or a requirement, stop and report it with its impact. Do not widen the change silently or work around the conflict.
- Ask before destructive or hard-to-reverse actions, even on local resources: deleting data, resetting databases or Docker volumes, rewriting git history, pushing.
- When you suggest a destructive command for the user to run, say what it will destroy (for example, that `docker compose down -v` deletes the dev database volume).
