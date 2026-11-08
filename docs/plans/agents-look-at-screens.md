# Agents look at screens

Branch: `feature/agents-look-at-screens`
Roadmap item: **Agents look at screens** (Next, 2): agents open the running application in a browser, sign in as a persona of the sample center and look at a screen in each of its states, so that `frontend-dev` checks its screens are plausible before the owner looks, and the manual checks of `/feature` have a browser that is set up rather than improvised, in local and cloud sessions alike.
Requirements approved: 2026-11-09 "план прочитал, возражений не имею" (the scope and decisions of this plan; the change has no requirements)
<!-- Only the user's explicit approval of the requirements as they stand: replace "none" with <YYYY-MM-DD> "<their words>". This change has no requirements: the line records the approval of the scope. -->

## Scope

**Why.** When the owner first looks at the screens of a feature, the obvious flaws (broken layout, a missing state, an implausible view for a role) should already be found and fixed, and the manual checks of `/feature` should run in a browser that is set up rather than improvised.

**In:**
- A separate agent, `manual-tester`, that tests the running application through its UI: it opens the running application in a headless browser through `playwright-cli`, signs in as personas of the sample center, brings a screen to each of its states, takes screenshots and reports what looks implausible. The same agent runs the manual checks of `/feature`. `frontend-dev` fixes what it reports, through the coordinator.
- Running the stack for it: if the stack is not running, the agent starts it and stops it when done; if it is running, it looks alongside the owner and never restarts or stops it.
- Screenshots in a git-ignored folder in the repository, so the owner opens them in the IDE.
- Installing the browser locally (WSL, with system packages installed once by a person) and in cloud sessions.
- The process and the workflow: the manual tester as a role in `docs/process.md` and where its check sits in the stages, and the phrase "a cloud session has no browser" in `/feature`, which is about the user, not the agent.

**Out:**
- Any change of the product's behavior: the development mode (DEV) stays as it is, including failing a single endpoint to show a screen's first-load error state.
- Visual regression tests (comparing screenshots between runs).

## Requirements

None. The change is about the agents, the process and the setup; no behavior of the system changes.

## Design

**The agent, `.claude/agents/manual-tester.md`.**
- `model: sonnet`, the 5-minute cache (it runs once, without pauses), no context-budget hook (it cannot write the plan's Partial work). Tools: `Bash`, `Read`, `Grep`, `Glob`, with no `Edit` or `Write`, so that "edits no tracked files" holds by construction: screenshots are written by `playwright-cli` into `.screens/`.
- Two kinds of brief, one definition: **look at screens** (the brief names the screens, the personas and the states, taken from the plan and the requirements; the agent adds a state it sees the screen has) and **manual checks** (numbered steps; the coordinator passes `model: haiku`).
- **The stack.** It checks `http://localhost:4200` and `http://localhost:8080/actuator/health`. Not running: it starts the backend (`./mvnw spring-boot:run`) and the frontend (`npm start`) in the background with the commands from `README.md`, waits for both, and stops exactly those processes when done (never `docker compose down`: the compose services started by Spring Boot stay, as after a person's run). Running: it uses it as is, never restarts or stops it, and its data changes land in the owner's database, so the report lists them.
- **Sign-in.** Through the Keycloak form, persona email and the password `mentorhub` from `README.md`; one named `playwright-cli` session per persona, so personas do not share cookies.
- **States.** Roles through personas; loading and error through the DEV-3 switch turned on with the screen open (a screen's first-load error is out of reach by decision); empty and the rest through data changes it makes in the UI, each listed, with a note when the sample center could hold that state.
- **What it reads.** The accessibility snapshot to find elements and states; a screenshot for each screen, persona and state it reports on, read back to judge the look. Screenshots: `.screens/<change>/<run>/<screen>-<persona>-<state>.png`, `<run>` a timestamp.
- **A broken environment** (no browser, missing system packages, a stack that does not start): NOT RUN with the exact command and error, no workaround.
- **Report** as the decision of 2026-11-09 says.
- Its commands are written once in the definition and each matches an allow rule in `.claude/settings.json` (the `playwright-cli` calls, the health checks, the two start commands; stopping its processes goes through the permission mode).

**Tooling.**
- `@playwright/cli` (binary `playwright-cli`): the developer confirms the npm package name and the latest version with `npm view` (or `api-lookup`) before adding it as an exact devDependency of `frontend/`, and runs it with `~/.nvm/nvm-exec npx playwright-cli` from `frontend/`.
- `.gitignore`: `.screens/`.
- `README.md` **Setup**: install Chromium for `playwright-cli` once, and in WSL the system packages once with `sudo` (the exact commands, tried on this machine; the developer stops and reports the `sudo` line for the owner to run if it is needed).
- `session-start.sh` (cloud only): install the Chromium revision `playwright-cli` needs when it is missing; a failure only warns, like Docker. Whether a pre-installed browser fits is found in the cloud trial run, not guessed.

**Process and workflow.**
- `docs/process.md` **Roles**: a row "Manual tester | Tests the running application through its UI: looks at the changed screens in each state as the personas who see them, runs manual checks, reports what is implausible | Edit code, tests or documents; fix what it finds". Stage 5: one sentence that the manual tester looks at the changed screens and runs the manual checks before the owner does, the owner chooses what the developer fixes, and the manual tester re-checks the affected states once. No client named.
- `/feature` (`.claude/skills/feature/SKILL.md`): the **Stop when the frontend is green** paragraph becomes the flow of the 2026-11-09 decision; the manual checks go to `manual-tester` with `model: haiku` (the "a cloud session has no browser" phrase and the `general-purpose` agent go); stage 8 of the skill also deletes `.screens/<change>/`.
- `.claude/README.md`: the Flow diagram gains the manual tester line, and the Cost row on manual browser checks names `manual-tester`.
- `docs/development/frontend.md` stays unchanged (decision).

**A new agent type** was picked up by the running session as soon as its definition appeared: no restart is needed.

## What the tests rely on

None: there are no scenarios. The change is checked by a trial run on a real screen.

## Tasks

| Task | Scenarios | Who | Status |
|---|---|---|---|
| T1 Tooling: `playwright-cli` devDependency, `.gitignore`, `README.md` setup tried locally, `session-start.sh`, allow rules for the commands | none | `frontend-dev` | done |
| T2 The `manual-tester` definition, `docs/process.md` Roles and stage 5, `/feature`, `.claude/README.md`; docs verification green. **Checkpoint:** the owner reads the diff of T1 and T2 | none | `frontend-dev` | done |
| T3 Trial run, local: `manual-tester` on Users and Invitations, personas `admin@` and `mentor@`, reaching the list's own error state; the owner reads the report and the screenshots | none | coordinator, `manual-tester` | done |
| T4 Trial run, cloud: the same brief in a cloud session; settles whether the pre-installed browser fits | none | coordinator, `manual-tester` | pending |
| T5 Fixes from the trial runs to the definition or the setup, if any | none | `frontend-dev` | done |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

## Red tests

None: there are no scenarios.

## Decisions

- 2026-11-09: A separate agent looks at screens (option B), rather than `frontend-dev` itself.
- 2026-11-09: No requirements; the reviewer's requirements round is skipped; the requirements gate approves the scope; no red tests; the check is a trial run on a real screen ("без требований как предлагаешь").
- 2026-11-09: The feature branch is built on the branch of the roadmap work-out (commit `d997676`), one pull request with the roadmap work-out.
- 2026-11-09: The flow in `/feature`: frontend green → manual tester on the changed screens and the manual checks → stop: the owner reads its report and screenshots and chooses what goes to `frontend-dev` → `frontend-dev` fixes → one re-check of the affected states → the owner looks. Outside `/feature`, the coordinator calls it on request ("Согласен с потоком и моделями").
- 2026-11-09: Models: Sonnet in the agent's definition for looking at screens; the manual checks from given steps run on the same agent with `model: haiku`.
- 2026-11-09: `playwright-cli` is a devDependency of `frontend/` with the version in the lockfile. Locally a person installs Chromium and the WSL system packages once, as `README.md` says; in the cloud `session-start.sh` installs Chromium when the needed revision is missing (whether the pre-installed one fits is checked by a trial run in the cloud). Screenshots go to `.screens/<change>/<run>/`, git-ignored, deleted with the plan at stage 8. The procedure lives in the agent's definition, the setup in `README.md`, one sentence in `docs/process.md` stage 5 (widened below by the role); `docs/development/frontend.md` is unchanged ("согласен").
- 2026-11-09: The agent is `manual-tester`: it walks scenarios and looks for discrepancies, which is a tester's work, not only a screen check; plain `qa` would clash with the QA track of the domain. Its role goes into the **Roles** table of `docs/process.md` ("да, давай `manual-tester`. роль агента тоже закрепляем в процессе").
- 2026-11-09: The manual tester changes and adds data whenever a scenario or a state needs it, and lists every change in its report; it notes a state it had to create that the sample center could hold ("если ему нужно что-то менять для прохождения сценария - пусть меняет").
- 2026-11-09 (proposed by the agent, approved with the plan): it edits no tracked files, writing only to `.screens/` and its scratchpad; it never restarts or stops a stack it did not start; on a broken environment (no browser, missing packages, a stack that fails) it returns NOT RUN with the exact error instead of working around it. Report: per screen, persona and state, what is shown, what looks implausible and the screenshot, then "likely a bug" / "doubtful, owner decides" / "as the requirement says"; manual checks as PASS / FAIL / NOT RUN per step with a screenshot of each FAIL. Trial run: Users and Invitations, locally and in the cloud, with the list's own error state as the known case to reach.
- 2026-11-09: Plan gate passed: Design and Tasks approved ("план утверждаю, запускай T1").
- 2026-11-09 (T1, the agent's finding, not yet seen by the owner): the package is `@playwright/cli` at exact `0.1.23` (binary `playwright-cli`); `playwright-cli` on npm is deprecated in its favor. It pulls `playwright` `1.65.0-alpha-2026-10-30`. `.playwright-cli/` (page snapshots it writes into `frontend/`) is git-ignored too, beyond the plan. `open` needs `--browser=chromium`, otherwise it looks for Google Chrome.
- 2026-11-09: No `Bash(kill *)` allow rule: stopping the stack the agent started goes through the permission mode ("да, убирай kill"). `@playwright/cli` with its alpha Playwright accepted (not objected to).
- 2026-11-09: Both folders stay: `.screens/` for the report's screenshots, `.playwright-cli/` as the tool's git-ignored scratch ("да, оставляем обе").
- 2026-11-09: If stopping the stack is denied, `manual-tester` does not work around it and reports the running stack with the PIDs and the command for the owner. T1 and T2 accepted and committed with the plan ("передавай правку и коммить").
- 2026-11-09: T3 accepted; friction items 1–5 go to T5, 6 and 7 stay; product findings 1–3 became GitHub issues #26 (paginator range past the last page), #27 (Invitations Invited column squeezed), #28 (Users column widths shift) ("согласен, запускай T5 и заведи issues по 1, 2, 3").
- 2026-11-09: The other T3 findings stay as they are: no status chip, the paginator over old rows during a slow search, the sample row "Emma Brooks". T5 accepted, committed and the branch pushed for T4 ("chip не нужен, остальное оставляем, коммить и пушь").
- From the exploration (roadmap product decisions): `playwright-cli`; the stack rule; screenshots in a git-ignored folder opened in the IDE; a screen's own error state is reached with the failure switch on an open screen; Chromium needs system packages in WSL; the `/feature` phrase is fixed.

## Handover

- Waiting for: the owner to run T4 in a cloud session.
- To read: this plan.
- T5 (committed): `.claude/agents/manual-tester.md` gains: snapshots written with `--filename` into the run folder and searched with grep/head, or of one element; role and text locators over refs; `select` for the DEV-3 switch; `screenshot --full-page`; `<run>` from `date +%Y%m%d-%H%M`; wait or snapshot after a `goto` that may redirect. No new allow rule; whether `date` prompts is seen in T4 (add `Bash(date *)` if it does).
- T3 result (local, run `20261109-0000`, screenshots in `.screens/agents-look-at-screens/20261109-0000/`): stack started and stopped by the agent without prompts, no data changed, all states of both lists reached except the list's first-load error (out of reach by decision); the mentor on `/users/{id}` not confirmed (a redirect read too early, fixed in T5).
- Documents changed:
  - `docs/roadmap.md`, changed (committed in `d997676`, the commit this branch is built on): the item worked out in `/explore`, its product decisions, and an idea on meaning lost in shortened passages.
  - `docs/plans/agents-look-at-screens.md`, added (committed in `82acc80`, changed since): this plan.
  - `README.md`, changed (committed in `82acc80`): Setup gains installing Chromium for `playwright-cli`, the WSL libraries (`--with-deps`, not tried: needs `sudo`) and a check.
  - `docs/process.md`, changed (committed in `82acc80`): a Manual tester row in Roles; one sentence in stage 5.
- State: T1, T2 committed in `82acc80`; T3 done; T5 committed; the branch pushed; T4 pending.
- Open questions: none.
- Next: T4 needs a cloud session: the owner opens this branch in Claude Code on the web and runs `/feature agents-look-at-screens`; the coordinator there briefs `manual-tester` with the T3 brief (Users and Invitations, `admin@` and `mentor@`, the list's own error state) and checks whether the pre-installed browser fits and `session-start.sh` installs Chromium. Then stage 7 (review) and stage 8.

## Agents

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| frontend-dev | sonnet | ad63877eb745d0cdb | implementation (T1, T2, T5) | 81k |
| manual-tester | sonnet | af4b2a9d422ffd344 | implementation (T3) | 74k |
