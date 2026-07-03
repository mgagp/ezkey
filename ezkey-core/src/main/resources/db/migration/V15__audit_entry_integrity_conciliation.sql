-- ============================================================================
-- Ezkey Migration V15: Per-entry audit integrity conciliation registry (B2.6)
-- ============================================================================
-- Description: Operational index for operator-acknowledged per-entry HMAC violations.
--              Distinct from ezkey_audit_log mutation — rows stay HMAC-invalid; conciliation
--              records who acknowledged the known invalid state under audited justification.
--
-- Partition note: ezkey_audit_log is RANGE (created_at) + LIST (api_name) sub-partitioned (V4).
-- A PostgreSQL PK/FK target would require (audit_log_id, created_at, api_name) — not the same
-- shape as ezkey_auth_attempt (RANGE only). No PK added here; conciliation uses snapshot without FK.
--
-- Reference model: conciliation stores (audit_log_id, audit_log_created_at) as an immutable
-- snapshot at reconcile time. No FK to ezkey_audit_log — sealed/purged partitions drop audit
-- rows while this registry must persist for SOC 2 operator narrative (see docs).
-- ============================================================================

CREATE TABLE IF NOT EXISTS ezkey_audit_entry_integrity_conciliation (
    conciliation_id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    audit_log_id                 BIGINT        NOT NULL,
    audit_log_created_at         TIMESTAMPTZ   NOT NULL,
    violation_reason             VARCHAR(32)   NOT NULL,
    observed_state_fingerprint   VARCHAR(64)   NOT NULL,
    category                     VARCHAR(64)   NOT NULL,
    justification                VARCHAR(500)  NOT NULL,
    external_ticket_reference    VARCHAR(128),
    source_alert_id              BIGINT        NOT NULL,
    conciliated_by_admin_id      INT           NOT NULL,
    conciliated_at               TIMESTAMPTZ   NOT NULL,
    status                       VARCHAR(16)   NOT NULL,
    created_at                   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                   TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_entry_integrity_conciliation_source_alert
        FOREIGN KEY (source_alert_id)
        REFERENCES ezkey_alert (alert_id),

    CONSTRAINT fk_entry_integrity_conciliation_admin
        FOREIGN KEY (conciliated_by_admin_id)
        REFERENCES ezkey_admin (admin_id),

    CONSTRAINT chk_entry_integrity_conciliation_status CHECK (
        status IN ('ACTIVE', 'SUPERSEDED')
    ),

    CONSTRAINT chk_entry_integrity_conciliation_reason CHECK (
        violation_reason IN ('HMAC_MISMATCH', 'MISSING_ENTRY_HMAC')
    )
);

COMMENT ON TABLE ezkey_audit_entry_integrity_conciliation IS
'Operator conciliation registry for per-entry HMAC integrity violations. One ACTIVE row per '
'audit_log_id records an acknowledged invalid signature state; does not re-sign entry_hmac. '
'Survives audit partition purge — composite entry snapshot columns are immutable; no FK to '
'ezkey_audit_log because lifecycle-eligible rows are physically deleted after ARCHIVE_SEAL.';

COMMENT ON COLUMN ezkey_audit_entry_integrity_conciliation.audit_log_id IS
'Target audit log identity (part 1). Snapshot at conciliation time; not an FK — row may be purged.';

COMMENT ON COLUMN ezkey_audit_entry_integrity_conciliation.audit_log_created_at IS
'Target audit log created_at (part 2). Composite with audit_log_id matches partitioned entry '
'identity (audit_log_id, created_at). Immutable snapshot; survives audit partition purge.';

COMMENT ON COLUMN ezkey_audit_entry_integrity_conciliation.observed_state_fingerprint IS
'SHA-256 hex of canonical entry form at conciliation time (excludes entry_hmac). Used to skip '
're-alerting while fingerprint matches; mismatch signals re-tamper.';

CREATE UNIQUE INDEX IF NOT EXISTS uq_entry_integrity_conciliation_active_audit_log
    ON ezkey_audit_entry_integrity_conciliation (audit_log_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_entry_integrity_conciliation_audit_entry_status
    ON ezkey_audit_entry_integrity_conciliation (audit_log_id, audit_log_created_at, status);

CREATE INDEX IF NOT EXISTS idx_entry_integrity_conciliation_source_alert
    ON ezkey_audit_entry_integrity_conciliation (source_alert_id);
