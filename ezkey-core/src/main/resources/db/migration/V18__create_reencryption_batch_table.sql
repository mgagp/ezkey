-- ============================================================================
-- Ezkey Migration V18: Create Re-encryption Batch Tracking Table
-- ============================================================================
-- Description: Creates table to track batch re-encryption progress for
--              resumability, monitoring, and SOC2 audit compliance.
--
-- Context: Part of encryption key rotation strategy implementation.
--          Enables fault-tolerant batch processing with progress tracking.
--
-- Author: Ezkey contributors
-- Date: 2025-12-03
-- ============================================================================

-- ============================================================================
-- STEP 1: Create Re-encryption Batch Tracking Table
-- ============================================================================

CREATE TABLE ezkey_reencryption_batch (
    batch_id        SERIAL PRIMARY KEY,
    target_table    VARCHAR(100) NOT NULL,            -- Table being re-encrypted
    target_column   VARCHAR(100) NOT NULL,            -- Column being re-encrypted
    old_key_id      BIGINT NOT NULL REFERENCES ezkey_encryption_key(key_id),
    new_key_id      BIGINT NOT NULL REFERENCES ezkey_encryption_key(key_id),
    records_total   INT NOT NULL,                     -- Total records to process
    records_done    INT DEFAULT 0,                    -- Successfully re-encrypted
    records_failed  INT DEFAULT 0,                    -- Failed re-encryption
    records_skipped INT DEFAULT 0,                    -- Already with new key
    status          VARCHAR(20) NOT NULL,             -- PENDING, IN_PROGRESS, COMPLETED, FAILED, PAUSED
    progress_pct    DECIMAL(5,2) DEFAULT 0.00,        -- Progress percentage (0.00-100.00)
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    last_batch_at   TIMESTAMPTZ,                      -- Last processed batch timestamp
    last_record_id  BIGINT,                           -- Resume point (last processed ID)
    error_message   TEXT,
    error_count     INT DEFAULT 0,                    -- Count of errors in this batch
    retry_count     INT DEFAULT 0,
    max_retries     INT DEFAULT 3,
    created_by      VARCHAR(100) DEFAULT 'SYSTEM',
    created_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- ============================================================================
-- STEP 2: Add Constraints
-- ============================================================================

ALTER TABLE ezkey_reencryption_batch
    ADD CONSTRAINT chk_reencryption_batch_status 
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'PAUSED'));

ALTER TABLE ezkey_reencryption_batch
    ADD CONSTRAINT chk_reencryption_batch_progress 
    CHECK (progress_pct >= 0.00 AND progress_pct <= 100.00);

ALTER TABLE ezkey_reencryption_batch
    ADD CONSTRAINT chk_reencryption_batch_records 
    CHECK (records_done + records_failed + records_skipped <= records_total);

-- Enforce unsigned (non-negative) key IDs to match ENC:keyID: format representation
-- PostgreSQL doesn't have native UNSIGNED types, so we use CHECK constraints
ALTER TABLE ezkey_reencryption_batch
    ADD CONSTRAINT chk_reencryption_batch_old_key_id_unsigned
    CHECK (old_key_id >= 0);

ALTER TABLE ezkey_reencryption_batch
    ADD CONSTRAINT chk_reencryption_batch_new_key_id_unsigned
    CHECK (new_key_id >= 0);

-- ============================================================================
-- STEP 3: Create Indexes
-- ============================================================================

CREATE INDEX idx_reencryption_batch_status ON ezkey_reencryption_batch(status);
CREATE INDEX idx_reencryption_batch_keys ON ezkey_reencryption_batch(old_key_id, new_key_id);
CREATE INDEX idx_reencryption_batch_target ON ezkey_reencryption_batch(target_table, target_column);
CREATE INDEX idx_reencryption_batch_progress ON ezkey_reencryption_batch(status, progress_pct);

-- ============================================================================
-- STEP 4: Add Table and Column Comments
-- ============================================================================

COMMENT ON TABLE ezkey_reencryption_batch IS 
'Tracks batch re-encryption progress for resumability and audit (REQUIRED for production). Each row represents a re-encryption operation for a specific table/column combination, enabling fault-tolerant batch processing.';

COMMENT ON COLUMN ezkey_reencryption_batch.batch_id IS 
'Primary key - unique identifier for this re-encryption batch operation.';

COMMENT ON COLUMN ezkey_reencryption_batch.target_table IS 
'Database table name containing encrypted data to be re-encrypted (e.g., ezkey_enrollment).';

COMMENT ON COLUMN ezkey_reencryption_batch.target_column IS 
'Column name within target_table containing encrypted data (e.g., integration_private_key).';

COMMENT ON COLUMN ezkey_reencryption_batch.old_key_id IS 
'Foreign key to encryption key currently used in encrypted data (source key for re-encryption). Unsigned 64-bit integer (>= 0).';

COMMENT ON COLUMN ezkey_reencryption_batch.new_key_id IS 
'Foreign key to encryption key that will be used after re-encryption (target key). Unsigned 64-bit integer (>= 0).';

COMMENT ON COLUMN ezkey_reencryption_batch.records_total IS 
'Total number of records that need to be re-encrypted (counted at batch creation).';

COMMENT ON COLUMN ezkey_reencryption_batch.records_done IS 
'Number of records successfully re-encrypted. Incremented as batch progresses.';

COMMENT ON COLUMN ezkey_reencryption_batch.records_failed IS 
'Number of records that failed re-encryption (e.g., decryption error, invalid format).';

COMMENT ON COLUMN ezkey_reencryption_batch.records_skipped IS 
'Number of records already encrypted with target key (optimization: skip if already correct).';

COMMENT ON COLUMN ezkey_reencryption_batch.status IS 
'Batch status: PENDING (created, not started), IN_PROGRESS (actively processing), COMPLETED (finished successfully), FAILED (error occurred), PAUSED (manually paused by admin).';

COMMENT ON COLUMN ezkey_reencryption_batch.progress_pct IS 
'Progress percentage (0.00-100.00) calculated as (records_done / records_total) * 100.';

COMMENT ON COLUMN ezkey_reencryption_batch.started_at IS 
'Timestamp when batch processing started (set when status changes to IN_PROGRESS).';

COMMENT ON COLUMN ezkey_reencryption_batch.completed_at IS 
'Timestamp when batch processing completed (set when status changes to COMPLETED or FAILED).';

COMMENT ON COLUMN ezkey_reencryption_batch.last_batch_at IS 
'Timestamp of the last batch of records processed. Updated periodically during processing for monitoring.';

COMMENT ON COLUMN ezkey_reencryption_batch.last_record_id IS 
'Resume point: last successfully processed record ID. Enables fault tolerance - if service crashes, resume from this ID.';

COMMENT ON COLUMN ezkey_reencryption_batch.error_message IS 
'Error message if batch failed. Contains summary of failure reason for troubleshooting.';

COMMENT ON COLUMN ezkey_reencryption_batch.error_count IS 
'Count of individual record errors encountered during processing. May be less than records_failed if errors were retried.';

COMMENT ON COLUMN ezkey_reencryption_batch.retry_count IS 
'Number of times this batch has been retried after failure. Incremented on each retry attempt.';

COMMENT ON COLUMN ezkey_reencryption_batch.max_retries IS 
'Maximum number of retry attempts allowed before marking batch as FAILED. Default: 3.';

COMMENT ON COLUMN ezkey_reencryption_batch.created_by IS 
'Identifier of who/what created this batch: SYSTEM (scheduled job), or admin username (manual trigger).';

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- Re-encryption batch tracking table created successfully.
-- Ready for re-encryption service implementation.
-- ============================================================================

