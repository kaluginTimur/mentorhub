-- The status is derived, not stored: ACCEPTED (accepted_at set), REVOKED (revoked_at set),
-- EXPIRED (expires_at <= now()), PENDING otherwise. Rows are never deleted.
create table invitations (
    id               uuid        primary key default gen_random_uuid(),
    email            text        not null check (email <> ''),
    -- SHA-256 of the token, lowercase hex; the token itself is never stored.
    token_hash       text        not null check (token_hash ~ '^[0-9a-f]{64}$'),
    created_at       timestamptz not null default now(),
    created_by       uuid        not null references users (id) on delete restrict,
    expires_at       timestamptz not null,
    accepted_at      timestamptz,
    accepted_user_id uuid        references users (id) on delete restrict,
    revoked_at       timestamptz,
    revoked_by       uuid        references users (id) on delete restrict,
    constraint invitations_token_hash_uq unique (token_hash),
    constraint invitations_accepted_ck check ((accepted_at is null) = (accepted_user_id is null)),
    constraint invitations_revoked_ck check ((revoked_at is null) = (revoked_by is null)),
    constraint invitations_closed_once_ck check (accepted_at is null or revoked_at is null)
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
