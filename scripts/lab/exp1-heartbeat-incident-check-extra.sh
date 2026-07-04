#!/usr/bin/env bash
set -euo pipefail
SSH_HOST="${LIGHTSAIL_SSH_HOST:-ezkey}"
ANCHOR="${1:-18425}"
ssh "${SSH_HOST}" bash -s "${ANCHOR}" <<'REMOTE'
set -euo pipefail
ANCHOR="$1"
PSQL='docker exec ezkey-exp-postgres psql -U postgres -d ezkey_db -P pager=off'

echo "=== Recent heartbeat alerts (any status) ==="
$PSQL -c "
SELECT alert_id, alert_type, status, created_at, resolved_at, resolution_reason, payload
FROM ezkey_alert
WHERE alert_type = 'AUDIT_CHAIN_HEARTBEAT_STALE'
ORDER BY alert_id DESC
LIMIT 5;
"

echo ""
echo "=== Audit chain incidents (latest) ==="
$PSQL -c "
SELECT incident_id, status, anchor_checkpoint_id, stale_since, degraded_since, recovered_at, created_at
FROM ezkey_audit_chain_incident
ORDER BY incident_id DESC
LIMIT 5;
"
REMOTE
