# Schema checks review

Branch: `chore/schema-checks-review`
Roadmap item: none

## Scope

Goal: the schema keeps only the constraints that guard data invariants; the checks that only repeat the services' validation go, so a rule lives in one place (as the owner did for the tracks checks on 2026-11-15).

Remove from `V1__schema.sql`, edited in place:

- `users.email <> ''` and `invitations.email <> ''`: the services validate the email.
- `invitations_token_ck`: tokens are generated and their format checked only by `InvitationTokens`.
- `char_length(failure) between 1 and 500` on `invitation_emails`: `InvitationEmailFailure` bounds the length.

Keep: the enum value checks (`status`, `reason`, `disabled_reason`), the checks across columns, unique indexes and foreign keys.

Requirements: INV, UADM and IEM searched; behavior does not change, since the services keep their validation, and no requirement describes these constraints or should.

## Tasks

| Task | Who | Status |
|---|---|---|
| Patch of `V1__schema.sql`, applied by the owner (the migration guard blocks agents) | coordinator, owner | done |
| Backend verification | verifier | done |
| Review | reviewer | done |
| Review fixes chosen by the owner | backend-dev | done |

## Partial work

## Decisions

- 2026-11-17: Risks limited to the dev database (sample data written by SQL) are not a reason to keep a check: they show on a dev start and cost little.
- 2026-11-17: `invitations_token_ck` is removed with the email and failure length checks.
- 2026-11-17: Enum value checks stay (recommendation accepted).
- 2026-11-17: V1 is edited in place: no database outside the repository has applied it.
- 2026-11-17: The migration guard is not bypassed; the owner applies the patch.
- 2026-11-18: The path stays `chore/`: the rule that sends every migration to the stages is fixed first in a separate chore (`chore/migration-without-behavior`), which lets a migration that changes no requirement and no contract go through a chore.
- 2026-11-18: Review findings: delete the test of the removed failure check, fix the `InvitationEmailFailure` Javadoc and rename its test; `TextInput` stays.

## Handover

- Waiting for: `chore/migration-without-behavior` to merge; then rebase this branch on `origin/main` and run `/pr` (`check-path` fails until then). The brief is committed on this branch to survive the switch; `/pr` deletes it and commits the deletion.
- To read: this brief, `git diff origin/main`.
- Documents changed: none.
- State: the owner applied the patch to `V1__schema.sql`. Verification PASS: `./mvnw -B -q verify`, check-docs, requirements tests 64/64, trace 247/247. Review NON-BLOCKING, three minors: (1) `InvitationEmailSchemaTests.acceptsAFailureOf500Characters` tests the removed check and can no longer fail; (2) `InvitationEmailFailure` Javadoc cites the removed check, and `InvitationEmailFailureTests.theConstructorRejectsTextTheDatabaseWouldNotStoreAsIs` is named after it; (3) `TextInput.java:18` mentions PostgreSQL's `char_length`, which no check uses now. Every write path to the four columns goes through the validation the brief names (the bootstrap administrator's email is checked by `hasText` against the configured address).
- Open questions: none. Findings 1 and 2 fixed by `backend-dev` (`./mvnw -B -q verify` PASS), 3 left.
- On approval: after that merge, `/pr`.
- On remarks: `/chore schema-checks-review`.

## Agents

| Agent | Model | Id | Context |
|---|---|---|---|
| backend-dev | inherited | review fixes | small |
