-- ============================================================================
-- Ezkey Migration V8: Deprecate Password Infrastructure
-- ============================================================================
-- Description: Marks password-related columns as deprecated and nullable.
--              This is a non-breaking migration for gradual transition.
--              Password infrastructure will be fully removed in V9.
-- 
-- Context: Ezkey is transitioning to passwordless-only authentication.
--          This migration allows existing deployments to migrate gracefully.
-- 
-- Author: Ezkey contributors
-- Date: 2025-10-12
-- ============================================================================

-- Make password columns nullable (allow passwordless admins)
ALTER TABLE ezkey_admin 
ALTER COLUMN password_hash DROP NOT NULL;

ALTER TABLE ezkey_admin 
ALTER COLUMN password_change_required DROP NOT NULL;

-- Add deprecation comments
COMMENT ON COLUMN ezkey_admin.password_hash IS 
'DEPRECATED in v1.5 - Will be removed in v2.0. Ezkey is now passwordless-only. Use passwordless_enabled instead.';

COMMENT ON COLUMN ezkey_admin.password_change_required IS 
'DEPRECATED in v1.5 - Will be removed in v2.0. No longer used in passwordless authentication.';

COMMENT ON COLUMN ezkey_admin.last_password_change IS 
'DEPRECATED in v1.5 - Will be removed in v2.0. No longer tracked in passwordless authentication.';

COMMENT ON COLUMN ezkey_admin.mfa_enabled IS 
'DEPRECATED in v1.5 - Will be removed in v2.0. MFA is always enabled via Ezkey enrollment.';

COMMENT ON COLUMN ezkey_admin.mfa_required IS 
'DEPRECATED in v1.5 - Will be removed in v2.0. MFA is always required via Ezkey enrollment.';

-- Rename temp tokens table for archival (no longer used in passwordless-only mode)
ALTER TABLE ezkey_admin_temp_tokens 
RENAME TO ezkey_admin_temp_tokens_deprecated;

COMMENT ON TABLE ezkey_admin_temp_tokens_deprecated IS
'DEPRECATED - Legacy temp tokens from password+MFA workflow. No longer used in passwordless-only mode. Will be dropped in v2.0.';

-- ============================================================================
-- Migration Notes:
-- - Existing admins with passwords can still authenticate during transition
-- - New admins created via bootstrap are passwordless-only
-- - Recovery codes provide emergency access for passwordless admins
-- - V9 will complete the removal of all password infrastructure
-- ============================================================================

