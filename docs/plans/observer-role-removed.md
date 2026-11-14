# Observer role removed

Branch: `feature/observer-role-removed`
Roadmap item: **Observer role removed:** an administrator no longer sees or assigns a role that grants nothing and stands for nobody at the center. Out of scope: any new role. Touches authorization, user administration, invitations and development mode; the role is also in the contract and the schema.
Requirements approved: 2026-11-15, "да, одобряю"

## Scope

Goal: an administrator no longer sees or assigns the observer role. It grants no permission and stands for nobody at the center, so it only confuses role assignment and invitations. No new role is added.

Approach: remove `OBSERVER` everywhere in one change:

- requirements: the row of the PERM-1 role table and the note on roles without permissions; USR-6.1 and INV-12.4, which use observer only as an example role, get another existing role; the Observer persona leaves the DEV-1 table;
- the contract: the value leaves the `Role` and `RoleFilter` enums in `api/openapi.yaml`;
- the schema: the existing `V1__schema.sql` is edited to drop the role (the owner's word, below), with no new migration;
- decision 0004: its list of initial roles no longer names OBSERVER;
- the code: the backend `Role` enum, the frontend (models generated from the contract, the invite dialog test) and the sample center data;
- the local identity provider: the `observer@mentorhub.local` account leaves `infra/keycloak/mentorhub-realm.json`, so DEV-1 keeps Not invited as the only unlinked account (requirements review);
- `README.md`: the observer account, the observer among the sample center users, and `observer@` in the note on recreating the Keycloak container.

Out: any new role; audit entries need nothing, since AUD-7 shows only the roles the running version knows.

## Requirements

Changed: PERM-1 (table) and the authorization known limitation on roles without permissions; USR-6.1 (now mentor and coordinator); INV-12.4 (now the student role); DEV-1 (the Observer persona leaves the table); the user administration out-of-scope line on access to user data no longer names observers. Decision 0004 no longer lists OBSERVER among the initial roles. No new rule, no new scenario.

## Design

- `V1__schema.sql` is edited in place (the owner's word at the path gate). A database where V1 already ran fails Flyway's checksum validation, so a local dev database volume has to be recreated once; nothing is deployed yet (only V1 exists).

## What the tests rely on

- The role values are those of the contract's `Role` enum: ADMIN, COORDINATOR, MENTOR, STUDENT. A test that needs a stored role the version does not know inserts its own row into `roles` (see Notes for later tasks).
- The development personas and their fixed ids are those of DEV-1 without Observer; the other personas keep their ids.

## Tasks

| Task | Scenarios | Who | Status |
|---|---|---|---|
| 1. Contract and schema: `OBSERVER` leaves `Role` and `RoleFilter` in `api/openapi.yaml` and the roles insert in `V1__schema.sql` | PERM-1, USR-6.1, INV-12.4, DEV-1 | backend-dev | done |
| 2. Red tests: changed scenarios, and tests that use OBSERVER as an example or as the unknown stored role | PERM-1.1, USR-6.1, USR-6.3, INV-12.4, DEV-1 | test-engineer | done |
| 3. Backend: `Role` enum, sample center data (`db/sample/users.sql`), the realm account in `infra/keycloak/mentorhub-realm.json`, `README.md` | PERM-1, DEV-1 | backend-dev | pending |
| 4. Frontend: models regenerated from the contract, any test or code naming the role; then manual-tester on the invite dialog and the users list role filter | PERM-1, USR-6.1 | frontend-dev | pending |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

- Task 1: the contract is done by backend-dev; the owner made the `V1__schema.sql` edit themselves, since the project's hook guarding migrations in main refused it to the agent (2026-11-15, "поправил V1 сам, продолжай"). Backend verify: 6 failures expected until task 3 (`RoleTests.contractRolesEqualCodeRoles` and sample center tests: the `Role` enum and the sample data still hold OBSERVER).

## Notes for later tasks

- Red state after task 2: PERM-1.1 (`RoleTests`) and `DevelopmentRealmConsistencyTests` fail on assertions; `RoleTests.contractRolesEqualCodeRoles` and `IdentitySchemaTests.rolesTableEqualsRoleEnum` fail until the enum changes; every dev-profile test class fails to load its context because `db/sample/users.sql` still inserts OBSERVER (task 3). USR-6.1, USR-6.3 and INV-12.4 already pass: only their example role changed.
- Frontend: `npm test` stops on TS2353 at `frontend/src/app/features/users/labels.ts:10` (`OBSERVER` not in `Record<Role, string>`) until task 4; `invite-dialog.spec.ts` expects Administrator, Coordinator, Mentor, Student.

- Tests of USR-6.3 and INV-12.4 insert a stored role the version does not know; `user_roles.role` references `roles`, so once `V1__schema.sql` drops OBSERVER such a test adds a role row of its own instead of relying on OBSERVER (requirements review).
- `--import-realm` skips a realm that already exists, so a Keycloak container created before this change keeps the observer account until it is recreated (which deletes the container's local Keycloak data); with the edited `V1__schema.sql` the dev database volume is recreated too. `README.md` says so for the owner.

## Red tests

Commit:

## Decisions

- 2026-11-15: plan gate approved: "да, одобряю план".

- 2026-11-15: path gate confirmed: goal, approach and path `feature/`, "Подтверждаю, но миграция не нужна - правь существующую, 0004 тоже поправь, ветку и план создай здесь" (no new migration: edit the existing `V1__schema.sql`; also correct decision 0004).

## Handover

- Waiting for: the owner on committing the red tests with the requirements, the contract, the schema and the plan; then tasks 3 and 4.
- To read in full: `docs/requirements/authorization.md` (PERM-1 and Known limitations), `docs/requirements/user-administration.md` (USR-6, Out of scope), `docs/requirements/invitations.md` (INV-12), `docs/requirements/development-mode.md` (DEV-1), decision `docs/architecture/decisions/0004-permissions-in-code-role-assignments-in-database.md`, and this plan.
- Documents changed (`git status`):
  - `docs/architecture/decisions/0004-permissions-in-code-role-assignments-in-database.md`
  - `docs/requirements/authorization.md`
  - `docs/requirements/development-mode.md`
  - `docs/requirements/invitations.md`
  - `docs/requirements/user-administration.md`
  - `docs/plans/observer-role-removed.md` (untracked)
- State: requirements drafted and reviewed once (non-blocking; all three findings applied). Nothing committed: the requirements are committed with the red tests.
- Open questions: none.
- On approval: record it in `Requirements approved:`, then `/feature observer-role-removed`.
- On remarks: `/spec observer-role-removed`.

## Agents

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| reviewer | inherited | a09a705458f3c9fdf | spec | requirements review, one round |
| backend-dev | inherited | a90a9a02e77431f39 | plan | task 1 |
| test-engineer | inherited | afb54232fdd1b1661 | implementation | task 2, first pass |
