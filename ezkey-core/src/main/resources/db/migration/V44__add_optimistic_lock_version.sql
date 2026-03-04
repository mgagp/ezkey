-- Add optimistic locking version column to updatable entities.
-- Enables JPA @Version for concurrent update protection (409 on stale version).
-- Phase 0 of Partial Update Analysis Plan.

ALTER TABLE ezkey_tenant ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE ezkey_admin ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE ezkey_enrollment ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE ezkey_api_key ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
