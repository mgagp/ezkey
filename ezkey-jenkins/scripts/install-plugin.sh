#!/usr/bin/env bash
# Copy ezkey-lab-hello.hpi into the running M0 Jenkins container and restart.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
HPI="${ROOT}/ezkey-lab-hello/target/ezkey-lab-hello.hpi"
CONTAINER="${EZKEY_JENKINS_CONTAINER:-ezkey-jenkins}"
PORT="${EZKEY_JENKINS_HTTP_PORT:-28080}"
BASE="http://127.0.0.1:${PORT}"

if [[ ! -f "${HPI}" ]]; then
  echo "Missing ${HPI}. Run ./scripts/build-plugin.sh first." >&2
  exit 1
fi

if ! docker inspect "${CONTAINER}" >/dev/null 2>&1; then
  echo "Container ${CONTAINER} not found. Run ./scripts/up.sh first." >&2
  exit 1
fi

echo "Installing ${HPI} into ${CONTAINER} ..."
docker cp "${HPI}" "${CONTAINER}:/var/jenkins_home/plugins/ezkey-lab-hello.hpi"
# Ensure detached marker is absent so Jenkins loads the .hpi on boot.
docker exec "${CONTAINER}" rm -f /var/jenkins_home/plugins/ezkey-lab-hello.jpi.disabled \
  /var/jenkins_home/plugins/ezkey-lab-hello.hpi.disabled 2>/dev/null || true

echo "Restarting ${CONTAINER} ..."
docker restart "${CONTAINER}" >/dev/null

echo "Waiting for Jenkins health ..."
for _ in $(seq 1 60); do
  status="$(docker inspect --format='{{.State.Health.Status}}' "${CONTAINER}" 2>/dev/null || echo starting)"
  if [[ "$status" == "healthy" ]]; then
    break
  fi
  sleep 2
done

echo "Waiting for plugin API ..."
for _ in $(seq 1 60); do
  if curl -fsS "${BASE}/pluginManager/api/json?depth=1" >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

# Confirm plugin is listed as active (shortName = artifactId).
json="$(curl -fsS "${BASE}/pluginManager/api/json?depth=1")"
if printf '%s' "$json" | grep -q '"shortName":"ezkey-lab-hello"'; then
  echo "Plugin ezkey-lab-hello is installed."
else
  echo "Plugin not found in Plugin Manager API yet. Check ${BASE}/manage/pluginManager/installed" >&2
  exit 1
fi

echo "UI (installed plugins): ${BASE}/manage/pluginManager/installed"
echo "UI (home): ${BASE}/"
