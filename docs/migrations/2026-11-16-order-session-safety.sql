-- MANUAL REVIEW ONLY. Not wired to application startup, Flyway or Liquibase.
-- Never executed against production; rehearsed only in disposable synthetic PostgreSQL.
-- First verify the intended database, backup/restore, schema/search_path and existing columns/indexes.
-- Run only during an authorized ordering pause after isolated PostgreSQL rehearsal.
-- Unqualified users/orders MUST resolve to the actual application's reviewed tables.
-- IF NOT EXISTS permits a retry; it does not validate an existing column/index definition.
-- Stop if preflight definitions differ from the reviewed entity mappings.

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '120s';

ALTER TABLE users ADD COLUMN IF NOT EXISTS session_version bigint NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS enabled boolean NOT NULL DEFAULT true;

ALTER TABLE orders ADD COLUMN IF NOT EXISTS request_key_hash varchar(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS request_fingerprint varchar(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS inventory_reserved boolean;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS accepted_at timestamp without time zone;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS accepted_by varchar(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS acceptance_reference varchar(160);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_collected_at timestamp without time zone;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_collected_by varchar(255);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cod_collection_reference varchar(160);

-- PostgreSQL unique indexes allow multiple NULLs, preserving historical rows without retry keys.
CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_request_key_hash ON orders (request_key_hash);

COMMIT;

-- No order amounts, delivery fees, prices, stock, owners, status or payment history are rewritten.
-- Historical inventory_reserved/audit/retry values intentionally remain NULL.
-- Session defaults do not approve any existing account: separately review compromised access.
