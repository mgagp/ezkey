#!/usr/bin/env bash
# Shared helpers for Ezkey PAM demo scripts.
# shellcheck disable=SC2034

set -euo pipefail

PAM_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_ROOT="$(cd "${PAM_ROOT}/.." && pwd)"
RUNTIME_DIR="${PAM_ROOT}/runtime"
ENV_FILE="${PAM_ROOT}/.env"

ADMIN_API_URL="${EZKEY_ADMIN_API_URL:-http://localhost:9080}"
ADMIN_HEALTH_URL="${EZKEY_ADMIN_HEALTH_URL:-http://localhost:9081/actuator/health}"
AUTH_API_URL="${EZKEY_AUTH_API_URL:-http://localhost:8080}"
INTEGRATION_API_URL="${EZKEY_INTEGRATION_API_HOST:-http://localhost:7080}"
DEMO_DEVICE_URL="${EZKEY_DEMO_DEVICE_URL:-http://localhost:8083}"
DEMO_DEVICE_HEALTH_URL="${EZKEY_DEMO_DEVICE_HEALTH_URL:-http://localhost:8083/actuator/health}"
SSH_USER="${EZKEY_SSH_USER:-testuser}"
SSH_PORT="${EZKEY_PAM_SSH_PORT:-2222}"

load_env_file() {
  local path="$1"
  if [ ! -f "${path}" ]; then
    return 0
  fi
  eval "$(python3 - "${path}" <<'PY'
import shlex, sys
path = sys.argv[1]
with open(path, encoding="utf-8") as fh:
    for raw in fh:
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        if not key:
            continue
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        print(f"export {key}={shlex.quote(value)}")
PY
)"
}

mkdir -p "${RUNTIME_DIR}"
load_env_file "${ENV_FILE}"
SSH_USER="${EZKEY_SSH_USER:-${SSH_USER}}"
SSH_PORT="${EZKEY_PAM_SSH_PORT:-${SSH_PORT}}"

json_get() {
  local json="$1"
  local path="$2"
  python3 - "$json" "$path" <<'PY'
import json, sys
raw, path = sys.argv[1], sys.argv[2]
data = json.loads(raw)
cur = data
for part in path.split("."):
    if isinstance(cur, dict):
        cur = cur.get(part)
    else:
        cur = None
        break
if cur is None:
    sys.exit(1)
if isinstance(cur, bool):
    print("true" if cur else "false")
else:
    print(cur)
PY
}

html_input_value() {
  local html="$1"
  local name="$2"
  python3 - "$html" "$name" <<'PY'
import re, sys
html, name = sys.argv[1], sys.argv[2]
patterns = [
    rf'name=["\']{re.escape(name)}["\'][^>]*value=["\']([^"\']*)["\']',
    rf'value=["\']([^"\']*)["\'][^>]*name=["\']{re.escape(name)}["\']',
]
for pat in patterns:
    m = re.search(pat, html, re.I | re.S)
    if m:
        print(m.group(1))
        sys.exit(0)
sys.exit(1)
PY
}

detect_ezkey_network() {
  if [ -n "${EZKEY_DOCKER_NETWORK:-}" ]; then
    echo "${EZKEY_DOCKER_NETWORK}"
    return
  fi
  if docker network inspect ezkey_ezkey-network >/dev/null 2>&1; then
    echo ezkey_ezkey-network
    return
  fi
  if docker network inspect ezkey-network >/dev/null 2>&1; then
    echo ezkey-network
    return
  fi
  docker network ls --format '{{.Name}}' | grep -E 'ezkey' | grep -E 'network' | head -n 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

wait_http_ok() {
  local url="$1"
  local attempts="${2:-60}"
  local i
  for i in $(seq 1 "${attempts}"); do
    if curl -fsS --max-time 3 "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for ${url}" >&2
  return 1
}
