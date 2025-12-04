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
'Stores encrypted Tink keyset as database blob for distributed synchronization. Single-row table (id=1 enforced by constraint). Keyset is encrypted with master key before storage.';

COMMENT ON COLUMN ezkey_keyset_blob.id IS 
'Primary key, always 1 (single-row table enforced by constraint).';

COMMENT ON COLUMN ezkey_keyset_blob.keyset_data IS 
'Encrypted Tink keyset JSON blob. Encrypted with master key using AES-256-GCM before storage.';

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

