# Cheaper mockup rounds and screen runs against the mockup

Branch: `chore/mockup-rounds-and-screen-compare`
Roadmap item: none

## Scope

Goal, as confirmed at the path gate (2026-11-23): mockup rounds cost the owner and the budget too much (in track-mentor-students the coordinator's work on the mockup took about 1.6M Opus of 4.62M), and the implementation drifts from the approved mockup in ways the screen run does not catch, so the owner finds them. Rounds should cost less, and `manual-tester` should find the drift before the owner looks. Plus small mechanisms that did not take effect in that change. The evidence is the `/usage-review` of track-mentor-students.

Approach, as confirmed:

1. Mockup rounds without the coordinator's own analysis (`.claude/skills/feature/SKILL.md`, `.claude/agents/ui-designer.md`): the coordinator relays the owner's remarks verbatim, in the original language, and does not open the mockup itself; "no visible change" is investigated by `ui-designer`, which can read its own file; a contrast measurement the owner asks for goes to `general-purpose` on Sonnet, not to the coordinator on Opus; `ui-designer` keeps the remarks of each round in the mockup's notes under `.screens/`, and the plan gets only the approved variant and the final decisions in the owner's words. The roadmap idea of a measured contrast check for every mockup stays separate.
2. `general-purpose` is always started with an explicit `model` (`feature/SKILL.md`).
3. The context-budget hook (`.claude/hooks/context-budget.mjs`) tells an agent once, past 150k, to say in its report that the next task goes to a fresh agent. The 250k budget with a PARTIAL stop is unchanged.
4. Cost and context in `.claude/README.md`: the re-review row stays (both skills still run re-reviews, so it is not stale); the list of coordinator shares gains track-mentors (42%) and track-mentor-students (70%); the conclusion that the coordinator context reminder holds the average below about 130k (track-mentors and track-mentor-students) is recorded; rows for the rules of items 1 and 5, with the figures of track-mentor-students.
5. Screen runs against the mockup (`.claude/agents/manual-tester.md`, `feature/SKILL.md`): when a mockup exists, the tester screenshots the mockup (`file://`) and the implementation in the same state, width and theme, and names every visual mismatch (tints, lines, underlines, spacing, alignment) with both screenshots.

Rejected: mockup rounds in a separate `claude --agent ui-designer` session, a larger saving, but the owner's words would be recorded by `ui-designer` and the owner keeps a second session in mind; not until the simpler way is measured.

In: the files named above. Out: the roadmap idea of measured contrast for every mockup; any change to `docs/process.md`, the requirements or the guides.

Capabilities searched: `docs/process.md` (plan stage, screen runs) and **Requirements** in `docs/development/docs.md`. No requirement describes this or should: it is the configuration of the agent workflow; the process rule (a mockup before the tests) already stands in `feature/SKILL.md` and does not change.

## Tasks

| Task | Who | Status |
|---|---|---|
| 1. Mockup rounds without the coordinator's analysis; `general-purpose` with a model (`feature/SKILL.md`, `ui-designer.md`) | coordinator | done |
| 2. Fresh-agent reminder at 150k (`context-budget.mjs`, the three implementation agents) | coordinator | done |
| 3. Screen runs against the mockup (`manual-tester.md`, `feature/SKILL.md`, `settings.json`) | coordinator | done |
| 4. Cost and context (`.claude/README.md`) | coordinator | done |
| 5. Guard the mockup session in `guard-bash.mjs` (finding 2) | coordinator | done |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

## Decisions

- 2026-11-23: "подтверждаю всё, включая пункт 3, запускай /chore": goal, approach (items 1 to 5, the 150k reminder included) and the path `chore/mockup-rounds-and-screen-compare` confirmed.
- 2026-11-23: "правка мокапа на моменте имплементации была одна - это удаление строки с числом студентов под шапкой ... остальные правки касаемо фона под шевроном и чего-то ещё были потому что финальный результат не совпал с мокапом": the reason for item 5.
- 2026-11-23: on how `manual-tester` opens a mockup: "это просто мокап, я не хочу чтобы агент каждый раз спрашивал меня перед тем, как открыть мокап" — the variable in a session of its own, allowed by a rule in `settings.json` for exactly that command.
- 2026-11-23: on the review findings: "давай по рекомендациям": the findings with a recommendation (1, 3 to 8) fixed as recommended.
- 2026-11-23: on the residual width of finding 2 (a `goto file://` to any file from the mockup session): "добавляй": `guard-bash.mjs` closes it.
- 2026-11-23: on the five findings of the guard review: "исправляй все пять".
- 2026-11-23: on the guard's size (81 lines where "a few lines" was promised): "давай" to the version on exact forms, about 25 to 30 lines.
- 2026-11-23: "принимаю, коммить": the change accepted.

## Handover

- Waiting for: `/pr`. Accepted and committed; stage 8 added nothing (no guide passage, decision or roadmap item). The re-review of the shortened guard on `sonnet` is NON-BLOCKING with two minors: (1) a `$`, backtick, ` -s` or ` --s` in typed text is refused; not narrowed in code, since playwright-cli also reads `--session` after the subcommand, so the limit is named in the hook's message and `manual-tester.md`; (2) a command that splits both `playwright-cli` and the variable name with quotes escapes the guard; deliberate evasion only, and the allow rule in `settings.json` does not match it, so the owner is asked. Left as is.
- Guard review findings (`reviewer` on `sonnet`, NON-BLOCKING), all five fixed on the owner's "исправляй все пять":
  1. major: the guard reads the session only from `-s`, `-s=` and `--session`; playwright-cli also takes `--s=mockup` (minimist) and `PLAYWRIGHT_CLI_SESSION=mockup`, and then `goto`/`eval` reach the file-enabled session. Fix: deny `PLAYWRIGHT_CLI_SESSION`, `$` and backticks in a playwright-cli segment, and any session option other than `-s=<name>`, `-s <name>`, `--session=<name>`, `--session <name>`.
  2. minor: the variable name is matched in quoted text too, so a commit message or a `grep` that mentions it is refused. Fix: match it only in what the shell runs (`executed()`, already in the hook).
  3. minor: the mockup URL is not tied to the repository: any `/…/.screens/x/mockups/y.html` passes. Fix: require it under the repository root.
  4. minor: `--browser chromium` (a space) makes `chromium` a second argument and refuses the allowed open. Fix: take the value of `--browser` with the option.
  5. minor: `manual-tester.md` does not say what to do on a refusal. Fix: "a refusal means the command is not allowed there; do not rephrase it".
- To read: this brief; `git diff origin/main`.
- Documents changed: none in `docs/` besides this brief; `.claude/README.md` changed (Cost and context: coordinator shares 42–91% with enrollment-mentors, track-mentors, track-mentor-students; the mockup row gains track-mentor-students; new rows for mockup rounds with `general-purpose` models and for screen runs against the mockup; the context budget row gains the 150k reminder; the coordinator reminder row gains its result since the hook).
- State: the fix round is done (findings 1 to 8); the re-review on `sonnet` was CLEAN. Then, on the owner's "добавляй", `guard-bash.mjs` gained a guard: the file-access variable or `allowUnrestrictedFileAccess` anywhere, `--config` in `playwright-cli`, and any `file:` URL are refused except `-s=mockup open` of one `.screens/*/mockups/*.html` without `..`; the `mockup` session takes only commands that look at the page (`close`, `click`, `hover`, `press`, `mousewheel`, `resize`, `snapshot`, `find`, `screenshot`, `reload`, `set-color-scheme`, `clear-color-scheme`). After the guard review and the owner's "давай" to a shorter guard, the block accepts exact forms instead of parsing: file access (the variable, `allowUnrestrictedFileAccess`, `cli.config.json`, `--config`, `file:`) only by the mockup command of `manual-tester.md` exactly as written, under the repository root; a session only as `-s=<name>`; the `mockup` session only with the whitelisted commands. 28 lines, at the end of the hook (it uses `executed()`). Tested with 41 synthetic hook inputs (all as expected, the old guards included) and a live open of a temporary mockup; a live block could not be shown, because this session loads the hooks of the main checkout, where the old hook runs until the merge. `manual-tester.md` and the README row name the guard.
- Open for the owner: the narrowed allow rule in `settings.json` stays as the first line; the guard is the second. Out of this change: `playwright-cli` itself does not limit the files of `upload`, `state-load` and `--filename` in any session (its skill mode skips the file check); a mockup's own link to `file:` would be followed by a `click`; a `.playwright/cli.config.json` written with the Write tool is not seen by a Bash hook (`manual-tester` has no Write tool).
- On acceptance: stage 8 adds nothing to a guide (no guide passage in this change), no decision, no roadmap item; commit if the owner asks, then `/pr`.
- On remarks: as the owner says.

## Agents

<!-- Agents can be continued only within the session that started them; a new session starts fresh ones from this brief. -->

| Agent | Model | Id | Context |
|---|---|---|---|
| api-lookup | definition | a7623bbad47d7efdf | 43k |
| reviewer | definition | accf95d1df7e0d313 | — |
