CREATE TABLE auth_factors (
    account TEXT PRIMARY KEY REFERENCES auth_accounts(uuid),
    secret TEXT,
    pending TEXT,
    pending_until BIGINT NOT NULL DEFAULT 0,
    last_step BIGINT NOT NULL DEFAULT -1
);
CREATE TABLE auth_backup_codes (
    account TEXT NOT NULL REFERENCES auth_factors(account),
    digest TEXT NOT NULL,
    PRIMARY KEY(account,digest)
);
