-- ============================================================================
-- Ezkey Migration V24: One EVALUATOR_TEMP token row per admin (one-shot)
-- ============================================================================
-- Description: Temporary evaluator console mint checked existence then inserted
--              without a DB uniqueness guarantee. Concurrent mints could both
--              insert active EVALUATOR_TEMP rows. This partial unique index
--              enforces at most one EVALUATOR_TEMP row per admin_id for the
--              lifetime of that purpose (including after deactivate / active=false).
--              SESSION and RECOVERY stay many-per-admin (not in the index predicate).
--
-- Precedents: uq_alert_dedupe_key_open (V11), uq_integrity_async_job_one_running (V22).
--
-- Author: Ezkey contributors
-- Date:   2026-09
-- ============================================================================

-- Deduplicate any raced EVALUATOR_TEMP rows before the unique index (keep lowest token_id).
DELETE FROM ezkey_admin_tokens a
    USING ezkey_admin_tokens b
WHERE a.token_purpose = 'EVALUATOR_TEMP'
  AND b.token_purpose = 'EVALUATOR_TEMP'
  AND a.admin_id = b.admin_id
  AND a.token_id > b.token_id;

-- One-shot invariant: at most one EVALUATOR_TEMP row per admin_id.
-- Intentionally NOT filtered on active — deactivated TEMP still blocks re-mint.
CREATE UNIQUE INDEX IF NOT EXISTS uq_admin_tokens_one_evaluator_temp_per_admin
    ON ezkey_admin_tokens (admin_id)
    WHERE token_purpose = 'EVALUATOR_TEMP';

COMMENT ON INDEX uq_admin_tokens_one_evaluator_temp_per_admin IS
    'One-shot temporary evaluator console: at most one EVALUATOR_TEMP row per admin_id '
    '(active or inactive). SESSION/RECOVERY are unrestricted by this index.';
