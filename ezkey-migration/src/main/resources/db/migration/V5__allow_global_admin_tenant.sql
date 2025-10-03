-- Migration V5: Allow optional tenant for GLOBAL_ADMIN
-- This migration modifies the admin hierarchy constraint to allow GLOBAL_ADMIN
-- to optionally belong to a system tenant, representing the organization hosting the instance

-- Drop the existing constraint
ALTER TABLE ezkey_admin DROP CONSTRAINT check_admin_hierarchy;

-- Create new constraint allowing tenant_id for GLOBAL_ADMIN
ALTER TABLE ezkey_admin ADD CONSTRAINT check_admin_hierarchy CHECK (
    (admin_type = 'GLOBAL_ADMIN' AND integration_id IS NULL) OR  -- tenant_id now optional for GLOBAL_ADMIN
    (admin_type = 'TENANT_ADMIN' AND tenant_id IS NOT NULL AND integration_id IS NULL) OR
    (admin_type = 'INTEGRATION_ADMIN' AND tenant_id IS NOT NULL AND integration_id IS NOT NULL)
);

-- Link existing admin zero to system tenant (if exists)
UPDATE ezkey_admin 
SET tenant_id = (SELECT tenant_id FROM ezkey_tenant WHERE tenant_name = 'Ezkey System' LIMIT 1)
WHERE username = 'admin' 
  AND admin_type = 'GLOBAL_ADMIN'
  AND tenant_id IS NULL
  AND EXISTS (SELECT 1 FROM ezkey_tenant WHERE tenant_name = 'Ezkey System');

-- Add index for performance on global admin queries
CREATE INDEX IF NOT EXISTS idx_admin_global_tenant 
ON ezkey_admin(tenant_id, admin_type) 
WHERE admin_type = 'GLOBAL_ADMIN' AND active = TRUE;

