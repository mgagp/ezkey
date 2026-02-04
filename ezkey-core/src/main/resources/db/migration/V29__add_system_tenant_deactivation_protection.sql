-- ============================================================================
-- Ezkey Migration V29: Add System Tenant Deactivation Protection
-- ============================================================================
-- Description: Adds a CHECK constraint to prevent deactivation of the system
--              tenant through database operations. This provides defense in
--              depth alongside application-level validation.
-- 
-- Context: The system tenant (is_system_tenant=TRUE) hosts global administrators
--          and must remain active to ensure system integrity. This constraint
--          prevents accidental or malicious deactivation at the database level.
--
-- Author: Ezkey contributors
-- Date: 2025-02-04
-- ============================================================================

-- Add CHECK constraint to prevent deactivation of system tenant
-- This constraint ensures that if is_system_tenant is TRUE, then active must be TRUE
ALTER TABLE ezkey_tenant
    ADD CONSTRAINT chk_system_tenant_must_be_active 
    CHECK (
        is_system_tenant = FALSE OR 
        (is_system_tenant = TRUE AND active = TRUE)
    );

-- Add constraint comment
COMMENT ON CONSTRAINT chk_system_tenant_must_be_active ON ezkey_tenant IS
'Ensures system tenant remains active. System tenant (is_system_tenant=TRUE) cannot have active=FALSE to prevent loss of administrative access.';
