-- ============================================================
-- V37: Add optional reason/justification column to audit log
-- ============================================================
-- Purpose   : SOC 2 CC6.3 (access deprovisioning) and CC8.1 (authorized changes)
-- Design    : Optional VARCHAR(500) — no obligation to provide, validated ≥10 chars if supplied
-- HMAC      : Field is included in per-entry canonical form as position 15
--             (null reason → empty string in canonical form, consistent with nullSafe() convention)
-- Partitions: ADD COLUMN on the parent table propagates automatically to all monthly partitions
--             via PostgreSQL declarative partitioning inheritance
-- Rollback  : ALTER TABLE ezkey_audit_log DROP COLUMN reason;
-- ============================================================

ALTER TABLE ezkey_audit_log
  ADD COLUMN IF NOT EXISTS reason VARCHAR(500);

COMMENT ON COLUMN ezkey_audit_log.reason IS
  'Optional justification for sensitive operations (revoke, delete, deactivate). '
  'Supports SOC 2 CC6.3 / CC8.1. Included in per-entry HMAC canonical form.';
