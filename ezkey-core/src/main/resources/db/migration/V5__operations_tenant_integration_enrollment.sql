-- ============================================================================
-- ShedLock Table for Distributed Job Coordination
-- ============================================================================
-- Used by ShedLock library to prevent concurrent execution of scheduled jobs
-- across multiple Admin API instances in HA deployments.
--
-- Locking Model:
-- - Each job acquires a lock before execution
-- - Lock is held for duration of job (plus safety margin)
-- - If instance crashes, lock auto-expires after lock_until
-- - Any instance can acquire lock for next execution
--
-- SOC2 Auditability:
-- - locked_by: identifies which instance executed the job
-- - locked_at: timestamp of lock acquisition
-- - Query this table to audit job execution history
-- ============================================================================

CREATE TABLE ezkey_shedlock (
    -- Unique job identifier (matches @SchedulerLock name)
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    
    -- When the lock expires (allows failover if instance crashes)
    lock_until TIMESTAMPTZ NOT NULL,
    
    -- When the lock was acquired (audit trail)
    locked_at TIMESTAMPTZ NOT NULL,
    
    -- Instance that holds the lock (audit trail)
    locked_by VARCHAR(255) NOT NULL
);

-- Comments for documentation
COMMENT ON TABLE ezkey_shedlock IS 
    'Distributed lock table for scheduled job coordination (ShedLock library)';
COMMENT ON COLUMN ezkey_shedlock.name IS 
    'Job identifier: KEY_PROMOTION, KEY_ROTATION, REENCRYPTION, AUDIT_CLEANUP, DB_PARTITION_CREATION, ADMIN_TOKEN_CLEANUP, ADMIN_STARTUP_BOOTSTRAP';
COMMENT ON COLUMN ezkey_shedlock.lock_until IS 
    'Lock expiry timestamp - allows automatic failover if instance crashes';
COMMENT ON COLUMN ezkey_shedlock.locked_at IS 
    'Lock acquisition timestamp - for SOC2 audit trail';
COMMENT ON COLUMN ezkey_shedlock.locked_by IS 
    'Instance identifier that holds the lock - for SOC2 audit trail';
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

-- Ezkey Migration V28: Enrollment Uniqueness Constraint for VERIFIED Status
-- Purpose: Enforce uniqueness constraint on (integration_id, enrollment_name) for VERIFIED enrollments
--          while allowing multiple CREATED enrollments for retry scenarios.
--          The enrollment_active flag is separate and used for administrative control.

-- ============================================================================
-- STEP 1: Create Partial Unique Index
-- ============================================================================

-- Partial unique index: only one VERIFIED enrollment per integration+name
-- This allows multiple CREATED, BOUND, and INVALID enrollments while preventing duplicate VERIFIED ones
CREATE UNIQUE INDEX idx_enrollment_unique_verified_name
ON ezkey_enrollment(integration_id, enrollment_name)
WHERE enrollment_status = 'VERIFIED';

COMMENT ON INDEX idx_enrollment_unique_verified_name IS
'Ensures only one VERIFIED enrollment exists per integration and enrollment name. Allows multiple CREATED enrollments for retry scenarios. The enrollment_active flag is separate and used for administrative control.';

-- ============================================================================
-- STEP 2: Create Performance Index
-- ============================================================================

-- Index to optimize queries for finding VERIFIED enrollments by integration and name
-- Used for validation logic during enrollment creation and verification
CREATE INDEX idx_enrollment_integration_name_status
ON ezkey_enrollment(integration_id, enrollment_name, enrollment_status)
WHERE enrollment_status = 'VERIFIED';

COMMENT ON INDEX idx_enrollment_integration_name_status IS
'Performance index for validation queries that check for existing VERIFIED enrollments by integration and name. Used during enrollment creation and verification to enforce uniqueness constraint.';
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
--
-- V30: Enrich tenant with organizational identity, contact, and governance fields
--
-- SOC 2 Controls: CC2.1 (Communication), CC6.1 (Logical Access),
--                 CC6.3 (Access Removal), CC7.2 (Change Management)
--
-- All new columns are nullable for backward compatibility with existing tenants.
--

-- ============================================================
-- 1. Organizational Identity
-- ============================================================

ALTER TABLE ezkey_tenant
  ADD COLUMN organization_name VARCHAR(255);
COMMENT ON COLUMN ezkey_tenant.organization_name
  IS 'Legal name of the organization (distinct from tenant_name)';

ALTER TABLE ezkey_tenant
  ADD COLUMN organization_domain VARCHAR(255);
COMMENT ON COLUMN ezkey_tenant.organization_domain
  IS 'Primary domain of the organization (e.g. acme.com)';

ALTER TABLE ezkey_tenant
  ADD COLUMN country_code VARCHAR(2);
COMMENT ON COLUMN ezkey_tenant.country_code
  IS 'ISO 3166-1 alpha-2 country code for legal jurisdiction';

ALTER TABLE ezkey_tenant
  ADD COLUMN timezone VARCHAR(50);
COMMENT ON COLUMN ezkey_tenant.timezone
  IS 'IANA timezone identifier (e.g. America/Montreal)';

-- ============================================================
-- 2. Operational Contact
-- ============================================================

ALTER TABLE ezkey_tenant
  ADD COLUMN primary_contact_name VARCHAR(255);
COMMENT ON COLUMN ezkey_tenant.primary_contact_name
  IS 'Name of the primary technical contact for this tenant';

ALTER TABLE ezkey_tenant
  ADD COLUMN primary_contact_email VARCHAR(255);
COMMENT ON COLUMN ezkey_tenant.primary_contact_email
  IS 'Email of the primary technical contact for incidents and notifications';

-- ============================================================
-- 3. Governance and Lifecycle
-- ============================================================

ALTER TABLE ezkey_tenant
  ADD COLUMN updated_at TIMESTAMPTZ;
COMMENT ON COLUMN ezkey_tenant.updated_at
  IS 'Timestamp of the last modification to this tenant record';

ALTER TABLE ezkey_tenant
  ADD COLUMN updated_by_admin_id INT
    REFERENCES ezkey_admin(admin_id);
COMMENT ON COLUMN ezkey_tenant.updated_by_admin_id
  IS 'Admin who last modified this tenant record (SOC 2 CC7.2)';

ALTER TABLE ezkey_tenant
  ADD COLUMN deactivated_at TIMESTAMPTZ;
COMMENT ON COLUMN ezkey_tenant.deactivated_at
  IS 'Timestamp when the tenant was deactivated (SOC 2 CC6.3)';

ALTER TABLE ezkey_tenant
  ADD COLUMN deactivated_by_admin_id INT
    REFERENCES ezkey_admin(admin_id);
COMMENT ON COLUMN ezkey_tenant.deactivated_by_admin_id
  IS 'Admin who deactivated this tenant (SOC 2 CC6.3)';

-- ============================================================
-- 4. Indexes
-- ============================================================

CREATE INDEX idx_tenant_organization_domain
  ON ezkey_tenant (organization_domain)
  WHERE organization_domain IS NOT NULL;

CREATE INDEX idx_tenant_country_code
  ON ezkey_tenant (country_code)
  WHERE country_code IS NOT NULL;

-- ============================================================
-- 5. Constraints
-- ============================================================

ALTER TABLE ezkey_tenant
  ADD CONSTRAINT chk_tenant_country_code
  CHECK (country_code ~ '^[A-Z]{2}$');

ALTER TABLE ezkey_tenant
  ADD CONSTRAINT chk_tenant_contact_email
  CHECK (primary_contact_email ~ '^[^@]+@[^@]+\.[^@]+$');
-- ============================================================================
-- Ezkey Migration V31: Enrollment Contact and Audit Columns (Phase 1 & 2)
-- ============================================================================
-- Description: Adds columns for normative (SOC 2), operational, and contact
--              purposes as per enrollment enhancement plan.
--
-- Phase 1 (Normative + Operational):
--   - verified_at: When enrollment transitioned to VERIFIED
--   - created_by_admin_id: Admin who created (null when via API key)
--   - last_used_at: Last successful authentication
--
-- Phase 2 (Contact, optional):
--   - contact_email: Optional end-user contact for incident response
--   - user_identifier: Optional app user reference (unique per integration lookup)
--
-- Author: Ezkey contributors
-- Date: 2026-02
-- ============================================================================

-- ============================================================================
-- STEP 1: Add Phase 1 Columns
-- ============================================================================

ALTER TABLE ezkey_enrollment
    ADD COLUMN verified_at TIMESTAMPTZ;

COMMENT ON COLUMN ezkey_enrollment.verified_at IS
'When the enrollment transitioned to VERIFIED status (device completed binding). Used for audit trail and lifecycle metrics. Set once when status becomes VERIFIED.';

ALTER TABLE ezkey_enrollment
    ADD COLUMN created_by_admin_id INT REFERENCES ezkey_admin(admin_id);

COMMENT ON COLUMN ezkey_enrollment.created_by_admin_id IS
'Admin who created this enrollment (SOC 2 CC6.1, CC7.2). Populated when created via Admin API; null when created via API key (M2M).';

ALTER TABLE ezkey_enrollment
    ADD COLUMN last_used_at TIMESTAMPTZ;

COMMENT ON COLUMN ezkey_enrollment.last_used_at IS
'When this enrollment was last used for successful authentication. Updated on each accepted auth attempt. Used for operational hygiene and stale enrollment detection.';

-- ============================================================================
-- STEP 2: Add Phase 2 Columns
-- ============================================================================

ALTER TABLE ezkey_enrollment
    ADD COLUMN contact_email VARCHAR(255);

COMMENT ON COLUMN ezkey_enrollment.contact_email IS
'Optional contact for the end-user (device owner). Used for incident response, revocation notices, support. Provided by integrating app at creation.';

ALTER TABLE ezkey_enrollment
    ADD COLUMN user_identifier VARCHAR(255);

COMMENT ON COLUMN ezkey_enrollment.user_identifier IS
'Optional reference to integrating app user (username, user_id). Unique per integration for lookup. Enables future auth attempt creation by userIdentifier.';

-- ============================================================================
-- STEP 3: Create Lookup Index for user_identifier
-- ============================================================================

CREATE INDEX idx_enrollment_integration_user_identifier
    ON ezkey_enrollment(integration_id, user_identifier)
    WHERE user_identifier IS NOT NULL;

COMMENT ON INDEX idx_enrollment_integration_user_identifier IS
'Non-unique index for lookup by (integration_id, user_identifier). Supports single- and multi-device.';

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- ============================================================================
-- Ezkey Migration V32: Add Audit Log Tenant Index
-- ============================================================================
-- Description: Adds a partial index on tenant_id for the audit log table to
--              support efficient tenant-scoped audit queries. Tenant Admins
--              query audit logs filtered by their tenant_id; this index
--              ensures those queries perform well even as the audit table
--              grows.
--
-- Context: Supports the audit log tenant visibility feature where Tenant
--          Admins see only audit entries belonging to their tenant. The
--          partial index excludes NULL tenant_id rows (system-level events)
--          since they are never returned to Tenant Admins.
--
-- Author: Ezkey contributors
-- Date: 2026-02-18
-- ============================================================================

-- Partial index on tenant_id for tenant-scoped audit queries.
-- Excludes NULL values since system-level events (tenant_id IS NULL)
-- are only visible to Global Admins who query without tenant filtering.
CREATE INDEX idx_audit_log_tenant
    ON ezkey_audit_log(tenant_id)
    WHERE tenant_id IS NOT NULL;

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- Tenant-scoped audit index created for efficient multi-tenant queries.
-- ============================================================================
