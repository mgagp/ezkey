-- ============================================================================
-- Ezkey Migration V11: Minimal alert subsystem
-- ============================================================================
-- Description: Creates the ezkey_alert table — a dedicated, minimal
--              operator-facing alert surface owned by Ezkey itself
--              (eat-your-own-dog-food, no external alerting dependency).
--
-- First producer:  AuditChainScheduler.detectPreLookbackGap()
-- First consumer:  Admin UI dashboard alert widget + new /alerts page
--
-- Lifecycle:  OPEN → RESOLVED (no further states in this slice).
-- Dedupe:     "<ALERT_TYPE>:<discriminator>" stored in dedupe_key. A partial
--             unique index enforces a single OPEN row per dedupe_key; raise
--             of an existing OPEN alert touches last_seen_at and increments
--             occurrence_count instead of inserting a new row.
--
-- Lightsail-safe additive migration: pure DDL, no data migration. Idempotent
-- via IF NOT EXISTS on table and indexes.
--
-- Author: Ezkey contributors
-- Date:   2026-04
-- ============================================================================

-- ============================================================================
-- STEP 1: Create the ezkey_alert table
-- ============================================================================

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

    CONSTRAINT chk_alert_severity
        CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    CONSTRAINT chk_alert_status
        CHECK (status IN ('OPEN', 'RESOLVED'))
);

COMMENT ON TABLE ezkey_alert IS
'Operator-facing alerts raised by internal Ezkey producers (e.g. audit chain '
'gap detector). Minimal lifecycle: OPEN → RESOLVED. Producers use raise-or-touch '
'semantics keyed on dedupe_key; consumers are the dashboard widget and the '
'/alerts admin page. Notification channels (email, webhook, etc.) are out of '
'scope for this iteration.';

COMMENT ON COLUMN ezkey_alert.alert_type IS
'Alert kind. Validated at the JPA boundary by the org.ezkey.alert.domain.AlertType '
'enum. Stored as VARCHAR (not Postgres ENUM) to allow forward-compatible '
'introduction of new alert types without DB migrations.';

COMMENT ON COLUMN ezkey_alert.dedupe_key IS
'Producer-defined deduplication key, conventionally "<ALERT_TYPE>:<discriminator>". '
'A partial unique index enforces a single OPEN row per dedupe_key.';

COMMENT ON COLUMN ezkey_alert.payload IS
'JSON payload with alert-type-specific structured data (gap boundaries, anchor '
'checkpoint id, etc.). Stored as TEXT for parity with audit_log.event_details; '
'shape is owned by the producer and validated at the application layer.';

COMMENT ON COLUMN ezkey_alert.occurrence_count IS
'Number of times the producer has raised this alert while it was OPEN. '
'Incremented on every raise-or-touch of an existing OPEN row.';

COMMENT ON COLUMN ezkey_alert.resolution_reason IS
'Why the alert transitioned to RESOLVED (e.g. GAP_DECLARED for the audit-chain '
'gap producer, MANUAL for an admin-triggered dismissal). NULL while OPEN.';

COMMENT ON COLUMN ezkey_alert.resolved_by_admin_id IS
'Admin who resolved the alert. NULL when the resolution was system-driven '
'(e.g. auto-resolve from a GAP_DECLARATION checkpoint without admin context).';

-- ============================================================================
-- STEP 2: Indexes
-- ============================================================================

-- Enforces "one OPEN alert per dedupe_key" at the database level. Resolved
-- rows are excluded so the same dedupe_key may legitimately recur over time.
CREATE UNIQUE INDEX IF NOT EXISTS uq_alert_dedupe_key_open
    ON ezkey_alert (dedupe_key)
    WHERE status = 'OPEN';

-- Powers the dashboard "recent open alerts" widget query.
CREATE INDEX IF NOT EXISTS idx_alert_status_created_at
    ON ezkey_alert (status, created_at DESC);

-- Powers the /alerts admin page typical filter (alert_type + status + recency).
CREATE INDEX IF NOT EXISTS idx_alert_type_status_created_at
    ON ezkey_alert (alert_type, status, created_at DESC);

-- ============================================================================
-- Migration Complete
-- ============================================================================
