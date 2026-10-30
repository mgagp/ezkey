-- Minimal schema for keyset blob CAS + KeyRotationService transactional ITs (#961 R1/R2).
CREATE TABLE IF NOT EXISTS ezkey_keyset_blob (
    id              INTEGER PRIMARY KEY DEFAULT 1,
    keyset_data     BYTEA NOT NULL,
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      VARCHAR(100) DEFAULT 'SYSTEM',
    version         BIGINT DEFAULT 1,
    CONSTRAINT single_row CHECK (id = 1)
);

CREATE TABLE IF NOT EXISTS ezkey_encryption_key (
    key_id              BIGINT PRIMARY KEY,
    key_status          VARCHAR(20) NOT NULL,
    algorithm           VARCHAR(50) NOT NULL,
    introduced_at       TIMESTAMPTZ NOT NULL,
    promoted_primary_at TIMESTAMPTZ,
    effective_at        TIMESTAMPTZ,
    disabled_at         TIMESTAMPTZ,
    records_encrypted   BIGINT DEFAULT 0 NOT NULL,
    records_reencrypted BIGINT DEFAULT 0 NOT NULL,
    last_reencrypt_at   TIMESTAMPTZ,
    created_by          VARCHAR(100) DEFAULT 'SYSTEM',
    notes               TEXT,
    created_at          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_encryption_key_status
        CHECK (key_status IN ('PRIMARY', 'ENABLED', 'DISABLED', 'PENDING')),
    CONSTRAINT chk_encryption_key_id_unsigned CHECK (key_id >= 0)
);
