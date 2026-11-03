-- ============================================================================
-- Ezkey Migration V26: Dedicated ACTIVATION purpose for activation codes (#1122)
-- ============================================================================
-- Description: Deferred-onboarding activation codes (ezkey_activation_...) were
--              stored with the default SESSION purpose. A pending administrator
--              has no enrollment, so the bearer filter accepted the invite code as
--              a full Admin API session (no /activate, no device bind, no Mode C).
--              Activation codes now persist as ACTIVATION, which session
--              validation never accepts.
--
-- Existing rows: only the hash is stored, so rows are classified by owner.
--              Passwordless login refuses PENDING_ACTIVATION and requires an
--              enrollment, so an active SESSION row owned by a PENDING_ACTIVATION
--              admin without enrollment can only be an activation code.
--              * Never presented as a bearer (last_used_at IS NULL): converted to
--                ACTIVATION so outstanding invites keep working through /activate.
--              * Already presented as a bearer (last_used_at IS NOT NULL; /activate
--                deactivates on consume, so only bearer use sets it while active):
--                revoked, fail closed. The operator re-issues a code if needed.
--
-- Author: Ezkey contributors
-- Date:   2026-11
-- ============================================================================

ALTER TABLE ezkey_admin_tokens
    DROP CONSTRAINT IF EXISTS chk_admin_tokens_token_purpose;

ALTER TABLE ezkey_admin_tokens
    ADD CONSTRAINT chk_admin_tokens_token_purpose
    CHECK (token_purpose IN ('SESSION', 'RECOVERY', 'EVALUATOR_TEMP', 'ACTIVATION'));

UPDATE ezkey_admin_tokens t
SET active = FALSE
FROM ezkey_admin a
WHERE t.admin_id = a.admin_id
  AND t.active = TRUE
  AND t.token_purpose = 'SESSION'
  AND a.lifecycle_status = 'PENDING_ACTIVATION'
  AND a.enrollment_id IS NULL
  AND t.last_used_at IS NOT NULL;

UPDATE ezkey_admin_tokens t
SET token_purpose = 'ACTIVATION'
FROM ezkey_admin a
WHERE t.admin_id = a.admin_id
  AND t.active = TRUE
  AND t.token_purpose = 'SESSION'
  AND a.lifecycle_status = 'PENDING_ACTIVATION'
  AND a.enrollment_id IS NULL;

COMMENT ON COLUMN ezkey_admin_tokens.token_purpose IS
    'Token purpose: SESSION (passwordless Admin API); RECOVERY (enrollment reset only); '
    'EVALUATOR_TEMP (one-shot temporary evaluator console, never promoted to SESSION); '
    'ACTIVATION (deferred-onboarding activation code, consumed by /activate only, never a session)';
