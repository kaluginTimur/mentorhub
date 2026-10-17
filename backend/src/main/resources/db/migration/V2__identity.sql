create table users (
    id         uuid        primary key default gen_random_uuid(),
    email      text        not null check (email <> ''),
    first_name text,
    last_name  text,
    status     text        not null check (status in ('ACTIVE', 'DISABLED')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index users_email_lower_uq on users (lower(email));

create table user_identities (
    id         uuid        primary key default gen_random_uuid(),
    user_id    uuid        not null references users (id) on delete restrict,
    issuer     text        not null,
    subject    text        not null,
    created_at timestamptz not null default now(),
    constraint user_identities_issuer_subject_uq unique (issuer, subject)
);

create index user_identities_user_id_idx on user_identities (user_id);

-- Must equal the Role enum in code; a test compares them.
create table roles (
    name text primary key
);

insert into roles (name) values ('ADMIN'), ('COORDINATOR'), ('MENTOR'), ('STUDENT'), ('OBSERVER');

create table user_roles (
    user_id    uuid        not null references users (id) on delete restrict,
    role       text        not null references roles (name) on delete restrict,
    created_at timestamptz not null default now(),
    primary key (user_id, role)
);

-- The bootstrap rule asks whether any active user has the ADMIN role.
create index user_roles_role_idx on user_roles (role);
