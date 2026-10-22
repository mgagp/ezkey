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
DEMO_DEVICE_HEALTH_URL="${EZKEY_DEMO_DEVICE_HEALTH_URL:-http://localhost:8084/actuator/health}"
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

# M1 m5 — secret-grep proofs must not silently skip when .env keys are empty.
require_integration_keys() {
  if [ -z "${EZKEY_INTEGRATION_KEY:-}" ] || [ -z "${EZKEY_SECRET_KEY:-}" ]; then
    echo "EZKEY_INTEGRATION_KEY and EZKEY_SECRET_KEY must be set (non-empty) in ${ENV_FILE:-.env} or the environment." >&2
    exit 1
  fi
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

# True when a live SSH capture already shows a PAM_PROMPT_ECHO_ON line —
# pre-create confirm (#975 / #982) and/or post-create challenge code (#982).
ezkey_ssh_capture_has_prewait_prompt() {
  local log="$1"
  local flat
  flat="$(tr -cd '\11\12\15\40-\176' <"${log}" 2>/dev/null || true)"
  echo "${flat}" | grep -qE \
    'SSH login for user |Ezkey challenge code:|Type ok and press Enter to request device approval|Approve on your phone, then press Enter'
}

# Run one keyboard-interactive SSH under a PTY; auto-send "ok" / Enter when a
# confirm/challenge prompt appears (#941 / #975 / #982).
# Re-sends on cooldown: ok #1 creates; challenge path needs a 2nd Enter (#982).
# Create-fail paths that never show a prompt — helper waits for ssh to exit.
# Args: <log> [remote_cmd] [ConnectTimeout] [NumberOfPasswordPrompts]
# Default prompts=1 (Match MaxAuthTries 1). Pass 3 for F9 multitry case A.
ezkey_ssh_kbdint_autoenter() {
  local log="$1"
  local remote_cmd="${2:-echo X}"
  local connect_to="${3:-15}"
  local password_prompts="${4:-1}"
  local ctrl=""
  local ctrl_dir=""
  local bg=""
  local i
  local rc=1
  local since_enter=999
  local script_dir
  script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

  require_cmd python3
  require_cmd ssh
  : >"${log}"
  # Nit a (#956): mktemp -d then fifo inside — avoid mktemp -u race.
  ctrl_dir="$(mktemp -d /tmp/ezkey-pam-kbdint.XXXXXX)"
  ctrl="${ctrl_dir}/enter.fifo"
  mkfifo "${ctrl}"

  set +e
  python3 "${script_dir}/ssh-pty-capture.py" "${log}" "${ctrl}" \
    ssh -tt \
      -p "${SSH_PORT}" \
      -o StrictHostKeyChecking=no \
      -o UserKnownHostsFile=/dev/null \
      -o PreferredAuthentications=keyboard-interactive \
      -o PubkeyAuthentication=no \
      -o PasswordAuthentication=no \
      -o "NumberOfPasswordPrompts=${password_prompts}" \
      -o ConnectTimeout="${connect_to}" \
      "${SSH_USER}@127.0.0.1" \
      "${remote_cmd}" \
    >/dev/null 2>&1 &
  bg=$!
  set -e

  # First visible prompt → ok (pre-create). Later challenge line → bare Enter
  # (#982 tier 1). Cooldown covers OpenSSH multitry (#907 F9 / #975 D4).
  for i in $(seq 1 600); do
    if ! kill -0 "${bg}" 2>/dev/null; then
      break
    fi
    since_enter=$((since_enter + 1))
    if ezkey_ssh_capture_has_prewait_prompt "${log}" && [ "${since_enter}" -ge 15 ]; then
      flat="$(tr -cd '\11\12\15\40-\176' <"${log}" 2>/dev/null || true)"
      if echo "${flat}" | grep -q 'Ezkey challenge code:'; then
        printf 'enter\n' >"${ctrl}" 2>/dev/null || true
      else
        printf 'ok\n' >"${ctrl}" 2>/dev/null || true
      fi
      since_enter=0
    fi
    sleep 0.1
  done

  # Keep set +e through return: a non-zero `return` under set -e aborts the
  # whole caller script in Bash (not only the function).
  set +e
  wait "${bg}"
  rc=$?
  rm -f "${ctrl}" 2>/dev/null || true
  rmdir "${ctrl_dir}" 2>/dev/null || true
  return "${rc}"
}

# Create a control fifo under a private mktemp -d (Patrick #956 nit a).
# Prints the fifo path on stdout; caller should rm -rf the parent dir later.
ezkey_mk_ctrl_fifo() {
  local dir fifo
  dir="$(mktemp -d /tmp/ezkey-pam-ctrl.XXXXXX)"
  fifo="${dir}/enter.fifo"
  mkfifo "${fifo}"
  printf '%s\n' "${fifo}"
}
