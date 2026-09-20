#!/usr/bin/env bash
# Static verification of Ezkey base / integrity runtime profile wiring.
#
# Usage (from repo root):
#   ./docker/verify-runtime-profile.sh
#   ./docker/verify-runtime-profile.sh --live   # requires a running stack (base or integrity)
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

ADMIN_BASE="${ROOT}/ezkey-admin-api/config/application-docker-base.properties"
AUTH_BASE="${ROOT}/ezkey-auth-api/config/application-docker-base.properties"
INTEG_BASE="${ROOT}/ezkey-integration-api/config/application-docker-base.properties"
ADMIN_DOCKER="${ROOT}/ezkey-admin-api/config/application-docker.properties"
HELPER="${ROOT}/docker/runtime-profile.sh"

[[ -f "$ADMIN_BASE" ]] || fail "missing $ADMIN_BASE"
[[ -f "$AUTH_BASE" ]] || fail "missing $AUTH_BASE"
[[ -f "$INTEG_BASE" ]] || fail "missing $INTEG_BASE"
[[ -f "$HELPER" ]] || fail "missing $HELPER"
# Guard against leftover eval naming
[[ ! -f "${ROOT}/ezkey-admin-api/config/application-docker-eval.properties" ]] \
  || fail "stale application-docker-eval.properties present — rename to docker-base"
[[ ! -f "${ROOT}/docker/docker-compose.eval.yml" ]] \
  || fail "stale docker-compose.eval.yml present — rename to docker-compose.base.yml"

# --- Base matrix (Admin) ---
require_prop "$ADMIN_BASE" "ezkey.audit.chain.enabled" "false"
require_prop "$ADMIN_BASE" "ezkey.audit.chain.heartbeat.enabled" "false"
require_prop "$ADMIN_BASE" "ezkey.audit.chain.heartbeat.required" "false"
require_prop "$ADMIN_BASE" "ezkey.audit.integrity.nightly.enabled" "false"
require_prop "$ADMIN_BASE" "ezkey.audit.archive.auto-seal.enabled" "false"
require_prop "$ADMIN_BASE" "ezkey.audit.archive.purge.enabled" "false"
require_prop "$ADMIN_BASE" "ezkey.encryption.rotation.enabled" "false"
require_prop "$ADMIN_BASE" "ezkey.encryption.reencryption.enabled" "false"
require_prop "$ADMIN_BASE" "ezkey.enrollment.expired-cleanup.enabled" "false"
require_prop "$ADMIN_BASE" "ezkey.admin.token.cleanup.enabled" "false"
pass "Admin docker-base property matrix"

# --- Hard coupling on Auth / Integration ---
require_prop "$AUTH_BASE" "ezkey.audit.chain.heartbeat.enabled" "false"
require_prop "$AUTH_BASE" "ezkey.audit.chain.heartbeat.required" "false"
require_prop "$INTEG_BASE" "ezkey.audit.chain.heartbeat.enabled" "false"
require_prop "$INTEG_BASE" "ezkey.audit.chain.heartbeat.required" "false"
pass "Auth/Integration heartbeat OFF under base (★ coupling)"

# --- Integrity default unchanged in docker base ---
require_prop "$ADMIN_DOCKER" "ezkey.audit.chain.enabled" "true"
require_prop "$ADMIN_DOCKER" "ezkey.encryption.rotation.enabled" "true"
require_prop "$ADMIN_DOCKER" "ezkey.audit.integrity.enabled" "true"
pass "Integrity docker defaults unchanged (chain + rotation + HMAC write)"

# --- Helper: integrity default / base append ---
# shellcheck source=runtime-profile.sh
source "$HELPER"

unset EZKEY_RUNTIME_PROFILE || true
export SPRING_PROFILES_ACTIVE="docker,docker-dev,docker-test"
resolve_ezkey_runtime_profile >/dev/null
[[ "${EZKEY_RUNTIME_PROFILE}" == "integrity" ]] || fail "default must be integrity"
[[ "${SPRING_PROFILES_ACTIVE}" != *docker-base* ]] || fail "integrity must not keep docker-base"
pass "Helper: unset → integrity, no docker-base"

export EZKEY_RUNTIME_PROFILE=base
export SPRING_PROFILES_ACTIVE="docker,docker-dev,docker-test"
resolve_ezkey_runtime_profile >/dev/null
[[ "${SPRING_PROFILES_ACTIVE}" == "docker,docker-dev,docker-test,docker-base" ]] \
  || fail "base must append docker-base last (got: ${SPRING_PROFILES_ACTIVE})"
pass "Helper: base appends docker-base last"

export EZKEY_RUNTIME_PROFILE=integrity
export SPRING_PROFILES_ACTIVE="docker,docker-test,docker-base"
resolve_ezkey_runtime_profile >/dev/null
[[ "${SPRING_PROFILES_ACTIVE}" != *docker-base* ]] || fail "integrity must strip docker-base"
pass "Helper: integrity strips docker-base"

if [[ -z "$LIVE" ]]; then
  echo ""
  echo "Static checks passed. For live stack proof:"
  echo "  ./ezkey-tests/clean-start.sh --runtime=base"
  echo "  ./docker/verify-runtime-profile.sh --live"
  exit 0
fi

# --- Live: profiles + startup logs + MFA gate (no env actuator exposure in docker-dev) ---
if ! docker inspect ezkey-admin-api >/dev/null 2>&1; then
  fail "Live check: container ezkey-admin-api not running"
fi
if ! docker inspect ezkey-auth-api >/dev/null 2>&1; then
  fail "Live check: container ezkey-auth-api not running"
fi

profiles="$(docker inspect -f '{{range .Config.Env}}{{println .}}{{end}}' ezkey-admin-api | grep '^SPRING_PROFILES_ACTIVE=' || true)"
profiles="${profiles#SPRING_PROFILES_ACTIVE=}"
echo "  Container SPRING_PROFILES_ACTIVE: ${profiles}"

admin_logs="$(docker logs ezkey-admin-api 2>&1 || true)"
auth_logs="$(docker logs ezkey-auth-api 2>&1 || true)"

pending_code="$(curl -s -o /tmp/ezkey-runtime-pending.txt -w '%{http_code}' \
  -X POST http://localhost:8080/api/v1/auth-attempts/pending \
  -H 'Content-Type: application/json' -d '{}' || echo '000')"

if echo "$profiles" | grep -q 'docker-base'; then
  echo "$admin_logs" | grep -F 'docker-base' >/dev/null \
    || fail "base live: Admin logs should mention docker-base profile"
  grep -Fq 'heartbeat supervision is disabled' <<<"$admin_logs" \
    || fail "base live: Admin should log heartbeat supervision disabled"
  grep -Fq 'heartbeat supervision is disabled' <<<"$auth_logs" \
    || fail "base live: Auth should log heartbeat supervision disabled"
  [[ "$pending_code" != "503" ]] \
    || fail "base live: Auth pending must not return 503 (heartbeat fail-closed); got ${pending_code}"
  pass "Live base: docker-base active, heartbeat off, Auth pending HTTP ${pending_code} (not 503)"
else
  echo "$admin_logs" | grep -F 'docker-base' >/dev/null \
    && fail "integrity live: Admin must not activate docker-base"
  grep -Fq 'heartbeat supervision is disabled' <<<"$auth_logs" \
    && fail "integrity live: Auth heartbeat should remain enabled by default"
  pass "Live integrity: no docker-base; Auth heartbeat supervision remains enabled"
fi

echo ""
echo "All runtime-profile checks passed."
