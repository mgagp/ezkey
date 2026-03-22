-- ============================================================================
-- Ezkey Migration V25: Create Partition Management Function with SECURITY DEFINER
-- ============================================================================
-- Description: Creates a PostgreSQL function with SECURITY DEFINER to enable
--              partition creation without granting DDL privileges to application role.
-- 
-- Context: In production, database roles are separated:
--          - EZKEY_owner: DDL privileges (used by Flyway)
--          - EZKEY_app: DML privileges only (used by application)
--          
--          This function allows the application to create partitions safely
--          without requiring CREATE TABLE privilege on the application role.
--
-- Security: Function uses SECURITY DEFINER to execute with owner privileges,
--           while application role only needs EXECUTE privilege.
--
-- Author: Ezkey contributors
-- Date: 2025-01-XX
-- ============================================================================

-- ============================================================================
-- STEP 1: Create Partition Management Function
-- ============================================================================

-- Function to create monthly partitions for partitioned tables.
-- For ezkey_audit_log: creates monthly partition and three LIST (api_name) sub-partitions.
-- For ezkey_auth_attempt: creates a single monthly partition.
-- Uses SECURITY DEFINER to execute with owner privileges.
CREATE OR REPLACE FUNCTION create_monthly_partition(
    p_table_name TEXT,
    p_partition_name TEXT,
    p_start_date TIMESTAMPTZ,
    p_end_date TIMESTAMPTZ
) RETURNS BOOLEAN
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF p_table_name NOT IN ('ezkey_auth_attempt', 'ezkey_audit_log') THEN
        RAISE EXCEPTION 'Invalid table name: %. Only ezkey_auth_attempt and ezkey_audit_log are allowed.', p_table_name;
    END IF;

    IF p_partition_name !~ '^ezkey_(auth_attempt|audit_log)_\d{4}_\d{2}$' THEN
        RAISE EXCEPTION 'Invalid partition name format: %. Expected format: ezkey_<table>_YYYY_MM', p_partition_name;
    END IF;

    -- relkind 'r' = regular table (ezkey_auth_attempt monthly partition), 'p' = partitioned table (ezkey_audit_log monthly parent)
    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = p_partition_name AND relkind IN ('r', 'p')) THEN
        RAISE NOTICE 'Partition % already exists, skipping creation', p_partition_name;
        RETURN false;
    END IF;

    IF p_table_name = 'ezkey_audit_log' THEN
        -- Composite: monthly partition then LIST (api_name) sub-partitions
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF ezkey_audit_log FOR VALUES FROM (%L) TO (%L) PARTITION BY LIST (api_name)',
            p_partition_name,
            p_start_date,
            p_end_date
        );
        EXECUTE format('CREATE TABLE %I PARTITION OF %I FOR VALUES IN (''ADMIN_API'')', p_partition_name || '_admin', p_partition_name);
        EXECUTE format('CREATE TABLE %I PARTITION OF %I FOR VALUES IN (''AUTH_API'')', p_partition_name || '_auth', p_partition_name);
        EXECUTE format('CREATE TABLE %I PARTITION OF %I FOR VALUES IN (''INTEGRATION_API'')', p_partition_name || '_integration', p_partition_name);
        RAISE NOTICE 'Created partition % and sub-partitions _admin, _auth, _integration for table ezkey_audit_log', p_partition_name;
    ELSE
        -- ezkey_auth_attempt: single monthly partition
        EXECUTE format(
            'CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
            p_partition_name,
            p_table_name,
            p_start_date,
            p_end_date
        );
        RAISE NOTICE 'Created partition: % for table: %', p_partition_name, p_table_name;
    END IF;
    RETURN true;
END;
$$;

-- ============================================================================
-- STEP 2: Set Function Ownership and Permissions
-- ============================================================================

-- Function should be owned by database owner role (not application role)
-- This ensures function executes with owner privileges via SECURITY DEFINER
-- Note: In production, replace 'postgres' with actual owner role name (e.g., EZKEY_owner)
-- ALTER FUNCTION create_monthly_partition OWNER TO EZKEY_owner;

-- Grant EXECUTE privilege to application role
-- Application role only needs EXECUTE, not CREATE TABLE privilege
-- Note: Replace EZKEY_app with actual application role name
-- GRANT EXECUTE ON FUNCTION create_monthly_partition(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ) TO EZKEY_app;

-- Revoke EXECUTE from public (security best practice)
REVOKE EXECUTE ON FUNCTION create_monthly_partition(TEXT, TEXT, TIMESTAMPTZ, TIMESTAMPTZ) FROM PUBLIC;

-- ============================================================================
-- STEP 3: Add Documentation Comments
-- ============================================================================

COMMENT ON FUNCTION create_monthly_partition IS 
'Creates monthly partition for ezkey_auth_attempt (single partition) or ezkey_audit_log (monthly partition plus LIST(api_name) sub-partitions _admin, _auth, _integration). '
'Executes with owner privileges via SECURITY DEFINER. Idempotent - returns BOOLEAN: true if created, false if already existed.';

-- ============================================================================
-- Migration Complete
-- ============================================================================
-- Partition management function created successfully.
-- 
-- Security Notes:
-- - Function uses SECURITY DEFINER to execute with owner privileges
-- - Application role only needs EXECUTE privilege (not CREATE TABLE)
-- - Function validates inputs to prevent SQL injection
-- - Function is idempotent (safe to call multiple times)
--
-- Production Setup:
-- 1. Ensure function is owned by owner role (EZKEY_owner)
-- 2. Grant EXECUTE to application role (EZKEY_app)
-- 3. Revoke EXECUTE from PUBLIC (already done)
--
-- Usage:
-- SELECT create_monthly_partition(
--     'ezkey_auth_attempt',
--     'ezkey_auth_attempt_2025_02',
--     '2025-02-01 00:00:00+00'::TIMESTAMPTZ,
--     '2025-03-01 00:00:00+00'::TIMESTAMPTZ
-- );
-- Returns: true if partition was created, false if it already existed
-- ============================================================================

