-- Add integration code column and unique constraint for per-tenant uniqueness
-- Integration codes are required business identifiers (slug format: alphanumeric, hyphens, underscores)
-- Codes must be unique per tenant to enable stable, human-readable integration references

-- Add the integration_code column to ezkey_integration
ALTER TABLE ezkey_integration
ADD COLUMN integration_code VARCHAR(100);

-- Add comment for the new column
COMMENT ON COLUMN ezkey_integration.integration_code IS 'Required unique business identifier code for the integration within a tenant (e.g., web-portal, mobile-app). Follows slug format with alphanumeric characters, hyphens, and underscores. Must be unique per tenant to prevent duplicate integrations.';

-- Backfill existing rows with a generated code based on UUID to ensure uniqueness temporarily
-- Existing integrations get UUID-based codes to unblock the migration
UPDATE ezkey_integration
SET integration_code = 'int_' || SUBSTR(MD5(RANDOM()::TEXT), 1, 20)
WHERE integration_code IS NULL;

-- Add NOT NULL constraint after backfilling
ALTER TABLE ezkey_integration
ALTER COLUMN integration_code SET NOT NULL;

-- Create unique constraint on (tenant_id, integration_code) for per-tenant uniqueness
-- This allows the same code to exist in different tenants
ALTER TABLE ezkey_integration
ADD CONSTRAINT unique_integration_code_per_tenant UNIQUE (tenant_id, integration_code);
