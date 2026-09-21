#!/usr/bin/env bash
# Fetch EXP1 admin-api logs around heartbeat incident declare failures.
set -euo pipefail

SSH_HOST="${LIGHTSAIL_SSH_HOST:-ezkey}"

ssh "${SSH_HOST}" bash -s <<'REMOTE'
set -euo pipefail

echo "=== Recent declare / incident / malformed / enum errors ==="
docker logs ezkey-exp-admin-api 2>&1 \
  | grep -iE 'declare|incident|malformed|HttpMessageNotReadable|PLANNED|rootCause|Cannot deserialize|enum|lifecycle/incidents|InvalidFormatException' \
  | tail -40 || true

echo ""
echo "=== Last 120 admin-api log lines ==="
docker logs ezkey-exp-admin-api 2>&1 | tail -120

echo ""
echo "=== WARN/ERROR since last hour (declare / deserialize / 400) ==="
docker logs ezkey-exp-admin-api --since 2h 2>&1 \
  | grep -iE 'WARN|ERROR' \
  | grep -iE 'declare|incident|HttpMessage|deserialize|InvalidFormat|PLANNED|malformed|lifecycle/incidents|ServletInvocableHandlerMethod' \
  | tail -30 || true

echo ""
echo "=== All WARN/ERROR last 2h (tail 40) ==="
docker logs ezkey-exp-admin-api --since 2h 2>&1 \
  | grep -iE 'WARN|ERROR' \
  | tail -40 || true

echo ""
echo "=== Incident row status ==="
PSQL='docker exec ezkey-exp-postgres psql -U postgres -d ezkey_db -P pager=off'
$PSQL -c "
SELECT incident_id, status, root_cause, LEFT(justification, 80) AS justification_preview,
       declared_at, recovered_at, anchor_checkpoint_id
FROM ezkey_audit_chain_incident
ORDER BY incident_id DESC
LIMIT 5;
"

echo ""
echo "=== Caddy access (declare / incidents POST) ==="
docker logs ezkey-exp-caddy 2>&1 | grep 'lifecycle/incidents' | tail -15 || true

echo ""
echo "=== Live api-docs enum snippet (via local curl) ==="
curl -sf http://127.0.0.1:9080/api-docs 2>/dev/null | tr ',' '\n' | grep -E 'PLANNED_SYSTEM_UPGRADE|ADMIN_API_DOWN|SCHEDULER_FAILURE|UNKNOWN' | head -12 || echo "(api-docs curl failed)"

echo ""
echo "=== Container image (admin-api) ==="
docker inspect ezkey-exp-admin-api --format '{{.Config.Image}} started {{.State.StartedAt}}'
REMOTE
