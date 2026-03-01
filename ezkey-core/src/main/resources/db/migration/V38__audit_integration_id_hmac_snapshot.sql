-- ============================================================================
-- Ezkey Migration V38: Add integration_id Snapshot for HMAC Integrity
-- ============================================================================
-- Description: Adds integration_id_hmac_snapshot column to preserve the
--              integration_id value used in HMAC canonical form when the
--              referenced integration is later deleted (ON DELETE SET NULL
--              would otherwise alter the row and break verification).
--
-- Context: When an integration is deleted, the audit_log.integration_id FK
--          cascades SET NULL (see V24), changing the row after HMAC was
--          computed. This column stores the value at sign time and is used
--          for verification; it has no FK and is never modified by cascades.
--
--          This mirrors the pattern introduced in V36 for enrollment_id.
--
-- Author: Ezkey contributors
-- Date: 2026-03
-- ============================================================================

ALTER TABLE ezkey_audit_log
    ADD COLUMN IF NOT EXISTS integration_id_hmac_snapshot INT;

COMMENT ON COLUMN ezkey_audit_log.integration_id_hmac_snapshot IS
'Copy of integration_id at HMAC sign time; used in canonical form for verification. '
'Never cascaded by integration deletion (no FK). Falls back to integration_id when null.';
