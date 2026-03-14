-- ============================================================================
-- Replace bearer_token with bearer_token_hash (SHA-256 hex) for defense in depth.
-- Admin and recovery tokens are looked up by hash; plain token is never stored.
-- ============================================================================

-- Add new column (nullable initially to allow backfill if table has rows)
ALTER TABLE ezkey_admin_tokens ADD COLUMN bearer_token_hash VARCHAR(64);

-- Backfill: hash existing bearer_token so migration works on dev DBs with data
UPDATE ezkey_admin_tokens
SET bearer_token_hash = encode(sha256(bearer_token::bytea), 'hex')
WHERE bearer_token_hash IS NULL;

-- Enforce NOT NULL and UNIQUE
ALTER TABLE ezkey_admin_tokens ALTER COLUMN bearer_token_hash SET NOT NULL;
ALTER TABLE ezkey_admin_tokens ADD CONSTRAINT uk_admin_tokens_bearer_token_hash UNIQUE (bearer_token_hash);

-- Drop index that uses bearer_token before dropping the column
DROP INDEX IF EXISTS idx_admin_tokens_active;

-- Remove plaintext token column
ALTER TABLE ezkey_admin_tokens DROP COLUMN bearer_token;

-- Recreate index for active token lookup by hash
CREATE INDEX idx_admin_tokens_active ON ezkey_admin_tokens(bearer_token_hash, active) WHERE active = TRUE;

-- Column comment
COMMENT ON COLUMN ezkey_admin_tokens.bearer_token_hash IS
'SHA-256 hash (hex) of the bearer token. Used for lookup; plain token is never stored. Protects against DB breach.';
