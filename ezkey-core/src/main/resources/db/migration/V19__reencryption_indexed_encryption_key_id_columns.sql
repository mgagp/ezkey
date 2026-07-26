-- I-2026-0029 / TB-2026-07-26: indexed encryption key id columns for re-encryption discovery.
--
-- Replaces LIKE 'ENC:{keyId}:%' prefix scans on ciphertext TEXT columns with equality lookups on
-- dedicated, indexed BIGINT columns (one per encrypted field), each referencing
-- ezkey_encryption_key(key_id). Columns are nullable: NULL means the value is currently stored as
-- plaintext (encryption disabled / not required) and is excluded from key-based discovery, which
-- matches today's LIKE behavior on non-ENC: values. Populated by EncryptionEntityListener on
-- initial encrypt and by ReencryptionRecordCipher on re-encrypt.

ALTER TABLE ezkey_enrollment
    ADD COLUMN integration_private_key_encryption_key_id BIGINT REFERENCES ezkey_encryption_key(key_id),
    ADD COLUMN enrollment_proof_token_encryption_key_id BIGINT REFERENCES ezkey_encryption_key(key_id);

CREATE INDEX idx_enrollment_integration_private_key_key_id
    ON ezkey_enrollment (integration_private_key_encryption_key_id, enrollment_id);

CREATE INDEX idx_enrollment_enrollment_proof_token_key_id
    ON ezkey_enrollment (enrollment_proof_token_encryption_key_id, enrollment_id);

COMMENT ON COLUMN ezkey_enrollment.integration_private_key_encryption_key_id IS
'Tink key id used to encrypt integration_private_key, parsed from its ENC:{keyId}: prefix. NULL when the value is stored as plaintext (encryption disabled). Enables indexed re-encryption discovery (I-2026-0029), replacing LIKE ''ENC:{keyId}:%'' scans.';

COMMENT ON COLUMN ezkey_enrollment.enrollment_proof_token_encryption_key_id IS
'Tink key id used to encrypt enrollment_proof_token, parsed from its ENC:{keyId}: prefix. NULL when the value is stored as plaintext (encryption disabled). Enables indexed re-encryption discovery (I-2026-0029), replacing LIKE ''ENC:{keyId}:%'' scans.';

ALTER TABLE ezkey_auth_attempt
    ADD COLUMN auth_attempt_proof_token_encryption_key_id BIGINT REFERENCES ezkey_encryption_key(key_id);

-- Partitioned table: CREATE INDEX on the parent cascades to a matching local index on every
-- existing (and future) partition, same pattern as idx_auth_attempt_enrollment_created (V4).
CREATE INDEX idx_auth_attempt_proof_token_key_id
    ON ezkey_auth_attempt (auth_attempt_proof_token_encryption_key_id, auth_attempt_id);

COMMENT ON COLUMN ezkey_auth_attempt.auth_attempt_proof_token_encryption_key_id IS
'Tink key id used to encrypt auth_attempt_proof_token, parsed from its ENC:{keyId}: prefix. NULL when the value is stored as plaintext (encryption disabled). Enables indexed re-encryption discovery (I-2026-0029), replacing LIKE ''ENC:{keyId}:%'' scans.';

ALTER TABLE ezkey_api_key
    ADD COLUMN secret_key_hash_encryption_key_id BIGINT REFERENCES ezkey_encryption_key(key_id);

CREATE INDEX idx_api_key_secret_key_hash_key_id
    ON ezkey_api_key (secret_key_hash_encryption_key_id, api_key_id);

COMMENT ON COLUMN ezkey_api_key.secret_key_hash_encryption_key_id IS
'Tink key id used to encrypt secret_key_hash, parsed from its ENC:{keyId}: prefix. NULL when the value is stored as plaintext (encryption disabled). Enables indexed re-encryption discovery (I-2026-0029), replacing LIKE ''ENC:{keyId}:%'' scans.';
