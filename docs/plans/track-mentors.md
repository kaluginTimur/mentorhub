# Mentors of a track

Branch: `feature/track-mentors`
Roadmap item: Tracking core, 1. **Mentors of a track** (the first of the two parts it was split into)
Requirements approved: 2026-11-21, "требования принимаю" (the files of the handover as they stood then)

## Scope

Goal: a coordinator of a track sees who teaches on it, and a mentor of another track is not assigned to its enrollments by mistake. Today ENR-9 assigns any mentor to an enrollment on any track, and the candidates (ENR-10) are every mentor of the center.

Approach, confirmed at the path gate: an explicit list of the mentors of a track, alongside its coordinators (TRK-6):

- the mentors of a track are named and removed, with a list of candidates, and each change is audited;
- only a mentor of the track is assigned to an enrollment on it, and the candidates of ENR-10 narrow to them;
- the track page shows the mentors of the track.

Rejected alternative: deriving the mentors of a track from the mentors of its enrollments, with no list and no migration; a new mentor without students would not show, and the assignment would not be restricted.

In: naming and removing the mentors of a track, the restriction of the mentors of an enrollment, the list on the track page, the history, the sample center's data.

Out, as the second feature split from the roadmap item: the number of students of each mentor on the track page and, on request, those students. No search scenario for the candidates of ENR-14, as for ENR-10.

## Requirements

- Enrollments: ENR-13 Mentors of a track and ENR-14 their candidates (new, scenarios ENR-13.1 to 13.7, ENR-14.1); ENR-1 (naming covered by the permissions; ENR-1.4 changed, ENR-1.7 new); ENR-6 (ENR-6.3 new); ENR-7 (the naming on the track page; ENR-7.1 changed); ENR-9 (only a mentor of the track; ENR-9.1, 9.2, 9.4 changed); ENR-10 (candidates narrowed; ENR-10.1 changed); Purpose, Known limitations, Out of scope.
- Tracks: TRK-1, TRK-4 (mentors with marks; TRK-4.3 new), TRK-10, Purpose, Out of scope.
- Audit: AUD-7, four new actions.
- Authorization: descriptions of `TRACKS_READ` and `ENROLLMENTS_MANAGE`, the note on the mentor role.
- Roadmap: item 1 split; the second part is the new item 2.
- Sample center data (development mode): mentors of each track, so that every mentor of an enrollment is a mentor of its track; a detail of the code, DEV unchanged.

## Design

Agent's proposal, for the plan gate.

**Where it lives.** The mentors of a track are a relation of the `tracks` module, beside its coordinators: a table `track_mentors` (track, user), written and read by `tracks`, the four audit actions in `TrackAuditAction`, the operations in `TracksController` under the `Tracks` tag. `enrollments` already depends on `tracks`, and `tracks` asks `enrollments` through the interface it owns (`ActiveEnrollments`, implemented in `enrollments`); the check of ENR-13.4 (the user leads an active enrollment on the track) is added to that interface, so no module cycle appears. The permission and relation check of ENR-1 is the one `canEnroll` already uses.

**Contract** (`api/openapi.yaml`):

- `Track.mentors` (required): `TrackMentor` items `{id, name, email, disabled, mentorRole}`, sorted like the coordinators; `canEnroll` keeps its name and its description adds that the same check allows naming and removing the mentors of the track (ENR-1: "the track tells the caller whether they may enroll on it and name its mentors").
- `GET /api/v1/tracks/{id}/mentor-candidates` (`listTrackMentorCandidates`): paged with `q`, `page`, `size` as `listMentorCandidates`; returns `MentorCandidatePage`, its description widened to "not a mentor of the track or the enrollment it is listed for"; 409 `track-archived`.
- `PUT /api/v1/tracks/{id}/mentors/{userId}` (`addTrackMentor`) and `DELETE` (`removeTrackMentor`): no body, 200 with the `Track`; PUT 409 `track-archived` or `mentor-not-eligible`, DELETE 409 `track-archived` or `mentor-leads-enrollments` (new problem type). Checked in the order of ENR-1 and ENR-13: the permission (403), the parameters (400), the track (404), the relation (403), the status (409 `track-archived`), then the eligibility or the enrollments the mentor leads.
- `assignEnrollmentMentor` and `listMentorCandidates`: the description says the mentor must be a mentor of the enrollment's track; `mentor-not-eligible` covers a user who is not.
- `TrackHistoryAction` and the user history's action enum gain the four actions of AUD-7, with the details the coordinator entries have.

**Schema.** The owner writes the schema change into `V1__schema.sql` themselves (decision of 2026-11-21): `backend-dev` puts the SQL under **Schema change** below and does not edit `V1__schema.sql`. No data is carried over.

**Concurrency** (ENR-13.6, 13.7, TRK-5.4). Naming, removing, archiving, enrolling and assigning a mentor of an enrollment take the same lock of the track row, so they run one after another; the assignment checks the track's mentors under it. A repeated parallel naming is settled by the primary key with an `on conflict` insert.

**Sample center** (DEV unchanged): every mentor of an enrollment is a mentor of its track; one track has a mentor marked disabled or without the mentor role; one active track has no mentor; at least one active user with the mentor role is a candidate somewhere, so every state of the track page and both dialogs can be seen.

**Frontend.** The track page gets a **Mentors** card under **Coordinators**, built the same way: the mentors with their marks (`No mentor role`, disabled as for coordinators), `No mentors` when empty, **Add mentor** and a removal after a confirmation for a caller with `canEnroll` on an active track. The add dialog offers the candidates with search and pages, as the dialog that assigns a mentor to an enrollment does; reuse it where it fits. ENR-7.1: a refusal (`track-archived`, `mentor-not-eligible`, `mentor-leads-enrollments`) shows a message and reloads the track and its enrollments. The track's and the user's history show the four new actions. No mockup: the card follows the coordinators' pattern.

### Schema change

Goes into `backend/src/main/resources/db/migration/V1__schema.sql` right after `create table track_coordinators (...)`, before `create table enrollments`:

```sql
-- Who mentors on which track. Only an active user with the MENTOR role is named, which the service checks; the row
-- stays when the user is later disabled or loses the role. The primary key decides parallel namings.
create table track_mentors (
    track_id   uuid        not null references tracks (id) on delete restrict,
    user_id    uuid        not null references users (id) on delete restrict,
    created_at timestamptz not null default now(),
    primary key (track_id, user_id)
);
```

No further index: the reads go by track (the primary key's leading column), as for the coordinators.

## What the tests rely on

- API: the operations and schemas above, once in the contract.
- Track page: a card with the heading `Mentors`; each mentor by name and email; marks `No mentor role` and the disabled mark used for coordinators; `No mentors` when empty; a button `Add mentor`; a remove action per mentor named as the coordinators' is, followed by the shared confirmation dialog.
- Add dialog: a title naming the track, the candidates with a search field and the shared paginator, as in the enrollment mentor dialog.

## Tasks

| Task | Scenarios | Who | Status |
|---|---|---|---|
| T1 Contract, the schema SQL for the owner, stubs that keep the build green | all | backend-dev | done |
| T2 Red tests | ENR-1.4, 1.7, 6.3, 7.1, 9.1, 9.2, 9.4, 10.1, 13.1–13.7, 14.1; TRK-4.3, 5.1, 5.4 | test-engineer | done |
| T3 Backend: mentors of a track, candidates, `Track.mentors`, the narrowed assignment and candidates of an enrollment, the lock, audit and both histories, sample center data (checkpoint: backend green) | all backend ones | backend-dev | pending |
| T4 Frontend: Mentors card, add dialog, removal, refusals (the history texts are done, early, to keep the build green) | ENR-7.1, TRK-10 | frontend-dev | pending |

Status: `pending`, `in progress`, `in review`, `done`. Name a task "checkpoint" when the user wants to stop after it.

## Partial work

## Notes for later tasks

- T1 (backend-dev): the three new operations in `TracksController` answer 501; `TracksMapper.track` passes `List.of()` for `mentors`; nothing writes `TrackChange.MentorAdded/MentorRemoved` or `UserChange.TrackMentorAdded/TrackMentorRemoved` yet, while the history read side for them works. The domain audit enums already hold the four actions (`ContractEnumsTests` compares them with the contract).
- T1 (backend-dev): `tracks` cannot reuse `EnrollmentProblems.MENTOR_NOT_ELIGIBLE` (internal to `enrollments`); T3 adds `mentor-not-eligible` and `mentor-leads-enrollments` to `TrackProblems`.
- T1 (backend-dev): `track_mentors` exists only once the owner pastes the SQL into V1; tests touching it fail with SQL errors until then. In `dev`, `SampleCenter.restore()` cleans and migrates on every start, so the V1 change needs no reset there; a database migrated without `dev` would fail Flyway validation and have to be dropped (the owner is asked whether one exists).
- Early T4 (frontend-dev): history texts "Named {user} a mentor", "Removed {user} as a mentor", "Named a mentor of {track}", "Removed as a mentor of {track}", placed after the coordinator entries.
- test-engineer: `track()` in `features/tracks/test-data.ts` defaults `mentors: []`.
- T2 (test-engineer), for T3: `Track.mentors` sorted by name like the coordinators, with `disabled` and `mentorRole`; mentor-candidates returns `items` and `totalItems`; checks in the plan's order, an unknown track is 404 `not-found` for admin and coordinator alike (ENR-1.4); `TRACK_MENTOR_*` entries carry `actor` and `user`, `USER_TRACK_MENTOR_*` carry `actor` and `track` (id, code, name at that moment); ENR-13.7 and TRK-5.4 need the one track-row lock shared by naming, removing, archiving, enrolling and assigning; the response validator rejects any status or problem type not in the contract. The ENR-9.1 and ENR-9.4 tests already pass (the new rule only adds refusals) and must stay green. Test helpers: `TestTracks.addMentor`; `TestUsers.deleteAll` clears `track_mentors`.
- T2 (test-engineer), for T4: a button `Add mentor`, only on an active track and only with `canEnroll`; per mentor a button with `aria-label="Remove <name>"`, then the shared confirmation dialog with the confirm button `Remove`; the add dialog has a `Mentor` field (or its first input), clicked to open the candidates, and a confirm button `Add`, the candidate picked as in the assign-mentor tests of `track-enrollments.spec.ts`; test data `trackMentor()` and `MENTOR_ID` in `test-data.ts`.

## Red tests

Commit: <the commit holding the requirements, the contract, this plan and the red tests>

## Decisions

- 2026-11-21: "менторов на трек назначают координаторы этого трека" (the mentors of a track are named by the coordinators of that track).
- 2026-11-21: "бей на две фичи" — split the roadmap item: this feature is the mentors of a track; the students of each mentor on the track page are a second feature.
- 2026-11-21: "1 да" — administrators also name the mentors of any track (`ENROLLMENTS_MANAGE`), coordinators of their track (`ENROLLMENTS_MANAGE_COORDINATED`); no new permission.
- 2026-11-21: "2 (a)" — removing a mentor of a track who leads an active enrollment on it is refused (409 `mentor-leads-enrollments`).
- 2026-11-21: "5 с пометкой" — the track shows all its mentors, a disabled one or one without the mentor role marked, as its coordinators.
- 2026-11-21: "остальное ок" — eligibility, repeats, archived track and parallel requests as for coordinators (TRK-6); enrollment mentors only from the track's mentors; four audit actions mirroring the coordinators'.
- 2026-11-21: "sql миграции можно будет дать мне, чтобы я записал его в v1" — the schema change goes to the owner as SQL, and the owner writes it into `V1__schema.sql` instead of a new migration.
- 2026-11-21: "всё по рекомендациям" (requirements review): findings 1 to 8, 11 and 12 applied; no search scenario for ENR-14; no further split.
- 2026-11-21: "таких, за пределами sample center, нет. так что такой миграции не нужно" — no existing enrollment mentors outside the sample center, so the schema change carries no data over.
- 2026-11-21, plan gate: "схему я руками дополнил, схема v1 ещё никуда не применялась. можешь продолжать" — plan and contract approved; the owner wrote the SQL of **Schema change** into `V1__schema.sql` as given; no database was migrated without `dev`, so nothing has to be dropped.

## Handover

- Waiting for: the owner agrees to the red commit (requirements, contract, the owner's V1 change, the T1 stubs, the plan, the red tests, the usage record), or has remarks on the tests.
- To read: this plan; the new tests `backend/src/test/java/com/mentorhub/tracks/internal/web/TrackMentorsEndpointTests.java`, `frontend/src/app/features/tracks/track-mentors.spec.ts`, and `git diff backend/src/test frontend/src/app/features/tracks/test-data.ts`.
- Documents changed since the last stop: this plan (plan-gate approval in Decisions, T2 done, Notes from T2). The owner's change to `V1__schema.sql` (table `track_mentors`).
- State: T2 done. Backend: 791 tests, 29 failing, all new or changed ones, on assertions (501 rejected by the response validator, empty `mentors`, ENR-9.2 assigns a user off the track, ENR-10.1 lists users off the track, TRK-5.4 does not wait for the lock); ENR-9.1 and ENR-9.4 pass already. Frontend: 414 existing pass, 3 new (ENR-7.1) fail on assertions. `trace`: 270 of 270 scenarios have tests, 4 manual. Nothing committed.
- Open questions: none.
- On agreement: commit, write the commit into Red tests, then brief `backend-dev` for T3 (a fresh agent; its last context was 108k, but the earlier agent is cold).
- On remarks: relay them to `test-engineer`.

## Agents

| Agent | Model | Id | Phase | Context |
|---|---|---|---|---|
| reviewer | definition default | a75f4fb72747262c2 | spec: requirements review | 79k |
| backend-dev | definition default | aac54c3bbeb9bde94 | plan: T1 | 108k |
| frontend-dev | definition default | a7886742f5099df2d | plan: early T4 history texts | 43k |
| test-engineer | definition default | a4b8f8775bb676be0 | plan: test-data fix | small |
| test-engineer | definition default | a52b0b9f1e262d0eb | tests: T2 red tests | 143k |
