-- Patrick craft (Alex lock B): opaque onboarding-resume secrets — not bearer sessions.
ALTER TABLE ezkey_admin_tokens
    DROP CONSTRAINT chk_admin_tokens_token_purpose;

ALTER TABLE ezkey_admin_tokens
    ADD CONSTRAINT chk_admin_tokens_token_purpose
    CHECK (token_purpose IN ('SESSION', 'RECOVERY', 'BOOTSTRAP', 'ONBOARDING_RESUME'));

ALTER TABLE ezkey_admin_tokens
    ADD COLUMN token_use_count INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN ezkey_admin_tokens.token_purpose IS
    'Token purpose: SESSION for passwordless admin API access; RECOVERY for enrollment reset only; BOOTSTRAP for evaluator console foothold (narrow allowlist, absolute TTL); ONBOARDING_RESUME for capability redeem to remint BOOTSTRAP (not a bearer session)';

COMMENT ON COLUMN ezkey_admin_tokens.token_use_count IS
    'Successful redeem count for limited-use purposes (ONBOARDING_RESUME max 3). Unused for SESSION/RECOVERY/BOOTSTRAP.';
