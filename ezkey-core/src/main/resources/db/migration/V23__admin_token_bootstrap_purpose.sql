-- V-2026-09-26: Admin UI bootstrap sessions for evaluator self-registration (community/alpha).
ALTER TABLE ezkey_admin_tokens
    DROP CONSTRAINT chk_admin_tokens_token_purpose;

ALTER TABLE ezkey_admin_tokens
    ADD CONSTRAINT chk_admin_tokens_token_purpose
    CHECK (token_purpose IN ('SESSION', 'RECOVERY', 'BOOTSTRAP'));

COMMENT ON COLUMN ezkey_admin_tokens.token_purpose IS
    'Token purpose: SESSION for passwordless admin API access; RECOVERY for enrollment reset only; BOOTSTRAP for evaluator pending-activation console foothold (narrow allowlist, absolute TTL)';
