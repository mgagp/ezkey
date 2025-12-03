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

