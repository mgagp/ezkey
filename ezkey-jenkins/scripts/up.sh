#!/usr/bin/env bash
# Start the M0 Jenkins LTS container (from ezkey-jenkins/).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

export EZKEY_JENKINS_HTTP_PORT="${EZKEY_JENKINS_HTTP_PORT:-28080}"
export EZKEY_JENKINS_AGENT_PORT="${EZKEY_JENKINS_AGENT_PORT:-50000}"

echo "Starting ezkey-jenkins on http://localhost:${EZKEY_JENKINS_HTTP_PORT} ..."
docker compose up -d

echo "Waiting for Jenkins healthcheck ..."
for _ in $(seq 1 60); do
  status="$(docker inspect --format='{{.State.Health.Status}}' ezkey-jenkins 2>/dev/null || echo starting)"
  if [[ "$status" == "healthy" ]]; then
    echo "Jenkins is healthy."
    echo "UI: http://localhost:${EZKEY_JENKINS_HTTP_PORT}/"
    echo "Job: http://localhost:${EZKEY_JENKINS_HTTP_PORT}/job/m0-ls/"
    exit 0
  fi
  sleep 2
done

echo "Timed out waiting for Jenkins health. Recent logs:" >&2
docker compose logs --tail=80 jenkins >&2 || true
exit 1
