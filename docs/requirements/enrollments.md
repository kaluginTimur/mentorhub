# Enrollments

## Purpose

Who studies on which track. An enrollment links one student to one track from a start date; a coordinator of the track, or an administrator, records it, and assigns the mentors who lead the student. A mentor sees the students they lead. Coordinators and administrators see the students of every track, since the center interviews candidates across its tracks and everyone knows who went where; a student sees their own enrollment and whom to contact, their coordinators and mentors. An enrollment cannot be changed, cancelled or ended yet. Tracks and their coordinators are described by tracks (TRK), roles and permissions by authorization (PERM), recorded changes by audit (AUD). The API contract holds the fields, limits and response shapes.

## Requirements

### ENR-1 Who may do what

The system SHALL require `ENROLLMENTS_READ` to read the enrollments of a track. A user reads their own enrollments with no permission, only active (IDN-3). Enrolling a student on a track, assigning and removing the mentors of an enrollment on it, and listing the candidates for either, SHALL need `ENROLLMENTS_MANAGE`, which allows it on any track, or `ENROLLMENTS_MANAGE_COORDINATED` together with being a coordinator of that track at the time of the request (decision 0004). After the common refusals (PLT-10), a request is checked for the track, or for the enrollment, either being 404 `not-found` when it does not exist, then for the relation (403 `forbidden`), and only then for anything else (ENR-2); a refusal changes nothing. The track tells the caller whether they may enroll on it, by the permission and the relation alone, whatever its status.

#### ENR-1.1 Reading the enrollments of a track needs ENROLLMENTS_READ

- GIVEN a track with an enrollment, and an active user without `ENROLLMENTS_READ`, such as a mentor of the enrollment or the enrolled student
- WHEN the user lists the enrollments of the track
- THEN the response is 403 `forbidden`

#### ENR-1.2 A coordinator enrolls only on a track they coordinate

- GIVEN two active tracks, a coordinator of the first only, and an active user with the student role
- WHEN the coordinator lists the candidates for the second track and enrolls the user on it
- THEN each response is 403 `forbidden`
- AND the second track does not tell the coordinator that they may enroll on it
- AND the user is not enrolled and nothing is recorded

#### ENR-1.3 A coordinator removed from a track no longer enrolls on it

- GIVEN an active track, a coordinator who has been removed from it, and an active user with the student role
- WHEN the former coordinator lists the candidates for the track and enrolls the user on it
- THEN each response is 403 `forbidden`
- AND the user is not enrolled and nothing is recorded

#### ENR-1.4 An unknown track is not found

- GIVEN an administrator, a coordinator of a track, and an id that no track has
- WHEN each lists the enrollments of that id, lists the candidates for it, and enrolls a student on it
- THEN each response is 404 `not-found`

#### ENR-1.5 A coordinator assigns mentors only on a track they coordinate

- GIVEN an enrollment on a track, a coordinator of another track only, a coordinator who has been removed from the track, an active user with the mentor role, and a mentor of the enrollment
- WHEN each coordinator lists the candidates for its mentors, assigns the user, and removes the mentor
- THEN each response is 403 `forbidden`
- AND the enrollment keeps its one mentor and nothing is recorded

#### ENR-1.6 An unknown enrollment is not found

- GIVEN an administrator, a coordinator of a track, and an id that no enrollment has
- WHEN each lists the candidates for the mentors of that id, assigns a mentor to it and removes one from it
- THEN each response is 404 `not-found`

### ENR-2 Enrolling a student

The system SHALL let a caller who may (ENR-1) enroll on an active track an active user who holds the student role and has no active enrollment, from a start date the caller gives: any date, past or future. The enrollment is `ACTIVE` from the moment it is recorded, whatever its start date, and keeps the student, the track, the start date, who enrolled and when. A student has at most one active enrollment at a time, on any track. A user who coordinates the track, the caller included, is enrolled like anyone else. After the checks of ENR-1, a request is checked for an archived track, then for the eligibility of the student, then for an active enrollment. When the student is later disabled or loses the student role, the enrollment stays. Parallel requests never end in a server error.

#### ENR-2.1 A coordinator enrolls a student on their track

- GIVEN an active track, a coordinator of it, and an active user with the student role and no enrollment
- WHEN the coordinator enrolls the user on the track from a start date
- THEN the response is a new enrollment of the user on the track with that start date and the status `ACTIVE`
- AND the enrollments of the track list it

#### ENR-2.2 An invalid enrollment is rejected

- GIVEN an administrator and an active track
- WHEN the administrator enrolls on it without a student, or without a start date
- THEN the response is 400 `validation-failed` naming the missing field
- AND nothing is stored or recorded

#### ENR-2.3 Only an active user with the student role is enrolled

- GIVEN an active track, a disabled user with the student role, an active user without it, and an id that no user has
- WHEN an administrator enrolls each on the track
- THEN each response is 409 `student-not-eligible`
- AND the track has no new enrollment and nothing is recorded

#### ENR-2.4 A student with an active enrollment is not enrolled again

- GIVEN two active tracks and a student enrolled on the first
- WHEN an administrator enrolls the student on the second, and on the first again
- THEN each response is 409 `student-already-enrolled`
- AND the student has the one enrollment and nothing is recorded

#### ENR-2.5 Nobody is enrolled on an archived track

- GIVEN an archived track and an active user with the student role and no enrollment
- WHEN an administrator lists the candidates for the track and enrolls the user on it
- THEN each response is 409 `track-archived`
- AND the user is not enrolled and nothing is recorded

#### ENR-2.6 Parallel enrollments of one student enroll them once

- GIVEN two active tracks and an active user with the student role and no enrollment
- WHEN two administrators enroll the user at the same moment, one on each track
- THEN one response is a new enrollment and the other 409 `student-already-enrolled`, never 500
- AND the user has one enrollment, with one entry in each history

### ENR-3 The candidates for enrollment

The system SHALL let a caller who may enroll on a track (ENR-1) list the candidates for it without `USERS_READ`: the active users with the student role who have no active enrollment, each with their name and email, ordered by name, a page at a time (PLT-3), and narrowed by a search for a part of the name or the email.

#### ENR-3.1 The candidates are the eligible students not enrolled

- GIVEN an active track, a coordinator of it without `USERS_READ`, a student enrolled on another track, an active user with the student role and no enrollment, a disabled user with the student role, and an active user without it
- WHEN the coordinator lists the candidates for the track
- THEN only the active user with the student role and no enrollment is listed

#### ENR-3.2 The candidates are searched by name or email

- GIVEN an active track and three candidates for it
- WHEN an administrator searches the candidates for a part of the name of the first, and for a part of the email of the second
- THEN the first search lists only the first candidate and the second only the second

### ENR-4 The enrollments of a track

The system SHALL return, for a caller with `ENROLLMENTS_READ`, the enrollments of an active or an archived track, ordered by the student's name and a page at a time (PLT-3). Each has the student's name and email, the start date, the status and the mentors with their names and emails. The student is marked when disabled or no longer holding the student role, and a mentor when disabled or no longer holding the mentor role.

#### ENR-4.1 A coordinator of another track sees the enrollments with the marks

- GIVEN a track with three enrollments: of an active student, of a student since disabled, and of one whose student role has since been removed, and a coordinator of another track only
- WHEN the coordinator lists the enrollments of the track
- THEN all three are listed, ordered by the student's name
- AND only the second is marked disabled and only the third is marked as without the student role

#### ENR-4.2 The mentors of an enrollment are listed with their marks

- GIVEN an enrollment with three mentors: one active with the mentor role, one since disabled, and one whose mentor role has since been removed
- WHEN an administrator lists the enrollments of its track
- THEN the enrollment has all three mentors
- AND only the second is marked disabled and only the third is marked as without the mentor role

### ENR-5 One's own enrollment

The system SHALL return, for any active caller, whatever roles they hold, the caller's own enrollments and nothing about anyone else's: each with the track's code and name, the start date, the status, the name, email and phone of every coordinator of the track who is active and holds the coordinator role, and the same of every mentor of the enrollment who is active and holds the mentor role. A caller without an enrollment gets an empty answer, not an error.

#### ENR-5.1 A student sees their own enrollment and the track's coordinators

- GIVEN a student enrolled on a track with three coordinators, one of them since disabled and one whose coordinator role has since been removed, and another student enrolled on another track
- WHEN the first student reads their own enrollments
- THEN the answer is their one enrollment, with the track's code and name, the start date and the name, email and phone of only the coordinator who is active and holds the role

#### ENR-5.2 A user without an enrollment gets an empty answer

- GIVEN an active user without roles and with no enrollment, while other students are enrolled
- WHEN the user reads their own enrollments
- THEN the response is 200 with no enrollment

#### ENR-5.3 A student sees the mentors who may lead them

- GIVEN a student whose enrollment has three mentors: one active with the mentor role, one since disabled, and one whose mentor role has since been removed
- WHEN the student reads their own enrollments
- THEN the enrollment has the name, email and phone of only the first mentor

### ENR-6 The history of an enrollment

The system SHALL record an enrollment in the history of its track (TRK-7) and in the history of the student (USR-10), and the assignment and removal of a mentor in those and in the history of the mentor, as the catalog of audited actions says (AUD-7).

#### ENR-6.1 An enrollment shows in the track's and the student's history

- GIVEN a student enrolled on a track by a coordinator, after which the track's code and name were changed
- WHEN an administrator reads the history of the track and the history of the student
- THEN the track's history has an entry with the coordinator as the actor, the student and the start date
- AND the student's history has an entry with the coordinator as the actor, the start date, and the track with the code and the name it had then

#### ENR-6.2 Assigning and removing a mentor show in three histories

- GIVEN a mentor assigned to an enrollment and later removed from it by a coordinator, after which the track's code and name were changed
- WHEN an administrator reads the history of the track, of the student and of the mentor
- THEN the track's history has an entry of the assignment and one of the removal, each with the coordinator as the actor, the student and the mentor
- AND the student's history has the two entries with the mentor, and the mentor's history the two entries with the student, each also with the track with the code and the name it had then

### ENR-7 Enrollments on the track page

The client SHALL show on the track page (TRK-10), to a user with `ENROLLMENTS_READ`, the enrollments of the track as a paged list (PLT-9) with the student's name and email, the start date, the mentors and the marks (ENR-4), and SHALL say when the track has no enrollment yet. To a user who may enroll on the track (ENR-1) it SHALL offer "Enroll student" on an active track, in a dialog that offers the candidates with their search (ENR-3) and a start date, today unless changed, and on each enrollment the assignment of a mentor, in a dialog that offers the candidates with their search (ENR-10), and the removal of a mentor after a confirmation.

#### ENR-7.1 A refused enrollment or mentor assignment is explained and the list reloaded

- GIVEN the server refuses an enrollment, the assignment of a mentor, or the candidates for either, with 409 `track-archived`, `student-not-eligible`, `student-already-enrolled` or `mentor-not-eligible`
- WHEN the client receives it
- THEN a message explains the refusal
- AND the track and its enrollments are shown as they are now

### ENR-8 My enrollment

The client SHALL offer the My enrollment link only to a user who has an enrollment. The page SHALL show the user's enrollment: the track, the start date, and the coordinators and the mentors to contact with their emails and phones (ENR-5), and SHALL say when the user is not enrolled yet, when the track has no coordinator to contact, and when the enrollment has no mentor to contact.

### ENR-9 Mentors of an enrollment

The system SHALL let a caller who may (ENR-1) assign as a mentor of an enrollment an active user who holds the mentor role, and remove any mentor of it. An enrollment may have any number of mentors, and a user may lead any number of enrollments, on any track. Any eligible user is assigned, the student of the enrollment and the caller included, and whatever the student's status or roles. When a mentor is later disabled or loses the mentor role, the assignment stays and grants nothing. Assigning a mentor already assigned, even one no longer eligible, or removing a user who is not a mentor of the enrollment or an id no user has, changes and records nothing. After the checks of ENR-1, a request is checked for the eligibility of the mentor. Parallel requests never end in a server error. Each change is audited (ENR-6).

#### ENR-9.1 A coordinator assigns and removes the mentors of an enrollment on their track

- GIVEN an enrollment on a track, a coordinator of the track, an active user with the mentor role, and a mentor of the enrollment who has since been disabled
- WHEN the coordinator assigns the user and removes the disabled mentor
- THEN the enrollments of the track list the enrollment with the user as its only mentor

#### ENR-9.2 Only an active user with the mentor role is assigned

- GIVEN an enrollment, a disabled user with the mentor role, an active user without it, and an id that no user has
- WHEN an administrator assigns each as a mentor of the enrollment
- THEN each response is 409 `mentor-not-eligible`
- AND the enrollment has no new mentor and nothing is recorded

#### ENR-9.3 A repeated assignment or removal records nothing

- GIVEN an enrollment with a mentor and a mentor who has since lost the mentor role
- WHEN an administrator assigns each of them again, removes a user who is not a mentor of the enrollment, and removes an id that no user has
- THEN each response is a success, and the enrollment keeps the same two mentors
- AND nothing is recorded

#### ENR-9.4 Parallel assignments of one mentor assign them once

- GIVEN an enrollment and an active user with the mentor role
- WHEN two administrators assign the user as its mentor at the same moment
- THEN both responses are a success, never 500
- AND the user is a mentor of the enrollment once, with one entry in each history

### ENR-10 The candidates for mentors

The system SHALL let a caller who may assign the mentors of an enrollment (ENR-1) list the candidates for them without `USERS_READ`: the active users with the mentor role who are not its mentors, each with their name and email, ordered by name, a page at a time (PLT-3), and narrowed by a search for a part of the name or the email.

#### ENR-10.1 The candidates are the eligible mentors not yet assigned

- GIVEN an enrollment with a mentor, a coordinator of its track without `USERS_READ`, another active user with the mentor role, a disabled user with the mentor role, and an active user without it
- WHEN the coordinator lists the candidates for the mentors of the enrollment
- THEN only the other active user with the mentor role is listed

### ENR-11 A mentor's students

The system SHALL return, for a caller with `ENROLLMENTS_READ_MENTORED`, the enrollments the caller is a mentor of at the time of the request, and nothing about any other: each with the student's name and email, the track's code and name, the start date and the status, the student marked as in ENR-4, ordered by the student's name and a page at a time (PLT-3). A caller who leads no enrollment gets an empty answer, not an error.

#### ENR-11.1 A mentor sees only the students they lead now

- GIVEN a mentor of three enrollments on two tracks, the student of one of them since disabled and of another without the student role since, an enrollment from which the mentor has been removed, and an enrollment of another mentor only
- WHEN the mentor reads their students
- THEN the answer is the three enrollments, ordered by the student's name, each with its track
- AND the disabled student is marked disabled and the other as without the student role, and the third is not marked

#### ENR-11.2 A mentor who lost the mentor role no longer sees their students

- GIVEN a mentor of an enrollment whose mentor role has since been removed
- WHEN the user reads their students
- THEN the response is 403 `forbidden`

### ENR-12 My students

The client SHALL offer the My students link only to a user with `ENROLLMENTS_READ_MENTORED`, and take anyone else who opens its address to the home page (PERM-7). The page SHALL show the enrollments the user leads (ENR-11) as a paged list (PLT-9) with the student's name and email, the track, the start date and the marks, and SHALL say when the user leads no student yet.

## Known limitations

- An enrollment cannot be changed, cancelled or ended: a mistaken one, a wrong start date included, stays, and keeps the student from being enrolled elsewhere and the track from being archived (TRK-5), until the end of an enrollment exists. So does the enrollment of a student who was disabled since, such as an expelled one.
- A user enrolled, or assigned as a mentor, at the moment an administrator disables them or removes their student or mentor role may keep an enrollment or an assignment that is already marked (ENR-4).
- A coordinator removed from the track, or losing the role, at the moment they enroll a student or assign or remove a mentor may still do it; the entry names that coordinator as the actor.
- Without `USERS_READ`, a caller with `ENROLLMENTS_READ` sees the names and emails of enrolled students, a caller who may enroll on a track those of every candidate, a caller with `TRACKS_READ` the students enrolled on a track and their mentors in its history (TRK-7), a student the names, emails and phones of the coordinators of their track and of their mentors, a caller with `ENROLLMENTS_READ` also the names and emails of the mentors of every enrollment, a caller who may assign mentors those of every candidate mentor, and a mentor the names and emails of the students they lead and their tracks.
- A student whose student role is removed still reads their own enrollment while active; what a student loses when their study ends is decided with the access lifecycle.

## Out of scope

- Changing, cancelling and ending an enrollment, its outcome, and moving a student to another track.
- Mentors of a cohort, and cohorts.
- Mentors of a track: any mentor of the center may lead an enrollment on any track.
- What happens to the mentors of an enrollment when it ends, which enrollments a mentor sees after they end, and assigning a mentor to the enrollment of a student who is disabled or no longer holds the student role; all are decided with the end of an enrollment.
- A page of one enrollment and a history of its own.
- The enrollments of a user shown on the user page; the user's history shows when they were enrolled (USR-10).
- Search and filters in the enrollments of a track.
- What a student loses when their study ends.
