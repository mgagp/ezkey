-- ============================================================================
-- Ezkey Migration V23: Widen admin token purpose for EVALUATOR_TEMP
-- ============================================================================
-- Description: Temporary evaluator console sessions persist AdminTokenPurpose
--              EVALUATOR_TEMP. V18 check constraint only allowed SESSION|RECOVERY,
--              causing DataIntegrityViolationException on mint.
--
-- Author: Ezkey contributors
-- Date:   2026-09
-- ============================================================================

ALTER TABLE ezkey_admin_tokens
    DROP CONSTRAINT IF EXISTS chk_admin_tokens_token_purpose;

ALTER TABLE ezkey_admin_tokens
    ADD CONSTRAINT chk_admin_tokens_token_purpose
    CHECK (token_purpose IN ('SESSION', 'RECOVERY', 'EVALUATOR_TEMP'));

COMMENT ON COLUMN ezkey_admin_tokens.token_purpose IS
    'Token purpose: SESSION (passwordless Admin API); RECOVERY (enrollment reset only); '
    'EVALUATOR_TEMP (one-shot temporary evaluator console, never promoted to SESSION)';
