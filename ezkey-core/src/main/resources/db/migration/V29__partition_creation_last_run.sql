-- ============================================================================
-- Ezkey Migration V29: Partition creation last-run row
-- ============================================================================
-- Description: Seeds the last-run registry for monthly partition creation.
--              PartitionSchedulerService records SUCCESS or FAILED here and
--              raises PARTITION_CREATION_FAILED when a run misses the horizon.
--
-- Manual rollback (no Flyway undo): DELETE FROM ezkey_scheduled_job_last_run
--              WHERE job_key = 'PARTITION_CREATION'; delete version 29 from
--              flyway_schema_history.
--
-- Author: Ezkey contributors
-- Date: 2026-11-13
-- ============================================================================

INSERT INTO ezkey_scheduled_job_last_run (job_key, last_status)
VALUES ('PARTITION_CREATION', 'NEVER_RUN')
ON CONFLICT (job_key) DO NOTHING;
