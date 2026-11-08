COMMENT ON SCHEMA public IS 'MentorHub';

-- disabled_reason must equal the DisableReason enum in code.
create table users (
    id               uuid        primary key default gen_random_uuid(),
    email            text        not null check (email <> ''),
    first_name       text,
    last_name        text,
    status           text        not null check (status in ('ACTIVE', 'DISABLED')),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    phone            text,
    disabled_reason  text,
    disabled_comment text,
    constraint users_disabled_reason_value_ck check (
        disabled_reason in ('GRADUATED', 'EXPELLED', 'LEFT_PROGRAM', 'STAFF_OFFBOARDED', 'SECURITY', 'OTHER')),
    constraint users_disabled_reason_ck check (
        (status = 'DISABLED') = (disabled_reason is not null)
        and (disabled_comment is null or status = 'DISABLED')
        and (disabled_reason <> 'OTHER' or disabled_comment is not null))
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

create table audit_log (
    id            uuid        primary key default gen_random_uuid(),
    occurred_at   timestamptz not null default now(),
    actor_user_id uuid        references users (id) on delete restrict,
    action        text        not null,
    subject_type  text        not null,
    -- No foreign key: the subject lives in the table of the module that wrote the entry (decision 0005).
    subject_id    uuid        not null,
    details       jsonb       not null default '{}'
);

create index audit_log_subject_idx on audit_log (subject_type, subject_id, occurred_at desc, id desc);

create function audit_log_reject_change() returns trigger
    language plpgsql
as
$$
begin
    raise exception 'audit_log is append-only: % is not allowed', tg_op;
end;
$$;

-- TRUNCATE is not covered; no application path uses it.
create trigger audit_log_append_only
    before update or delete on audit_log
    for each statement
execute function audit_log_reject_change();

-- The status is derived, not stored: ACCEPTED (accepted_at set), REVOKED (revoked_at set),
-- EXPIRED (expires_at <= now()), PENDING otherwise. Rows are never deleted.
-- Tokens are stored in plaintext: acceptance requires a verified email equal to the invitation's, which is the only
-- protection of a link.
create table invitations (
    id               uuid        primary key default gen_random_uuid(),
    email            text        not null check (email <> ''),
    created_at       timestamptz not null default now(),
    created_by       uuid        not null references users (id) on delete restrict,
    expires_at       timestamptz not null,
    accepted_at      timestamptz,
    accepted_user_id uuid        references users (id) on delete restrict,
    revoked_at       timestamptz,
    revoked_by       uuid        references users (id) on delete restrict,
    token            text        not null,
    constraint invitations_accepted_ck check ((accepted_at is null) = (accepted_user_id is null)),
    constraint invitations_revoked_ck check ((revoked_at is null) = (revoked_by is null)),
    constraint invitations_closed_once_ck check (accepted_at is null or revoked_at is null),
    constraint invitations_token_ck check (token ~ '^[A-Za-z0-9_-]{43}$'),
    constraint invitations_token_uq unique (token)
);

-- At most one open (pending or expired) invitation per email; decides races between parallel creates.
create unique index invitations_open_email_uq on invitations (lower(email))
    where accepted_at is null and revoked_at is null;

create index invitations_created_idx on invitations (created_at desc, id desc);

create table invitation_roles (
    invitation_id uuid not null references invitations (id) on delete restrict,
    role          text not null references roles (name) on delete restrict,
    primary key (invitation_id, role)
);

-- Requests to email an invitation's link. The row stores no link or token: the link is built from the invitation's
-- current token when the email is sent. Rows are never deleted.
create table invitation_emails (
    id            uuid        primary key default gen_random_uuid(),
    invitation_id uuid        not null references invitations (id) on delete restrict,
    -- Must equal the reasons in code; also the `reason` detail of INVITATION_EMAIL_REQUESTED.
    reason        text        not null check (reason in ('CREATED', 'LINK_REISSUED', 'RESENT')),
    requested_at  timestamptz not null default now(),
    requested_by  uuid        not null references users (id) on delete restrict,
    status        text        not null default 'QUEUED' check (status in ('QUEUED', 'SENT', 'FAILED', 'SKIPPED')),
    finished_at   timestamptz,
    -- A short reason for the log reader; never the token or the message body.
    failure       text        check (char_length(failure) between 1 and 500),
    constraint invitation_emails_finished_ck check ((status = 'QUEUED') = (finished_at is null)),
    constraint invitation_emails_failure_ck check ((status = 'FAILED') = (failure is not null))
);

create index invitation_emails_latest_idx on invitation_emails (invitation_id, requested_at desc, id desc);

-- At most one queued email per invitation; decides races between parallel requests.
create unique index invitation_emails_queued_uq on invitation_emails (invitation_id) where status = 'QUEUED';
