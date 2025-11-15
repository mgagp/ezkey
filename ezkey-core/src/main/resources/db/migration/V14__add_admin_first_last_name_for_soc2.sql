-- ============================================================================
-- Ezkey Migration V14: Add First Name and Last Name to ezkey_admin for SOC 2 Compliance
-- ============================================================================
-- Description: Adds first_name and last_name columns to ezkey_admin table to support
--              SOC 2 compliance requirements for individual identification.
-- 
-- SOC 2 Requirements:
--   - CC6.1: Logical access controls require individual identification
--   - CC7.2: Audit trail must identify individuals (not generic accounts)
--   - First name and last name are required for GLOBAL_ADMIN type to ensure
--     proper identification and accountability
--
-- Author: Ezkey contributors
-- Date: 2025-01-XX
-- ============================================================================

-- ============================================================================
-- STEP 1: Add First Name and Last Name Columns
-- ============================================================================

-- Add first_name column to ezkey_admin table
-- Required for GLOBAL_ADMIN type (SOC 2 compliance)
-- Optional for other admin types (can be added later if needed)
ALTER TABLE ezkey_admin
    ADD COLUMN first_name VARCHAR(100);

-- Add last_name column to ezkey_admin table
-- Required for GLOBAL_ADMIN type (SOC 2 compliance)
-- Optional for other admin types (can be added later if needed)
ALTER TABLE ezkey_admin
    ADD COLUMN last_name VARCHAR(100);

-- ============================================================================
-- STEP 2: Add Documentation Comments
-- ============================================================================

COMMENT ON COLUMN ezkey_admin.first_name IS
'First name of the administrator. Required for GLOBAL_ADMIN type for SOC 2 compliance (CC6.1, CC7.2). '
'Used for audit trail and accountability. Must be provided for initial global admin.';

COMMENT ON COLUMN ezkey_admin.last_name IS
'Last name of the administrator. Required for GLOBAL_ADMIN type for SOC 2 compliance (CC6.1, CC7.2). '
'Used for audit trail and accountability. Must be provided for initial global admin.';

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- First name and last name columns added to ezkey_admin table.
-- 
-- IMPORTANT: For existing installations, the initial global admin first name
-- and last name must be configured via application properties:
--   ezkey.admin.initial.username=<identifiable-username>
--   ezkey.admin.initial.email=<email-address>
--   ezkey.admin.initial.first-name=<first-name>
--   ezkey.admin.initial.last-name=<last-name>
--
-- The system will fail to start if first name and last name are not configured
-- for GLOBAL_ADMIN.
-- ============================================================================

