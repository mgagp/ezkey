-- ============================================================================
-- Ezkey Database Migration - V7
-- API Keys for Machine-to-Machine Authentication
-- ============================================================================
-- Description: Adds API keys table for M2M authentication of integrated
--              applications, enabling server-to-server authentication without
--              login/logout overhead.
--
-- Author: Ezkey contributors
-- Date: 2025
-- License: MIT
-- ============================================================================

-- ============================================================================
-- STEP 1: Create API Keys Table
-- ============================================================================

-- API Keys table for machine-to-machine authentication
-- Stores Duo-style integration keys (public) and secret keys (BCrypt hashed)
CREATE TABLE ezkey_api_key (
    api_key_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    integration_id INT NOT NULL REFERENCES ezkey_integration(integration_id),
    
    -- Duo-style keys
    integration_key VARCHAR(255) NOT NULL UNIQUE, -- ezkey_ikey_xxx (public identifier)
    secret_key_hash VARCHAR(255) NOT NULL,        -- BCrypt hash of ezkey_skey_xxx
    
    -- Metadata
    description VARCHAR(255),                      -- Human-readable description
    created_by_admin_id INT NOT NULL REFERENCES ezkey_admin(admin_id),
    
    -- Security features
    last_used_at TIMESTAMPTZ,                      -- Tracks last usage for audit
    expires_at TIMESTAMPTZ,                        -- Optional expiration for rotation
    ip_whitelist TEXT[],                           -- Optional IP restrictions (CIDR format)
    
    -- Status and audit
    active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_by_admin_id INT REFERENCES ezkey_admin(admin_id),
    
    -- Constraints
    CONSTRAINT check_expiration CHECK (expires_at IS NULL OR expires_at > created_at),
    CONSTRAINT check_revoked_consistency CHECK (
        (active = TRUE AND revoked_at IS NULL AND revoked_by_admin_id IS NULL) OR
        (active = FALSE AND revoked_at IS NOT NULL AND revoked_by_admin_id IS NOT NULL)
    )
);

-- ============================================================================
-- STEP 2: Create Indexes for Performance
-- ============================================================================

-- Index for looking up active keys by integration
CREATE INDEX idx_api_key_integration ON ezkey_api_key(integration_id, active);

-- Partial index for fast active key lookup (most common query)
CREATE INDEX idx_api_key_lookup ON ezkey_api_key(integration_key) 
WHERE active = true;

-- Index for admin audit queries
CREATE INDEX idx_api_key_admin ON ezkey_api_key(created_by_admin_id);

-- Index for expiration cleanup job
CREATE INDEX idx_api_key_expiration ON ezkey_api_key(expires_at) 
WHERE active = true AND expires_at IS NOT NULL;

-- ============================================================================
-- STEP 3: Add Table and Column Comments
-- ============================================================================

-- Table-level comment
COMMENT ON TABLE ezkey_api_key IS
'API keys for machine-to-machine (M2M) authentication of integrated applications.
Enables server-to-server authentication without login/logout overhead.
Uses Duo-style dual key system: public integration key + private secret key.';

-- Column comments for clarity
COMMENT ON COLUMN ezkey_api_key.api_key_id IS 
'Primary key identifier for the API key record';

COMMENT ON COLUMN ezkey_api_key.integration_id IS 
'Foreign key to integration - links API key to specific integration';

COMMENT ON COLUMN ezkey_api_key.integration_key IS 
'Public integration key (ezkey_ikey_xxx) - stored in plain text, safe to display in logs and UI';

COMMENT ON COLUMN ezkey_api_key.secret_key_hash IS 
'BCrypt hash of secret key (ezkey_skey_xxx) - plain text secret NEVER stored, shown only once at creation';

COMMENT ON COLUMN ezkey_api_key.description IS 
'Optional human-readable description for key identification (e.g., "Production Server API Key")';

COMMENT ON COLUMN ezkey_api_key.created_by_admin_id IS 
'Foreign key to admin who created this API key - required for audit trail';

COMMENT ON COLUMN ezkey_api_key.last_used_at IS 
'Timestamp of last successful authentication using this key - updated on each use for monitoring';

COMMENT ON COLUMN ezkey_api_key.expires_at IS 
'Optional expiration date for automatic key rotation enforcement - null means no expiration';

COMMENT ON COLUMN ezkey_api_key.ip_whitelist IS 
'Optional array of IP addresses or CIDR ranges allowed to use this key (e.g., ["192.168.1.0/24", "10.0.0.1"])';

COMMENT ON COLUMN ezkey_api_key.active IS 
'Active status flag - inactive keys cannot authenticate but are preserved for audit';

COMMENT ON COLUMN ezkey_api_key.created_at IS 
'Audit timestamp recording when API key was created - immutable for compliance';

COMMENT ON COLUMN ezkey_api_key.revoked_at IS 
'Timestamp when key was revoked - null for active keys, set when explicitly revoked';

COMMENT ON COLUMN ezkey_api_key.revoked_by_admin_id IS 
'Foreign key to admin who revoked this key - required when revoked for audit trail';

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- This migration adds support for API key authentication, enabling integrated
-- applications to authenticate via HTTP Basic Auth without login/logout.
--
-- Security features:
-- - BCrypt hashed secrets (like passwords)
-- - Optional expiration dates
-- - IP whitelist support  
-- - Rate limiting (configured in application)
-- - Comprehensive audit trail
--
-- Next steps after migration:
-- 1. Implement ApiKey entity and repository
-- 2. Create ApiKeyService for generation and validation
-- 3. Add ApiKeyAuthenticationFilter to security chain
-- 4. Create management endpoints in ApiKeyController
-- ============================================================================

