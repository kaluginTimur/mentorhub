-- Requests to email an invitation's link (spec invitation-emails). The row stores no link or token: the link is built
-- from the invitation's current token when the email is sent. Rows are never deleted.
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

