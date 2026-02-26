-- ============================================================================
-- Ezkey Migration V34: Create Audit Chain Checkpoint Table
-- ============================================================================
-- Description: Creates the checkpoint table for periodic audit log chaining.
--              Provides completeness proof (detects row insertion, deletion,
--              reordering) by linking consecutive time windows with HMAC chains.
--
-- Context: Per-entry HMAC (V33) proves individual entry integrity but cannot
--          detect deleted or inserted rows. Periodic chain checkpoints fill
--          this gap by computing a digest over each 5-minute window of entries
--          and chaining consecutive windows via HMAC.
--
-- Author: Ezkey contributors
-- Date: 2026-02
-- ============================================================================

-- ============================================================================
-- STEP 1: Create Chain Checkpoint Table
-- ============================================================================

CREATE TABLE IF NOT EXISTS ezkey_audit_chain_checkpoint (
    checkpoint_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    window_start     TIMESTAMPTZ NOT NULL,
    window_end       TIMESTAMPTZ NOT NULL,
    entry_count      INT NOT NULL,
    first_entry_id   BIGINT,
    last_entry_id    BIGINT,
    entries_digest   VARCHAR(88) NOT NULL,
    prev_chain_hmac  VARCHAR(88),
    chain_hmac       VARCHAR(88) NOT NULL,
    created_at       TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,

    CONSTRAINT uq_chain_checkpoint_window UNIQUE (window_start, window_end)
);

-- ============================================================================
-- STEP 2: Add Comments
-- ============================================================================

COMMENT ON TABLE ezkey_audit_chain_checkpoint IS
'Periodic chain checkpoints for audit log completeness proof. Each row represents a '
'time window (default 5 minutes) and contains an HMAC digest of all audit entries in '
'that window, chained with the previous checkpoint for tamper-evidence. Detects row '
'insertion, deletion, and reordering between checkpoints.';

COMMENT ON COLUMN ezkey_audit_chain_checkpoint.checkpoint_id IS
'Primary key - auto-generated unique identifier';

COMMENT ON COLUMN ezkey_audit_chain_checkpoint.window_start IS
'Start of the time window (inclusive) covered by this checkpoint';

COMMENT ON COLUMN ezkey_audit_chain_checkpoint.window_end IS
'End of the time window (exclusive) covered by this checkpoint';

COMMENT ON COLUMN ezkey_audit_chain_checkpoint.entry_count IS
'Number of audit log entries in this time window (0 for empty windows)';

COMMENT ON COLUMN ezkey_audit_chain_checkpoint.first_entry_id IS
'Smallest audit_log_id in the window; NULL if entry_count = 0';

COMMENT ON COLUMN ezkey_audit_chain_checkpoint.last_entry_id IS
'Largest audit_log_id in the window; NULL if entry_count = 0';

COMMENT ON COLUMN ezkey_audit_chain_checkpoint.entries_digest IS
'HMAC-SHA256 of the ordered concatenation of entry_hmac values in this window. '
'For empty windows: HMAC("EMPTY_WINDOW")';

COMMENT ON COLUMN ezkey_audit_chain_checkpoint.prev_chain_hmac IS
'Chain HMAC from the immediately preceding checkpoint; NULL for the very first checkpoint (GENESIS)';

COMMENT ON COLUMN ezkey_audit_chain_checkpoint.chain_hmac IS
'HMAC(entries_digest + "|" + prev_chain_hmac). Links this checkpoint to the previous one, '
'forming an append-only chain. Any modification to prior checkpoints or entries breaks the chain.';

COMMENT ON COLUMN ezkey_audit_chain_checkpoint.created_at IS
'Timestamp when this checkpoint was created by the batch job';

-- ============================================================================
-- STEP 3: Add Indexes
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_chain_checkpoint_window_start
    ON ezkey_audit_chain_checkpoint(window_start);

CREATE INDEX IF NOT EXISTS idx_chain_checkpoint_created_at
    ON ezkey_audit_chain_checkpoint(created_at DESC);

-- ============================================================================
-- Migration Complete
-- ============================================================================
