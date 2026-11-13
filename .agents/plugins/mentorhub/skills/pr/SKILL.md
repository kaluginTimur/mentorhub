---
name: pr
description: Prepare and open a GitHub pull request for the current branch, with a description generated from the changes, the requirements and the verification, after the user approves the draft.
---

Open a pull request for the current work. Base branch: `<base>` (if omitted, the repository's default branch).

A pull request is published outside the machine: nothing is pushed or created until the user approves the draft.

## Prepare

1. Check that the GitHub CLI is signed in (`gh auth status` via `run_command`, using `BypassSandbox: true` if network/auth access is needed). If not, ask the user to run `gh auth login` and stop.
2. If the current branch is the base branch, stop and use the `change` skill: nothing is committed there, and the branch's prefix names the path of the change.
3. If there are uncommitted changes, show them and ask whether to commit them. Invoking this skill is not a request to commit. Commit messages follow the language rule in `docs/constitution.md`.
4. Collect what goes into the pull request: the commits and the diff from the base branch, including the changes under `docs/requirements/`. If this session ran the `feature` skill, reuse its final report instead of recomputing it. On a `feature/` branch, a plan still there should not be: it is deleted at the finish, as stage 8 of `docs/process.md` says, so ask. Its last version is in the branch's history (`git log -p -1 -- docs/plans/`), with the user's decisions and the accepted deviations. On any other branch the file in `docs/plans/` is a brief, and it should still be there: read the user's decisions and the answers for each guide passage from it. Without one on a `chore/` or `change/` branch, look in the branch's history, and ask the user if it is not there either.

## Check

1. Register (if not yet registered in the session, from `.claude/agents/verifier.md` per `.agents/plugins/mentorhub/rules/AGENTS.md`) and invoke the `verifier` subagent for the areas the changes touch, unless this session already verified the same commit.
2. Run the check from the **Verification** section of `docs/development/docs.md` with `--since <base>` (`node scripts/check-docs.mjs --since <base>`) to list the documents that refer to changed files.
3. Run `node scripts/check-path.mjs <base>`: it fails when the branch's prefix names no path, or the diff holds what that path does not change. A failure means the change is on the wrong path: use the `change` skill, do not rename the branch to make the check pass.
4. A plan or a `docs/explorations/` file still on the branch counts as red. A brief does not: it is deleted in **Publish**. If anything is red, report it and stop, unless the user explicitly wants a draft pull request despite it.

## Draft

Write a title (imperative, under 70 characters) and a description following `.claude/skills/pr/template.md`. Keep only what applies; be concrete.

Show the user the title and the description, and ask whether to open it as a draft or ready for review. Apply their corrections.

## Publish

After the user approves:

1. If the branch has a brief, delete it now that the description holds its decisions and answers, and commit the deletion if the brief was ever committed.
2. Push the branch (`git push -u origin <branch>` via `run_command`, using `BypassSandbox: true` if network access is needed).
3. Create the pull request (`gh pr create --base <base> --title "<title>" --body "<description>"`, adding `--draft` if chosen), passing the description through a quoted heredoc.
4. Give the user the pull request URL.
