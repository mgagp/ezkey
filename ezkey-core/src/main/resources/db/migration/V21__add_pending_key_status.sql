-- ============================================================================
-- Ezkey Migration V21: Add PENDING Key Status and Effective Date
-- ============================================================================
-- Description: Adds PENDING status for keys waiting synchronization window
--              and effective_at timestamp for scheduled promotion to PRIMARY.
--
-- Context: Part of distributed key rotation synchronization strategy.
--          PENDING keys become PRIMARY after effective_at timestamp passes,
--          allowing all instances to synchronize before key activation.
--
-- Author: Ezkey contributors
-- Date: 2025-12-03
-- ============================================================================

-- ============================================================================
-- STEP 1: Add effective_at Column
-- ============================================================================
-- Timestamp when PENDING key becomes PRIMARY (null for non-PENDING keys)

ALTER TABLE ezkey_encryption_key 
    ADD COLUMN effective_at TIMESTAMPTZ;

COMMENT ON COLUMN ezkey_encryption_key.effective_at IS 
'Timestamp when a PENDING key is scheduled to become PRIMARY. Used for distributed synchronization: all instances wait until this time before the key is promoted. Null for non-PENDING keys.';

-- ============================================================================
-- STEP 2: Update Key Status Constraint to Include PENDING
-- ============================================================================

ALTER TABLE ezkey_encryption_key
    DROP CONSTRAINT IF EXISTS chk_encryption_key_status;

ALTER TABLE ezkey_encryption_key
    ADD CONSTRAINT chk_encryption_key_status 
    CHECK (key_status IN ('PRIMARY', 'ENABLED', 'DISABLED', 'PENDING'));

-- ============================================================================
-- STEP 3: Create Index for Efficient Pending Key Queries
-- ============================================================================
-- Used by scheduled job to find keys ready for promotion

CREATE INDEX idx_encryption_key_status_effective 
    ON ezkey_encryption_key(key_status, effective_at)
    WHERE key_status = 'PENDING';

COMMENT ON INDEX idx_encryption_key_status_effective IS 
'Partial index for efficient querying of PENDING keys ready for promotion. Used by scheduled promotion job.';

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- PENDING status and effective_at column added successfully.
-- Ready for distributed key rotation synchronization.
-- ============================================================================

