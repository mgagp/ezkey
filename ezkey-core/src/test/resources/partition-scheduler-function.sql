-- copy of V4 function; keep in sync
-- Source: V4__partitioning_auth_audit_and_function.sql
-- PartitionSchedulerPostgresTest re-applies this file before each test.
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

    IF EXISTS (SELECT 1 FROM pg_class WHERE relname = p_partition_name AND relkind IN ('r', 'p')) THEN
        RAISE NOTICE 'Partition % already exists, skipping creation', p_partition_name;
        RETURN false;
    END IF;

    IF p_table_name = 'ezkey_audit_log' THEN
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
