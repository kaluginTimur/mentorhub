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
2. If the current branch is the base branch, stop and go to `/change`: nothing is committed there, and the branch's prefix names the path of the change.
3. If there are uncommitted changes, show them and ask whether to commit them. Invoking this command is not a request to commit. Commit messages follow the language rule in `docs/constitution.md`.
4. Collect what goes into the pull request: the commits and the diff from the base branch, including the changes under `docs/requirements/`. If this session ran `/feature`, reuse its final report instead of recomputing it. On a `feature/` branch, a plan still there should not be: it is deleted at the finish, as stage 8 of `docs/process.md` says, so ask. Its last version is in the branch's history (`git log -p -1 -- docs/plans/`), with the user's decisions and the accepted deviations. On any other branch the file in `docs/plans/` is a brief, and it should still be there: read the user's decisions and the answers for each guide passage from it. Without one on a `chore/` or `change/` branch, look in the branch's history, and ask the user if it is not there either.

## Check

1. Run the `verifier` subagent for the areas the changes touch, unless this session already verified the same commit.
2. Run the check from the **Verification** section of `docs/development/docs.md` with `--since <base>` to list the documents that refer to changed files.
3. Run `node scripts/check-path.mjs <base>`: it fails when the branch's prefix names no path, or the diff holds what that path does not change. A failure means the change is on the wrong path: go to `/change`, do not rename the branch to make the check pass.
4. A plan or a `docs/explorations/` file still on the branch counts as red. A brief does not: it is deleted in **Publish**. If anything is red, report it and stop, unless the user explicitly wants a draft pull request despite it.

## Draft

Write a title (imperative, under 70 characters) and a description following [template.md](template.md). Keep only what applies; be concrete.

Show the user the title and the description, and ask whether to open it as a draft or ready for review. Apply their corrections.

## Publish

After the user approves:

1. If the branch has a brief, delete it now that the description holds its decisions and answers, and commit the deletion if the brief was ever committed.
2. `git push -u origin <branch>`.
3. `gh pr create --base <base> --title "<title>" --body "<description>"` (add `--draft` if chosen), passing the description through a quoted heredoc.
4. Give the user the pull request URL.
   If you can subscribe to the pull request's activity, do not subscribe while it is green and only waits on reviewers: every event wakes the session and re-reads its whole context. Offer it for red CI or review work, or when the user asks.
5. If this session ran the `review` phase of `/feature` for this pull request, record the session again as its `review` phase (`node .claude/scripts/agent-usage.mjs --record <change> review`, without a session id; it replaces this session's earlier record), so the record covers the work after the final commit. Check that the session it prints is this one, and offer to commit the record on the branch and push it.
