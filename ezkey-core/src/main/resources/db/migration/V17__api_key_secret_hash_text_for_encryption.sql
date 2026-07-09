-- SEC-010: Widen secret_key_hash for Tink ENC: payloads (BCrypt fits in VARCHAR(255); encrypted values may not).
ALTER TABLE ezkey_api_key
    ALTER COLUMN secret_key_hash TYPE TEXT;

COMMENT ON COLUMN ezkey_api_key.secret_key_hash IS
    'BCrypt hash of ezkey_skey_xxx, encrypted at rest with Tink (ENC: prefix) when encryption is enabled';
