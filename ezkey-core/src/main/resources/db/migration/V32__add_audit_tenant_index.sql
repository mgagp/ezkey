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
