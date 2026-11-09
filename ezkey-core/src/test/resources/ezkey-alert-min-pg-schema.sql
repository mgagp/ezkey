-- Alert table for AuthAttemptAlertPersistedPostgresTest. Mirrors V11 without comments.
CREATE TABLE IF NOT EXISTS ezkey_alert (
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

CREATE UNIQUE INDEX IF NOT EXISTS uq_alert_dedupe_key_open
    ON ezkey_alert (dedupe_key)
    WHERE status = 'OPEN';
