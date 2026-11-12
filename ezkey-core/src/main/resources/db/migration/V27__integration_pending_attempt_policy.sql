-- ============================================================================
-- Ezkey Migration V27: Per-integration pending-attempt policy (#992)
-- ============================================================================
-- Description: Each integration chooses what happens when a live authentication
--              attempt already exists for an enrollment.
--              REPLACE (default) expires that attempt and creates the new one.
--              REFUSE rejects the create while the live attempt remains.
--              Existing rows stay REPLACE so current callers do not change.
--              Cooldown is derived from attempt rows and has no column here.
--
-- Manual rollback (no Flyway undo): drop the check and the column, then delete
--              version 27 from flyway_schema_history.
--
-- Author: Ezkey contributors
-- Date: 2026-11-09
-- ============================================================================

ALTER TABLE ezkey_integration
    ADD COLUMN pending_attempt_policy VARCHAR(16) NOT NULL DEFAULT 'REPLACE';

ALTER TABLE ezkey_integration
    ADD CONSTRAINT ck_ezkey_integration_pending_attempt_policy
        CHECK (pending_attempt_policy IN ('REPLACE', 'REFUSE'));

COMMENT ON COLUMN ezkey_integration.pending_attempt_policy IS
    'REPLACE expires a live attempt and creates the new one. REFUSE rejects the create. Default REPLACE (#992).';
