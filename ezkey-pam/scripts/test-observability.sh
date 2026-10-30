#!/usr/bin/env bash
# M1 observability: exactly one authpriv attempt line per scenario in /var/log/secure.
# Scenarios: success, reject, timeout, network error. Never secrets in any log sink.
# #1016 D3: isolate from caller ENV (CI stub keys → Integration API http=401).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck disable=SC1091
. "${SCRIPT_DIR}/common.sh"

require_cmd docker
require_cmd ssh

# D3: drop caller/CI stub keys, then reload provisioned .env only.
unset EZKEY_INTEGRATION_KEY EZKEY_SECRET_KEY EZKEY_INTEGRATION_API_URL \
  EZKEY_M2M_API_URL
load_env_file "${ENV_FILE}"
require_integration_keys
case "${EZKEY_INTEGRATION_KEY}" in
  *ci_dummy*|*not_a_secret*)
    echo "test-observability needs provisioned .env keys (got CI stub pattern in EZKEY_INTEGRATION_KEY)" >&2
    exit 1
    ;;
esac
case "${EZKEY_SECRET_KEY}" in
  *ci_dummy*|*not_a_secret*)
    echo "test-observability needs provisioned .env keys (got CI stub pattern in EZKEY_SECRET_KEY)" >&2
    exit 1
    ;;
esac

CONTAINER="${EZKEY_PAM_CONTAINER:-ezkey-pam-ssh}"
CONF="/etc/security/pam_ezkey.conf"
BACKUP="/etc/security/pam_ezkey.conf.obsbak"
PORT="${SSH_PORT}"
PASS=0
FAIL=0

ok()   { echo "[PASS] $1"; PASS=$((PASS+1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL+1)); }

if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
  echo "${CONTAINER} is not running. Start with ./scripts/up.sh" >&2
  exit 1
fi

restore_conf() {
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${BACKUP}' ]; then mv -f '${BACKUP}' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'; fi" \
    || true
}
trap restore_conf EXIT

# Sync provisioned .env credentials into the running container conf so a prior
# CI-stub start cannot leave http=401 keys in place (#1016 D3).
docker exec "${CONTAINER}" cp -a "${CONF}" "${BACKUP}"
# Values from .env (validated non-empty / non-stub). Pass via env; rewrite in
# container Python so the host shell never interpolates secrets into argv.
docker exec -i \
  -e "OBS_IKEY=${EZKEY_INTEGRATION_KEY}" \
  -e "OBS_SKEY=${EZKEY_SECRET_KEY}" \
  -e "OBS_URL=${EZKEY_INTEGRATION_API_URL:-http://integration-api:7080}" \
  -e "OBS_CONF=${CONF}" \
  "${CONTAINER}" python3 - <<'PY'
import os, pathlib, tempfile
conf = pathlib.Path(os.environ["OBS_CONF"])
ikey, skey, url = os.environ["OBS_IKEY"], os.environ["OBS_SKEY"], os.environ["OBS_URL"]
for label, val in (("ikey", ikey), ("skey", skey), ("url", url)):
    if any(c in val for c in "\n\r"):
        raise SystemExit(f"refusing conf rewrite: {label} contains newline")
lines = conf.read_text(encoding="utf-8").splitlines()
out = []
for line in lines:
    if line.startswith("integration_api_url="):
        out.append(f"integration_api_url={url}")
    elif line.startswith("integration_key="):
        out.append(f"integration_key={ikey}")
    elif line.startswith("secret_key="):
        out.append(f"secret_key={skey}")
    else:
        out.append(line)
text = "\n".join(out) + "\n"
fd, tmp_name = tempfile.mkstemp(prefix="pam_ezkey.obs.", dir=str(conf.parent))
os.close(fd)
tmp = pathlib.Path(tmp_name)
tmp.write_text(text, encoding="utf-8")
tmp.chmod(0o600)
os.chown(tmp, 0, 0)
tmp.replace(conf)
print("obs: synced conf keys from provisioned .env")
PY
# EXIT restore should return to provisioned keys, not a prior stub conf.
docker exec "${CONTAINER}" cp -a "${CONF}" "${BACKUP}"

attempt_count() {
  # GNU grep -c prints 0 and exits 1 when there are no matches — do not append another 0.
  docker exec "${CONTAINER}" sh -c \
    "grep -c 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null || true" | tr -d ' \r'
}

attempts_tail_from() {
  local before="$1"
  local after
  after="$(attempt_count)"
  local new=$((after - before))
  if [ "${new}" -le 0 ]; then
    return 0
  fi
  docker exec "${CONTAINER}" sh -c \
    "grep 'pam_ezkey: attempt ' /var/log/secure | tail -n ${new}"
}

wait_new_attempt() {
  local before="$1"
  local i after
  for i in $(seq 1 80); do
    after="$(attempt_count)"
    if [ "$((after - before))" -ge 1 ]; then
      return 0
    fi
    sleep 0.1
  done
  return 1
}

assert_one_clean_line() {
  local before="$1"
  local expect_result="$2"
  local expect_reason="$3"
  local label="$4"
  local lines count line

  wait_new_attempt "${before}" || true
  lines="$(attempts_tail_from "${before}")"
  count="$(echo "${lines}" | grep -c 'pam_ezkey: attempt ' || true)"
  line="$(echo "${lines}" | grep 'pam_ezkey: attempt ' | tail -n 1 || true)"

  if [ "${count}" -lt 1 ]; then
    fail "${label}: expected at least 1 new attempt line, got ${count}"
    return 0
  fi
  # Demo Match User MaxAuthTries 1 → one pam_sm_authenticate per SSH try.
  if [ "${count}" -gt 1 ]; then
    fail "${label}: expected exactly 1 new attempt line (Match MaxAuthTries 1), got ${count}"
    echo "${lines}" | sed 's/^/  /' || true
    return 0
  fi
  if ! echo "${line}" | grep -q "result=${expect_result}"; then
    fail "${label}: expected result=${expect_result} in: ${line}"
    return 0
  fi
  # N1: assert the expected reason (not merely "any reason= token").
  if ! echo "${line}" | grep -Eq " reason=${expect_reason}( |$)"; then
    fail "${label}: expected reason=${expect_reason} in: ${line}"
    return 0
  fi
  if ! echo "${line}" | grep -Eq 'user=[^ ]+ authAttemptId=[^ ]+ result=[^ ]+ pam_code=[0-9]+ latency_ms=(-|[0-9]+) lab_http=(true|false) cancel=(ok|fail|skipped|final) timeoutSeconds=(-|[0-9]+) stage=(init|conf|confirm|create|challenge|wait) reason=([-a-z0-9_]+) curl_rc=(-|[0-9]+) http=(-|[0-9]+) confirm_ms=(-|[0-9]+) api_ms=(-|[0-9]+)$'; then
    fail "${label}: line missing required fields (incl. #1011 stage/reason/timings, \$ anchored): ${line}"
    return 0
  fi
  if echo "${line}" | grep -Eiq 'secret|proof|token|Authorization|body=\{'; then
    fail "${label}: sensitive pattern in attempt line: ${line}"
    return 0
  fi
  if [ -n "${EZKEY_SECRET_KEY:-}" ] && echo "${line}" | grep -Fq "${EZKEY_SECRET_KEY}"; then
    fail "${label}: secret_key leaked"
    return 0
  fi
  if [ -n "${EZKEY_INTEGRATION_KEY:-}" ] && echo "${line}" | grep -Fq "${EZKEY_INTEGRATION_KEY}"; then
    fail "${label}: integration_key leaked"
    return 0
  fi
  ok "${label}: one clean line (${expect_result} reason=${expect_reason})"
  echo "  ${line}"
}

assert_no_secret_in_logs() {
  local blob
  blob="$(docker exec "${CONTAINER}" sh -c \
    'cat /var/log/secure /var/log/ezkey-pam/debug.out /var/log/ezkey-pam/debug.err 2>/dev/null' || true)"
  local leak=0
  local needle
  for needle in "${EZKEY_SECRET_KEY:-}" "${EZKEY_INTEGRATION_KEY:-}" authAttemptProofToken; do
    if [ -n "${needle}" ] && echo "${blob}" | grep -Fq "${needle}"; then
      fail "secret/proof leaked into secure/debug logs: ${needle:0:12}..."
      leak=1
    fi
  done
  if [ "${leak}" -eq 0 ]; then
    ok "no ikey/skey/proof across /var/log/secure and debug filelogs"
  fi
}

ssh_run() {
  local out="$1"
  local cmd="$2"
  local rc=0
  # #941: no-challenge waiting prompt needs Enter; capture includes PAM markers.
  set +e
  ezkey_ssh_kbdint_autoenter "${out}" "${cmd}" 10
  rc=$?
  set -e
  echo "${rc}" >"${out}.rc"
}

echo "=== M1 observability (/var/log/secure on ${CONTAINER}) ==="

PROBE="m1-rsyslog-probe-$$-$(date +%s)"
docker exec "${CONTAINER}" logger -p authpriv.info "${PROBE}"
PROBE_OK=0
for _ in $(seq 1 50); do
  if docker exec "${CONTAINER}" grep -qF "${PROBE}" /var/log/secure 2>/dev/null; then
    PROBE_OK=1
    break
  fi
  sleep 0.1
done
if [ "${PROBE_OK}" -eq 1 ]; then
  ok "rsyslog authpriv → /var/log/secure"
else
  fail "rsyslog authpriv probe missing from /var/log/secure"
fi

# --- success ---
BEFORE="$(attempt_count)"
LOG="${RUNTIME_DIR}/obs-success.log"
"${SCRIPT_DIR}/respond-pending.sh" approve >"${RUNTIME_DIR}/obs-approve.log" 2>&1 &
APID=$!
ssh_run "${LOG}" 'echo PAM_SSH_OK; exit 0'
wait "${APID}" 2>/dev/null || true
if grep -q PAM_SSH_OK "${LOG}"; then
  ok "success path SSH PAM_SSH_OK"
else
  fail "success path did not print PAM_SSH_OK"
  tail -n 40 "${LOG}" || true
fi
assert_one_clean_line "${BEFORE}" "SUCCESS" "-" "success"

# --- reject ---
BEFORE="$(attempt_count)"
LOG="${RUNTIME_DIR}/obs-reject.log"
"${SCRIPT_DIR}/respond-pending.sh" reject >"${RUNTIME_DIR}/obs-reject-resp.log" 2>&1 &
APID=$!
ssh_run "${LOG}" 'echo SHOULD_NOT'
wait "${APID}" 2>/dev/null || true
RC="$(cat "${LOG}.rc" 2>/dev/null || echo 1)"
if [ "${RC}" != "0" ] && ! grep -q SHOULD_NOT "${LOG}"; then
  ok "reject path SSH denied"
else
  fail "reject path unexpectedly succeeded (rc=${RC})"
fi
assert_one_clean_line "${BEFORE}" "DENIED" "-" "reject"

# --- timeout (short wait, no approve) → result=TIMEOUT (#895) ---
BEFORE="$(attempt_count)"
docker exec "${CONTAINER}" cp -a "${CONF}" "${BACKUP}"
docker exec "${CONTAINER}" sh -c \
  "sed -i 's/^wait_timeout=.*/wait_timeout=15/' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'"
LOG="${RUNTIME_DIR}/obs-timeout.log"
ssh_run "${LOG}" 'echo SHOULD_NOT'
restore_conf
RC="$(cat "${LOG}.rc" 2>/dev/null || echo 1)"
if [ "${RC}" != "0" ]; then
  ok "timeout path SSH denied"
else
  fail "timeout path unexpectedly succeeded"
fi
assert_one_clean_line "${BEFORE}" "TIMEOUT" "-" "timeout"

# --- network error (unreachable Integration API) ---
BEFORE="$(attempt_count)"
docker exec "${CONTAINER}" cp -a "${CONF}" "${BACKUP}"
docker exec "${CONTAINER}" sh -c \
  "sed -i 's|^integration_api_url=.*|integration_api_url=http://127.0.0.1:9|' '${CONF}'; chown root:root '${CONF}'; chmod 600 '${CONF}'"
LOG="${RUNTIME_DIR}/obs-net.log"
ssh_run "${LOG}" 'echo SHOULD_NOT'
restore_conf
RC="$(cat "${LOG}.rc" 2>/dev/null || echo 1)"
if [ "${RC}" != "0" ]; then
  ok "network-error path SSH denied"
else
  fail "network-error path unexpectedly succeeded"
fi
assert_one_clean_line "${BEFORE}" "UNAVAIL" "curl" "network-error"

assert_no_secret_in_logs

echo ""
echo "=== Results: $PASS passed, $FAIL failed ==="
[ "${FAIL}" -eq 0 ]
