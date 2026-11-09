# MentorHub

An internal platform for the mentors of a training center (SWE, QA, DevOps tracks): student and cohort tracking, notes and feedback, assignments with automatic checking, meeting scheduling.

The rules every change must keep are in `docs/constitution.md`.

## Map

- `api/openapi.yaml`: the API contract, **the source of truth** (decision 0002).
- `backend/`, `frontend/`: the code; their guides are `docs/development/backend.md` and `docs/development/frontend.md`.
- `infra/compose.yaml`: local environment.
- `docs/`: the documentation. `docs/README.md` says which document holds what and which one decides; read it before writing anything down. `docs/process.md` says how a change is made, from an idea to main.

## Rules

- Every change starts with **Choosing a path** in `docs/process.md`, a one-line fix and a change begun in an ordinary conversation too: before anything is changed, the owner confirms the goal, the approach and the path, and the change gets its branch. Nothing is changed on the default branch.
- Before planning a change, read the decisions and requirements it touches and its roadmap item, as **What to read** in `docs/process.md` says. Propose a new decision when a choice meets the threshold in the decisions index.
- When a change conflicts with the constitution, a decision or a requirement, stop and report it with its impact. Do not widen the change silently or work around the conflict.
- Ask before destructive or hard-to-reverse actions, even on local resources: deleting data, resetting databases or Docker volumes, rewriting git history, pushing.
- When you suggest a destructive command for the user to run, say what it will destroy (for example, that `docker compose down -v` deletes the dev database volume).
