-- ============================================================================
-- Ezkey Migration V31: Retire evaluator self-registration and the temporary
--                      evaluator console (#1365, #1368)
-- ============================================================================
-- Description: Ezkey keeps one onboarding path: a Global Admin creates the
--              tenant and the Tenant Admin and hands over an activation code.
--              Anonymous self-registration and the temporary console session
--              (EVALUATOR_TEMP) are removed from the code. This migration
--              removes what they left in the schema:
--   1. EVALUATOR_TEMP rows in ezkey_admin_tokens are deleted. The Java enum
--      no longer has that constant, so such a row could not be read; these
--      sessions authenticate nothing any more.
--   2. chk_admin_tokens_token_purpose is restored to the purposes that
--      remain: SESSION, RECOVERY, ACTIVATION.
--   3. The partial unique index uq_admin_tokens_one_evaluator_temp_per_admin
--      (V24) is dropped.
--   4. ezkey_tenant.self_registered (V28) is dropped.
--   5. The ShedLock row of the retired expiry job (EVALUATOR_TEMP_SESSION_EXPIRY) is deleted.
--
-- Strict, no silent loss of a guard: self_registered is what stopped a
--              tenant created by anonymous signup from sending email through a
--              delivery connector (no proof that it owns any address). If an
--              ACTIVE tenant still carries the marker, the migration stops
--              with an error and changes nothing. Decide each such tenant
--              first (community instance: #1369, part 2), then run again:
--                * retire it: deactivate the tenant in the Admin UI, or
--                * keep it, once a known person holds a permanent Tenant Admin:
--                  UPDATE ezkey_tenant SET self_registered = FALSE
--                  WHERE tenant_id = <id>;
--              Check: SELECT tenant_id, tenant_name, created_at FROM ezkey_tenant
--                     WHERE self_registered = TRUE AND active = TRUE;
--              A deactivated tenant does not block: reactivating it later is a
--              deliberate Global Admin action.
--
-- Identities left behind: an administrator who only ever used a temporary
--              session stays as is (lifecycle ACTIVE, enrollment not yet
--              VERIFIED). That is the state of any administrator who activated
--              and has not finished binding a device; finishing the bind and
--              signing in is the normal path. Nothing is deactivated here.
--
-- Audit history: rows with event_type EVALUATOR_SELF_REGISTRATION stay as
--              written and stay verifiable (the Java constant is kept, retired).
--
-- Manual rollback (no Flyway undo): the deleted EVALUATOR_TEMP rows are not
--              restored (they were dead sessions). Re-add the column with
--              ALTER TABLE ezkey_tenant ADD COLUMN self_registered BOOLEAN
--              DEFAULT FALSE NOT NULL; re-run the V28 UPDATE to mark tenants;
--              restore the CHECK with 'EVALUATOR_TEMP'; recreate the V24
--              index; delete version 31 from flyway_schema_history. The
--              previous release is required to use any of it.
--
-- Author: Ezkey contributors
-- Date: 2026-11-18
-- ============================================================================

DO $$
DECLARE
    marked_active INT;
BEGIN
    SELECT COUNT(*) INTO marked_active
    FROM ezkey_tenant
    WHERE self_registered = TRUE AND active = TRUE;

    IF marked_active > 0 THEN
        RAISE EXCEPTION
            'V31: % active tenant(s) still marked self_registered. Deactivate each one, or clear '
            'the marker once a known person holds a permanent Tenant Admin, then run again. '
            'See the V31 header.', marked_active;
    END IF;
END $$;

DELETE FROM ezkey_admin_tokens
WHERE token_purpose = 'EVALUATOR_TEMP';

DROP INDEX IF EXISTS uq_admin_tokens_one_evaluator_temp_per_admin;

ALTER TABLE ezkey_admin_tokens
    DROP CONSTRAINT IF EXISTS chk_admin_tokens_token_purpose;

ALTER TABLE ezkey_admin_tokens
    ADD CONSTRAINT chk_admin_tokens_token_purpose
    CHECK (token_purpose IN ('SESSION', 'RECOVERY', 'ACTIVATION'));

COMMENT ON COLUMN ezkey_admin_tokens.token_purpose IS
    'Token purpose: SESSION (passwordless Admin API); RECOVERY (enrollment reset only); '
    'ACTIVATION (deferred-onboarding activation code, consumed by /activate only, never a session)';

ALTER TABLE ezkey_tenant
    DROP COLUMN IF EXISTS self_registered;

DELETE FROM ezkey_shedlock
WHERE name = 'EVALUATOR_TEMP_SESSION_EXPIRY';
