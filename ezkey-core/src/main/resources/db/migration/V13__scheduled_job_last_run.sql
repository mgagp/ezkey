-- ============================================================================
-- Ezkey Migration V13: Scheduled job last-run registry
-- ============================================================================
-- Description: One row per logical scheduled job for operator visibility
--              (dashboard widgets in I-2026-0007). Populated by checkpoint
--              scheduler and nightly integrity validation (Wave B B1).
--
-- Author: Ezkey contributors
-- Date:   2026-06
-- ============================================================================

CREATE TABLE IF NOT EXISTS ezkey_scheduled_job_last_run (
    job_key              VARCHAR(64)   PRIMARY KEY,
    last_execution_at    TIMESTAMPTZ,
    last_status          VARCHAR(16)   NOT NULL,
    last_run_scope       VARCHAR(512),
    last_error_summary   VARCHAR(512),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_scheduled_job_last_status
        CHECK (last_status IN ('SUCCESS', 'FAILED', 'NEVER_RUN'))
);

COMMENT ON TABLE ezkey_scheduled_job_last_run IS
'Last-run metadata for Admin API scheduled jobs (checkpoint scheduler, nightly '
'integrity validation, re-encryption). Batch infrastructure failure records FAILED '
'here without raising integrity rupture alerts (C9).';

INSERT INTO ezkey_scheduled_job_last_run (job_key, last_status)
VALUES
    ('AUDIT_CHAIN_CHECKPOINT', 'NEVER_RUN'),
    ('NIGHTLY_INTEGRITY_VALIDATION', 'NEVER_RUN'),
    ('REENCRYPTION', 'NEVER_RUN')
ON CONFLICT (job_key) DO NOTHING;
