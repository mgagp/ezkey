-- ============================================================================
-- Ezkey Migration V22: Integrity async job slot (operator long-running jobs)
-- ============================================================================
-- Description: Single global slot for Admin UI Integrity long ops (chain/HMAC range
--              verify, run validation). Distinct from ezkey_scheduled_job_last_run
--              (last-run dashboard signal only — never in-flight state).
--
-- Concurrency: at most one RUNNING row (partial unique index on slot_key).
-- ============================================================================

CREATE TABLE IF NOT EXISTS ezkey_integrity_async_job (
    job_id                   UUID          PRIMARY KEY,
    slot_key                 VARCHAR(32)   NOT NULL DEFAULT 'GLOBAL',
    job_type                 VARCHAR(64)   NOT NULL,
    status                   VARCHAR(32)   NOT NULL,
    started_by_admin_id      INT           NOT NULL,
    started_by_username      VARCHAR(255)  NOT NULL,
    started_at               TIMESTAMPTZ   NOT NULL,
    heartbeat_at             TIMESTAMPTZ   NOT NULL,
    finished_at              TIMESTAMPTZ,
    scope_from               TIMESTAMPTZ,
    scope_to                 TIMESTAMPTZ,
    raise_alert              BOOLEAN,
    result_summary           VARCHAR(1024),
    error_summary            VARCHAR(512),
    result_intact            BOOLEAN,
    result_alert_id          BIGINT,
    abandoned_at             TIMESTAMPTZ,
    abandoned_by_admin_id    INT,
    created_at               TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_integrity_async_job_started_by
        FOREIGN KEY (started_by_admin_id)
        REFERENCES ezkey_admin (admin_id),

    CONSTRAINT fk_integrity_async_job_abandoned_by
        FOREIGN KEY (abandoned_by_admin_id)
        REFERENCES ezkey_admin (admin_id),

    CONSTRAINT chk_integrity_async_job_type
        CHECK (job_type IN (
            'VERIFY_CHAIN_RANGE',
            'VERIFY_ENTRY_HMAC_RANGE',
            'RUN_VALIDATION'
        )),

    CONSTRAINT chk_integrity_async_job_status
        CHECK (status IN (
            'RUNNING',
            'SUCCEEDED',
            'FAILED',
            'CANCELLED',
            'EXPIRED',
            'INTERRUPTED'
        ))
);

COMMENT ON TABLE ezkey_integrity_async_job IS
'Operator Integrity async job slot / recent outcomes. One RUNNING row for the whole '
'deployment (partial unique on slot_key). Not the scheduled-job last-run registry.';

CREATE UNIQUE INDEX IF NOT EXISTS uq_integrity_async_job_one_running
    ON ezkey_integrity_async_job (slot_key)
    WHERE status = 'RUNNING';

CREATE INDEX IF NOT EXISTS idx_integrity_async_job_started_at
    ON ezkey_integrity_async_job (started_at DESC);
