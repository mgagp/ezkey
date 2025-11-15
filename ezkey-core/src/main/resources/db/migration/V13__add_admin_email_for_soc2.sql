-- ============================================================================
-- Ezkey Migration V13: Add Email Column to ezkey_admin for SOC 2 Compliance
-- ============================================================================
-- Description: Adds email column to ezkey_admin table to support SOC 2 compliance
--              requirements for individual accountability and audit trail.
-- 
-- SOC 2 Requirements:
--   - CC6.1: Logical access controls require individual identification
--   - CC7.2: Audit trail must identify individuals (not generic accounts)
--   - Email is required for GLOBAL_ADMIN type to ensure accountability
--
-- Author: Ezkey contributors
-- Date: 2025-01-XX
-- ============================================================================

-- ============================================================================
-- STEP 1: Add Email Column
-- ============================================================================

-- Add email column to ezkey_admin table
-- Email is required for GLOBAL_ADMIN type (SOC 2 compliance)
-- Optional for other admin types (can be added later if needed)
ALTER TABLE ezkey_admin
    ADD COLUMN email VARCHAR(255);

-- ============================================================================
-- STEP 2: Add Constraints
-- ============================================================================

-- Add unique constraint on email (one email per admin)
ALTER TABLE ezkey_admin
    ADD CONSTRAINT uq_admin_email UNIQUE (email);

-- Add check constraint to ensure email format is valid (basic validation)
-- More comprehensive validation is done at application level
ALTER TABLE ezkey_admin
    ADD CONSTRAINT chk_admin_email_format 
    CHECK (email IS NULL OR email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$');

-- ============================================================================
-- STEP 3: Update Existing Admin (if exists)
-- ============================================================================

-- For existing admin with username 'admin', we cannot set email automatically
-- as it must be identifiable. This will be handled by configuration validation.
-- Migration will leave email as NULL for existing admin, which will cause
-- startup validation to fail if not configured properly.

-- ============================================================================
-- STEP 4: Add Documentation Comments
-- ============================================================================

COMMENT ON COLUMN ezkey_admin.email IS
'Email address for the administrator. Required for GLOBAL_ADMIN type for SOC 2 compliance (CC6.1, CC7.2). '
'Used for audit trail and accountability. Must be unique and valid email format.';

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- Email column added to ezkey_admin table.
-- 
-- IMPORTANT: For existing installations, the initial global admin email
-- must be configured via application properties:
--   ezkey.admin.initial.username=<identifiable-username>
--   ezkey.admin.initial.email=<email-address>
--
-- The system will fail to start if email is not configured for GLOBAL_ADMIN.
-- ============================================================================

