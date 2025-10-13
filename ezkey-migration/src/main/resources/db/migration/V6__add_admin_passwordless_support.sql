-- ============================================================================
-- Migration V6: Add Passwordless Authentication Support for Admin
-- ============================================================================
-- Description: Adds columns to support passwordless authentication mode
--              where admins can authenticate using only Ezkey cryptographic
--              authentication without requiring passwords.
-- 
-- Author: Ezkey Contributors
-- Date: October 2025
-- ============================================================================

-- Add passwordless_enabled column
-- When true, admin can authenticate using Ezkey cryptographic auth only
ALTER TABLE ezkey_admin 
ADD COLUMN passwordless_enabled BOOLEAN DEFAULT FALSE NOT NULL;

-- Add challenge_required column
-- When true, auth attempts for this admin must include challenge verification
ALTER TABLE ezkey_admin 
ADD COLUMN challenge_required BOOLEAN DEFAULT FALSE NOT NULL;

-- Add comments for documentation
COMMENT ON COLUMN ezkey_admin.passwordless_enabled IS 
'Indicates if this admin can authenticate using passwordless (Ezkey-only) mode. When true, admin can login without password using cryptographic device authentication.';

COMMENT ON COLUMN ezkey_admin.challenge_required IS 
'Indicates if challenge code verification is required during passwordless authentication. When true, device must verify 6-digit challenge code during approval.';

-- Enable passwordless for admin zero (bootstrap admin)
-- This demonstrates "eat your own dogfood" by using Ezkey for admin auth
UPDATE ezkey_admin 
SET passwordless_enabled = TRUE
WHERE username = 'admin' 
  AND admin_type = 'GLOBAL_ADMIN'
  AND tenant_id = 0;

-- ============================================================================
-- End of migration V6
-- ============================================================================

