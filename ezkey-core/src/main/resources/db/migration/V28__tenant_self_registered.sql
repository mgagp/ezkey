-- ============================================================================
-- Ezkey Migration V28: Self-registered tenant marker (#1206 step 7)
-- ============================================================================
-- Description: Records that a tenant was created by anonymous evaluator
--              self-registration. A fact about the tenant, not a permission:
--              QR delivery through a connector is refused for such a tenant,
--              which keeps integrated delivery (on-screen QR). Self-signup
--              gives no proof that the tenant owns any email address, so a
--              connector must not become an open relay to arbitrary inboxes.
--
-- Existing rows: self-registration names the tenant "eval-<suffix>" and gives
--              it the default description "Anonymous evaluator preview tenant",
--              optionally followed by " (<label>)". Rows matching both are
--              marked. The system tenant is never marked.
--
-- Manual rollback (no Flyway undo): ALTER TABLE ezkey_tenant DROP COLUMN
--              self_registered; delete version 28 from flyway_schema_history.
--              Self-registered tenants can then send again if a connector is
--              configured.
--
-- Author: Ezkey contributors
-- Date: 2026-11-12
-- ============================================================================

ALTER TABLE ezkey_tenant
    ADD COLUMN self_registered BOOLEAN DEFAULT FALSE NOT NULL;

UPDATE ezkey_tenant
SET self_registered = TRUE
WHERE is_system_tenant = FALSE
  AND tenant_name LIKE 'eval-%'
  AND tenant_description LIKE 'Anonymous evaluator preview tenant%';

COMMENT ON COLUMN ezkey_tenant.self_registered IS
    'TRUE when anonymous evaluator self-registration created this tenant. '
    'QR delivery through a connector is refused for it (#1206 step 7).';
