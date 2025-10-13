-- ============================================================================
-- Ezkey Migration V9: Remove Password Infrastructure (BREAKING)
-- ============================================================================
-- Description: Complete removal of password-based authentication infrastructure.
--              This is a BREAKING migration - all password-based auth is removed.
-- 
-- Context: Ezkey is now passwordless-only. This migration finalizes the
--          transition by removing all password-related database artifacts.
-- 
-- WARNING: This migration is irreversible. Ensure all admins have:
--          1. Passwordless enrollment bound (device_public_key set)
--          2. Recovery codes saved securely
--          3. Tested passwordless login successfully
-- 
-- Author: Ezkey contributors
-- Date: 2025-10-12
-- ============================================================================

-- ============================================================================
-- STEP 1: Prepare existing admins for passwordless-only mode
-- ============================================================================

-- Set challenge_required to false by default (convenience over paranoia)
UPDATE ezkey_admin
SET challenge_required = false
WHERE active = true AND challenge_required IS NULL;

-- Log admins without enrollment (bootstrap will create enrollment for them)
DO $$
DECLARE
    orphaned_admins INT;
BEGIN
    SELECT COUNT(*) INTO orphaned_admins
    FROM ezkey_admin
    WHERE mfa_enrollment_id IS NULL
       AND active = true;
    
    IF orphaned_admins > 0 THEN
        RAISE WARNING 'Found % admin(s) without MFA enrollment. Bootstrap will create enrollment and recovery codes.', orphaned_admins;
    END IF;
END $$;

-- ============================================================================
-- STEP 2: Drop password-related columns AND redundant flags
-- ============================================================================

-- Drop all password infrastructure
ALTER TABLE ezkey_admin 
DROP COLUMN IF EXISTS password_hash,
DROP COLUMN IF EXISTS password_change_required,
DROP COLUMN IF EXISTS last_password_change,
DROP COLUMN IF EXISTS mfa_enabled,
DROP COLUMN IF EXISTS mfa_required,
DROP COLUMN IF EXISTS passwordless_enabled;  -- Redundant: if it exists, it's passwordless-only

-- Rationale: In passwordless-ONLY mode, having a "passwordless_enabled" flag
-- is like having "is_user = true" in a users table - it provides no information.

-- ============================================================================
-- STEP 3: Drop temp tokens infrastructure
-- ============================================================================

-- Drop deprecated temp tokens table (renamed in V8)
DROP TABLE IF EXISTS ezkey_admin_temp_tokens_deprecated CASCADE;

-- Drop any remaining indexes from temp tokens
DROP INDEX IF EXISTS idx_admin_temp_tokens_expired;
DROP INDEX IF EXISTS idx_admin_temp_tokens_admin_id;

-- ============================================================================
-- STEP 4: Add comments documenting passwordless-only architecture
-- ============================================================================

-- No constraints needed: existence of admin in this table = passwordless authentication
-- Bootstrap service ensures all admins have enrollment and recovery codes

-- ============================================================================
-- STEP 5: Update table comments
-- ============================================================================

COMMENT ON TABLE ezkey_admin IS
'Administrative users for Ezkey management. Uses passwordless-only authentication via Ezkey enrollment. No passwords stored.';

COMMENT ON COLUMN ezkey_admin.mfa_enrollment_id IS
'Links to Ezkey enrollment for passwordless authentication. Bootstrap service ensures all active admins have enrollment.';

COMMENT ON COLUMN ezkey_admin.challenge_required IS
'If true, authentication attempts for this admin require 6-digit challenge code verification on device.';

COMMENT ON COLUMN ezkey_admin.recovery_codes IS
'BCrypt-hashed recovery codes (32-digit, 106-bit entropy) for emergency access when device is lost. Single-use codes.';

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- Passwordless-only authentication is now enforced at the database level.
-- All password-based authentication infrastructure has been removed.
-- Recovery codes provide emergency access for device loss scenarios.
-- ============================================================================

