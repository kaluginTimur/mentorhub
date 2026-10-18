alter table users
    add column phone            text,
    add column disabled_reason  text,
    add column disabled_comment text;

update users
set disabled_reason  = 'OTHER',
    disabled_comment = 'Disabled before reasons were recorded'
where status = 'DISABLED';

-- disabled_reason must equal the DisableReason enum in code.
alter table users
    add constraint users_disabled_reason_value_ck check (
        disabled_reason in ('GRADUATED', 'EXPELLED', 'LEFT_PROGRAM', 'STAFF_OFFBOARDED', 'SECURITY', 'OTHER')),
    add constraint users_disabled_reason_ck check (
        (status = 'DISABLED') = (disabled_reason is not null)
        and (disabled_comment is null or status = 'DISABLED')
        and (disabled_reason <> 'OTHER' or disabled_comment is not null));

create table audit_log (
    id            uuid        primary key default gen_random_uuid(),
    occurred_at   timestamptz not null default now(),
    actor_user_id uuid        references users (id) on delete restrict,
    action        text        not null,
    subject_type  text        not null,
    -- No foreign key: the subject lives in the table of the module that wrote the entry (ADR 0006).
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
