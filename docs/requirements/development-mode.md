# Development mode

## Purpose

What a local development run of MentorHub does: the backend and the client both run in development mode, against the local identity provider. Development mode turns the run into the sample center: a fictional training center in which people and agents can open every screen as every kind of user, on plausible data and in a reproducible state, to review screens per role, to choose the style of the client and to check that screens are plausible.

Every start in development mode replaces MentorHub's data with the sample center data. Tests do not rely on it.

## Requirements

### DEV-1 Development users

The system SHALL create, only in development mode, a development user for each persona in the table, matching an account in the local identity provider (decision 0003), and on every start there SHALL bring each back to its status and exactly its roles in the table. The persona Not invited SHALL be an account in the local identity provider only, linked to no user.

| Persona | Status | Roles |
|---|---|---|
| Administrator | active | Administrator |
| Coordinator | active | Coordinator |
| Mentor | active | Mentor |
| Student | active | Student |
| Observer | active | Observer |
| Administrator and mentor | active | Administrator, Mentor |
| Mentor and coordinator | active | Mentor, Coordinator |
| Without roles | active | none |
| Disabled | disabled | Mentor |
| Not invited | none | none |

#### DEV-1.1 Development users exist only in development mode

- GIVEN the application starts without development mode
- WHEN it has started
- THEN no development user is created

#### DEV-1.2 A restart restores the configured access

- GIVEN a development user whose status or roles were changed since the last start
- WHEN the application starts in development mode
- THEN the user has the status and exactly the roles in the table

#### DEV-1.3 A restart unlinks the persona Not invited

- GIVEN the identity of the persona Not invited was linked to a user since the last start
- WHEN the application starts in development mode
- THEN that identity is linked to no user

### DEV-2 The sample center data

In development mode the system SHALL hold the sample center data, a plausible training center, and on every start SHALL replace all of MentorHub's data, the audit log included, with it. Changes made while the application runs last until the next start. The accounts in the identity provider are not MentorHub's data.

#### DEV-2.1 A start restores the sample center data

- GIVEN MentorHub's data was changed since the last start in development mode
- WHEN the application starts in development mode
- THEN MentorHub's data is exactly the sample center data

#### DEV-2.2 Data outside development mode is never replaced

- GIVEN existing data
- WHEN the application starts without development mode
- THEN the existing data is not replaced by the sample center data

### DEV-3 Simulated failures

In development mode the client SHALL offer, on every screen including the unavailable message (PLT-8), a switch that makes every API request respond normally, slowly, with a server error, or as if there were no connection. It SHALL show while a failure mode is on, and a reload of the page SHALL turn the failure mode off.

#### DEV-3.1 The switch is absent outside development mode

- GIVEN the client is built for production
- WHEN any screen is shown
- THEN the switch is not offered

#### DEV-3.2 Requests are not altered outside development mode

- GIVEN the client is built for production
- WHEN the client makes an API request
- THEN the request and its response are not altered

#### DEV-3.3 A failure mode on is shown

- GIVEN the client in development mode
- WHEN a failure mode is turned on
- THEN every screen shows that a failure mode is on, and which one
- AND after a reload of the page no failure mode is on

## Known limitations

- Every start discards what was changed in MentorHub while the application ran.
- Accounts registered at the local identity provider during a run stay there; after the next start they are linked to no user.

## Out of scope

- Running the client without the backend, and a run where only one of the backend and the client is in development mode.
- Keeping changes across starts, an empty data set, and signing in as a persona without the identity provider's sign-in form.
- Choosing a failure for one request or endpoint.
- Data for tests: tests create their own.
