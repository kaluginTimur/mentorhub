---
name: pr
description: Prepare and open a GitHub pull request for the current branch, with a description generated from the changes, the spec and the verification, after the user approves the draft.
argument-hint: "[base-branch]"
arguments: [base]
disable-model-invocation: true
---

Open a pull request for the current work. Base branch: `$base` (if empty, the repository's default branch).

A pull request is published outside the machine: nothing is pushed or created until the user approves the draft.

## Prepare

1. Check that the GitHub CLI is signed in (`gh auth status`). If not, ask the user to run `! gh auth login` and stop.
2. If the current branch is the base branch, propose a branch name (`feature/<feature>` for a spec, `fix/<topic>` otherwise) and create it when the user agrees.
3. If there are uncommitted changes, show them and ask whether to commit them. Invoking this command is not a request to commit. Commit messages follow `AGENTS.md`.
4. Collect what goes into the pull request: the commits and the diff from the base branch, and the related spec in `docs/features/` if the changes belong to a feature. If this session ran `/feature`, reuse its final report (AC coverage, verification) instead of recomputing it. If the feature has a progress log (`docs/features/<feature>.progress.md`), take the user's decisions and the accepted deviations from it.

## Check

1. Run the `verifier` subagent for the areas the changes touch, unless this session already verified the same commit.
2. Run the check from the **Verification** section of `docs/development/docs.md` with `--since <base>` to list the documents that refer to changed files.
3. If anything is red, report it and stop, unless the user explicitly wants a draft pull request despite it.

## Draft

Write a title (imperative, under 70 characters) and a description following [template.md](template.md). Keep only what applies; be concrete. Follow the rules for commit messages in `AGENTS.md` for the title and description too: no trailers, no "Generated with" lines, no mention of AI agents or models.

Show the user the title and the description, and ask whether to open it as a draft or ready for review. Apply their corrections.

## Publish

After the user approves:

1. `git push -u origin <branch>`.
2. `gh pr create --base <base> --title "<title>" --body "<description>"` (add `--draft` if chosen), passing the description through a quoted heredoc.
3. Give the user the pull request URL.
