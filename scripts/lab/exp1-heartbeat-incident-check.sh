#!/usr/bin/env bash
# Correlate EXP1 heartbeat alert with checkpoint anchor and container restart times.
set -euo pipefail

SSH_HOST="${LIGHTSAIL_SSH_HOST:-ezkey}"
ANCHOR="${1:-18425}"

ssh "${SSH_HOST}" bash -s "${ANCHOR}" <<'REMOTE'
set -euo pipefail
ANCHOR="$1"
PSQL='docker exec ezkey-exp-postgres psql -U postgres -d ezkey_db -P pager=off'

echo "=== OPEN alerts (heartbeat / gap) ==="
$PSQL -c "
SELECT alert_id, alert_type, status, severity, created_at, last_seen_at, payload
FROM ezkey_alert
WHERE status = 'OPEN'
ORDER BY alert_id DESC
LIMIT 5;
"

echo ""
echo "=== Checkpoint anchor #${ANCHOR} and neighbors ==="
$PSQL -c "
SELECT checkpoint_id, checkpoint_type, window_start, window_end, created_at
FROM ezkey_audit_chain_checkpoint
WHERE checkpoint_id BETWEEN $((ANCHOR - 3)) AND $((ANCHOR + 3))
ORDER BY checkpoint_id;
"

echo ""
echo "=== Latest checkpoints (tail) ==="
$PSQL -c "
SELECT checkpoint_id, checkpoint_type, window_start, window_end, created_at
FROM ezkey_audit_chain_checkpoint
ORDER BY checkpoint_id DESC
LIMIT 8;
"

echo ""
echo "=== Container start times ==="
for c in ezkey-exp-admin-api ezkey-exp-auth-api ezkey-exp-integration-api ezkey-exp-migration; do
  started=$(docker inspect "$c" --format '{{.State.StartedAt}}' 2>/dev/null || echo "n/a")
  echo "$c: $started"
done

echo ""
echo "=== Admin API heartbeat phase transitions (grep, last 30 lines) ==="
docker logs ezkey-exp-admin-api 2>&1 | grep -i 'heartbeat phase transition' | tail -15 || true
REMOTE
