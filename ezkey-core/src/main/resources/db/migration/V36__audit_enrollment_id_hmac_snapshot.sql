-- ============================================================================
-- Ezkey Migration V36: Add enrollment_id Snapshot for HMAC Integrity
-- ============================================================================
-- Description: Adds enrollment_id_hmac_snapshot column to preserve the
--              enrollment_id value used in HMAC canonical form when the
--              referenced enrollment is later deleted (ON DELETE SET NULL
--              would otherwise alter the row and break verification).
--
-- Context: When an enrollment is deleted, the audit_log.enrollment_id FK
--          cascades SET NULL, changing the row after HMAC was computed.
--          This column stores the value at sign time and is used for
--          verification; it has no FK and is never modified by cascades.
--
-- Author: Ezkey contributors
-- Date: 2026-02
-- ============================================================================

ALTER TABLE ezkey_audit_log
    ADD COLUMN IF NOT EXISTS enrollment_id_hmac_snapshot INT;

COMMENT ON COLUMN ezkey_audit_log.enrollment_id_hmac_snapshot IS
'Copy of enrollment_id at HMAC sign time; used in canonical form for verification. '
'Never cascaded by enrollment deletion (no FK). Falls back to enrollment_id when null.';
