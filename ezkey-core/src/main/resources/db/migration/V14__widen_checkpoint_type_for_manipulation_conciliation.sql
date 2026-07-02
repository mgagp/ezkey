-- ============================================================================
-- Ezkey Migration V14: Widen checkpoint_type for MANIPULATION_CONCILIATION
-- ============================================================================
-- Description: B2 integrity rupture reconciliation inserts checkpoint_type
--              'MANIPULATION_CONCILIATION' (25 chars). Column was VARCHAR(20)
--              since V6 (REGULAR, ARCHIVE_SEAL, GAP_DECLARATION only).
--
-- Author: Ezkey contributors
-- Date:   2026-07
-- ============================================================================

ALTER TABLE ezkey_audit_chain_checkpoint
    ALTER COLUMN checkpoint_type TYPE VARCHAR(40);

COMMENT ON COLUMN ezkey_audit_chain_checkpoint.checkpoint_type IS
'Lifecycle type: REGULAR, ARCHIVE_SEAL, GAP_DECLARATION, MANIPULATION_CONCILIATION '
'(integrity rupture reconciliation bridge).';
