# Tracks

## Purpose

The tracks of the training center, such as SWE, QA and DevOps, and who coordinates each. Administrators keep the list current without a developer: they create a track, change its code or name, archive it and restore it, and name and remove its coordinators. A track is never deleted, since enrollments and history will refer to it. Coordinators read every track and change none; a coordinator of a track enrolls students on it, as enrollments (ENR) describe. Roles and permissions are described by authorization (PERM), recorded changes by audit (AUD). The API contract holds the fields, limits and response shapes.

## Requirements

### TRK-1 Who may do what

The system SHALL require `TRACKS_READ` to read tracks, their coordinators and their history, and `TRACKS_MANAGE` to change them. A refusal is 403 `forbidden` and changes nothing (PERM-3).

#### TRK-1.1 Reading tracks needs TRACKS_READ

- GIVEN an active user without `TRACKS_READ`, such as a mentor
- WHEN the user lists tracks, views a track or views a track's history
- THEN each response is 403 `forbidden`

#### TRK-1.2 Changing a track needs TRACKS_MANAGE

- GIVEN a track and an active user with `TRACKS_READ` and without `TRACKS_MANAGE`, such as a coordinator of the track
- WHEN the user creates a track, changes the track's code or name, archives or restores it, lists the candidates for its coordinators, or names or removes a coordinator
- THEN each response is 403 `forbidden`
- AND nothing is changed

### TRK-2 A track

The system SHALL keep for a track a code and a name, both required and neither unique, a status `ACTIVE` or `ARCHIVED`, and its coordinators. A code and a name are stored without surrounding spaces. Creating a track is audited (AUD-7).

#### TRK-2.1 A track is created active without coordinators

- GIVEN an administrator
- WHEN the administrator creates a track with the code ` QA ` and the name `Quality Assurance`
- THEN the response is a new track with the code `QA`, the name `Quality Assurance`, the status `ACTIVE` and no coordinators

#### TRK-2.2 An invalid track is rejected

- GIVEN an administrator
- WHEN the administrator creates a track, or changes one, with a blank code or a blank name
- THEN the response is 400 `validation-failed` naming `code` or `name`
- AND nothing is stored or recorded

#### TRK-2.3 The code and the name of another track are accepted

- GIVEN a track with the code `QA` and the name `Quality Assurance`
- WHEN an administrator creates another track with the same code and name
- THEN the second track is created, and both are listed

### TRK-3 The list of tracks

The system SHALL return, for a caller with `TRACKS_READ`, all tracks of the requested status, active by default, ordered by code and then by name, each with its coordinators and their marks (TRK-4). The list is not paged. The order of tracks with the same code and name is not defined.

#### TRK-3.1 The status narrows the list

- GIVEN two active tracks and an archived one
- WHEN an administrator lists tracks without a status, with `ARCHIVED`, and with `ALL`
- THEN the first lists the two active tracks, the second the archived one, and the third all three, each ordered by code and then by name

### TRK-4 One track

The system SHALL return a track, for a caller with `TRACKS_READ`, with its code, name, status and coordinators. Each coordinator is shown with their name and email, and marked when the assignment grants nothing: the user is disabled or no longer holds the coordinator role (TRK-6).

#### TRK-4.1 A coordinator who is disabled or lost the role is marked

- GIVEN a track with three coordinators: one active with the coordinator role, one since disabled, one whose coordinator role has since been removed
- WHEN an administrator views the track
- THEN all three are listed
- AND only the second is marked disabled and only the third is marked as without the coordinator role

#### TRK-4.2 An unknown track is not found

- GIVEN an id that no track has
- WHEN an administrator views it, reads its history, changes its code or name, archives or restores it, lists the candidates for its coordinators, or names or removes a coordinator of it
- THEN the response is 404 `not-found`

### TRK-5 Changing, archiving and restoring a track

The system SHALL let a caller with `TRACKS_MANAGE` change the code and the name of an active track, each independently, archive an active track and restore an archived one. Archiving a track that has an active enrollment (ENR-2) is 409 `track-has-active-enrollments`. An archived track is read-only until restored: every change except restoring and archiving it again is 409 `track-archived`, even one that would change nothing, and so is listing the candidates for its coordinators; it keeps its coordinators. A request is checked for validity first (400), then for an archived track, then for the eligibility of a coordinator (TRK-6). Changes of one track, enrollments on it and its archiving take effect one after another, so no change is ever stored on an archived track and an archived track never has an active enrollment. On an active track, a request that changes nothing, and archiving an archived track, answer with the track and record nothing (AUD-4). Each change is audited (AUD-7).

#### TRK-5.1 An archived track keeps its coordinators and is restored with them

- GIVEN an active track with a coordinator
- WHEN an administrator archives it and later restores it
- THEN after archiving the track is `ARCHIVED` with the coordinator
- AND after restoring it is `ACTIVE` with the same coordinator

#### TRK-5.2 An archived track cannot be changed

- GIVEN an archived track with a coordinator, and an active user with the coordinator role
- WHEN an administrator changes its name, saves its code and name unchanged, lists the candidates for its coordinators, names the user, names the coordinator again, removes the coordinator, or removes a user who is not its coordinator
- THEN each response is 409 `track-archived`
- AND the track is unchanged and nothing is recorded

#### TRK-5.3 A change to the same values records nothing

- GIVEN an active track
- WHEN an administrator saves its code and name unchanged, and restores it
- THEN each response is 200 with the track
- AND nothing is recorded

#### TRK-5.4 A change at the moment of archiving is never stored on an archived track

- GIVEN an active track and an active user with the coordinator role
- WHEN an administrator archives the track while another changes its name or names the user, at the same moment
- THEN the change is either stored before the archiving with its entry, or answered 409 `track-archived` and not stored
- AND no response is 500

#### TRK-5.5 A track with an active enrollment is not archived

- GIVEN an active track with an active enrollment
- WHEN an administrator archives it
- THEN the response is 409 `track-has-active-enrollments`
- AND the track is `ACTIVE` and nothing is recorded

#### TRK-5.6 An enrollment at the moment of archiving never leaves a student on an archived track

- GIVEN an active track without enrollments and an active user with the student role and no enrollment
- WHEN an administrator archives the track while another enrolls the user on it, at the same moment
- THEN either the user is enrolled and the archiving answered 409 `track-has-active-enrollments`, or the track is archived and the enrollment answered 409 `track-archived`
- AND no response is 500

### TRK-6 Coordinators of a track

The system SHALL let a caller with `TRACKS_MANAGE` name as a coordinator of an active track only an active user who holds the coordinator role, and remove any coordinator of it. A track may have any number of coordinators, and a user may coordinate any number of tracks. When a coordinator is later disabled or loses the coordinator role, the assignment stays and grants nothing (decision 0004); what an assignment grants is described by enrollments (ENR-1). Naming a coordinator already named, even one no longer eligible, or removing a user who is not one or an id no user has, changes and records nothing. The caller may list the candidates for coordinators of a track with `TRACKS_MANAGE` alone: the active users with the coordinator role who are not its coordinators. Parallel requests never end in a server error. Each change is audited in the track's and the user's history (AUD-7).

#### TRK-6.1 Only an active user with the coordinator role is named

- GIVEN an active track, a disabled user with the coordinator role, an active user without it, and an id that no user has
- WHEN an administrator names each as a coordinator of the track
- THEN each response is 409 `coordinator-not-eligible`
- AND the track has no new coordinator and nothing is recorded

#### TRK-6.2 A disabled coordinator can be removed

- GIVEN an active track whose coordinator has been disabled
- WHEN an administrator removes the coordinator
- THEN the track no longer has that coordinator

#### TRK-6.3 A repeated naming or removal records nothing

- GIVEN an active track with a coordinator and a coordinator who has since been disabled
- WHEN an administrator names each of them again, removes a user who is not a coordinator of the track, and removes an id that no user has
- THEN each response is 200 with the track, unchanged
- AND nothing is recorded

#### TRK-6.4 Parallel namings of one coordinator name them once

- GIVEN an active track and an active user with the coordinator role
- WHEN two administrators name the user as its coordinator at the same moment
- THEN each response is 200, never 500
- AND the user is a coordinator of the track once, with one entry in each history

#### TRK-6.5 The candidates are the eligible users not yet named

- GIVEN an active track with a coordinator, another active user with the coordinator role, a disabled user with the coordinator role, and an active user without it
- WHEN a caller with `TRACKS_MANAGE` and without `USERS_READ` lists the candidates for coordinators of the track
- THEN only the other active user with the coordinator role is listed

### TRK-7 The history of a track

The system SHALL return, for a caller with `TRACKS_READ`, the audit entries about a track (AUD-7, AUD-6), newest first and a page at a time (PLT-3), with the actor and what changed, for an active and an archived track alike.

#### TRK-7.1 The history tells each change of the track

- GIVEN a track that was created, had its name changed, had a coordinator named and removed, and was archived and restored
- WHEN an administrator reads its history
- THEN it holds one entry per change, newest first, each with the administrator as the actor and the details AUD-7 gives

### TRK-8 Navigation to tracks

The client SHALL offer the Tracks link only to a user with `TRACKS_READ` and take anyone else who opens a tracks address to the home page (PERM-7).

### TRK-9 The tracks page

The client SHALL show the tracks as a list with a status filter, active by default, each track with the code, the name, the coordinators with their marks (TRK-4) and the status and linking to the track page, and SHALL offer "Create track" in a dialog to a user with `TRACKS_MANAGE`. It is a list page (PLT-9) without paging or search. An empty list SHALL say that no track has the chosen status or, when all statuses are chosen, that no track exists yet.

### TRK-10 The track page

The client SHALL show a track's code, name, status, coordinators with their marks (TRK-4) and history (TRK-7), read-only without `TRACKS_MANAGE`, with a back link to the list, the enrollments of the track (ENR-7), and that the track is not found for an unknown or invalid id. A user with `TRACKS_MANAGE` may change the code or the name of an active track in a dialog, archive it or restore an archived one after a confirmation, and name coordinators of an active one and remove them after a confirmation. The choice of a new coordinator offers the candidates (TRK-6).

#### TRK-10.1 A refused change is explained and the track reloaded

- GIVEN the server refuses a change of the track, or the candidates for a new coordinator, with 409 `track-archived`, `track-has-active-enrollments` or `coordinator-not-eligible`
- WHEN the client receives it
- THEN a message explains the refusal
- AND the track is shown as it is now

## Known limitations

- When two administrators change the same track at the same moment, the last save of the code or the name wins; each change is still audited.
- A history page can hold fewer entries than its size, and its total counts entries that are left out (AUD-6).
- A user named a coordinator at the moment another administrator disables them or removes their coordinator role may keep an assignment that is already marked (TRK-4).
- A caller with `TRACKS_READ`, a coordinator included, sees the names and emails of coordinators and the history of every track, with the students enrolled on it (ENR-6), without `USERS_READ`, and a caller with `USERS_READ` sees in a user's history the tracks they coordinated without `TRACKS_READ`.

## Out of scope

- Cohorts and mentors of a track.
- A coordinator's own view of the tracks they coordinate.
- Deleting a track.
- The tracks a user coordinates, shown on the user page; the user's history shows when they were named and removed (USR-10).
- Search in the list of tracks.
