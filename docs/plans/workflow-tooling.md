# Workflow tooling

Branch: `chore/workflow-tooling`
Roadmap item: none

## Scope

Confirmed at the path gate on 2026-11-19, from the `/usage-review` of enrollment-mentors.

**Goal.** The workflow loses less time and money: the coordinator itself tells the owner when its context is large and whether a new session is safe now; agents do not hang on a build that died or never started; a disputed screen layout is chosen on mockups, not by implementing variants; the usage records see every cache rebuild; a small fix after a long pause does not rewrite an agent's whole cache.

**Approach.**

1. **Coordinator context Stop hook** (`.claude/hooks/coordinator-context.mjs`): instead of a `systemMessage` the owner rarely notices, the hook returns `additionalContext` to the main session (guarded by `stop_hook_active`), and the session says in its own message the context size and either that `/clear` is safe now, with the command that continues, or what must happen first (a running agent to finish, the handover to update). Stops with background tasks are no longer skipped. The safety criteria go into `/feature`, `/chore` and `/spec`.
2. **Agent build hangs:** `.claude/hooks/guard-bash.mjs` refuses polling loops (`while`/`until` with `sleep`, `pgrep` in a loop), `run_in_background` for verification commands (`mvnw verify`/`test`, `npm test`/`build`/`lint`) and `npm run build` while a dev server is alive, each with a hint of what to do instead; one project script brings up the stack for `manual-tester`, developers' browser checks and developers (start under `setsid`, wait for health with a hard timeout, check each tick that the process is alive, fail fast with the log tail); the coordinator runs builds one at a time (a rule in `/feature`).
3. **A `ui-designer` agent:** Sonnet, `effort: medium`, 1-hour cache, writes only to `.screens/<change>/` (enforced by a hook, not only its definition). At the plan stage, before the red tests, and only when a screen has a real layout choice, it makes an HTML mockup with 2–3 variants; rounds go through `SendMessage`; the accepted variant goes into the plan's "What the tests rely on", and `frontend-dev` reads the mockup file. `docs/process.md` gains a line about the mockup at the plan stage, without naming the agent.
4. **`.claude/scripts/agent-usage.mjs`:** a rebuild is counted on the first call of a record window too, by remembering the agent's previous call before `since`. Existing records are not recomputed.
5. **`/feature`:** an agent is resumed for a fix only if its last call was less than an hour ago; otherwise a fresh agent is briefed from the finding and the plan's notes.
6. The conclusion that `test-engineer` keeps the 5-minute cache (a 1-hour cache would have cost 0.29M in enrollment-mentors and 0.43M in tracks, against rebuilds of 0.10M and 0.13M) moves into **Cost and context** of `.claude/README.md`, and the reservation in `docs/roadmap.md` ("unless the usage records show it rebuilding") goes.

**Evidence** (enrollment-mentors): one coordinator session from plan to review averaged 181k in implementation and 276k in review; `backend-dev` was stuck about 20 minutes in a `pgrep -f` loop that matched itself, and `frontend-dev` waited for a dev server broken by `npm run build`; the track page's mentors were implemented in two variants before mockup E was accepted (`frontend-dev` 1.89M Sonnet, `manual-tester` 0.78M); `backend-dev` resumed after 2h16m wrote 126k (0.28M for 3 calls) and was recorded with no rebuild.

**Out of scope:** the quality-risking proposals of the review (removing the second test pass, settling `test-engineer` on Sonnet).

**Requirements:** the requirements index was searched; none describes what changes, and none should: these are agents, hooks, scripts and process documents.

## Tasks

| Task | Who | Status |
|---|---|---|
| T1 Stop hook: `additionalContext` to the session instead of `systemMessage`, background tasks named instead of skipped; the safety criteria of `/clear` in `/feature`, `/chore`, `/spec`; the row in `.claude/README.md` | coordinator | done |
| T2 `guard-bash.mjs`: refuse polling loops and verification or servers in `run_in_background`, each with a hint | coordinator | done |
| T3 The API client is regenerated only when the contract changed (`npm start`, `build`, `test`, `lint` all regenerate it now, and two at once deleted it under the dev server) | coordinator | done |
| T4 `scripts/stack.mjs` (`start`, `status`, `stop`); `manual-tester` and the developers use it; permission rules; `README.md`; the line in the guides with its four answers | coordinator | done |
| T5 `/feature`: one backend build at a time; resuming an agent past its cache lifetime | coordinator | done |
| T6 `ui-designer` agent, its file guard, `/feature`, `/chore`, `frontend-dev`, the line in `docs/process.md` | coordinator | done |
| T7 `agent-usage.mjs`: a rebuild on the first call of a record window | coordinator | done |
| T8 `.claude/README.md`: the `test-engineer` cache conclusion; `docs/roadmap.md`: the reservation goes | coordinator | done |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

<!-- Written by an agent stopped by the context budget, under a heading with its task; a fresh agent continues from it. Deleted when the task is done. -->

## Decisions

- 2026-11-19: the hook, the build hangs and the designer in one chore, with the other quality-neutral proposals of the review: "давай объединим хук, зависания и дизайнера в один chore. все остальные quality-neutral улучшения, предложенные тобой, тоже включи в этот chore".
- 2026-11-19: path, approach, a plain branch from `origin/main` instead of a worktree, the test-engineer cache conclusion moved into `.claude/README.md`: "подтверждаю, всё по рекомендациям, кроме пункта 3".
- 2026-11-19: the stand script is named in the development guides too, not only `README.md` and the agents' definitions: "скрипт стенда может понадобиться разработчикам, т.к. если мне не изменяет память, в прошлый раз завис backend-dev". The passage comes to the owner with its four answers at stage 8.
- 2026-11-19: the review findings, each as recommended (fix 1–8, 11, 12; 9 stays out of scope; drop the widening of `frontend.md:13`): "давай всё по рекомендациям".
- 2026-11-20: the re-review's findings as recommended (fix A, B, D; leave C, E): "давай всё по рекомендациям".
- 2026-11-20: the stack row in the backend and frontend guides, with its four answers: "согласен, коммить".

## Handover

- Waiting for: `/pr` (committed). A, B, D fixed and tested by hand (a lock left without a pid is cleared; the reminder starts over after a compaction); C and E left, as the owner chose.
- To read: `git diff origin/main` and the untracked files; the findings below.
- Documents changed:
  - `docs/process.md` changed: stage 3 says a screen with a real layout choice gets a mockup with two or three variants first.
  - `docs/roadmap.md` changed: the `test-engineer` cache reservation goes.
  - `docs/development/backend.md` and `docs/development/frontend.md` changed: a row in Commands, `node scripts/stack.mjs start` for the whole stack, agents start it only this way, in the foreground with the Bash timeout at 600000; `stop <token>` stops what that run started. Four answers: (1) any change whose developer or agent looks at a screen in the browser, or brings up the backend for sample data; (2) hand-started servers and wait loops that hang on a dead server, and stacks stopped by name; (3) `README.md` is for a person and agents do not load it, the definitions of `manual-tester` and `frontend-dev` miss `backend-dev` and a developer without Claude, while a guide is loaded by every agent in its area; (4) yes: in enrollment-mentors `backend-dev` lost about 20 minutes in a wait loop, and `frontend-dev` waited on a broken dev server until the owner stopped it.
  - The widening of `docs/development/frontend.md:13` (the client skipped while the contract is unchanged) is dropped: the header comment of `frontend/scripts/generate-api.mjs` holds it.
- State: T1–T8 done; the review's fix round done, as the owner chose:
  1. `stack.mjs`: `start` prints a run token, `stop <token>` stops only what that run started; agents, guides and `README.md` say so.
  2. The state of a started group records the boot time and its leader's start time (`ps -o lstart`); a group that does not match is not ours.
  3. An owned service whose log reports a failure after its last success is not up, in the wait and when `start` finds it running.
  4. Stop hook: said again only when the context grew by 30k or the running tasks changed (state in the temp folder by session id). Tested: 210k said, 210k quiet, 235k quiet, 241k said, agent started said, same quiet, agent finished said.
  5. `guard-bash.mjs`: the loop check reads only what the shell runs (quoted text and heredocs skipped unless handed to `sh -c`, `bash`, `eval`); `for` with `exit` or `return` is a wait too; the hint names Monitor for the main session. Tested on 19 commands.
  6. `generate-api.mjs`: the lock's `statSync` race.
  7. "In the foreground" with the 600000 timeout in `frontend-dev.md`, the guides and both hints.
  8. `ui-designer` has `permissionMode: acceptEdits`, and `.claude/README.md` names it.
  11. The two `curl` allow rules are removed.
  12. `start` holds a lock (a second waits, then uses what the first started; tested with two at once); `start` and `status` print the checkout an answering service runs from (via `lsof`), and warn when it is another one.
  - 9 stays out of scope: the Antigravity port (`.agents/`) has not followed `.claude/` for a while.
  - Tested by hand: `stack.mjs` start, two starts at once, failed bundle, `stop` with a foreign and the right token, stale and foreign recorded groups.
- Verification after the fix round (`verifier`, frontend and docs): format, lint, 414/414 tests, build, check-docs, requirements tests 64/64, trace 259/259: PASS.
- Re-review (Sonnet): NON-BLOCKING, all previous findings fixed; new minor findings, each with the recommendation:
  - A. `stack.mjs`: the boot-time check (±60 s) may misfire after a WSL clock jump: fix, widen the tolerance to 10 minutes.
  - B. `stack.mjs`: a `start` killed between creating the lock and writing its pid leaves a lock every later run waits 420 s on: fix, a lock without a pid older than 10 s is stale.
  - C. `generate-api.mjs`: two waiters on a lock older than 120 s may remove each other's fresh lock: leave, it needs a lock left by a killed run and two runs at once.
  - D. Stop hook: after a compaction in the same session the reminder stays silent until the old size plus 30k: fix, reset when the context dropped.
  - E. `guard-bash.mjs`: an apostrophe in a comment (`# don't`) hides the rest of the command from the loop check: leave, the hook is a guard against mistakes, not a boundary.
- Open questions: none.
- On approval: commit with the proposed message, then `/pr`. Stage 8: the guide rows (stack script) carry their four answers above; no roadmap item.
- On remarks: as the owner says.

## Agents

<!-- Agents can be continued only within the session that started them; a new session starts fresh ones from this brief. -->

| Agent | Model | Id | Context |
|---|---|---|---|
| reviewer | definition default | aebbc3673189226c9 | 95k |
| verifier | definition default | aa09625dc982eced5 | 15k |
| verifier (fix round) | definition default | a5d657cc6dc5ff0e6 | 15k |
| reviewer (re-review) | sonnet | a16c0b0a577f02c87 | 40k |
