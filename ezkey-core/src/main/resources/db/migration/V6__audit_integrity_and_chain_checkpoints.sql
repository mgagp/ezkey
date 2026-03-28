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
-- ============================================================
-- V37: Add optional reason/justification column to audit log
-- ============================================================
-- Purpose   : SOC 2 CC6.3 (access deprovisioning) and CC8.1 (authorized changes)
-- Design    : Optional VARCHAR(500) — no obligation to provide, validated ≥10 chars if supplied
-- HMAC      : Field is included in per-entry canonical form as position 15
--             (null reason → empty string in canonical form, consistent with nullSafe() convention)
-- Partitions: ADD COLUMN on the parent table propagates automatically to all monthly partitions
--             via PostgreSQL declarative partitioning inheritance
-- Rollback  : ALTER TABLE ezkey_audit_log DROP COLUMN reason;
-- ============================================================

ALTER TABLE ezkey_audit_log
  ADD COLUMN IF NOT EXISTS reason VARCHAR(500);

COMMENT ON COLUMN ezkey_audit_log.reason IS
  'Optional justification for sensitive operations (revoke, delete, deactivate). '
  'Supports SOC 2 CC6.3 / CC8.1. Included in per-entry HMAC canonical form.';
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
