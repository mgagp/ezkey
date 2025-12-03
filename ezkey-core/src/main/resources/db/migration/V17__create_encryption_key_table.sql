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
    records_encrypted   BIGINT DEFAULT 0,             -- Count of records encrypted with this key
    records_reencrypted BIGINT DEFAULT 0,             -- Count re-encrypted to newer key
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
'Total count of records encrypted with this key. Incremented when new data is encrypted.';

COMMENT ON COLUMN ezkey_encryption_key.records_reencrypted IS 
'Count of records that were re-encrypted from this key to a newer key. Used to track migration progress.';

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

