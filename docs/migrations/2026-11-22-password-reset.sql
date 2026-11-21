-- Additive manual migration. Review the target database/schema and take a tested backup first.
-- JPA remains validate. This script never changes passwords, users, orders or product records.
-- Prerequisite: users.id is bigint; users.session_version/enabled already match current mappings
-- (see the separate 2026-11-16-order-session-safety.sql migration if those columns are missing).
-- IF NOT EXISTS supports retries, not compatibility checks: inspect existing definitions first.
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    token_hash varchar(64) PRIMARY KEY,
    user_id bigint NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    CONSTRAINT ck_password_reset_lifetime CHECK (expires_at > created_at),
    CONSTRAINT ck_password_reset_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);
CREATE INDEX IF NOT EXISTS idx_password_reset_user ON password_reset_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_password_reset_expiry ON password_reset_tokens(expires_at);
COMMIT;
