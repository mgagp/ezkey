-- ============================================================================
-- Ezkey Migration V12: Audit chain operational incidents (heartbeat partial outage)
-- ============================================================================
-- Description: Stores operator-declared explanations for periods where Auth API /
--              Integration API observed Admin API checkpoint heartbeat degradation.
--              Cryptographic checkpoint rows remain REGULAR / GAP_DECLARATION only;
--              this table is operational SOC 2 narrative, not chain linkage.
-- ============================================================================

CREATE TABLE IF NOT EXISTS ezkey_audit_chain_incident (
    incident_id BIGSERIAL PRIMARY KEY,
    status VARCHAR(40) NOT NULL,
    anchor_checkpoint_id BIGINT,
    stale_since TIMESTAMPTZ NOT NULL,
    degraded_since TIMESTAMPTZ,
    recovered_at TIMESTAMPTZ,
    justification TEXT,
    root_cause VARCHAR(64),
    declared_at TIMESTAMPTZ,
    declared_by_admin_id INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_chain_incident_anchor_checkpoint
        FOREIGN KEY (anchor_checkpoint_id)
        REFERENCES ezkey_audit_chain_checkpoint (checkpoint_id),

    CONSTRAINT chk_audit_chain_incident_status CHECK (
        status IN ('IN_PROGRESS', 'RECOVERED_PENDING_DECLARATION', 'CLOSED')
    )
);

COMMENT ON TABLE ezkey_audit_chain_incident IS
'Operational incident record for audit-chain heartbeat degradation (Admin API scheduler missed '
'expected checkpoints while peripheral APIs could still write audit rows). Distinct from '
'GAP_DECLARATION checkpoints (no-activity downtime).';

COMMENT ON COLUMN ezkey_audit_chain_incident.status IS
'IN_PROGRESS: heartbeat stale/degraded outage ongoing; RECOVERED_PENDING_DECLARATION: heartbeat '
'restored, operator explanation required; CLOSED: declared by Global Admin.';

CREATE INDEX IF NOT EXISTS idx_audit_chain_incident_status_created
    ON ezkey_audit_chain_incident (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_chain_incident_anchor_checkpoint
    ON ezkey_audit_chain_incident (anchor_checkpoint_id);
