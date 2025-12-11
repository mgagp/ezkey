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

-- Function to create monthly partitions for partitioned tables
-- Uses SECURITY DEFINER to execute with owner privileges
CREATE OR REPLACE FUNCTION create_monthly_partition(
    p_table_name TEXT,
    p_partition_name TEXT,
    p_start_date TIMESTAMPTZ,
    p_end_date TIMESTAMPTZ
) RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER  -- Execute with owner privileges (not caller privileges)
SET search_path = public  -- Prevent search_path injection attacks
AS $$
BEGIN
    -- Validate table name (whitelist to prevent injection)
    -- Only allow creation of partitions for known partitioned tables
    IF p_table_name NOT IN ('ezkey_auth_attempt', 'ezkey_audit_log') THEN
        RAISE EXCEPTION 'Invalid table name: %. Only ezkey_auth_attempt and ezkey_audit_log are allowed.', p_table_name;
    END IF;
    
    -- Validate partition name format (security: prevent injection)
    IF p_partition_name !~ '^ezkey_(auth_attempt|audit_log)_\d{4}_\d{2}$' THEN
        RAISE EXCEPTION 'Invalid partition name format: %. Expected format: ezkey_<table>_YYYY_MM', p_partition_name;
    END IF;
    
    -- Check if partition already exists (idempotent operation)
    IF EXISTS (
        SELECT 1 FROM pg_class 
        WHERE relname = p_partition_name AND relkind = 'r'
    ) THEN
        RAISE NOTICE 'Partition % already exists, skipping creation', p_partition_name;
        RETURN;
    END IF;
    
    -- Create partition using dynamic SQL
    -- Note: format() with %I and %L prevents SQL injection
    EXECUTE format(
        'CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
        p_partition_name,
        p_table_name,
        p_start_date,
        p_end_date
    );
    
    RAISE NOTICE 'Created partition: % for table: %', p_partition_name, p_table_name;
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
'Creates monthly partition for partitioned tables (ezkey_auth_attempt, ezkey_audit_log). '
'Executes with owner privileges via SECURITY DEFINER, allowing application role to create '
'partitions without requiring CREATE TABLE privilege. This maintains security best practices '
'by separating DDL privileges (owner role) from DML privileges (application role). '
'Function is idempotent - safely skips creation if partition already exists.';

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
-- ============================================================================

