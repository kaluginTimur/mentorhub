-- The sample center data of the users module (DEV-1, DEV-2): a fictional training center with SWE, QA and DevOps
-- tracks. Every row has a fixed id and every token is fixed, so every start produces the same rows apart from the time
-- columns, which are relative to the start. Rows refer to users by email, which is unique.
--
-- Fixed ids by kind, with the number in the last group:
--   users        00000000-0000-4000-a000-...   personas ...001 to ...00a (the persona Not invited, ...006, has no
--                                              user), other users ...101 and up
--   identities   00000000-0000-4000-b000-...   the number of the user; the subject is 00000000-0000-4000-8000-<number>,
--                                              the id of the Keycloak account for a persona and no account otherwise,
--                                              so nobody signs in as the other users
--   invitations  00000000-0000-4000-c000-...   open and revoked ones 1 to 99, accepted ones 101 and up
--   emails       00000000-0000-4000-d000-...   requested for the open and revoked invitations 1 to 99, the email of an
--                                              accepted invitation has the number of the invitation
--   audit_log    00000000-0000-4000-e<k>00-... e000 written by hand, e100 invitation created, e200 invitation accepted,
--                                              e300 invitation revoked, e400 email requested (number of the email),
--                                              e500 the roles of Daniel Kim
--
-- The audit history is consistent with the data: every user except the bootstrapped administrator joined by an
-- accepted invitation, and the current status, roles and profile are the result of the entries about the user.
-- Times inside the details are relative to the start like the time columns, and are taken from the rows they describe:
-- the new expiry of a link is the expires_at of its invitation (DEV-2.1 leaves times out of the comparison).

-- The personas of DEV-1 are linked to the accounts of infra/keycloak/mentorhub-realm.json by their ids and have the
-- emails of those accounts; DevelopmentRealmConsistencyTests and SampleCenterTests check that the two agree.
with sample (id, email, first_name, last_name, phone, status, disabled_reason, disabled_comment, created_ago, roles) as (
    values ('00000000-0000-4000-a000-000000000001'::uuid, 'admin@mentorhub.local', 'Ada', 'Admin', null, 'ACTIVE',
            null, null, interval '300 days', array ['ADMIN']),
           ('00000000-0000-4000-a000-000000000002', 'mentor@mentorhub.local', 'Mark', 'Mentor', null, 'ACTIVE', null,
            null, interval '294 days', array ['MENTOR']),
           ('00000000-0000-4000-a000-000000000003', 'student@mentorhub.local', 'Sofia', 'Student', '+77015550123',
            'ACTIVE', null, null, interval '150 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000004', 'noroles@mentorhub.local', 'Nora', 'Noroles', null, 'ACTIVE', null,
            null, interval '4 days', array []::text[]),
           ('00000000-0000-4000-a000-000000000005', 'disabled@mentorhub.local', 'Dan', 'Disabled', null, 'DISABLED',
            'OTHER', 'Contract paused until the next cohort', interval '259 days', array ['MENTOR']),
           ('00000000-0000-4000-a000-000000000007', 'coordinator@mentorhub.local', 'Cora', 'Coordinator', null,
            'ACTIVE', null, null, interval '281 days', array ['COORDINATOR']),
           ('00000000-0000-4000-a000-000000000008', 'observer@mentorhub.local', 'Otto', 'Observer', null, 'ACTIVE',
            null, null, interval '199 days', array ['OBSERVER']),
           ('00000000-0000-4000-a000-000000000009', 'admin.mentor@mentorhub.local', 'Alma', 'Admin-Mentor', null,
            'ACTIVE', null, null, interval '287 days', array ['ADMIN', 'MENTOR']),
           ('00000000-0000-4000-a000-00000000000a', 'mentor.coordinator@mentorhub.local', 'Milo', 'Mentor-Coordinator',
            null, 'ACTIVE', null, null, interval '266 days', array ['COORDINATOR', 'MENTOR']),

           -- Students of the spring and the autumn cohorts.
           ('00000000-0000-4000-a000-000000000101', 'liam.carter@example.com', 'Liam', 'Carter', null, 'ACTIVE', null,
            null, interval '151 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000102', 'emma.miller@example.com', 'Emma', 'Brooks', null, 'ACTIVE', null,
            null, interval '150 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000103', 'noah.bennett@example.com', 'Noah', 'Bennett', null, 'ACTIVE', null,
            null, interval '151 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000104', 'olivia.hayes@example.com', 'Olivia', 'Hayes', '+77052345678',
            'ACTIVE', null, null, interval '150 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000105', 'ethan.reed@example.com', 'Ethan', 'Reed', null, 'ACTIVE', null,
            null, interval '149 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000106', 'ava.collins@example.com', 'Ava', 'Collins', null, 'ACTIVE', null,
            null, interval '41 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000107', 'lucas.price@example.com', 'Lucas', 'Price', null, 'ACTIVE', null,
            null, interval '149 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000108', 'mia.foster@example.com', 'Mia', 'Foster', null, 'ACTIVE', null,
            null, interval '148 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000109', 'mason.ward@example.com', 'Mason', 'Ward', null, 'ACTIVE', null,
            null, interval '40 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000110', 'chloe.turner@example.com', 'Chloe', 'Turner', '+77017654321',
            'ACTIVE', null, null, interval '41 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000111', 'james.porter@example.com', 'James', 'Porter', null, 'ACTIVE', null,
            null, interval '40 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000112', 'grace.morgan@example.com', 'Grace', 'Morgan', null, 'ACTIVE', null,
            null, interval '39 days', array ['STUDENT']),
           -- No names: the token had none when the student accepted.
           ('00000000-0000-4000-a000-000000000125', 'n.shah@example.com', null, null, null, 'ACTIVE', null, null,
            interval '38 days', array ['STUDENT']),
           -- Disabled with each reason, with and without a comment.
           ('00000000-0000-4000-a000-000000000113', 'henry.ellis@example.com', 'Henry', 'Ellis', null, 'DISABLED',
            'GRADUATED', null, interval '278 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000114', 'lily.barnes@example.com', 'Lily', 'Barnes', null, 'DISABLED',
            'EXPELLED', 'Repeated plagiarism in graded assignments', interval '147 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000115', 'owen.fisher@example.com', 'Owen', 'Fisher', null, 'DISABLED',
            'LEFT_PROGRAM', null, interval '150 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000116', 'zoe.palmer@example.com', 'Zoe', 'Palmer', null, 'DISABLED',
            'SECURITY', 'Shared her account with another person', interval '40 days', array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000117', 'ruby.hughes@example.com', 'Ruby', 'Hughes', null, 'DISABLED',
            'OTHER', 'Paused her studies for medical reasons until the next cohort', interval '150 days',
            array ['STUDENT']),
           ('00000000-0000-4000-a000-000000000118', 'paul.becker@mentorhub.local', 'Paul', 'Becker', null, 'DISABLED',
            'STAFF_OFFBOARDED', null, interval '289 days', array ['ADMIN', 'MENTOR']),

           -- Staff.
           ('00000000-0000-4000-a000-000000000119', 'daniel.kim@mentorhub.local', 'Daniel', 'Kim', '+77013334455',
            'ACTIVE', null, null, interval '284 days', array ['MENTOR']),
           ('00000000-0000-4000-a000-000000000120', 'sarah.lopez@mentorhub.local', 'Sarah', 'Lopez', null, 'ACTIVE',
            null, null, interval '274 days', array ['COORDINATOR', 'MENTOR']),
           ('00000000-0000-4000-a000-000000000121', 'victor.novak@mentorhub.local', 'Victor', 'Novak', '+77017778899',
            'ACTIVE', null, null, interval '269 days', array ['MENTOR']),
           -- A graduate of the QA track, invited back as a mentor.
           ('00000000-0000-4000-a000-000000000122', 'helen.park@example.com', 'Helen', 'Park', null, 'ACTIVE', null,
            null, interval '279 days', array ['MENTOR']),
           ('00000000-0000-4000-a000-000000000123', 'irene.walsh@mentorhub.local', 'Irene', 'Walsh', '+77012223344',
            'ACTIVE', null, null, interval '229 days', array ['COORDINATOR']),
           -- An external examiner.
           ('00000000-0000-4000-a000-000000000124', 'thomas.grant@example.com', 'Thomas', 'Grant', null, 'ACTIVE', null,
            null, interval '208 days', array ['OBSERVER']),
           -- Waits for the next DevOps cohort without roles.
           ('00000000-0000-4000-a000-000000000126', 'kevin.doyle@example.com', 'Kevin', 'Doyle', null, 'ACTIVE', null,
            null, interval '39 days', array []::text[])),
     inserted as (
         insert into users (id, email, first_name, last_name, phone, status, disabled_reason, disabled_comment,
                            created_at)
             select id, email, first_name, last_name, phone, status, disabled_reason, disabled_comment,
                    now() - created_ago
             from sample)
insert
into user_roles (user_id, role)
select id, unnest(roles)
from sample;

insert into user_identities (id, user_id, issuer, subject)
select ('00000000-0000-4000-b000-' || right(id::text, 12))::uuid,
       id,
       'http://localhost:8180/realms/mentorhub',
       '00000000-0000-4000-8000-' || right(id::text, 12)
from users;

-- Invitations. The token is sample-center-invitation-token- and the number in 12 digits. README.md documents the
-- link of invitation 1, which the persona Not invited can accept. A link lasts 7 days, the default link lifetime,
-- after the invitation was created or, with link_ago, given a new link.
with sample (n, email, created_by, created_ago, link_ago, accepted_ago, revoked_by, revoked_ago, roles) as (
    values (1, 'stranger@mentorhub.local', 'admin@mentorhub.local', interval '1 day', null::interval,
            null::interval, null, null::interval, array ['MENTOR']),
           -- Its email stays QUEUED for the whole run, kept as a display state: the sample data publishes no event, so
           -- nothing delivers or resubmits it, "Send email" on it answers 409 email-queued, and a new link sends no
           -- email of its own.
           (2, 'jordan.blake@example.com', 'admin.mentor@mentorhub.local', interval '10 minutes', null, null, null,
            null, array ['STUDENT']),
           -- Expires within a day, its email failed.
           (3, 'nina.cho@example.com', 'admin@mentorhub.local', interval '6 days 20 hours', null, null, null, null,
            array ['STUDENT']),
           -- Its first email failed and was sent again.
           (4, 'oscar.hale@example.com', 'admin@mentorhub.local', interval '3 days', null, null, null, null,
            array ['MENTOR']),
           -- Expired, then given a new link with an email.
           (5, 'priya.nair@example.com', 'admin@mentorhub.local', interval '12 days', interval '1 day', null, null,
            null, array ['STUDENT']),
           -- Invites the disabled Owen Fisher back.
           (6, 'owen.fisher@example.com', 'admin.mentor@mentorhub.local', interval '2 days', null, null, null, null,
            array ['STUDENT']),
           (7, 'rachel.stone@mentorhub.local', 'admin@mentorhub.local', interval '1 day', null, null, null, null,
            array []::text[]),
           (8, 'leo.martin@mentorhub.local', 'admin@mentorhub.local', interval '4 days', null, null, null, null,
            array ['COORDINATOR', 'MENTOR']),
           -- Expired.
           (9, 'sam.taylor@example.com', 'admin@mentorhub.local', interval '20 days', null, null, null, null,
            array ['STUDENT']),
           (10, 'mark.ellison@example.com', 'admin.mentor@mentorhub.local', interval '60 days', null, null, null, null,
            array ['OBSERVER']),
           -- Revoked; the email of the first was due after the revocation and was skipped.
           (11, 'tom.wright@example.com', 'admin@mentorhub.local', interval '30 days', null, null,
            'admin@mentorhub.local', interval '29 days 23 hours 58 minutes', array ['STUDENT']),
           (12, 'anna.white@example.com', 'admin.mentor@mentorhub.local', interval '90 days', null, null,
            'admin@mentorhub.local', interval '85 days', array ['COORDINATOR']),

           -- Accepted: how every user but the bootstrapped administrator joined.
           (101, 'mentor@mentorhub.local', 'admin@mentorhub.local', interval '295 days', null,
            interval '294 days', null, null, array ['MENTOR']),
           (102, 'paul.becker@mentorhub.local', 'admin@mentorhub.local', interval '290 days', null,
            interval '289 days', null, null, array ['MENTOR']),
           (103, 'admin.mentor@mentorhub.local', 'admin@mentorhub.local', interval '288 days', null,
            interval '287 days', null, null, array ['MENTOR']),
           (104, 'daniel.kim@mentorhub.local', 'admin@mentorhub.local', interval '285 days', null,
            interval '284 days', null, null, array ['MENTOR']),
           (105, 'coordinator@mentorhub.local', 'admin@mentorhub.local', interval '282 days', null,
            interval '281 days', null, null, array ['COORDINATOR']),
           (106, 'helen.park@example.com', 'admin@mentorhub.local', interval '280 days', null, interval '279 days',
            null, null, array ['STUDENT']),
           (107, 'henry.ellis@example.com', 'admin@mentorhub.local', interval '280 days', null, interval '278 days',
            null, null, array ['STUDENT']),
           (108, 'sarah.lopez@mentorhub.local', 'admin@mentorhub.local', interval '275 days', null,
            interval '274 days', null, null, array ['COORDINATOR', 'MENTOR']),
           (109, 'victor.novak@mentorhub.local', 'admin@mentorhub.local', interval '270 days', null,
            interval '269 days', null, null, array ['MENTOR']),
           (110, 'mentor.coordinator@mentorhub.local', 'admin@mentorhub.local', interval '268 days', null,
            interval '266 days', null, null, array ['MENTOR']),
           (111, 'disabled@mentorhub.local', 'admin@mentorhub.local', interval '260 days', null, interval '259 days',
            null, null, array ['MENTOR']),
           (112, 'irene.walsh@mentorhub.local', 'admin.mentor@mentorhub.local', interval '230 days', null,
            interval '229 days', null, null, array ['COORDINATOR']),
           (113, 'thomas.grant@example.com', 'admin.mentor@mentorhub.local', interval '210 days', null,
            interval '208 days', null, null, array ['OBSERVER']),
           (114, 'observer@mentorhub.local', 'admin.mentor@mentorhub.local', interval '200 days', null,
            interval '199 days', null, null, array ['OBSERVER']),
           (115, 'student@mentorhub.local', 'admin.mentor@mentorhub.local', interval '152 days', null,
            interval '150 days', null, null, array ['STUDENT']),
           (116, 'liam.carter@example.com', 'admin@mentorhub.local', interval '152 days', null, interval '151 days',
            null, null, array ['STUDENT']),
           (117, 'emma.miller@example.com', 'admin@mentorhub.local', interval '152 days', null, interval '150 days',
            null, null, array ['STUDENT']),
           (118, 'noah.bennett@example.com', 'admin@mentorhub.local', interval '152 days', null, interval '151 days',
            null, null, array ['STUDENT']),
           (119, 'olivia.hayes@example.com', 'admin.mentor@mentorhub.local', interval '151 days', null,
            interval '150 days', null, null, array ['STUDENT']),
           (120, 'ethan.reed@example.com', 'admin.mentor@mentorhub.local', interval '151 days', null,
            interval '149 days', null, null, array ['STUDENT']),
           (121, 'lucas.price@example.com', 'paul.becker@mentorhub.local', interval '150 days', null,
            interval '149 days', null, null, array ['STUDENT']),
           (122, 'mia.foster@example.com', 'paul.becker@mentorhub.local', interval '150 days', null,
            interval '148 days', null, null, array ['STUDENT']),
           (123, 'lily.barnes@example.com', 'paul.becker@mentorhub.local', interval '150 days', null,
            interval '147 days', null, null, array ['STUDENT']),
           (124, 'owen.fisher@example.com', 'admin.mentor@mentorhub.local', interval '151 days', null,
            interval '150 days', null, null, array ['STUDENT']),
           (125, 'ruby.hughes@example.com', 'admin@mentorhub.local', interval '151 days', null, interval '150 days',
            null, null, array ['STUDENT']),
           (126, 'ava.collins@example.com', 'admin@mentorhub.local', interval '42 days', null, interval '41 days',
            null, null, array ['STUDENT']),
           (127, 'mason.ward@example.com', 'admin@mentorhub.local', interval '42 days', null, interval '40 days', null,
            null, array ['STUDENT']),
           (128, 'chloe.turner@example.com', 'admin@mentorhub.local', interval '42 days', null, interval '41 days',
            null, null, array ['STUDENT']),
           (129, 'james.porter@example.com', 'admin.mentor@mentorhub.local', interval '41 days', null,
            interval '40 days', null, null, array ['STUDENT']),
           (130, 'grace.morgan@example.com', 'admin.mentor@mentorhub.local', interval '41 days', null,
            interval '39 days', null, null, array ['STUDENT']),
           (131, 'zoe.palmer@example.com', 'admin.mentor@mentorhub.local', interval '41 days', null,
            interval '40 days', null, null, array ['STUDENT']),
           (132, 'n.shah@example.com', 'admin@mentorhub.local', interval '40 days', null, interval '38 days', null,
            null, array ['STUDENT']),
           (133, 'kevin.doyle@example.com', 'admin@mentorhub.local', interval '40 days', null, interval '39 days',
            null, null, array ['STUDENT']),
           -- Reactivates Helen Park, who had graduated, as a mentor.
           (134, 'helen.park@example.com', 'admin.mentor@mentorhub.local', interval '30 days', null,
            interval '28 days', null, null, array ['MENTOR']),
           (135, 'noroles@mentorhub.local', 'admin@mentorhub.local', interval '5 days', null, interval '4 days', null,
            null, array []::text[])),
     inserted as (
         insert into invitations (id, email, created_at, created_by, expires_at, accepted_at, accepted_user_id,
                                  revoked_at, revoked_by, token)
             select ('00000000-0000-4000-c000-' || lpad(s.n::text, 12, '0'))::uuid,
                    s.email,
                    now() - s.created_ago,
                    creator.id,
                    now() - coalesce(s.link_ago, s.created_ago) + interval '7 days',
                    now() - s.accepted_ago,
                    accepted.id,
                    now() - s.revoked_ago,
                    revoker.id,
                    'sample-center-invitation-token-' || lpad(s.n::text, 12, '0')
             from sample s
                      join users creator on creator.email = s.created_by
                      left join users accepted on s.accepted_ago is not null and accepted.email = s.email
                      left join users revoker on revoker.email = s.revoked_by)
insert
into invitation_roles (invitation_id, role)
select ('00000000-0000-4000-c000-' || lpad(n::text, 12, '0'))::uuid, unnest(roles)
from sample;

-- Emails of the open and revoked invitations, in every status.
insert into invitation_emails (id, invitation_id, reason, requested_at, requested_by, status, finished_at, failure)
select ('00000000-0000-4000-d000-' || lpad(s.n::text, 12, '0'))::uuid,
       ('00000000-0000-4000-c000-' || lpad(s.invitation::text, 12, '0'))::uuid,
       s.reason,
       now() - s.requested_ago,
       requester.id,
       s.status,
       now() - s.requested_ago + s.finished_after,
       s.failure
from (values (1, 1, 'CREATED', 'admin@mentorhub.local', interval '1 day', 'SENT', interval '3 seconds', null),
             (2, 2, 'CREATED', 'admin.mentor@mentorhub.local', interval '10 minutes', 'QUEUED', null, null),
             (3, 3, 'CREATED', 'admin@mentorhub.local', interval '6 days 20 hours', 'FAILED', interval '5 seconds',
              'Mail server connection failed: Couldn''t connect to host, port: localhost, 1025, timeout: 5000'),
             (4, 4, 'CREATED', 'admin@mentorhub.local', interval '3 days', 'FAILED', interval '5 seconds',
              'Mail server connection failed: Couldn''t connect to host, port: localhost, 1025, timeout: 5000'),
             (5, 4, 'RESENT', 'admin.mentor@mentorhub.local', interval '2 days', 'SENT', interval '3 seconds', null),
             (6, 5, 'CREATED', 'admin@mentorhub.local', interval '12 days', 'SENT', interval '3 seconds', null),
             (7, 5, 'LINK_REISSUED', 'admin@mentorhub.local', interval '1 day', 'SENT', interval '3 seconds', null),
             (8, 8, 'CREATED', 'admin@mentorhub.local', interval '4 days', 'SENT', interval '3 seconds', null),
             (9, 9, 'CREATED', 'admin@mentorhub.local', interval '20 days', 'SENT', interval '3 seconds', null),
             (10, 11, 'CREATED', 'admin@mentorhub.local', interval '30 days', 'SKIPPED',
              interval '2 minutes 5 seconds', null))
         as s (n, invitation, reason, requested_by, requested_ago, status, finished_after, failure)
         join users requester on requester.email = s.requested_by;

-- Every accepted invitation was emailed when it was created.
insert into invitation_emails (id, invitation_id, reason, requested_at, requested_by, status, finished_at)
select ('00000000-0000-4000-d000-' || right(id::text, 12))::uuid,
       id,
       'CREATED',
       created_at,
       created_by,
       'SENT',
       created_at + interval '3 seconds'
from invitations
where accepted_at is not null;

-- The audit history. Roles in details are in the order of the Role enum, as the application writes them.

insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-e100-' || right(i.id::text, 12))::uuid,
       i.created_at,
       i.created_by,
       'INVITATION_CREATED',
       'INVITATION',
       i.id,
       jsonb_build_object('email', i.email, 'roles', coalesce(
               (select jsonb_agg(r.role order by array_position(
                       array ['ADMIN', 'COORDINATOR', 'MENTOR', 'STUDENT', 'OBSERVER'], r.role))
                from invitation_roles r
                where r.invitation_id = i.id), '[]'::jsonb))
from invitations i;

insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-e400-' || right(e.id::text, 12))::uuid,
       e.requested_at,
       e.requested_by,
       'INVITATION_EMAIL_REQUESTED',
       'INVITATION',
       e.invitation_id,
       jsonb_build_object('reason', e.reason)
from invitation_emails e;

insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-e300-' || right(id::text, 12))::uuid,
       revoked_at,
       revoked_by,
       'INVITATION_REVOKED',
       'INVITATION',
       id,
       '{}'::jsonb
from invitations
where revoked_at is not null;

-- The new link of invitation 5 expires at its expires_at, a link lifetime after the reissue. The application writes
-- the expiry as Instant.toString(): UTC, with no fraction, milliseconds or microseconds, whichever is exact.
insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select '00000000-0000-4000-e000-000000000020'::uuid,
       i.expires_at - interval '7 days',
       admin.id,
       'INVITATION_LINK_REISSUED',
       'INVITATION',
       i.id,
       jsonb_build_object('expiresAt', to_char(expiry, 'YYYY-MM-DD"T"HH24:MI:SS')
           || case
                  when micros = 0 then ''
                  when micros % 1000 = 0 then to_char(expiry, '.MS')
                  else to_char(expiry, '.US')
              end
           || 'Z')
from invitations i
         cross join lateral (select i.expires_at at time zone 'UTC' as expiry,
                                    extract(microseconds from i.expires_at)::bigint % 1000000 as micros) t
         join users admin on admin.email = 'admin@mentorhub.local'
where i.id = '00000000-0000-4000-c000-000000000005';

-- A first acceptance created the user and linked the identity; a later one is written below.
insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-e200-' || right(i.id::text, 12))::uuid,
       i.accepted_at,
       i.accepted_user_id,
       'USER_INVITATION_ACCEPTED',
       'USER',
       i.accepted_user_id,
       jsonb_build_object(
               'invitationId', i.id,
               'invitedBy', i.created_by,
               'userCreated', true,
               'identityLinked', true,
               'enabled', false,
               'rolesAdded', coalesce(
                       (select jsonb_agg(r.role order by array_position(
                               array ['ADMIN', 'COORDINATOR', 'MENTOR', 'STUDENT', 'OBSERVER'], r.role))
                        from invitation_roles r
                        where r.invitation_id = i.id), '[]'::jsonb),
               'rolesRemoved', '[]'::jsonb)
from invitations i
where i.accepted_at is not null
  and not exists (select 1
                  from invitations earlier
                  where earlier.accepted_user_id = i.accepted_user_id
                    and earlier.accepted_at < i.accepted_at);

-- Helen Park, disabled as a graduate, accepts an invitation as a mentor: enabled with exactly its roles.
insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-e200-' || right(i.id::text, 12))::uuid,
       i.accepted_at,
       i.accepted_user_id,
       'USER_INVITATION_ACCEPTED',
       'USER',
       i.accepted_user_id,
       jsonb_build_object(
               'invitationId', i.id,
               'invitedBy', i.created_by,
               'userCreated', false,
               'identityLinked', false,
               'enabled', true,
               'rolesAdded', jsonb_build_array('MENTOR'),
               'rolesRemoved', jsonb_build_array('STUDENT'))
from invitations i
where i.id = '00000000-0000-4000-c000-000000000134';

insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-e000-' || lpad(s.n::text, 12, '0'))::uuid,
       now() - s.ago,
       actor.id,
       s.action,
       'USER',
       subject.id,
       s.details::jsonb
from (values (1, interval '300 days', null, 'USER_BOOTSTRAPPED_ADMIN', 'admin@mentorhub.local',
              '{"userCreated": true, "identityLinked": true, "enabled": false, "adminAdded": true}'),
             (2, interval '250 days', 'admin@mentorhub.local', 'USER_ROLES_CHANGED', 'admin.mentor@mentorhub.local',
              '{"rolesAdded": ["ADMIN"], "rolesRemoved": []}'),
             (3, interval '240 days', 'admin@mentorhub.local', 'USER_ROLES_CHANGED', 'paul.becker@mentorhub.local',
              '{"rolesAdded": ["ADMIN"], "rolesRemoved": []}'),
             (4, interval '120 days', 'admin.mentor@mentorhub.local', 'USER_ROLES_CHANGED',
              'mentor.coordinator@mentorhub.local', '{"rolesAdded": ["COORDINATOR"], "rolesRemoved": []}'),
             (5, interval '3 days', 'admin@mentorhub.local', 'USER_ROLES_CHANGED', 'kevin.doyle@example.com',
              '{"rolesAdded": [], "rolesRemoved": ["STUDENT"]}'),
             (6, interval '100 days', 'admin@mentorhub.local', 'USER_DISABLED', 'henry.ellis@example.com',
              '{"reason": "GRADUATED"}'),
             (7, interval '100 days', 'admin@mentorhub.local', 'USER_DISABLED', 'helen.park@example.com',
              '{"reason": "GRADUATED", "comment": "Completed the QA track with distinction"}'),
             (8, interval '50 days', 'admin.mentor@mentorhub.local', 'USER_DISABLED', 'lily.barnes@example.com',
              '{"reason": "EXPELLED", "comment": "Repeated plagiarism in graded assignments"}'),
             (9, interval '70 days', 'admin.mentor@mentorhub.local', 'USER_DISABLED', 'owen.fisher@example.com',
              '{"reason": "LEFT_PROGRAM"}'),
             (10, interval '10 days', 'admin.mentor@mentorhub.local', 'USER_DISABLED', 'zoe.palmer@example.com',
              '{"reason": "SECURITY", "comment": "Shared her account with another person"}'),
             (11, interval '20 days', 'admin@mentorhub.local', 'USER_DISABLED', 'ruby.hughes@example.com',
              '{"reason": "OTHER", "comment": "Paused her studies for medical reasons until the next cohort"}'),
             (12, interval '60 days', 'admin@mentorhub.local', 'USER_DISABLED', 'paul.becker@mentorhub.local',
              '{"reason": "STAFF_OFFBOARDED"}'),
             (13, interval '40 days', 'admin@mentorhub.local', 'USER_DISABLED', 'disabled@mentorhub.local',
              '{"reason": "OTHER", "comment": "Contract paused until the next cohort"}'),
             (14, interval '120 days', 'admin@mentorhub.local', 'USER_DISABLED', 'noah.bennett@example.com',
              '{"reason": "LEFT_PROGRAM", "comment": "Took a job abroad"}'),
             (15, interval '90 days', 'admin@mentorhub.local', 'USER_ENABLED', 'noah.bennett@example.com',
              '{"reason": "LEFT_PROGRAM", "comment": "Took a job abroad"}'),
             (16, interval '30 days', 'student@mentorhub.local', 'USER_PROFILE_UPDATED', 'student@mentorhub.local',
              '{"changes": [{"field": "phone", "oldValue": null, "newValue": "+77015550123"}]}'),
             (17, interval '15 days', 'admin@mentorhub.local', 'USER_PROFILE_UPDATED', 'emma.miller@example.com',
              '{"changes": [{"field": "lastName", "oldValue": "Miller", "newValue": "Brooks"}]}'),
             (18, interval '7 days', 'chloe.turner@example.com', 'USER_PROFILE_UPDATED', 'chloe.turner@example.com',
              '{"changes": [{"field": "phone", "oldValue": "+77011234567", "newValue": "+77017654321"}]}'),
             (19, interval '60 days', 'admin.mentor@mentorhub.local', 'USER_PROFILE_UPDATED',
              'victor.novak@mentorhub.local',
              '{"changes": [{"field": "firstName", "oldValue": "Vic", "newValue": "Victor"},'
                  || ' {"field": "phone", "oldValue": null, "newValue": "+77017778899"}]}'),
             (21, interval '120 days', 'olivia.hayes@example.com', 'USER_PROFILE_UPDATED', 'olivia.hayes@example.com',
              '{"changes": [{"field": "phone", "oldValue": null, "newValue": "+77052345678"}]}'),
             (22, interval '35 days', 'chloe.turner@example.com', 'USER_PROFILE_UPDATED', 'chloe.turner@example.com',
              '{"changes": [{"field": "phone", "oldValue": null, "newValue": "+77011234567"}]}'),
             (23, interval '270 days', 'daniel.kim@mentorhub.local', 'USER_PROFILE_UPDATED',
              'daniel.kim@mentorhub.local',
              '{"changes": [{"field": "phone", "oldValue": null, "newValue": "+77013334455"}]}'),
             (24, interval '200 days', 'admin.mentor@mentorhub.local', 'USER_PROFILE_UPDATED',
              'irene.walsh@mentorhub.local',
              '{"changes": [{"field": "phone", "oldValue": null, "newValue": "+77012223344"}]}'))
         as s (n, ago, actor, action, subject, details)
         join users subject on subject.email = s.subject
         left join users actor on actor.email = s.actor;

-- Daniel Kim covered for the coordinator 13 times; his history has more than one page.
insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-e500-' || lpad(k::text, 12, '0'))::uuid,
       now() - interval '250 days' + (k / 2) * interval '17 days' + (k % 2) * interval '4 days',
       admin.id,
       'USER_ROLES_CHANGED',
       'USER',
       daniel.id,
       case
           when k % 2 = 0 then '{"rolesAdded": ["COORDINATOR"], "rolesRemoved": []}'
           else '{"rolesAdded": [], "rolesRemoved": ["COORDINATOR"]}'
           end::jsonb
from generate_series(0, 25) k
         join users admin on admin.email = 'admin@mentorhub.local'
         join users daniel on daniel.email = 'daniel.kim@mentorhub.local';
