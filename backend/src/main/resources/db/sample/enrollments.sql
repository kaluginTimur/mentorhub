-- The sample center data of the enrollments module (DEV-1, DEV-2): who studies on which track. Runs after users.sql
-- and tracks.sql and refers to users by email. Times are relative to the start, and so are the start dates.
--
-- Fixed ids by kind, with the number in the last group:
--   enrollments  00000000-0000-4000-9000-...   1 to 99
--   audit_log    00000000-0000-4000-9<k>00-... 9100 the entry about the track and 9200 the entry about the student,
--                                              with the number of the enrollment; 9300, 9400 and 9500 a mentor
--                                              assigned, 9600, 9700 and 9800 a mentor removed, about the track, the
--                                              student and the mentor in this order, with the number of the mentor
--                                              assignment
--
-- Every enrollment is one the application could have recorded: the student was active with the student role, the
-- track active and the one who enrolled an administrator or then a coordinator of the track; a student has one
-- enrollment, and no track with one was archived afterwards, so DevOps (archived and restored before), Frontend
-- Development and the enrollments all agree. Software Engineering has more than one page; Quality Assurance holds the
-- Student persona, Ruby Hughes since disabled and Helen Park, who lost the student role when she came back as a
-- mentor; QA Automation has one student and no coordinator to contact; DevOps has none. James Porter, Mason Ward and
-- the students who joined in the last two weeks are the candidates.
with sample (n, track, student, enrolled_by, enrolled_ago, start_ago) as (
    -- The spring cohort of Software Engineering; Marcus Lane and Nadia Rahman joined late.
    values (1, 1, 'liam.carter@example.com', 'coordinator@mentorhub.local', interval '144 days', interval '140 days'),
           (2, 1, 'emma.miller@example.com', 'coordinator@mentorhub.local', interval '144 days', interval '140 days'),
           (3, 1, 'noah.bennett@example.com', 'coordinator@mentorhub.local', interval '144 days', interval '140 days'),
           (4, 1, 'ethan.reed@example.com', 'sarah.lopez@mentorhub.local', interval '144 days', interval '140 days'),
           (5, 1, 'lucas.price@example.com', 'sarah.lopez@mentorhub.local', interval '143 days', interval '140 days'),
           (6, 1, 'lily.barnes@example.com', 'coordinator@mentorhub.local', interval '143 days', interval '140 days'),
           (7, 1, 'amelia.clarke@example.com', 'coordinator@mentorhub.local', interval '143 days',
            interval '140 days'),
           (8, 1, 'benjamin.hart@example.com', 'coordinator@mentorhub.local', interval '143 days', interval '140 days'),
           (9, 1, 'charlotte.dunn@example.com', 'sarah.lopez@mentorhub.local', interval '143 days',
            interval '140 days'),
           (10, 1, 'diego.ortiz@example.com', 'sarah.lopez@mentorhub.local', interval '143 days', interval '140 days'),
           (11, 1, 'eleanor.shaw@example.com', 'admin@mentorhub.local', interval '142 days', interval '140 days'),
           (12, 1, 'felix.romero@example.com', 'coordinator@mentorhub.local', interval '142 days', interval '140 days'),
           (13, 1, 'hannah.pierce@example.com', 'coordinator@mentorhub.local', interval '142 days',
            interval '140 days'),
           (14, 1, 'isaac.moreno@example.com', 'coordinator@mentorhub.local', interval '142 days', interval '140 days'),
           (15, 1, 'julia.webb@example.com', 'sarah.lopez@mentorhub.local', interval '142 days', interval '140 days'),
           (16, 1, 'kai.sullivan@example.com', 'sarah.lopez@mentorhub.local', interval '142 days', interval '140 days'),
           (17, 1, 'leah.fleming@example.com', 'coordinator@mentorhub.local', interval '142 days', interval '140 days'),
           (18, 1, 'marcus.lane@example.com', 'coordinator@mentorhub.local', interval '138 days', interval '138 days'),
           (19, 1, 'nadia.rahman@example.com', 'coordinator@mentorhub.local', interval '138 days', interval '138 days'),
           -- The autumn cohort of Software Engineering; Sienna Holt was enrolled after it had started.
           (20, 1, 'ava.collins@example.com', 'coordinator@mentorhub.local', interval '37 days', interval '35 days'),
           (21, 1, 'chloe.turner@example.com', 'coordinator@mentorhub.local', interval '37 days', interval '35 days'),
           (22, 1, 'n.shah@example.com', 'sarah.lopez@mentorhub.local', interval '37 days', interval '35 days'),
           (23, 1, 'zoe.palmer@example.com', 'coordinator@mentorhub.local', interval '37 days', interval '35 days'),
           (24, 1, 'oliver.quinn@example.com', 'coordinator@mentorhub.local', interval '37 days', interval '35 days'),
           (25, 1, 'penelope.ross@example.com', 'sarah.lopez@mentorhub.local', interval '36 days', interval '35 days'),
           (26, 1, 'rafael.santos@example.com', 'coordinator@mentorhub.local', interval '36 days', interval '35 days'),
           (27, 1, 'sienna.holt@example.com', 'admin.mentor@mentorhub.local', interval '30 days', interval '35 days'),
           (34, 1, 'alexandra.konstantinopoulou@students.northbridge-training-academy.example.com',
            'coordinator@mentorhub.local', interval '37 days', interval '35 days'),
           -- Quality Assurance. Helen Park was enrolled before the track had a coordinator.
           (28, 2, 'helen.park@example.com', 'admin@mentorhub.local', interval '260 days', interval '255 days'),
           (29, 2, 'student@mentorhub.local', 'laura.chen@mentorhub.local', interval '148 days', interval '140 days'),
           (30, 2, 'olivia.hayes@example.com',
            'irene.margaret.walsh@qa-faculty.northbridge-training-academy.example.com', interval '147 days',
            interval '140 days'),
           (31, 2, 'ruby.hughes@example.com',
            'irene.margaret.walsh@qa-faculty.northbridge-training-academy.example.com', interval '147 days',
            interval '140 days'),
           (32, 2, 'mia.charlotte.foster@students.northbridge-training-academy.example.com',
            'laura.chen@mentorhub.local', interval '146 days', interval '140 days'),
           -- QA Automation, from a start date a week ahead.
           (33, 5, 'grace.morgan@example.com', 'admin.mentor@mentorhub.local', interval '2 days', interval '-7 days'))
insert
into enrollments (id, student_id, track_id, start_date, enrolled_by, created_at)
select ('00000000-0000-4000-9000-' || lpad(s.n::text, 12, '0'))::uuid,
       student.id,
       ('00000000-0000-4000-f000-' || lpad(s.track::text, 12, '0'))::uuid,
       (now() - s.start_ago)::date,
       enrolled_by.id,
       now() - s.enrolled_ago
from sample s
         join users student on student.email = s.student
         join users enrolled_by on enrolled_by.email = s.enrolled_by;

insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-9100-' || right(e.id::text, 12))::uuid,
       e.created_at,
       e.enrolled_by,
       'TRACK_STUDENT_ENROLLED',
       'TRACK',
       e.track_id,
       jsonb_build_object('userId', e.student_id, 'startDate', to_char(e.start_date, 'YYYY-MM-DD'))
from enrollments e;

-- The same enrollments in the students' histories, with the track as it was at that moment.
insert into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-9200-' || right(e.id::text, 12))::uuid,
       e.created_at,
       e.enrolled_by,
       'USER_ENROLLED',
       'USER',
       e.student_id,
       jsonb_build_object('track', jsonb_build_object('id', e.track_id, 'code', track.code, 'name', track.name),
                          'startDate', to_char(e.start_date, 'YYYY-MM-DD'))
from enrollments e
         join audit_log created
              on created.subject_type = 'TRACK' and created.subject_id = e.track_id
                  and created.action = 'TRACK_CREATED'
         cross join lateral (
    select coalesce((select c ->> 'newValue'
                     from audit_log u
                              cross join jsonb_array_elements(u.details -> 'changes') c
                     where u.subject_type = 'TRACK' and u.subject_id = e.track_id and u.action = 'TRACK_UPDATED'
                       and c ->> 'field' = 'code' and u.occurred_at < e.created_at
                     order by u.occurred_at desc
                     limit 1), created.details ->> 'code') as code,
           coalesce((select c ->> 'newValue'
                     from audit_log u
                              cross join jsonb_array_elements(u.details -> 'changes') c
                     where u.subject_type = 'TRACK' and u.subject_id = e.track_id and u.action = 'TRACK_UPDATED'
                       and c ->> 'field' = 'name' and u.occurred_at < e.created_at
                     order by u.occurred_at desc
                     limit 1), created.details ->> 'name') as name) track;

-- The mentors of the enrollments (ENR-9 to ENR-12). An assignment is one period in which a user was a mentor of an
-- enrollment: assigned, then removed unless still a mentor. It is numbered in the order of the enrollment, then the
-- mentor's email. Every mentor was assigned while active with the mentor role, after the enrollment, by an
-- administrator or a coordinator of the track at that moment. Mark Mentor leads every Software Engineering student but
-- Alexandra Konstantinopoulou, and three of Quality Assurance, more than one page, among them Lily Barnes, Zoe Palmer
-- and Ruby Hughes since disabled and Helen Park without the student role; he was removed from Mia Foster. Alma
-- Admin-Mentor leads four, Milo Mentor-Coordinator none. The Student persona has four mentors and is shown two: Dan
-- Disabled was disabled and Thomas Grant lost the mentor role afterwards, so they are marked on the track page. Paul
-- Becker was disabled afterwards too. On Software Engineering every count of students shows: Mark Mentor more than a
-- scroll of them, Paul Becker one, Daniel Kim two with Alexandra Konstantinopoulou and her long email, Konstantin
-- Vasilenko two with Zoe Palmer since disabled, Thomas Grant two after losing the role. Olivia Hayes had Daniel Kim
-- until he was removed, and has no mentor now, nor do Mia Foster and Grace Morgan.
with assignment (enrollment, mentor, assigned_by, assigned_ago, removed_by, removed_ago) as (
    select e,
           'mentor@mentorhub.local',
           'coordinator@mentorhub.local',
           case when e <= 19 then interval '135 days' else interval '29 days' end,
           null,
           null::interval
    from generate_series(1, 27) e
    union all
    values (1, 'admin.mentor@mentorhub.local', 'admin.mentor@mentorhub.local', interval '100 days', null, null),
           (2, 'daniel.kim@mentorhub.local', 'sarah.lopez@mentorhub.local', interval '120 days', null, null),
           (34, 'daniel.kim@mentorhub.local', 'sarah.lopez@mentorhub.local', interval '29 days', null, null),
           (3, 'paul.becker@mentorhub.local', 'coordinator@mentorhub.local', interval '135 days', null, null),
           (4, 'thomas.grant@example.com', 'coordinator@mentorhub.local', interval '99 days', null, null),
           (5, 'thomas.grant@example.com', 'coordinator@mentorhub.local', interval '99 days', null, null),
           (20, 'sarah.lopez@mentorhub.local', 'coordinator@mentorhub.local', interval '29 days', null, null),
           (21, 'sarah.lopez@mentorhub.local', 'coordinator@mentorhub.local', interval '29 days', null, null),
           (22, 'sarah.lopez@mentorhub.local', 'coordinator@mentorhub.local', interval '29 days', null, null),
           (23, 'sarah.lopez@mentorhub.local', 'coordinator@mentorhub.local', interval '29 days', null, null),
           (24, 'sarah.lopez@mentorhub.local', 'coordinator@mentorhub.local', interval '29 days', null, null),
           (23, 'konstantin.vasilenko@mentoring-staff.northbridge-training-academy.example.com',
            'coordinator@mentorhub.local', interval '28 days', null, null),
           (24, 'konstantin.vasilenko@mentoring-staff.northbridge-training-academy.example.com',
            'coordinator@mentorhub.local', interval '28 days', null, null),
           (25, 'admin.mentor@mentorhub.local', 'admin.mentor@mentorhub.local', interval '28 days', null, null),
           (26, 'admin.mentor@mentorhub.local', 'admin.mentor@mentorhub.local', interval '28 days', null, null),
           (27, 'admin.mentor@mentorhub.local', 'admin.mentor@mentorhub.local', interval '28 days', null, null),
           (28, 'mentor@mentorhub.local', 'laura.chen@mentorhub.local', interval '135 days', null, null),
           (29, 'mentor@mentorhub.local', 'laura.chen@mentorhub.local', interval '135 days', null, null),
           (29, 'disabled@mentorhub.local', 'laura.chen@mentorhub.local', interval '125 days', null, null),
           (29, 'victor.novak@mentorhub.local', 'laura.chen@mentorhub.local', interval '120 days', null, null),
           (29, 'thomas.grant@example.com',
            'irene.margaret.walsh@qa-faculty.northbridge-training-academy.example.com', interval '100 days', null,
            null),
           (30, 'daniel.kim@mentorhub.local',
            'irene.margaret.walsh@qa-faculty.northbridge-training-academy.example.com', interval '140 days',
            'irene.margaret.walsh@qa-faculty.northbridge-training-academy.example.com', interval '15 days'),
           (31, 'mentor@mentorhub.local',
            'irene.margaret.walsh@qa-faculty.northbridge-training-academy.example.com', interval '135 days', null,
            null),
           (32, 'mentor@mentorhub.local', 'laura.chen@mentorhub.local', interval '130 days',
            'laura.chen@mentorhub.local', interval '50 days')),
     resolved as (
         select row_number() over (order by a.enrollment, a.mentor) as n,
                e.id                                                 as enrollment_id,
                e.track_id,
                e.student_id,
                mentor.id                                            as mentor_id,
                assigned_by.id                                       as assigned_by,
                now() - a.assigned_ago                               as assigned_at,
                removed_by.id                                        as removed_by,
                now() - a.removed_ago                                as removed_at
         from assignment a
                  join enrollments e on e.id = ('00000000-0000-4000-9000-' || lpad(a.enrollment::text, 12, '0'))::uuid
                  join users mentor on mentor.email = a.mentor
                  join users assigned_by on assigned_by.email = a.assigned_by
                  left join users removed_by on removed_by.email = a.removed_by),
     mentors as (
         insert into enrollment_mentors (enrollment_id, mentor_id, assigned_at, assigned_by)
             select enrollment_id, mentor_id, assigned_at, assigned_by
             from resolved
             where removed_at is null),
     change (n, removed, occurred_at, actor, track_id, student_id, mentor_id) as (
         select n, false, assigned_at, assigned_by, track_id, student_id, mentor_id
         from resolved
         union all
         select n, true, removed_at, removed_by, track_id, student_id, mentor_id
         from resolved
         where removed_at is not null)
-- Each change in the histories of the track, the student and the mentor, with the track as it was at that moment.
insert
into audit_log (id, occurred_at, actor_user_id, action, subject_type, subject_id, details)
select ('00000000-0000-4000-9' || (subject.k + case when c.removed then 6 else 3 end)::text || '00-'
            || lpad(c.n::text, 12, '0'))::uuid,
       c.occurred_at,
       c.actor,
       case subject.k
           when 0 then 'TRACK_ENROLLMENT_MENTOR_'
           when 1 then 'USER_MENTOR_'
           else 'USER_STUDENT_' end || case when c.removed then 'REMOVED' else 'ASSIGNED' end,
       case subject.k when 0 then 'TRACK' else 'USER' end,
       case subject.k when 0 then c.track_id when 1 then c.student_id else c.mentor_id end,
       case subject.k
           when 0 then jsonb_build_object('userId', c.student_id, 'mentorId', c.mentor_id)
           when 1 then jsonb_build_object('mentorId', c.mentor_id, 'track', track.reference)
           else jsonb_build_object('studentId', c.student_id, 'track', track.reference) end
from change c
         cross join (values (0), (1), (2)) as subject (k)
         join audit_log created
              on created.subject_type = 'TRACK' and created.subject_id = c.track_id
                  and created.action = 'TRACK_CREATED'
         cross join lateral (
    select jsonb_build_object(
                   'id', c.track_id,
                   'code', coalesce((select ch ->> 'newValue'
                                     from audit_log u
                                              cross join jsonb_array_elements(u.details -> 'changes') ch
                                     where u.subject_type = 'TRACK' and u.subject_id = c.track_id
                                       and u.action = 'TRACK_UPDATED' and ch ->> 'field' = 'code'
                                       and u.occurred_at < c.occurred_at
                                     order by u.occurred_at desc
                                     limit 1), created.details ->> 'code'),
                   'name', coalesce((select ch ->> 'newValue'
                                     from audit_log u
                                              cross join jsonb_array_elements(u.details -> 'changes') ch
                                     where u.subject_type = 'TRACK' and u.subject_id = c.track_id
                                       and u.action = 'TRACK_UPDATED' and ch ->> 'field' = 'name'
                                       and u.occurred_at < c.occurred_at
                                     order by u.occurred_at desc
                                     limit 1), created.details ->> 'name')) as reference) track;
