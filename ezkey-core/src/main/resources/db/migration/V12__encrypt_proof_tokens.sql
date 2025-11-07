-- Ezkey Migration V12: Encrypt proof tokens and add hash columns for deterministic lookups
-- This migration prepares the database for Tink-encrypted proof tokens by introducing auxiliary
-- SHA-256 hash columns. Hashes enable secure equality checks while the encrypted values are stored
-- with the ENC: prefix. Uniqueness constraints are applied on the hashes to preserve the
-- one-time-token guarantees without exposing the original secrets.

ALTER TABLE ezkey_enrollment
    ADD COLUMN enrollment_proof_token_hash VARCHAR(128);

ALTER TABLE ezkey_enrollment
    ADD CONSTRAINT uq_enrollment_proof_token_hash UNIQUE (enrollment_proof_token_hash);

ALTER TABLE ezkey_auth_attempt
    ADD COLUMN auth_attempt_proof_token_hash VARCHAR(128);

ALTER TABLE ezkey_auth_attempt
    ADD COLUMN device_proof_token_hash VARCHAR(128);

ALTER TABLE ezkey_auth_attempt
    ADD CONSTRAINT uq_auth_attempt_proof_token_hash UNIQUE (auth_attempt_proof_token_hash);

ALTER TABLE ezkey_auth_attempt
    ADD CONSTRAINT uq_auth_attempt_device_proof_token_hash UNIQUE (device_proof_token_hash);

