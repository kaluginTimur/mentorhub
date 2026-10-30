-- Invitation tokens are stored in plaintext instead of their SHA-256 hash (spec invitation-links).
-- Acceptance requires a verified email equal to the invitation's, which is now the only protection of a link.
alter table invitations add column token text;

-- Existing hashes cannot be turned back into tokens, so every row gets a new token in the application's format:
-- 32 bytes from two random UUIDs (about 244 random bits), base64url without padding, 43 characters.
update invitations
set token = rtrim(
        translate(
            encode(
                decode(replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', ''), 'hex'),
                'base64'),
            '+/', '-_'),
        '=');

alter table invitations
    alter column token set not null,
    add constraint invitations_token_ck check (token ~ '^[A-Za-z0-9_-]{43}$'),
    add constraint invitations_token_uq unique (token);

-- Drops the hash's check and unique constraints with it.
alter table invitations drop column token_hash;
