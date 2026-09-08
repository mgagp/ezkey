-- V20: Add waiter secret hash and session issued timestamp to ezkey_auth_attempt
-- Supports one-time waiter capability token and CAS replay protection for Admin API passwordless login.

ALTER TABLE ezkey_auth_attempt
    ADD COLUMN waiter_secret_hash VARCHAR(64),
    ADD COLUMN session_issued_at TIMESTAMPTZ;

COMMENT ON COLUMN ezkey_auth_attempt.waiter_secret_hash IS 'SHA-256 hex digest of the client waiter secret capability minted at login. NULL for Integration API attempts.';
COMMENT ON COLUMN ezkey_auth_attempt.session_issued_at IS 'Timestamp when an admin session token was first minted for this attempt. Used for atomic CAS consume to prevent replay/hijack.';
