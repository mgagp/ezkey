-- ============================================================================
-- Ezkey Migration V35: Audit Chain Checkpoint Lifecycle Support
-- ============================================================================
-- Description: Adds lifecycle management columns to the chain checkpoint table
--              to support two operational events:
--
--              1. ARCHIVE_SEAL: when an audit partition is archived and dropped,
--                 the corresponding checkpoints are sealed so verification skips
--                 the entries_digest comparison (entries no longer in DB by design)
--                 while chain_hmac linkage is still verified.
--
--              2. GAP_DECLARATION: when the system was offline longer than the
--                 scheduler lookback window, an admin formally declares the gap
--                 with a justification. A single GAP_DECLARATION checkpoint covers
--                 the full gap period and is signed into the chain.
--
-- Author: Ezkey contributors
-- Date: 2026-02
-- ============================================================================

-- ============================================================================
-- STEP 1: Add checkpoint_type column
-- ============================================================================
-- Values: REGULAR (default), ARCHIVE_SEAL, GAP_DECLARATION

ALTER TABLE ezkey_audit_chain_checkpoint
    ADD COLUMN checkpoint_type VARCHAR(20) NOT NULL DEFAULT 'REGULAR';

-- ============================================================================
-- STEP 2: Add notes column
-- ============================================================================
-- Human-readable justification/provenance for non-REGULAR checkpoints.
-- NULL for standard REGULAR checkpoints.

ALTER TABLE ezkey_audit_chain_checkpoint
    ADD COLUMN notes TEXT;

-- ============================================================================
-- STEP 3: Add index on checkpoint_type for efficient filtering
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_chain_checkpoint_type
    ON ezkey_audit_chain_checkpoint(checkpoint_type)
    WHERE checkpoint_type <> 'REGULAR';

-- ============================================================================
-- STEP 4: Add column comments
-- ============================================================================

COMMENT ON COLUMN ezkey_audit_chain_checkpoint.checkpoint_type IS
'Lifecycle type of this checkpoint: REGULAR (normal scheduler window), '
'ARCHIVE_SEAL (entries archived to external storage, entries_digest not re-verifiable), '
'GAP_DECLARATION (admin-declared downtime gap, no entries expected).';

COMMENT ON COLUMN ezkey_audit_chain_checkpoint.notes IS
'Human-readable justification or provenance note set by the admin during lifecycle '
'operations (seal-archive, declare-gap). NULL for REGULAR checkpoints.';

-- ============================================================================
-- Migration Complete
-- ============================================================================
