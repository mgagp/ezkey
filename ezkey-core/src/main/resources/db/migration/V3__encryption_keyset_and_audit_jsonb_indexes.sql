-- ============================================================================
-- Ezkey Migration V17: Create Encryption Key Tracking Table
-- ============================================================================
-- Description: Creates table to track encryption key lifecycle for SOC2
--              compliance. Tracks Tink keyset keys with metadata, status,
--              and usage statistics.
--
-- Context: Part of encryption key rotation strategy implementation.
--          Enables scheduled key introduction and rotation tracking.
--
-- Author: Ezkey contributors
-- Date: 2025-12-03
-- ============================================================================

-- ============================================================================
-- STEP 1: Create Encryption Key Tracking Table
-- ============================================================================

CREATE TABLE ezkey_encryption_key (
    key_id              BIGINT PRIMARY KEY,           -- Tink key ID (from keyset)
    key_status          VARCHAR(20) NOT NULL,         -- PRIMARY, ENABLED, DISABLED
    algorithm           VARCHAR(50) NOT NULL,         -- AES256_GCM, CHACHA20_POLY1305
    introduced_at       TIMESTAMPTZ NOT NULL,         -- When key was added to keyset
    promoted_primary_at TIMESTAMPTZ,                  -- When became primary (nullable)
    disabled_at         TIMESTAMPTZ,                  -- When disabled (nullable)
    records_encrypted   BIGINT DEFAULT 0,             -- Baseline ciphertext units at demotion to ENABLED
    records_reencrypted BIGINT DEFAULT 0,             -- Cumulative units re-encrypted (reset at demotion)
    last_reencrypt_at   TIMESTAMPTZ,                  -- Last re-encryption batch timestamp
    created_by          VARCHAR(100) DEFAULT 'SYSTEM',-- Job or admin who introduced key
    notes               TEXT,                         -- Optional notes for audit
    created_at          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- ============================================================================
-- STEP 2: Add Constraints
-- ============================================================================

ALTER TABLE ezkey_encryption_key
    ADD CONSTRAINT chk_encryption_key_status 
    CHECK (key_status IN ('PRIMARY', 'ENABLED', 'DISABLED'));

-- Enforce unsigned (non-negative) key ID to match ENC:keyID: format representation
-- PostgreSQL doesn't have native UNSIGNED types, so we use CHECK constraint
ALTER TABLE ezkey_encryption_key
    ADD CONSTRAINT chk_encryption_key_id_unsigned
    CHECK (key_id >= 0);

-- ============================================================================
-- STEP 3: Create Indexes
-- ============================================================================

CREATE INDEX idx_encryption_key_status ON ezkey_encryption_key(key_status);
CREATE INDEX idx_encryption_key_introduced ON ezkey_encryption_key(introduced_at);

-- ============================================================================
-- STEP 4: Add Table and Column Comments
-- ============================================================================

COMMENT ON TABLE ezkey_encryption_key IS 
'Tracks encryption key lifecycle for SOC2 audit compliance. Each row represents a key in the Tink keyset with metadata about its status, usage, and rotation history.';

COMMENT ON COLUMN ezkey_encryption_key.key_id IS 
'Tink keyset key ID (unsigned 64-bit integer, enforced via CHECK constraint). Primary key matching Tink KeysetInfo.getPrimaryKeyId(). Values must be >= 0 to match ENC:keyID: format representation.';

COMMENT ON COLUMN ezkey_encryption_key.key_status IS 
'Key status: PRIMARY (active for new encryption), ENABLED (available for decryption only), DISABLED (retired, no longer used).';

COMMENT ON COLUMN ezkey_encryption_key.algorithm IS 
'Encryption algorithm used by this key: AES256_GCM or CHACHA20_POLY1305.';

COMMENT ON COLUMN ezkey_encryption_key.introduced_at IS 
'Timestamp when this key was first added to the keyset (key rotation event).';

COMMENT ON COLUMN ezkey_encryption_key.promoted_primary_at IS 
'Timestamp when this key was promoted to PRIMARY status (nullable, only set for keys that became primary).';

COMMENT ON COLUMN ezkey_encryption_key.disabled_at IS 
'Timestamp when this key was disabled (nullable, only set when key is retired).';

COMMENT ON COLUMN ezkey_encryption_key.records_encrypted IS 
'Migration-scope baseline: sum of tracked ciphertext units (ENC:keyId: prefix rows per column) when this key became ENABLED. Zero while PRIMARY until demotion.';

COMMENT ON COLUMN ezkey_encryption_key.records_reencrypted IS 
'Cumulative ciphertext units migrated off this key via completed re-encryption batches; reset to zero at demotion. Informational vs baseline; drain uses prefix verification.';

COMMENT ON COLUMN ezkey_encryption_key.last_reencrypt_at IS 
'Timestamp of the last re-encryption batch that processed records encrypted with this key.';

COMMENT ON COLUMN ezkey_encryption_key.created_by IS 
'Identifier of who/what introduced this key: SYSTEM (scheduled job), or admin username (manual rotation).';

COMMENT ON COLUMN ezkey_encryption_key.notes IS 
'Optional notes for audit trail, e.g., reason for manual rotation, incident reference.';

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- Encryption key tracking table created successfully.
-- Ready for key rotation service implementation.
-- ============================================================================

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
    shard_count     INT,                             -- NULL = single-stream batch; N = parallel shards for ezkey_auth_attempt (INT matches JPA Integer)
    shard_index     INT,                             -- 0..shard_count-1 when sharded; NULL when not sharded
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

ALTER TABLE ezkey_reencryption_batch
    ADD CONSTRAINT chk_reencryption_batch_shard_consistency
    CHECK (
        (shard_count IS NULL AND shard_index IS NULL)
        OR (
            shard_count IS NOT NULL
            AND shard_count >= 1
            AND shard_index IS NOT NULL
            AND shard_index >= 0
            AND shard_index < shard_count
        )
    );

-- ============================================================================
-- STEP 3: Create Indexes
-- ============================================================================

CREATE INDEX idx_reencryption_batch_status ON ezkey_reencryption_batch(status);
CREATE INDEX idx_reencryption_batch_keys ON ezkey_reencryption_batch(old_key_id, new_key_id);
CREATE INDEX idx_reencryption_batch_target ON ezkey_reencryption_batch(target_table, target_column);
CREATE INDEX idx_reencryption_batch_target_old_shard
    ON ezkey_reencryption_batch(target_table, target_column, old_key_id, shard_index);
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

COMMENT ON COLUMN ezkey_reencryption_batch.shard_count IS 
'When set (>=2), parallel shard count for ezkey_auth_attempt; mod(auth_attempt_id, shard_count)=shard_index. NULL for non-sharded batches.';

COMMENT ON COLUMN ezkey_reencryption_batch.shard_index IS 
'Shard index 0..shard_count-1 when sharded; NULL when shard_count is NULL.';

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

-- ============================================================================
-- Ezkey Migration V19: Add JSONB Indexes for Encryption Audit Queries
-- ============================================================================
-- Description: Creates JSONB GIN indexes on audit log event_details for
--              efficient queries on encryption key operations and re-encryption
--              batches. Enables fast lookups without requiring foreign keys.
--
-- Context: Part of encryption key rotation strategy implementation.
--          Supports SOC2 compliance reporting and audit trail queries.
--
-- Author: Ezkey contributors
-- Date: 2025-12-03
-- ============================================================================

-- ============================================================================
-- STEP 1: Create JSONB Index for Encryption Key Audits
-- ============================================================================

-- Index for querying audits by encryption_key_id
-- Enables queries like: WHERE event_details::jsonb->>'encryption_key_id' = '1234567890'
CREATE INDEX idx_audit_encryption_key_id ON ezkey_audit_log 
USING GIN ((event_details::jsonb))
WHERE event_type IN ('KEY_INTRODUCED', 'KEY_PROMOTED_PRIMARY', 'KEY_DEMOTED', 'KEY_DISABLED');

-- ============================================================================
-- STEP 2: Create JSONB Index for Re-encryption Batch Audits
-- ============================================================================

-- Index for querying audits by reencryption_batch_id
-- Enables queries like: WHERE event_details::jsonb->>'reencryption_batch_id' = '42'
CREATE INDEX idx_audit_reencryption_batch_id ON ezkey_audit_log 
USING GIN ((event_details::jsonb))
WHERE event_type IN ('REENCRYPTION_STARTED', 'REENCRYPTION_COMPLETED', 'REENCRYPTION_FAILED', 
                     'REENCRYPTION_PAUSED', 'REENCRYPTION_RESUMED', 'REENCRYPTION_BATCH_PROGRESS');

-- ============================================================================
-- STEP 3: Add Index Comments
-- ============================================================================

COMMENT ON INDEX idx_audit_encryption_key_id IS 
'JSONB GIN index for efficient queries on encryption key audit events. Enables fast lookups of all audit entries related to a specific encryption key ID without foreign key constraints.';

COMMENT ON INDEX idx_audit_reencryption_batch_id IS 
'JSONB GIN index for efficient queries on re-encryption batch audit events. Enables fast lookups of all audit entries related to a specific re-encryption batch without foreign key constraints.';

-- ============================================================================
-- STEP 4: Example Queries Enabled by These Indexes
-- ============================================================================

-- Example 1: Find all audits for a specific encryption key
-- SELECT * FROM ezkey_audit_log 
-- WHERE event_details::jsonb->>'encryption_key_id' = '1234567890'
-- ORDER BY created_at DESC;

-- Example 2: Find all audits for a specific re-encryption batch
-- SELECT * FROM ezkey_audit_log 
-- WHERE event_details::jsonb->>'reencryption_batch_id' = '42'
-- ORDER BY created_at;

-- Example 3: Count failed re-encryption attempts per batch
-- SELECT 
--   event_details::jsonb->>'reencryption_batch_id' as batch_id,
--   COUNT(*) as failure_count
-- FROM ezkey_audit_log 
-- WHERE event_type = 'REENCRYPTION_FAILED'
-- GROUP BY event_details::jsonb->>'reencryption_batch_id';

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- JSONB indexes created successfully.
-- Audit queries on encryption operations are now optimized.
-- ============================================================================

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

-- ============================================================================
-- Ezkey Migration V22: Create Keyset Blob Table
-- ============================================================================
-- Description: Creates table to store encrypted keyset as database blob.
--              Provides centralized keyset storage for distributed deployments
--              where file-based sharing is not available (cloud, Kubernetes).
--
-- Context: Part of distributed key rotation synchronization strategy.
--          Database serves as source of truth for keyset across all instances.
--          File-based keyset remains as local cache/fallback.
--
-- Author: Ezkey contributors
-- Date: 2025-12-03
-- ============================================================================

-- ============================================================================
-- STEP 1: Create Keyset Blob Table
-- ============================================================================
-- Single-row table to store encrypted keyset JSON blob

CREATE TABLE ezkey_keyset_blob (
    id              INTEGER PRIMARY KEY DEFAULT 1,
    keyset_data     BYTEA NOT NULL,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(100) DEFAULT 'SYSTEM',
    version         BIGINT DEFAULT 1,
    CONSTRAINT single_row CHECK (id = 1)
);

-- ============================================================================
-- STEP 2: Add Table and Column Comments
-- ============================================================================

COMMENT ON TABLE ezkey_keyset_blob IS 
'Stores Tink encrypted-keyset JSON envelope as database blob for distributed synchronization. Single-row table (id=1 enforced by constraint). Key material is encrypted with the master key.';

COMMENT ON COLUMN ezkey_keyset_blob.id IS 
'Primary key, always 1 (single-row table enforced by constraint).';

COMMENT ON COLUMN ezkey_keyset_blob.keyset_data IS 
'Tink encrypted-keyset JSON envelope. Key material is encrypted with the master key; non-secret keyset metadata may remain visible.';

COMMENT ON COLUMN ezkey_keyset_blob.last_updated_at IS 
'Timestamp of last keyset update. Used for change detection and synchronization.';

COMMENT ON COLUMN ezkey_keyset_blob.updated_by IS 
'Identifier of who/what updated the keyset: SYSTEM (scheduled rotation), or admin username (manual rotation).';

COMMENT ON COLUMN ezkey_keyset_blob.version IS 
'Optimistic locking version for concurrent update protection.';

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- Keyset blob table created successfully.
-- Ready for database-backed keyset storage.
-- ============================================================================

