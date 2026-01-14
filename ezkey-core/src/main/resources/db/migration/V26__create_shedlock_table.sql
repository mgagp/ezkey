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
