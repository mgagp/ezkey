-- ============================================================================
-- Ezkey Migration V27: Add System Tenant Flag
-- ============================================================================
-- Description: Adds is_system_tenant flag to distinguish system tenant from
--              application tenants. This provides a robust way to identify
--              the system tenant without relying on string comparisons.
-- 
-- Context: The system tenant (tenant_id=1) hosts global administrators and
--          should not contain tenant administrators. This flag enables
--          proper validation and separation of concerns.
--
-- Author: Ezkey contributors
-- Date: 2025-12-26
-- ============================================================================

-- Add is_system_tenant column to ezkey_tenant table
ALTER TABLE ezkey_tenant
    ADD COLUMN is_system_tenant BOOLEAN DEFAULT FALSE NOT NULL;

-- Mark existing System Tenant (created in V3) as system tenant
UPDATE ezkey_tenant
SET is_system_tenant = TRUE
WHERE tenant_name = 'Ezkey System';

-- Add column comment
COMMENT ON COLUMN ezkey_tenant.is_system_tenant IS
'Flag indicating if this is the system tenant. System tenant hosts global administrators and represents the organization hosting this Ezkey instance. Only one tenant should have this flag set to TRUE.';

-- Add unique constraint to ensure only one system tenant exists
-- Note: This uses a partial unique index to allow multiple FALSE values
CREATE UNIQUE INDEX idx_tenant_system_tenant_unique 
ON ezkey_tenant(is_system_tenant) 
WHERE is_system_tenant = TRUE;

-- Add index comment
COMMENT ON INDEX idx_tenant_system_tenant_unique IS
'Ensures only one system tenant exists in the database. Uses partial unique index to allow multiple application tenants (is_system_tenant=FALSE).';

