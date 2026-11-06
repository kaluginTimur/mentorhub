-- The personas of DEV-1, linked to the accounts of infra/keycloak/mentorhub-realm.json by their fixed ids. The persona
-- Not invited (stranger@mentorhub.local, ...0006) is an account only and has no user. Fixed ids keep every start equal.
insert into users (id, email, first_name, last_name, status, disabled_reason, disabled_comment)
values ('00000000-0000-4000-a000-000000000001', 'admin@mentorhub.local', 'Ada', 'Admin', 'ACTIVE', null, null),
       ('00000000-0000-4000-a000-000000000002', 'mentor@mentorhub.local', 'Mark', 'Mentor', 'ACTIVE', null, null),
       ('00000000-0000-4000-a000-000000000003', 'student@mentorhub.local', 'Sofia', 'Student', 'ACTIVE', null, null),
       ('00000000-0000-4000-a000-000000000004', 'noroles@mentorhub.local', 'Nora', 'Noroles', 'ACTIVE', null, null),
       ('00000000-0000-4000-a000-000000000005', 'disabled@mentorhub.local', 'Dan', 'Disabled', 'DISABLED', 'OTHER',
        'Development user'),
       ('00000000-0000-4000-a000-000000000007', 'coordinator@mentorhub.local', 'Cora', 'Coordinator', 'ACTIVE', null,
        null),
       ('00000000-0000-4000-a000-000000000008', 'observer@mentorhub.local', 'Otto', 'Observer', 'ACTIVE', null, null),
       ('00000000-0000-4000-a000-000000000009', 'admin.mentor@mentorhub.local', 'Alma', 'Admin-Mentor', 'ACTIVE', null,
        null),
       ('00000000-0000-4000-a000-00000000000a', 'mentor.coordinator@mentorhub.local', 'Milo', 'Mentor-Coordinator',
        'ACTIVE', null, null);

insert into user_identities (id, user_id, issuer, subject)
values ('00000000-0000-4000-b000-000000000001', '00000000-0000-4000-a000-000000000001',
        'http://localhost:8180/realms/mentorhub', '00000000-0000-4000-8000-000000000001'),
       ('00000000-0000-4000-b000-000000000002', '00000000-0000-4000-a000-000000000002',
        'http://localhost:8180/realms/mentorhub', '00000000-0000-4000-8000-000000000002'),
       ('00000000-0000-4000-b000-000000000003', '00000000-0000-4000-a000-000000000003',
        'http://localhost:8180/realms/mentorhub', '00000000-0000-4000-8000-000000000003'),
       ('00000000-0000-4000-b000-000000000004', '00000000-0000-4000-a000-000000000004',
        'http://localhost:8180/realms/mentorhub', '00000000-0000-4000-8000-000000000004'),
       ('00000000-0000-4000-b000-000000000005', '00000000-0000-4000-a000-000000000005',
        'http://localhost:8180/realms/mentorhub', '00000000-0000-4000-8000-000000000005'),
       ('00000000-0000-4000-b000-000000000007', '00000000-0000-4000-a000-000000000007',
        'http://localhost:8180/realms/mentorhub', '00000000-0000-4000-8000-000000000007'),
       ('00000000-0000-4000-b000-000000000008', '00000000-0000-4000-a000-000000000008',
        'http://localhost:8180/realms/mentorhub', '00000000-0000-4000-8000-000000000008'),
       ('00000000-0000-4000-b000-000000000009', '00000000-0000-4000-a000-000000000009',
        'http://localhost:8180/realms/mentorhub', '00000000-0000-4000-8000-000000000009'),
       ('00000000-0000-4000-b000-00000000000a', '00000000-0000-4000-a000-00000000000a',
        'http://localhost:8180/realms/mentorhub', '00000000-0000-4000-8000-00000000000a');

insert into user_roles (user_id, role)
values ('00000000-0000-4000-a000-000000000001', 'ADMIN'),
       ('00000000-0000-4000-a000-000000000002', 'MENTOR'),
       ('00000000-0000-4000-a000-000000000003', 'STUDENT'),
       ('00000000-0000-4000-a000-000000000005', 'MENTOR'),
       ('00000000-0000-4000-a000-000000000007', 'COORDINATOR'),
       ('00000000-0000-4000-a000-000000000008', 'OBSERVER'),
       ('00000000-0000-4000-a000-000000000009', 'ADMIN'),
       ('00000000-0000-4000-a000-000000000009', 'MENTOR'),
       ('00000000-0000-4000-a000-00000000000a', 'MENTOR'),
       ('00000000-0000-4000-a000-00000000000a', 'COORDINATOR');
