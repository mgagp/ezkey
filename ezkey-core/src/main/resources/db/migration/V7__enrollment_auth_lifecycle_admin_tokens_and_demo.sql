-- V39: Add contextual authentication fields to ezkey_auth_attempt
--
-- Extends authentication attempts with two optional plain-text context fields so the approver's
-- mobile device can display exactly what they are being asked to authorize.
--
-- Both columns are nullable to preserve full backward compatibility — existing auth attempts
-- and integrations that omit context are completely unaffected.

ALTER TABLE ezkey_auth_attempt
    ADD COLUMN context_title   VARCHAR(200),
    ADD COLUMN context_message VARCHAR(2000);

COMMENT ON COLUMN ezkey_auth_attempt.context_title   IS 'Optional short heading displayed on the mobile approval card (max 200 chars, e.g. "Payment Authorization")';
COMMENT ON COLUMN ezkey_auth_attempt.context_message IS 'Optional descriptive body explaining what the approver is authorizing (max 2000 chars, e.g. "Authorize payment of $5,000 to Suppliers Ltd.")';
-- V40: Add enrollment revocation audit fields
--
-- Adds four nullable audit columns to ezkey_enrollment to support the enrollment
-- revocation lifecycle (SOC 2 CC6.3 — removal of access).
--
-- deactivated_at / deactivated_by_admin_id : set when an admin soft-deactivates an
--   enrollment (reversible). Cleared on reactivation.
-- revoked_at / revoked_by_admin_id : set when an admin permanently revokes an
--   enrollment (irreversible). Never cleared.

ALTER TABLE ezkey_enrollment
    ADD COLUMN deactivated_at         TIMESTAMPTZ,
    ADD COLUMN deactivated_by_admin_id INTEGER REFERENCES ezkey_admin(admin_id),
    ADD COLUMN revoked_at              TIMESTAMPTZ,
    ADD COLUMN revoked_by_admin_id     INTEGER REFERENCES ezkey_admin(admin_id);
-- V41: Add optional expiration for pending enrollments
--
-- Optional expires_at on ezkey_enrollment. When set, bind/verify must complete before this time;
-- after that the enrollment is treated as expired (status EXPIRED). Null means no expiration.
-- Set at creation when policy is enabled (e.g. created_at + 30 days). Aligns with Duo/Okta
-- enrollment link validity and NIST lifecycle "expiration" traceability.

ALTER TABLE ezkey_enrollment
    ADD COLUMN expires_at TIMESTAMPTZ;

COMMENT ON COLUMN ezkey_enrollment.expires_at IS
    'Optional expiration for pending enrollment (CREATED/BOUND). When set, bind/verify must complete before this time; null means no expiration.';

-- Index for scheduled job: find CREATED enrollments with expires_at < now
CREATE INDEX idx_enrollment_expires_at_created
    ON ezkey_enrollment(expires_at)
    WHERE enrollment_status = 'CREATED' AND expires_at IS NOT NULL;
-- Phase 1: Remove integration logo column (unused; flatten plan).
-- Logo is not consumed meaningfully; model is simplified for flat integration (name/description only).
ALTER TABLE ezkey_integration DROP COLUMN IF EXISTS integration_logo;
-- Flatten integration i18n: add integration_name and integration_description to ezkey_integration,
-- backfill from ezkey_integration_i18n, then drop the i18n table.

-- Add new columns (nullable for backfill)
ALTER TABLE ezkey_integration
  ADD COLUMN integration_name VARCHAR(255) NULL,
  ADD COLUMN integration_description VARCHAR(500) NULL;

-- Backfill from first i18n entry per integration (clean start, simple approach)
UPDATE ezkey_integration ei
SET
  integration_name = (
    SELECT integration_i18n_name
    FROM ezkey_integration_i18n
    WHERE integration_id = ei.integration_id
    LIMIT 1
  ),
  integration_description = (
    SELECT integration_i18n_description
    FROM ezkey_integration_i18n
    WHERE integration_id = ei.integration_id
    LIMIT 1
  )
WHERE EXISTS (
  SELECT 1 FROM ezkey_integration_i18n WHERE integration_id = ei.integration_id
);

-- Force system integration name when null
UPDATE ezkey_integration
SET integration_name = 'Ezkey System'
WHERE is_system_integration = TRUE AND integration_name IS NULL;

-- Drop the i18n table
DROP TABLE ezkey_integration_i18n;
-- Add optimistic locking version column to updatable entities.
-- Enables JPA @Version for concurrent update protection (409 on stale version).
-- Phase 0 of Partial Update Analysis Plan.

ALTER TABLE ezkey_tenant ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE ezkey_admin ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE ezkey_enrollment ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
ALTER TABLE ezkey_api_key ADD COLUMN version BIGINT DEFAULT 0 NOT NULL;
-- Add EXPIRED to enrollment_status check constraint.
-- V41 introduced expires_at and the code marks expired enrollments as EXPIRED, but V1's
-- check constraint only allowed CREATED, BOUND, VERIFIED, INVALID, REVOKED.
-- This caused constraint violations when EnrollmentBindService/EnrollmentVerifyService
-- tried to mark expired enrollments via EnrollmentTxHelper.markExpiredAndEmitAudit().

ALTER TABLE ezkey_enrollment
    DROP CONSTRAINT ezkey_enrollment_enrollment_status_check;

ALTER TABLE ezkey_enrollment
    ADD CONSTRAINT ezkey_enrollment_enrollment_status_check
    CHECK (enrollment_status IN ('CREATED', 'BOUND', 'VERIFIED', 'INVALID', 'REVOKED', 'EXPIRED'));
-- ============================================================================
-- Replace bearer_token with bearer_token_hash (SHA-256 hex) for defense in depth.
-- Admin and recovery tokens are looked up by hash; plain token is never stored.
-- ============================================================================

-- Add new column (nullable initially to allow backfill if table has rows)
ALTER TABLE ezkey_admin_tokens ADD COLUMN bearer_token_hash VARCHAR(64);

-- Backfill: hash existing bearer_token so migration works on dev DBs with data
UPDATE ezkey_admin_tokens
SET bearer_token_hash = encode(sha256(bearer_token::bytea), 'hex')
WHERE bearer_token_hash IS NULL;

-- Enforce NOT NULL and UNIQUE
ALTER TABLE ezkey_admin_tokens ALTER COLUMN bearer_token_hash SET NOT NULL;
ALTER TABLE ezkey_admin_tokens ADD CONSTRAINT uk_admin_tokens_bearer_token_hash UNIQUE (bearer_token_hash);

-- Drop index that uses bearer_token before dropping the column
DROP INDEX IF EXISTS idx_admin_tokens_active;

-- Remove plaintext token column
ALTER TABLE ezkey_admin_tokens DROP COLUMN bearer_token;

-- Recreate index for active token lookup by hash
CREATE INDEX idx_admin_tokens_active ON ezkey_admin_tokens(bearer_token_hash, active) WHERE active = TRUE;

-- Column comment
COMMENT ON COLUMN ezkey_admin_tokens.bearer_token_hash IS
'SHA-256 hash (hex) of the bearer token. Used for lookup; plain token is never stored. Protects against DB breach.';
-- Demo-only: when combined with ezkey.demo.mitm-signature-enabled on Auth API, the Pending
-- response body is altered after signing to simulate a MITM (integration signature mismatch).
ALTER TABLE ezkey_auth_attempt
    ADD COLUMN demo_mitm_signature_enabled BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ezkey_auth_attempt.demo_mitm_signature_enabled IS
    'When true, Pending JSON may be tampered after signing if Auth API demo MITM is enabled.';
