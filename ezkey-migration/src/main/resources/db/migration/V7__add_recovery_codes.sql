-- ============================================================================
-- Migration V7: Add Recovery Codes Support for Admin Passwordless
-- ============================================================================
-- Description: Adds recovery codes column to support emergency access when
--              admin device is lost or unavailable. Recovery codes are
--              single-use cryptographically hashed codes that allow temporary
--              access to re-bind a new enrollment.
-- 
-- Author: Ezkey Contributors
-- Date: October 2025
-- ============================================================================

-- Add recovery_codes column
-- Stores array of BCrypt hashed recovery codes (10 codes per admin)
ALTER TABLE ezkey_admin 
ADD COLUMN recovery_codes TEXT[];

-- Add comments for documentation
COMMENT ON COLUMN ezkey_admin.recovery_codes IS 
'Array of single-use recovery codes (BCrypt hashed) for emergency access when device is lost. Each code can be used once to obtain a temporary token for re-binding enrollment. Codes are in format XXX-XXX-XXX.';

-- ============================================================================
-- Recovery Codes Security Model:
-- ============================================================================
-- - 10 codes generated during admin creation
-- - Each code is BCrypt hashed (same security as passwords)
-- - Single-use: Code is removed from array after successful use
-- - Format: XXX-XXX-XXX (9 characters, easy to type)
-- - Entropy: ~47 bits (alphanumeric, 9 chars)
-- - Recovery token: 30-minute validity, limited to enrollment binding only
-- ============================================================================

-- Note: Recovery codes will be generated and populated during bootstrap
-- or when admin is created. Existing admins can generate codes via admin API.

-- ============================================================================
-- End of migration V7
-- ============================================================================

