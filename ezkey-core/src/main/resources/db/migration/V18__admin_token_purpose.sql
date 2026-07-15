-- SEC-021: Distinguish session tokens from recovery (reset-only) tokens.
ALTER TABLE ezkey_admin_tokens
    ADD COLUMN token_purpose VARCHAR(20) NOT NULL DEFAULT 'SESSION';

ALTER TABLE ezkey_admin_tokens
    ADD CONSTRAINT chk_admin_tokens_token_purpose
    CHECK (token_purpose IN ('SESSION', 'RECOVERY'));

COMMENT ON COLUMN ezkey_admin_tokens.token_purpose IS
    'Token purpose: SESSION for passwordless admin API access; RECOVERY for enrollment reset only';
