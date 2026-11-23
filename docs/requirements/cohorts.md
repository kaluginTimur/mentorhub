# Cohorts

## Purpose

The intakes of a track. A cohort is one intake on a track: students who start and study together. It belongs to one track and has a name and a start date. An enrollment (ENR) is in at most one cohort of its track, or in none, since a cohort is optional. A coordinator of the track, or an administrator, creates a cohort together with its enrollments, adds enrollments to it later, moves one from another cohort or takes it out, and deletes a cohort that has none; whoever reads the enrollments of a track sees its cohorts, how many students each has and which enrollments are in it. Tracks are described by tracks (TRK), enrollments and who may change them by enrollments (ENR), roles and permissions by authorization (PERM), recorded changes by audit (AUD). The API contract holds the fields, limits and response shapes.

## Requirements

### COH-1 Who may do what

The system SHALL require `ENROLLMENTS_READ` to read the cohorts of a track. Creating and deleting a cohort, adding enrollments to it and taking one out, and listing the candidates for it SHALL need what changing the enrollments of the track needs: `ENROLLMENTS_MANAGE`, or `ENROLLMENTS_MANAGE_COORDINATED` together with being a coordinator of the track at the time of the request (ENR-1, decision 0004). A cohort addressed through a track it does not belong to is not found (PLT-10). The track tells the caller whether they may change its cohorts, by the permission and the relation alone, whatever its status.

#### COH-1.1 Reading the cohorts of a track needs ENROLLMENTS_READ

- GIVEN a track with a cohort, and an active user without `ENROLLMENTS_READ`, such as a mentor of an enrollment in the cohort or its student
- WHEN the user lists the cohorts of the track
- THEN the response is 403 `forbidden`

#### COH-1.2 A coordinator changes the cohorts only of a track they coordinate

- GIVEN an active track with an empty cohort and a cohort with an enrollment, an enrollment of the track in no cohort, a coordinator of another track only, and a coordinator who has been removed from the track
- WHEN each coordinator creates a cohort on the track, lists the candidates for a new cohort and for the empty cohort, adds the enrollment to it, takes the enrollment out of the other cohort, and deletes the empty cohort
- THEN each response is 403 `forbidden`
- AND the track does not tell either coordinator that they may change its cohorts
- AND the cohorts and the enrollments are unchanged and nothing is recorded

#### COH-1.3 An unknown track or cohort is not found

- GIVEN an administrator, a coordinator of a track with a cohort, a cohort of another track, and ids that no track and no cohort have
- WHEN each lists the cohorts of the unknown track, creates one on it and lists the candidates for a new cohort of it, and, for the unknown cohort and for the cohort of the other track addressed through the first track, lists its candidates, adds an enrollment to it, takes one out of it and deletes it
- THEN each response is 404 `not-found`

### COH-2 Creating a cohort

The system SHALL let a caller who may (COH-1) create a cohort on an active track with a name and a start date, both required, and with any number of enrollments, none included. A name is stored without surrounding spaces and need not be unique, even on its track; the start date is any date and need not match the start dates of the enrollments. Each enrollment given must be an active enrollment of the track, or the whole request is 409 `enrollment-not-eligible` and nothing is stored; each is then added as COH-3 says, moved from another cohort if it is in one. The creation and each added enrollment are audited (COH-7).

#### COH-2.1 A coordinator creates a cohort with its enrollments

- GIVEN an active track with three enrollments, the third of them in a cohort, and a coordinator of the track
- WHEN the coordinator creates a cohort with the name ` Spring 2026 `, a start date and the first and the third enrollments
- THEN the response is a new cohort with the name `Spring 2026`, that start date and 2 enrollments
- AND the enrollments of the track narrowed to the new cohort are the first and the third, and the other cohort has none

#### COH-2.2 An invalid cohort is rejected

- GIVEN an administrator and an active track
- WHEN the administrator creates a cohort on it with a blank name, or without a start date
- THEN the response is 400 `validation-failed` naming `name` or `startDate`
- AND nothing is stored or recorded

#### COH-2.3 An enrollment from outside the track refuses the whole request

- GIVEN an active track with an enrollment and an empty cohort, an enrollment on another track, and an id that no enrollment has
- WHEN an administrator creates a cohort on the track, and adds enrollments to the empty cohort, each time with the enrollment of the track and one of the other two
- THEN each response is 409 `enrollment-not-eligible`
- AND no cohort is created, the enrollment of the track is in no cohort, and nothing is recorded

#### COH-2.4 The cohorts of an archived track cannot be changed

- GIVEN an archived track with an empty cohort
- WHEN an administrator creates a cohort on it, lists the candidates for a new cohort and for the cohort, adds an enrollment id to it, takes an enrollment id out of it, and deletes it
- THEN each response is 409 `track-archived`
- AND the track keeps its one cohort and nothing is recorded

### COH-3 The enrollments of a cohort

The system SHALL let a caller who may (COH-1) add to a cohort of an active track one or more enrollments at once, and take one enrollment out of it. Adding no enrollment is 400 `validation-failed`. An enrollment is in at most one cohort, of its own track. Adding is all or nothing: each enrollment given must be an active enrollment of the track, or the whole request is 409 `enrollment-not-eligible` and nothing is stored. An enrollment in another cohort of the track is moved into this one; one already in this cohort, or given twice, changes and records nothing. Taking out leaves the enrollment in no cohort; taking out an enrollment that is not in the cohort, or an id no enrollment has, changes and records nothing. On an archived track every change, even one that would change nothing, and listing the candidates are 409 `track-archived` (COH-2.4). Changes of the cohorts of one enrollment take effect one after another, and parallel requests never end in a server error. Each change of an enrollment's cohort is audited (COH-7).

#### COH-3.1 Adding moves an enrollment from another cohort and skips one already in the cohort

- GIVEN an active track with two cohorts, the first with an enrollment and the second with another, and a third enrollment in no cohort
- WHEN an administrator adds to the first cohort all three enrollments
- THEN the first cohort has 3 enrollments and the second none
- AND the histories have an entry for the second and the third enrollments only

#### COH-3.2 Taking an enrollment out leaves it in no cohort

- GIVEN an active track with a cohort of two enrollments, and an enrollment of the track in no cohort
- WHEN a coordinator of the track takes the first enrollment out of the cohort, then takes out the enrollment in no cohort and an id that no enrollment has
- THEN each response is a success, and the cohort has only the second enrollment
- AND only the first taking out is recorded

#### COH-3.3 Parallel additions of one enrollment to two cohorts leave it in one

- GIVEN an active track with two cohorts and an enrollment in neither
- WHEN two administrators add the enrollment at the same moment, one to each cohort
- THEN no response is 500
- AND the enrollment is in exactly one of the cohorts, and the histories tell the changes in the order they were stored, ending in that cohort

### COH-4 Deleting a cohort

The system SHALL let a caller who may (COH-1) delete a cohort of an active track that has no enrollment; a cohort with one is 409 `cohort-not-empty`. A deleted cohort is gone, and its entries in the histories stay. A deletion and a change of the cohort's enrollments, enrolling into it (ENR-2) included, take effect one after another, so no enrollment is ever left in a deleted cohort: an addition to a deleted cohort is 404 `not-found`, and an enrollment into it 409 `cohort-not-eligible`. The deletion is audited (COH-7).

#### COH-4.1 Only an empty cohort is deleted

- GIVEN an active track with an empty cohort and a cohort with an enrollment
- WHEN a coordinator of the track deletes each
- THEN the first is deleted and the second answers 409 `cohort-not-empty`
- AND the track has only the second cohort, and only the deletion is recorded

#### COH-4.2 A deletion at the moment of an addition never leaves an enrollment in a deleted cohort

- GIVEN an active track with an empty cohort and an enrollment in no cohort
- WHEN one administrator deletes the cohort while another adds the enrollment to it, at the same moment
- THEN either the cohort is deleted and the addition answered 404 `not-found`, or the enrollment is in the cohort and the deletion answered 409 `cohort-not-empty`
- AND no response is 500

### COH-5 The cohorts of a track

The system SHALL return, for a caller with `ENROLLMENTS_READ`, the cohorts of an active or an archived track, newest start date first and then by name, each with its name, its start date and the number of active enrollments in it. The list is not paged. The enrollments of a track have each its cohort, and may be narrowed to one cohort (ENR-4).

#### COH-5.1 The cohorts are listed newest first with their numbers of students

- GIVEN a track with three cohorts: the oldest with two enrollments, one of them of a student since disabled, and two of a later start date, `B` with one enrollment and `A` with none
- WHEN a coordinator of another track lists the cohorts of the track
- THEN the three are listed `A`, `B` and the oldest, with 0, 1 and 2 enrollments

### COH-6 The candidates for a cohort

The system SHALL let a caller who may change the cohorts of a track (COH-1) list the candidates for a new cohort of it or for one of its cohorts: the active enrollments of the track, without the enrollments of that cohort, each with the student's name and email, the student's marks (ENR-4) and the cohort the enrollment is in now, ordered by the student's name, a page at a time (PLT-3), narrowed by a search for a part of the name or the email, and narrowed on request to the enrollments in no cohort.

#### COH-6.1 The candidates are the enrollments of the track outside the cohort

- GIVEN an active track with two cohorts, an enrollment in the first, an enrollment in the second, an enrollment in no cohort, and an enrollment on another track
- WHEN a coordinator of the track lists the candidates for the first cohort, then the same narrowed to the enrollments in no cohort, then the candidates for a new cohort
- THEN the first answer is the enrollment in the second cohort, with that cohort, and the one in no cohort, and not the enrollment already in the first cohort
- AND the second answer is the enrollment in no cohort only
- AND the third answer is the three enrollments of the track

#### COH-6.2 The candidates are searched by name or email

- GIVEN an active track with a cohort and three candidates for it
- WHEN an administrator searches the candidates for a part of the name of the first, and for a part of the email of the second
- THEN the first search lists only the first candidate and the second only the second

### COH-7 The history of cohorts

The system SHALL record the creation and the deletion of a cohort in the history of its track (TRK-7), and every change of an enrollment's cohort, by creating a cohort, adding to it or taking out of it, in the history of the track and of the student (USR-10), as the catalog of audited actions says (AUD-7).

#### COH-7.1 Cohorts and their enrollments show in the track's and the student's history

- GIVEN a coordinator who created a cohort with a student's enrollment, then created a second cohort with the same enrollment, then took it out of the second cohort and deleted the first, after which the track's code and name were changed
- WHEN an administrator reads the history of the track and of the student
- THEN the track's history has, each with the coordinator as the actor: the creation of each cohort with its name and start date, the student added to the first cohort, moved from the first to the second, taken out of the second, and the deletion of the first
- AND the student's history has the three changes of the cohort, each with the cohorts' names and the track with the code and the name it had then

### COH-8 Cohorts on the track page

The client SHALL show on the track page (TRK-10), to a user with `ENROLLMENTS_READ`, the cohorts of the track with their names, start dates and numbers of students (COH-5), and SHALL say when the track has no cohort yet; choosing a cohort narrows the enrollments of the track to it (ENR-7). To a user who may change the cohorts of the track (COH-1) it SHALL offer, only on an active track, "Create cohort" in a dialog with the name, the start date and a choice of enrollments among the candidates with their search, their current cohorts and the narrowing to those in no cohort, on by default (COH-6); on each cohort the addition of enrollments in the same choice; on each enrollment in a cohort its taking out after a confirmation; and on an empty cohort its deletion after a confirmation.

#### COH-8.1 Moving an enrollment from another cohort is confirmed

- GIVEN a user who creates a cohort or adds enrollments to one, and has chosen two enrollments in other cohorts and one in no cohort
- WHEN the user saves
- THEN a confirmation names the two students and the cohorts they leave, and nothing is sent yet
- AND the request is sent only after the user confirms, and not at all when they decline

#### COH-8.2 A refused change of cohorts is explained and the track reloaded

- GIVEN the server refuses the creation, the deletion or a change of the enrollments of a cohort, or its candidates, with 404 `not-found` for the cohort or 409 `track-archived`, `enrollment-not-eligible` or `cohort-not-empty`
- WHEN the client receives it
- THEN a message explains the refusal
- AND the cohorts and the enrollments of the track are shown as they are now

## Known limitations

- A cohort cannot be renamed and its start date cannot be changed: a mistaken one is deleted once empty and created again.
- The enrollments of a track cannot be narrowed to those in no cohort; only the choice of the candidates for a cohort can (COH-6).
- A student does not see their cohort, and a mentor does not see the cohorts of the students they lead.
- A coordinator removed from the track, or losing the role, at the moment they change its cohorts may still do it; the entry names that coordinator as the actor.

## Out of scope

- Renaming a cohort and changing its start date, the enrollments of a track narrowed to those in no cohort, and the cohort in a student's own enrollment (ENR-5): a change that follows this one.
- Mentors of a cohort, and groups inside a cohort.
- How many students of a cohort have finished, and a cohort that ends; they come with the end of an enrollment.
- Taking several enrollments out of a cohort at once.
- A page of one cohort and a history of its own.
- A cohort on more than one track, and moving a cohort to another track.
