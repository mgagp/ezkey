-- Minimal partitioned parents for PartitionSchedulerPostgresTest.
-- Only 2026-11 exists, so an insert in a later month fails until the scheduler runs.
-- The clock in that test is fixed to 2026-11-13T15:00:00Z.

CREATE TABLE ezkey_auth_attempt (
    auth_attempt_id       BIGINT GENERATED ALWAYS AS IDENTITY,
    enrollment_id         INT          NOT NULL,
    auth_attempt_status   VARCHAR(32)  NOT NULL,
    created_at            TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (auth_attempt_id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE ezkey_auth_attempt_2026_11
    PARTITION OF ezkey_auth_attempt
    FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00');

CREATE TABLE ezkey_audit_log (
    audit_log_id   BIGINT GENERATED ALWAYS AS IDENTITY,
    event_type     VARCHAR(64) NOT NULL,
    api_name       VARCHAR(32) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL
) PARTITION BY RANGE (created_at);

CREATE TABLE ezkey_audit_log_2026_11
    PARTITION OF ezkey_audit_log
    FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00')
    PARTITION BY LIST (api_name);

CREATE TABLE ezkey_audit_log_2026_11_admin
    PARTITION OF ezkey_audit_log_2026_11 FOR VALUES IN ('ADMIN_API');
CREATE TABLE ezkey_audit_log_2026_11_auth
    PARTITION OF ezkey_audit_log_2026_11 FOR VALUES IN ('AUTH_API');
CREATE TABLE ezkey_audit_log_2026_11_integration
    PARTITION OF ezkey_audit_log_2026_11 FOR VALUES IN ('INTEGRATION_API');

CREATE TABLE ezkey_scheduled_job_last_run (
    job_key              VARCHAR(64)   PRIMARY KEY,
    last_execution_at    TIMESTAMPTZ,
    last_status          VARCHAR(16)   NOT NULL,
    last_run_scope       VARCHAR(512),
    last_error_summary   VARCHAR(512),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_scheduled_job_last_status
        CHECK (last_status IN ('SUCCESS', 'FAILED', 'NEVER_RUN'))
);

INSERT INTO ezkey_scheduled_job_last_run (job_key, last_status)
VALUES ('PARTITION_CREATION', 'NEVER_RUN');

CREATE TABLE ezkey_alert (
    alert_id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    alert_type            VARCHAR(64)   NOT NULL,
    severity              VARCHAR(16)   NOT NULL,
    status                VARCHAR(16)   NOT NULL,
    dedupe_key            VARCHAR(256)  NOT NULL,
    payload               TEXT,
    occurrence_count      INT           NOT NULL DEFAULT 1,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at          TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at           TIMESTAMPTZ,
    resolved_by_admin_id  INT,
    resolution_reason     VARCHAR(64),
    CONSTRAINT chk_alert_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_alert_status CHECK (status IN ('OPEN', 'RESOLVED'))
);

CREATE UNIQUE INDEX uq_alert_dedupe_key_open
    ON ezkey_alert (dedupe_key)
    WHERE status = 'OPEN';
