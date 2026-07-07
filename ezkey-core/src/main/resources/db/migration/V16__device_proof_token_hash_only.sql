-- Device proof token: hash-only storage (ADR-0007, GitHub #296).
-- Replay prevention uses device_proof_token_hash; recoverable ciphertext is not required.

ALTER TABLE ezkey_auth_attempt DROP COLUMN IF EXISTS device_proof_token;

COMMENT ON COLUMN ezkey_auth_attempt.device_proof_token_hash IS
'SHA-256 hash of the client-generated device proof token at pending claim. Hash-only storage — plaintext is never persisted (ADR-0007). Unique constraint ensures token uniqueness.';
