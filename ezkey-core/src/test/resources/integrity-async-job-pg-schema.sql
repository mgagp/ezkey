-- Minimal V22+V25 schema for Integrity cut 3 Postgres ITs (no FK to ezkey_admin).
CREATE TABLE IF NOT EXISTS ezkey_integrity_async_job (
    job_id                   UUID          PRIMARY KEY,
    slot_key                 VARCHAR(32)   NOT NULL DEFAULT 'GLOBAL',
    job_type                 VARCHAR(64)   NOT NULL,
    status                   VARCHAR(32)   NOT NULL,
    started_by_admin_id      INT,
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
    "trigger"                VARCHAR(16)   NOT NULL DEFAULT 'MANUAL',
    chain_status             VARCHAR(64),
    total_checkpoints        INT,
    valid_checkpoints        INT,
    invalid_checkpoints      INT,
    archived_checkpoints     INT,
    undeclared_gaps          INT,
    entry_hmac_violations    INT,

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
        )),
    CONSTRAINT chk_integrity_async_job_trigger
        CHECK ("trigger" IN ('MANUAL', 'AUTO', 'NIGHTLY')),
    CONSTRAINT chk_integrity_async_job_nightly_terminal
        CHECK ("trigger" <> 'NIGHTLY' OR status IN ('SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_integrity_async_job_nightly_admin
        CHECK (("trigger" = 'NIGHTLY') = (started_by_admin_id IS NULL)),
    CONSTRAINT chk_integrity_async_job_chain_status
        CHECK (
            chain_status IS NULL
            OR chain_status IN (
                'OK',
                'UNDECLARED_GAP_DETECTED',
                'CHAIN_INTEGRITY_VIOLATION_DETECTED',
                'NOT_VERIFIED'
            )
        )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_integrity_async_job_one_running
    ON ezkey_integrity_async_job (slot_key)
    WHERE status = 'RUNNING';

CREATE TABLE IF NOT EXISTS ezkey_shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ  NOT NULL,
    locked_at  TIMESTAMPTZ  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
