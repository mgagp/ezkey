-- ============================================================================
-- Ezkey Migration V33: Add Audit Log Integrity Columns
-- ============================================================================
-- Description: Adds per-entry HMAC signing and instance tracking columns to
--              the audit log table for SOC 2 tamper-evidence in self-hosted
--              deployments.
--
-- New Columns:
--   instance_id  - Identifies which application instance created the entry
--   entry_hmac   - HMAC-SHA256 of the canonical entry content (Base64)
--
-- Context: In cloud environments, audit log integrity is handled by the
--          provider (e.g., AWS CloudWatch). For self-hosted / on-prem
--          deployments, per-entry HMAC signing provides tamper-evidence
--          aligned with HashiCorp Vault's audit backend approach.
--
-- Development Mode: Adds columns with ALTER TABLE to existing partitioned
--                   table. New columns are nullable to avoid breaking
--                   existing entries.
--
-- Author: Ezkey contributors
-- Date: 2026-02
-- ============================================================================

-- ============================================================================
-- STEP 1: Add instance_id Column
-- ============================================================================

ALTER TABLE ezkey_audit_log
    ADD COLUMN IF NOT EXISTS instance_id VARCHAR(50);

COMMENT ON COLUMN ezkey_audit_log.instance_id IS
'Application instance identifier (e.g., admin-api-1, auth-api-2) that created this audit entry. '
'Used for forensic analysis in HA deployments to trace which instance produced which log entries. '
'Populated from EZKEY_INSTANCE_ID environment variable; NULL for single-instance deployments.';

-- ============================================================================
-- STEP 2: Add entry_hmac Column
-- ============================================================================

ALTER TABLE ezkey_audit_log
    ADD COLUMN IF NOT EXISTS entry_hmac VARCHAR(88);

COMMENT ON COLUMN ezkey_audit_log.entry_hmac IS
'HMAC-SHA256 signature of the canonical entry content, Base64-encoded. '
'Provides per-entry tamper-evidence for SOC 2 compliance in self-hosted deployments. '
'Computed at write time using a dedicated HMAC key separate from the encryption master key. '
'NULL for entries created before integrity signing was enabled.';

-- ============================================================================
-- STEP 3: Add Index for Integrity Verification Queries
-- ============================================================================

-- Partial index on entries missing HMAC (useful for backfill or verification queries)
CREATE INDEX IF NOT EXISTS idx_audit_log_missing_hmac
    ON ezkey_audit_log(created_at)
    WHERE entry_hmac IS NULL;

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- New columns added to partitioned table (automatically propagated to all
-- existing and future partitions by PostgreSQL).
--
-- Next Steps:
-- 1. Deploy AuditHmacService to compute HMAC at write time
-- 2. Configure HMAC key file at /etc/ezkey/secrets/audit-hmac.key
-- 3. Existing entries will have entry_hmac = NULL (acceptable in dev mode)
-- ============================================================================
