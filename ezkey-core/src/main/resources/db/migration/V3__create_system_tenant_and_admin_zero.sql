-- ============================================================================
-- Ezkey Migration V3: Transform to Passwordless Schema + Create Initial Global Admin
-- ============================================================================
-- Description: Transforms ezkey_admin table to passwordless-only schema,
--              then creates the system tenant and initial global administrator.
-- 
-- Context: This migration consolidates V3-V9 from the original plan:
--          - Removes password infrastructure (password_hash, mfa_enabled, etc.)
--          - Adds passwordless infrastructure (challenge_required, recovery_codes)
--          - Drops ezkey_admin_temp_tokens table (no longer needed)
--          - Creates system tenant and initial global admin
--
-- Responsibility Split:
--   - Flyway (this migration): Transform schema, create structural data
--   - Bootstrap service: Create enrollment + recovery codes + crypto keys
-- 
-- IMPORTANT: The username 'admin' is a placeholder. For SOC 2 compliance,
--            the initial global admin must be configured with an identifiable
--            username via application properties (ezkey.admin.initial.username).
--            The bootstrap service will update the admin with the configured username.
-- 
-- Author: Ezkey contributors
-- Date: 2025-10-13
-- ============================================================================

-- ============================================================================
-- STEP 1: Transform ezkey_admin Schema to Passwordless-Only
-- ============================================================================

-- Remove password-based authentication columns
ALTER TABLE ezkey_admin DROP COLUMN IF EXISTS password_hash;
ALTER TABLE ezkey_admin DROP COLUMN IF EXISTS mfa_enabled;
ALTER TABLE ezkey_admin DROP COLUMN IF EXISTS mfa_required;
ALTER TABLE ezkey_admin DROP COLUMN IF EXISTS password_change_required;
ALTER TABLE ezkey_admin DROP COLUMN IF EXISTS last_password_change;

-- Add passwordless authentication columns
ALTER TABLE ezkey_admin ADD COLUMN IF NOT EXISTS challenge_required BOOLEAN DEFAULT FALSE NOT NULL;
ALTER TABLE ezkey_admin ADD COLUMN IF NOT EXISTS recovery_codes TEXT[];

-- Update the check_admin_hierarchy constraint (already exists from V2, but modify it)
ALTER TABLE ezkey_admin DROP CONSTRAINT IF EXISTS check_admin_hierarchy;

-- Drop ezkey_admin_temp_tokens table (no longer needed in passwordless architecture)
DROP TABLE IF EXISTS ezkey_admin_temp_tokens;

-- ============================================================================
-- STEP 2: Create System Tenant
-- ============================================================================

-- System tenant represents the organization hosting this Ezkey instance
INSERT INTO ezkey_tenant (tenant_name, tenant_description, created_at, active) 
VALUES (
    'Ezkey System', 
    'System tenant for global administrators', 
    CURRENT_TIMESTAMP, 
    true
);

COMMENT ON TABLE ezkey_tenant IS
'Multi-tenant isolation. System tenant (Ezkey System) hosts global administrators.';

-- ============================================================================
-- STEP 3: Create Initial Global Administrator (Passwordless-Only Schema)
-- ============================================================================

-- Initial global admin is the first global administrator with passwordless authentication
-- Bootstrap service will complete setup by creating enrollment and recovery codes
-- NOTE: Username 'admin' is a placeholder. For SOC 2 compliance, the initial global
--       admin must be configured with an identifiable username via application properties.
--       The bootstrap service will update the admin with the configured username and email.
INSERT INTO ezkey_admin (
    username, 
    admin_type, 
    tenant_id,
    integration_id,
    challenge_required,
    recovery_codes,
    mfa_enrollment_id,
    created_at, 
    active
) VALUES (
    'admin',  -- Placeholder - will be updated by bootstrap service with configured username
    'GLOBAL_ADMIN', 
    (SELECT tenant_id FROM ezkey_tenant WHERE tenant_name = 'Ezkey System'),
    NULL,
    false,  -- No challenge by default (convenience over paranoia)
    NULL,   -- Bootstrap will generate 10 recovery codes (32-digit, 106-bit entropy)
    NULL,   -- Bootstrap will create global admin enrollment
    CURRENT_TIMESTAMP, 
    true
);

-- ============================================================================
-- STEP 4: Link Tenant to Admin Creator (Circular Reference Resolution)
-- ============================================================================

-- Update tenant to reference the admin who "created" it (initial global admin)
-- This satisfies the foreign key relationship for audit purposes
-- NOTE: Uses placeholder username 'admin' - will be updated after bootstrap
UPDATE ezkey_tenant 
SET created_by_admin_id = (SELECT admin_id FROM ezkey_admin WHERE username = 'admin')
WHERE tenant_name = 'Ezkey System';

-- ============================================================================
-- STEP 5: Update Admin Hierarchy Constraint
-- ============================================================================

-- Update constraint to allow GLOBAL_ADMIN to optionally have system tenant
-- (The original constraint from V2 didn't allow GLOBAL_ADMIN to have tenant_id)
ALTER TABLE ezkey_admin ADD CONSTRAINT check_admin_hierarchy CHECK (
    (admin_type = 'GLOBAL_ADMIN' AND integration_id IS NULL) OR
    (admin_type = 'TENANT_ADMIN' AND tenant_id IS NOT NULL AND integration_id IS NULL) OR
    (admin_type = 'INTEGRATION_ADMIN' AND tenant_id IS NOT NULL AND integration_id IS NOT NULL)
);

-- ============================================================================
-- STEP 6: Add Indexes for Performance
-- ============================================================================

-- Index for global admin queries (filtered on active status)
CREATE INDEX IF NOT EXISTS idx_admin_global_tenant 
ON ezkey_admin(tenant_id, admin_type) 
WHERE admin_type = 'GLOBAL_ADMIN' AND active = TRUE;

-- ============================================================================
-- STEP 7: Documentation Comments
-- ============================================================================

COMMENT ON TABLE ezkey_admin IS
'Administrative users. Uses passwordless-only authentication via Ezkey enrollment. No passwords stored.';

COMMENT ON COLUMN ezkey_admin.challenge_required IS
'If true, authentication attempts for this admin require 6-digit challenge code verification on device.';

COMMENT ON COLUMN ezkey_admin.recovery_codes IS
'Array of BCrypt-hashed recovery codes (32-digit, 106-bit entropy) for emergency access when device is lost. Single-use codes.';

COMMENT ON COLUMN ezkey_admin.mfa_enrollment_id IS
'Links to Ezkey enrollment for passwordless authentication. Bootstrap service ensures all active admins have enrollment.';

COMMENT ON COLUMN ezkey_admin.username IS
'Unique username for admin authentication. Used in passwordless login flow.';

COMMENT ON COLUMN ezkey_admin.admin_type IS
'Administrator type: GLOBAL_ADMIN (system-wide), TENANT_ADMIN (tenant-scoped), or INTEGRATION_ADMIN (integration-scoped).';

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- Initial global admin created with passwordless infrastructure ready.
-- Bootstrap service will complete setup on first application startup:
--   - Update admin with configured username and email (SOC 2 compliance)
--   - Create system integration + global admin enrollment
--   - Generate 10 recovery codes (32-digit each)
--   - Display credentials in logs (one-time opportunity to save)
-- ============================================================================

