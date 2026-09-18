#!/usr/bin/env bash
# Static verification of Ezkey eval / integrity runtime profile wiring.
#
# Usage (from repo root):
#   ./docker/verify-runtime-profile.sh
#   ./docker/verify-runtime-profile.sh --live   # requires a running stack (eval or integrity)
#
# Does not redesign product locks — asserts the committed matrix and helper behaviour.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
LIVE=""

for arg in "$@"; do
  case "$arg" in
    --live) LIVE="1" ;;
    *)
      echo "Unknown option: $arg" >&2
      echo "Usage: $0 [--live]" >&2
      exit 1
      ;;
  esac
done

fail() {
  echo "❌ $*" >&2
  exit 1
}

pass() {
  echo "✅ $*"
}

require_prop() {
  local file="$1"
  local key="$2"
  local expected="$3"
  grep -Eq "^${key}=${expected}$" "$file" \
    || fail "${file}: expected ${key}=${expected}"
}

ADMIN_EVAL="${ROOT}/ezkey-admin-api/config/application-docker-eval.properties"
AUTH_EVAL="${ROOT}/ezkey-auth-api/config/application-docker-eval.properties"
INTEG_EVAL="${ROOT}/ezkey-integration-api/config/application-docker-eval.properties"
ADMIN_DOCKER="${ROOT}/ezkey-admin-api/config/application-docker.properties"
HELPER="${ROOT}/docker/runtime-profile.sh"

[[ -f "$ADMIN_EVAL" ]] || fail "missing $ADMIN_EVAL"
[[ -f "$AUTH_EVAL" ]] || fail "missing $AUTH_EVAL"
[[ -f "$INTEG_EVAL" ]] || fail "missing $INTEG_EVAL"
[[ -f "$HELPER" ]] || fail "missing $HELPER"

# --- Eval matrix (Admin) ---
require_prop "$ADMIN_EVAL" "ezkey.audit.chain.enabled" "false"
require_prop "$ADMIN_EVAL" "ezkey.audit.chain.heartbeat.enabled" "false"
require_prop "$ADMIN_EVAL" "ezkey.audit.chain.heartbeat.required" "false"
require_prop "$ADMIN_EVAL" "ezkey.audit.integrity.nightly.enabled" "false"
require_prop "$ADMIN_EVAL" "ezkey.audit.archive.auto-seal.enabled" "false"
require_prop "$ADMIN_EVAL" "ezkey.audit.archive.purge.enabled" "false"
require_prop "$ADMIN_EVAL" "ezkey.encryption.rotation.enabled" "false"
require_prop "$ADMIN_EVAL" "ezkey.encryption.reencryption.enabled" "false"
require_prop "$ADMIN_EVAL" "ezkey.enrollment.expired-cleanup.enabled" "false"
require_prop "$ADMIN_EVAL" "ezkey.admin.token.cleanup.enabled" "false"
pass "Admin docker-eval property matrix"

# --- Hard coupling on Auth / Integration ---
require_prop "$AUTH_EVAL" "ezkey.audit.chain.heartbeat.enabled" "false"
require_prop "$AUTH_EVAL" "ezkey.audit.chain.heartbeat.required" "false"
require_prop "$INTEG_EVAL" "ezkey.audit.chain.heartbeat.enabled" "false"
require_prop "$INTEG_EVAL" "ezkey.audit.chain.heartbeat.required" "false"
pass "Auth/Integration heartbeat OFF under eval (★ coupling)"

# --- Integrity default unchanged in docker base ---
require_prop "$ADMIN_DOCKER" "ezkey.audit.chain.enabled" "true"
require_prop "$ADMIN_DOCKER" "ezkey.encryption.rotation.enabled" "true"
require_prop "$ADMIN_DOCKER" "ezkey.audit.integrity.enabled" "true"
pass "Integrity docker defaults unchanged (chain + rotation + HMAC write)"

# --- Helper: integrity default / eval append ---
# shellcheck source=runtime-profile.sh
source "$HELPER"

unset EZKEY_RUNTIME_PROFILE || true
export SPRING_PROFILES_ACTIVE="docker,docker-dev,docker-test"
resolve_ezkey_runtime_profile >/dev/null
[[ "${EZKEY_RUNTIME_PROFILE}" == "integrity" ]] || fail "default must be integrity"
[[ "${SPRING_PROFILES_ACTIVE}" != *docker-eval* ]] || fail "integrity must not keep docker-eval"
pass "Helper: unset → integrity, no docker-eval"

export EZKEY_RUNTIME_PROFILE=eval
export SPRING_PROFILES_ACTIVE="docker,docker-dev,docker-test"
resolve_ezkey_runtime_profile >/dev/null
[[ "${SPRING_PROFILES_ACTIVE}" == "docker,docker-dev,docker-test,docker-eval" ]] \
  || fail "eval must append docker-eval last (got: ${SPRING_PROFILES_ACTIVE})"
pass "Helper: eval appends docker-eval last"

export EZKEY_RUNTIME_PROFILE=integrity
export SPRING_PROFILES_ACTIVE="docker,docker-test,docker-eval"
resolve_ezkey_runtime_profile >/dev/null
[[ "${SPRING_PROFILES_ACTIVE}" != *docker-eval* ]] || fail "integrity must strip docker-eval"
pass "Helper: integrity strips docker-eval"

if [[ -z "$LIVE" ]]; then
  echo ""
  echo "Static checks passed. For live stack proof:"
  echo "  ./ezkey-tests/clean-start.sh --runtime=eval"
  echo "  ./docker/verify-runtime-profile.sh --live"
  exit 0
fi

# --- Live: read effective config from Actuator (docker-dev management ports) ---
admin_env="$(curl -sf http://localhost:9081/actuator/env/ezkey.audit.chain.enabled 2>/dev/null || true)"
auth_hb="$(curl -sf http://localhost:8085/actuator/env/ezkey.audit.chain.heartbeat.enabled 2>/dev/null || true)"

if [[ -z "$admin_env" || -z "$auth_hb" ]]; then
  fail "Live check needs a running stack with docker-dev management ports (9081 / 8085)."
fi

profiles="$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' ezkey-admin-api 2>/dev/null | grep '^SPRING_PROFILES_ACTIVE=' || true)"
echo "  Container SPRING_PROFILES_ACTIVE: ${profiles#SPRING_PROFILES_ACTIVE=}"

if echo "$profiles" | grep -q 'docker-eval'; then
  echo "$admin_env" | grep -q '"value":"false"' \
    || fail "eval live: expected ezkey.audit.chain.enabled=false on Admin"
  echo "$auth_hb" | grep -q '"value":"false"' \
    || fail "eval live: expected ezkey.audit.chain.heartbeat.enabled=false on Auth"
  pass "Live eval: checkpoints + Auth heartbeat disabled (no heartbeat 503 path)"
else
  echo "$admin_env" | grep -q '"value":"true"' \
    || fail "integrity live: expected ezkey.audit.chain.enabled=true on Admin"
  echo "$auth_hb" | grep -q '"value":"true"' \
    || fail "integrity live: expected ezkey.audit.chain.heartbeat.enabled=true on Auth"
  pass "Live integrity: checkpoints + Auth heartbeat enabled (default posture)"
fi

echo ""
echo "All runtime-profile checks passed."
