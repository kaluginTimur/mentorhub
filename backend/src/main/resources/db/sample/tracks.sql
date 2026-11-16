-- The sample center data of the tracks module (DEV-1, DEV-2): the tracks of the training center and their
-- coordinators. Runs after users.sql and refers to users by email. Times are relative to the start.
--
-- Fixed ids by kind, with the number in the last group:
--   tracks       00000000-0000-4000-f000-...   1 to 99
--   audit_log    00000000-0000-4000-f<k>00-... f100 track created (number of the track), f200 track updated,
--                                              f300 track archived or restored, f400 coordinator named and f500
--                                              coordinator removed (number of the assignment), f600 and f700 the
--                                              entries about the user of f400 and f500, with the same number
--
-- An assignment is one period in which a user coordinated a track: named, then removed unless still a coordinator.
-- The history is consistent with the tracks: the code and the name of a track are those of its last update, its status
-- that of its last archiving or restoring, nothing changes an archived track, and every coordinator was named while
-- active with the coordinator role. Laura Chen was disabled and Daniel Kim lost the coordinator role afterwards, so
-- their assignments are marked. The entries about a user name the track with the code and the name it had at that
-- moment, derived from the entries about the track.

-- The updates come first: the creation entries below take the code and the name a track was created with from them.
-- DevOps changed both fields at once, then only the code, then only the name.
insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-f200-' || lpad(s.n::text, 12, '0'))::uuid,
       now() - s.ago,
       actor.id,
       'TRACK_UPDATED',
       'TRACK',
       ('00000000-0000-4000-f000-' || lpad(s.track::text, 12, '0'))::uuid,
       s.details::jsonb
from (values (1, 3, interval '255 days', 'admin@mentorhub.local',
              '{"changes": [{"field": "code", "oldValue": "OPS", "newValue": "DEVOPS"},'
                  || ' {"field": "name", "oldValue": "Operations", "newValue": "DevOps"}]}'),
             (2, 3, interval '150 days', 'admin.mentor@mentorhub.local',
              '{"changes": [{"field": "code", "oldValue": "DEVOPS", "newValue": "DevOps"}]}'),
             (3, 3, interval '60 days', 'admin@mentorhub.local',
              '{"changes": [{"field": "name", "oldValue": "DevOps", "newValue": "DevOps Engineering"}]}'))
         as s (n, track, ago, actor, details)
         join users actor on actor.email = s.actor;

-- Two active tracks share the code QA; Frontend Development is archived with its coordinator.
with sample (n, code, name, status, created_ago, created_by) as (
    values (1, 'SWE', 'Software Engineering', 'ACTIVE', interval '280 days', 'admin@mentorhub.local'),
           (2, 'QA', 'Quality Assurance', 'ACTIVE', interval '262 days', 'admin@mentorhub.local'),
           (3, 'DevOps', 'DevOps Engineering', 'ACTIVE', interval '265 days', 'admin@mentorhub.local'),
           (4, 'FE', 'Frontend Development', 'ARCHIVED', interval '240 days', 'admin@mentorhub.local'),
           -- No coordinators yet.
           (5, 'QA', 'QA Automation', 'ACTIVE', interval '3 days', 'admin.mentor@mentorhub.local')),
     inserted as (
         insert into tracks (id, code, name, status, created_at)
             select ('00000000-0000-4000-f000-' || lpad(n::text, 12, '0'))::uuid, code, name, status,
                    now() - created_ago
             from sample)
insert
into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-f100-' || lpad(s.n::text, 12, '0'))::uuid,
       now() - s.created_ago,
       creator.id,
       'TRACK_CREATED',
       'TRACK',
       t.id,
       jsonb_build_object('code', coalesce(original.code, s.code), 'name', coalesce(original.name, s.name))
from sample s
         cross join lateral (select ('00000000-0000-4000-f000-' || lpad(s.n::text, 12, '0'))::uuid as id) t
         cross join lateral (
    select (select c ->> 'oldValue'
            from audit_log a
                     cross join jsonb_array_elements(a.details -> 'changes') c
            where a.subject_type = 'TRACK' and a.subject_id = t.id and a.action = 'TRACK_UPDATED'
              and c ->> 'field' = 'code'
            order by a.occurred_at
            limit 1) as code,
           (select c ->> 'oldValue'
            from audit_log a
                     cross join jsonb_array_elements(a.details -> 'changes') c
            where a.subject_type = 'TRACK' and a.subject_id = t.id and a.action = 'TRACK_UPDATED'
              and c ->> 'field' = 'name'
            order by a.occurred_at
            limit 1) as name) original
         join users creator on creator.email = s.created_by;

-- DevOps was paused between cohorts; Frontend Development was closed.
insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-f300-' || lpad(s.n::text, 12, '0'))::uuid,
       now() - s.ago,
       actor.id,
       s.action,
       'TRACK',
       ('00000000-0000-4000-f000-' || lpad(s.track::text, 12, '0'))::uuid,
       '{}'::jsonb
from (values (1, 3, interval '30 days', 'admin@mentorhub.local', 'TRACK_ARCHIVED'),
             (2, 3, interval '21 days', 'admin@mentorhub.local', 'TRACK_RESTORED'),
             (3, 4, interval '90 days', 'admin.mentor@mentorhub.local', 'TRACK_ARCHIVED'))
         as s (n, track, ago, actor, action)
         join users actor on actor.email = s.actor;

-- Daniel Kim was named a coordinator of DevOps an hour after each time he was given the coordinator role (users.sql),
-- and removed an hour before it was taken away, except the last time: his assignment stays, marked. The DevOps history
-- has more than one page.
with assignment (n, track, coordinator, named_by, named_at, removed_by, removed_at) as (
    select s.n,
           s.track,
           s.coordinator,
           s.named_by,
           now() - s.named_ago,
           s.removed_by,
           now() - s.removed_ago
    from (values (101, 1, 'coordinator@mentorhub.local', 'admin@mentorhub.local', interval '279 days', null,
                  null::interval),
                 (102, 1, 'sarah.lopez@mentorhub.local', 'admin@mentorhub.local', interval '273 days', null, null),
                 (103, 1, 'irene.margaret.walsh@qa-faculty.northbridge-training-academy.example.com',
                  'admin.mentor@mentorhub.local', interval '225 days', 'admin.mentor@mentorhub.local',
                  interval '205 days'),
                 (104, 2, 'laura.chen@mentorhub.local', 'admin@mentorhub.local', interval '230 days', null, null),
                 (105, 2, 'irene.margaret.walsh@qa-faculty.northbridge-training-academy.example.com',
                  'admin.mentor@mentorhub.local', interval '200 days', null, null),
                 (106, 3, 'mentor.coordinator@mentorhub.local', 'admin.mentor@mentorhub.local', interval '119 days',
                  null, null),
                 (107, 4, 'sarah.lopez@mentorhub.local', 'admin@mentorhub.local', interval '238 days', null, null))
             as s (n, track, coordinator, named_by, named_ago, removed_by, removed_ago)
    union all
    select j + 1,
           3,
           'daniel.kim@mentorhub.local',
           'admin@mentorhub.local',
           now() - interval '250 days' + j * interval '17 days' + interval '1 hour',
           case when j < 12 then 'admin@mentorhub.local' end,
           case when j < 12 then now() - interval '250 days' + j * interval '17 days' + interval '4 days'
                                     - interval '1 hour' end
    from generate_series(0, 12) j),
     resolved as (
         select a.n,
                ('00000000-0000-4000-f000-' || lpad(a.track::text, 12, '0'))::uuid as track_id,
                coordinator.id                                                   as user_id,
                named_by.id                                                      as named_by,
                a.named_at,
                removed_by.id                                                    as removed_by,
                a.removed_at
         from assignment a
                  join users coordinator on coordinator.email = a.coordinator
                  join users named_by on named_by.email = a.named_by
                  left join users removed_by on removed_by.email = a.removed_by),
     coordinators as (
         insert into track_coordinators (track_id, user_id, created_at)
             select track_id, user_id, named_at
             from resolved
             where removed_at is null),
     named as (
         insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
             select ('00000000-0000-4000-f400-' || lpad(n::text, 12, '0'))::uuid,
                    named_at,
                    named_by,
                    'TRACK_COORDINATOR_ADDED',
                    'TRACK',
                    track_id,
                    jsonb_build_object('userId', user_id)
             from resolved)
insert
into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-f500-' || lpad(n::text, 12, '0'))::uuid,
       removed_at,
       removed_by,
       'TRACK_COORDINATOR_REMOVED',
       'TRACK',
       track_id,
       jsonb_build_object('userId', user_id)
from resolved
where removed_at is not null;

-- The same namings and removals in the coordinators' histories, with the track as it was at that moment.
insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-' || case e.action when 'TRACK_COORDINATOR_ADDED' then 'f600' else 'f700' end || '-'
            || right(e.id::text, 12))::uuid,
       e.occurred_at,
       e.actor_user_id,
       'USER_' || e.action,
       'USER',
       (e.details ->> 'userId')::uuid,
       jsonb_build_object('track', jsonb_build_object('id', e.subject_id, 'code', track.code, 'name', track.name))
from audit_log e
         join audit_log created
              on created.subject_type = 'TRACK' and created.subject_id = e.subject_id
                  and created.action = 'TRACK_CREATED'
         cross join lateral (
    select coalesce((select c ->> 'newValue'
                     from audit_log u
                              cross join jsonb_array_elements(u.details -> 'changes') c
                     where u.subject_type = 'TRACK' and u.subject_id = e.subject_id and u.action = 'TRACK_UPDATED'
                       and c ->> 'field' = 'code' and u.occurred_at < e.occurred_at
                     order by u.occurred_at desc
                     limit 1), created.details ->> 'code') as code,
           coalesce((select c ->> 'newValue'
                     from audit_log u
                              cross join jsonb_array_elements(u.details -> 'changes') c
                     where u.subject_type = 'TRACK' and u.subject_id = e.subject_id and u.action = 'TRACK_UPDATED'
                       and c ->> 'field' = 'name' and u.occurred_at < e.occurred_at
                     order by u.occurred_at desc
                     limit 1), created.details ->> 'name') as name) track
where e.subject_type = 'TRACK'
  and e.action in ('TRACK_COORDINATOR_ADDED', 'TRACK_COORDINATOR_REMOVED');
