-- The sample center data of the enrollments module (DEV-1, DEV-2): who studies on which track. Runs after users.sql
-- and tracks.sql and refers to users by email. Times are relative to the start, and so are the start dates.
--
-- Fixed ids by kind, with the number in the last group:
--   enrollments  00000000-0000-4000-9000-...   1 to 99
--   audit_log    00000000-0000-4000-9<k>00-... 9100 the entry about the track and 9200 the entry about the student,
--                                              with the number of the enrollment
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
