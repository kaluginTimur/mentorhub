---
name: browser
description: How an agent looks at the running app and at an approved mockup in a headless browser - playwright-cli commands, sessions per persona, sign-in, screenshots, reaching states, the restricted mockup session. Preloaded into manual-tester and frontend-dev; not a task of its own.
---

The procedure for looking at the running MentorHub application and at an approved mockup. The stack must be up first (`node scripts/stack.mjs start`, as your own definition says). What you do with what you see, report it or fix it, is in your own definition.

## The browser

Run `playwright-cli` from `frontend/` with the long form `~/.nvm/nvm-exec npx playwright-cli`, one named session per persona (`-s=<persona>`), so personas do not share cookies:

```
cd <repo>/frontend && ~/.nvm/nvm-exec npx playwright-cli -s=<persona> open http://localhost:4200 --browser=chromium
cd <repo>/frontend && ~/.nvm/nvm-exec npx playwright-cli -s=<persona> snapshot --filename=../.screens/<change>/<run>/snapshot.md
cd <repo>/frontend && ~/.nvm/nvm-exec npx playwright-cli -s=<persona> click|fill|goto|press|select ...
cd <repo>/frontend && ~/.nvm/nvm-exec npx playwright-cli -s=<persona> screenshot --filename=../.screens/<change>/<run>/<screen>-<persona>-<state>.png
cd <repo>/frontend && ~/.nvm/nvm-exec npx playwright-cli -s=<persona> close
```

`--browser=chromium` is needed on every `open`: the default looks for Google Chrome. `<change>` is the plan's name, `<run>` the output of `date +%Y%m%d-%H%M`, taken once per run; create the folder first (`mkdir -p <repo>/.screens/<change>/<run>`). Sign in through the Keycloak form with the persona's email and `mentorhub`. Close every session when done.

A snapshot is long (about 400 lines for a 35-row page): write it to a file with `--filename` and search it with `grep` or `head`, or snapshot one element (`snapshot <locator>`), never read it whole. Prefer role and text locators (`role=searchbox`, `text=Clear filters`, `role=button[name=Retry]`) over snapshot refs, which change with every snapshot and navigation. The DEV-3 switch is a native select: `select "role=combobox[name='Simulated API failures']" "<mode>"`, with the mode `normal`, `slow`, `server-error` or `offline`. After a `goto` that may redirect (sign-in, access screens), wait or snapshot again before judging the URL or the screen. Use `screenshot --full-page` for a page longer than the viewport.

Use the snapshot to find elements and see states. Take a screenshot for every screen, persona and state you judge, and read it back with `Read` to judge the look: layout, overflow, alignment, truncated text, empty areas, unreadable contrast, a state that does not match the requirements.

## Against the mockup

The owner approved the mockup, so a difference from it is a defect unless the plan's Design overrides it (the Design says where it decides). Owners found such differences themselves when the screen run only judged plausibility (track-mentor-students: a seam in a tint, an underline, a button).

1. Open the mockup in a session of its own, `mockup`, and only there: `playwright-cli` blocks `file:` unless the variable is set when the session starts, and the owner allowed exactly this command.
   ```
   cd <repo>/frontend && PLAYWRIGHT_MCP_ALLOW_UNRESTRICTED_FILE_ACCESS=true ~/.nvm/nvm-exec npx playwright-cli -s=mockup open file://<repo>/.screens/<change>/mockups/<screen>.html --browser=chromium
   ```
   Open nothing but the mockup in that session: it can read every local file, so `guard-bash.mjs` lets it only look at the page (no `goto`, `eval` or other navigation, script or file command); run the command above exactly as written, and name every session as `-s=<name>`, with no `$`, backtick, ` -s` or ` --s` elsewhere in a `playwright-cli` command, typed text included. A refusal means the command is not allowed there: do not rephrase it. Switch it between the themes with the mockup's theme button.
2. For each state of the chosen variant that the mockup shows (its headings name the state and the width), screenshot that part of the mockup (`screenshot <locator> --filename=../.screens/<change>/<run>/mockup-<state>-<width>-<theme>.png`) and the built screen in the same state, at the same width (`resize 1280 800`, `resize 390 844`) and in the same theme. Read both back. The app switches its theme in the top bar: the button named `Theme: …` opens a menu with Light, Dark and System.
3. Name every visual difference: tints and backgrounds, lines and borders, underlines and other text decoration, spacing, alignment, sizes, icons, texts. Keep both screenshots for each, and the plan's line when the Design decides it. Data that differs (other names, other counts) is not a difference.

## Reaching states

- **A role's view:** sign in as the persona who has the role.
- **Loading and error:** with the screen open, turn on the failure switch of the development mode (requirement DEV-3, `docs/requirements/development-mode.md`), then act on the screen (a reload turns the mode off). A screen's own first-load error is out of reach this way; say so rather than skip silently.
- **Empty and the rest:** make the data change in the UI (filter to nothing, revoke, accept, and so on). Change and add data whenever a scenario or a state needs it, and list every change. When the sample center could hold the state itself, say so: it is a gap in the sample data.
