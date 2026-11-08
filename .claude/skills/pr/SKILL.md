---
name: pr
description: Prepare and open a GitHub pull request for the current branch, with a description generated from the changes, the requirements and the verification, after the user approves the draft.
argument-hint: "[base-branch]"
arguments: [base]
disable-model-invocation: true
---

Open a pull request for the current work. Base branch: `$base` (if empty, the repository's default branch).

A pull request is published outside the machine: nothing is pushed or created until the user approves the draft.

## Prepare

1. Check that the GitHub CLI is signed in (`gh auth status`). If not, ask the user to run `! gh auth login` and stop.
2. If the current branch is the base branch, propose a branch name (`feature/<change>` for a change with requirements, `fix/<topic>` otherwise) and create it when the user agrees.
3. If there are uncommitted changes, show them and ask whether to commit them. Invoking this command is not a request to commit. Commit messages follow the language rule in `docs/constitution.md`.
4. Collect what goes into the pull request: the commits and the diff from the base branch, including the changes under `docs/requirements/`. If this session ran `/feature`, reuse its final report instead of recomputing it. If the plan is still there, it should not be: it is deleted before the pull request, as stage 8 of `docs/process.md` says, so ask. Its last version is in the branch's history, with the user's decisions and the accepted deviations.

## Check

1. Run the `verifier` subagent for the areas the changes touch, unless this session already verified the same commit.
2. Run the check from the **Verification** section of `docs/development/docs.md` with `--since <base>` to list the documents that refer to changed files.
3. A `docs/plans/` file still on the branch counts as red. If anything is red, report it and stop, unless the user explicitly wants a draft pull request despite it.

## Draft

Write a title (imperative, under 70 characters) and a description following [template.md](template.md). Keep only what applies; be concrete.

Show the user the title and the description, and ask whether to open it as a draft or ready for review. Apply their corrections.

## Publish

After the user approves:

1. `git push -u origin <branch>`.
2. `gh pr create --base <base> --title "<title>" --body "<description>"` (add `--draft` if chosen), passing the description through a quoted heredoc.
3. Give the user the pull request URL.
   If you can subscribe to the pull request's activity, do not subscribe while it is green and only waits on reviewers: every event wakes the session and re-reads its whole context. Offer it for red CI or review work, or when the user asks.
4. If this session ran the `review` phase of `/feature` for this pull request, record the session again as its `review` phase (`node .claude/scripts/agent-usage.mjs --record <change> review`, without a session id; it replaces this session's earlier record), so the record covers the work after the final commit. Check that the session it prints is this one, and offer to commit the record on the branch and push it.
