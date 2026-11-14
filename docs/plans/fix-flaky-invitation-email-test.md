# Fix flaky invitation email resubmission test

Branch: `chore/fix-flaky-invitation-email-test`
Roadmap item: none

## Scope

- **Goal:** Make `InvitationEmailTests$Resubmitting.aSendStillInProgressIsNotStartedAgain` deterministic under any thread scheduling without changing application behavior.
- **Approach:** Replace the racy `assertThat(resubmittedPublications()).isEqualTo(1)` assertion in `backend/src/test/java/com/mentorhub/users/internal/invitation/InvitationEmailTests.java` with a deterministic check on `event_publication.completion_attempts` (incremented from `1` to `2` by `JdbcEventPublicationRepositoryV2.markResubmitted`, and untouched by `markProcessing` while the first send is still held by `release`). Keep `resubmittedPublications()` or adjust helper/comments only if needed for accuracy across `InvitationEmailTests`.
- **In scope:** `backend/src/test/java/com/mentorhub/users/internal/invitation/InvitationEmailTests.java`.
- **Out of scope:** Application code, requirements, API contract, migrations.
- **Requirements:** Searched `docs/requirements/README.md` and `docs/requirements/invitation-emails.md` (`IEM-6`). No requirement describes this internal concurrency detail and none should; `aSendStillInProgressIsNotStartedAgain` is an ordinary test without a scenario id.

## Tasks

| Task | Who | Status |
|---|---|---|
| T1: Replace racy `RESUBMITTED` status assertion in `InvitationEmailTests` with deterministic `completion_attempts` check and verify backend build | `backend-dev` | done |

## Partial work

## Decisions

- 2026-11-14: Confirmed goal, approach (replacing racy `RESUBMITTED` status check with `completion_attempts` check), and path (`chore/`), and confirmed removing `logs_118087506546.zip` ("confirm. logs can be removed").

## Handover

- Waiting for: owner acceptance of the fix, review, and verification.
- To read: `docs/plans/fix-flaky-invitation-email-test.md` and `git diff main`
- Documents changed: none (`git status --short`: `M backend/src/test/java/com/mentorhub/users/internal/invitation/InvitationEmailTests.java`, `?? docs/plans/`; `git diff --name-status main...HEAD`: empty)
- State: T1 done; `reviewer` CLEAN; `verifier` PASS for `backend` (`./mvnw -B -q verify -Dmaven.test.redirectTestOutputToFile=true`, 612/612 testcases in Surefire XML reports) and `docs` (`check-docs.mjs --since main`, `requirements.test.mjs` 64/64, `requirements.mjs trace` 206/206).
- Open questions: none
- On approval: commit when asked (keeping `docs/plans/fix-flaky-invitation-email-test.md` until the `pr` skill removes it before opening the pull request), then suggest running `/pr`.
- On remarks: apply remarks and re-verify.

## Agents

| Agent | Model | Id | Context |
|---|---|---|---|
| `backend-dev` | `pro` | `94e71c91-00d3-457f-87a9-e7729e828b76` | T1 |
| `reviewer` | `pro` | `ac8a7ea9-d0b7-4b70-926d-43abf7d94f91` | Review against `main` |
| `verifier` | `inherit` | `c8a7ec68-fd93-49d8-b09c-4d4a8c4a8177` | Verify `backend` and `docs` |
