#!/usr/bin/env bash
# #1054 / #1067 review — prove TLS verify mutants are killed by execution.
#
# NEVER point EZKEY_PAM_CONTAINER at anything but a disposable container.
# This script swaps pam_ezkey.so (and may rewrite conf) inside that container;
# a shared demo/stack host is not a safe target.
#
# Honest kill layers (do not merge into one killed=N counter):
#   runtime kills:  PEER=0 alone, both=0  (SAN-matching stub; stub sees ≥1 req)
#   check-source:   HOST=0 alone          (VERIFYHOST-only; PEER still rejects
#                     a self-signed chain, so runtime cannot kill HOST=0 alone
#                     without a trusted test CA in the image — lab-disproportionate)
#
# CN-mismatch case is intentionally not used (Patrick #1067: not required).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SCRATCH="$(mktemp -d /tmp/ezkey-pam-tls-mut-XXXXXX)"
REPORT="${EZKEY_PAM_ARTIFACT_DIR:-${ROOT}/runtime/artifacts}/pam-mutation-tls-verify-proof.txt"
mkdir -p "$(dirname "${REPORT}")"

CONTAINER="${EZKEY_PAM_CONTAINER:-ezkey-pam-ssh}"
TLS_PORT=18443
TLS_REQ_LOG="/tmp/ezkey-pam-tls-mut-reqs.log"
TLS_CERT="/tmp/ezkey-pam-tls-mut.crt"
TLS_KEY="/tmp/ezkey-pam-tls-mut.key"
TLS_PID_FILE="/tmp/ezkey-pam-tls-mut.pid"
SO_PATH="/lib64/security/pam_ezkey.so"
SO_BAK="/tmp/pam_ezkey.so.tls-mut.bak"
CONF_PATH="/etc/security/pam_ezkey.conf"
CONF_BAK="/tmp/pam_ezkey.conf.tls-mut.bak"
HOST_DIR="${SCRATCH}/tls-mut-certs"
mkdir -p "${HOST_DIR}"

# Container mutation state for EXIT restore.
SO_SWAPPED=0
CONF_SWAPPED=0
SO_BAK_SHA=""
RUNTIME_PEER_KILLED=0
RUNTIME_BOTH_KILLED=0
CHECKSOURCE_HOST_KILLED=0
SURVIVED=0

restore_container() {
  if ! docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "${CONTAINER}"; then
    return 0
  fi
  if [ "${CONF_SWAPPED}" -eq 1 ]; then
    docker exec "${CONTAINER}" sh -c \
      "if [ -f '${CONF_BAK}' ]; then
         cp -a '${CONF_BAK}' '${CONF_PATH}'
         chown root:root '${CONF_PATH}'; chmod 600 '${CONF_PATH}'
       fi" || true
    CONF_SWAPPED=0
  fi
  if [ "${SO_SWAPPED}" -eq 1 ]; then
    docker exec "${CONTAINER}" sh -c \
      "if [ -f '${SO_BAK}' ]; then cp -a '${SO_BAK}' '${SO_PATH}'; fi" || true
    local restored_sha
    restored_sha="$(docker exec "${CONTAINER}" sha256sum "${SO_PATH}" 2>/dev/null \
      | awk '{print $1}')"
    if [ -n "${SO_BAK_SHA}" ] && [ -n "${restored_sha}" ]; then
      if [ "${restored_sha}" != "${SO_BAK_SHA}" ]; then
        echo "error: restored ${SO_PATH} SHA-256 (${restored_sha}) != backup (${SO_BAK_SHA})" >&2
        return 1
      fi
      echo "restore OK: ${SO_PATH} SHA-256 matches backup (${SO_BAK_SHA})" \
        | tee -a "${REPORT}"
    fi
    SO_SWAPPED=0
  fi
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${TLS_PID_FILE}' ]; then kill \"\$(cat '${TLS_PID_FILE}')\" 2>/dev/null || true; fi
     fuser -k ${TLS_PORT}/tcp >/dev/null 2>&1 || true" || true
  return 0
}

cleanup() {
  set +e
  restore_container
  rm -rf "${SCRATCH}"
}
trap cleanup EXIT

cp -a "${ROOT}/." "${SCRATCH}/"
rm -rf "${SCRATCH}/build"

: >"${REPORT}"
echo "=== #1054 / #1067 TLS verify mutation proof ===" | tee -a "${REPORT}"
echo "NOTE: EZKEY_PAM_CONTAINER must name a disposable container only." \
  | tee -a "${REPORT}"

restore_http() {
  cp -a "${ROOT}/src/ezkey_http.c" "${SCRATCH}/src/ezkey_http.c"
}

# --- check-source: HOST=0 alone (runtime cannot kill this without a trusted CA) ---
echo "" | tee -a "${REPORT}"
echo "--- M_tls_host: VERIFYHOST 0 alone (check-source) ---" | tee -a "${REPORT}"
restore_http
rm -rf "${SCRATCH}/build"
sed -i 's/CURLOPT_SSL_VERIFYHOST, 2L/CURLOPT_SSL_VERIFYHOST, 0L/' \
  "${SCRATCH}/src/ezkey_http.c"
set +e
(cd "${SCRATCH}" && make check-source) >"${SCRATCH}/mut-host.out" 2>&1
host_rc=$?
set -e
if [ "${host_rc}" -eq 0 ]; then
  echo "[SURVIVED] M_tls_host check-source (exit 0)" | tee -a "${REPORT}"
  SURVIVED=$((SURVIVED + 1))
else
  echo "[KILLED] M_tls_host by make check-source (rc=${host_rc})" | tee -a "${REPORT}"
  CHECKSOURCE_HOST_KILLED=1
  grep -E 'VERIFYPEER|VERIFYHOST|error:' "${SCRATCH}/mut-host.out" | tail -n 6 \
    | tee -a "${REPORT}" || true
fi
restore_http

# --- runtime helpers ---
require_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "missing $1" >&2; return 1; }; }

backup_so_or_abort() {
  # No || true — backup failure must abort before any mutant .so is copied.
  docker exec "${CONTAINER}" sh -c "cp -a '${SO_PATH}' '${SO_BAK}'"
  SO_BAK_SHA="$(docker exec "${CONTAINER}" sha256sum "${SO_BAK}" | awk '{print $1}')"
  if [ -z "${SO_BAK_SHA}" ] || [ "${#SO_BAK_SHA}" -ne 64 ]; then
    echo "error: failed to record SHA-256 of ${SO_BAK}" >&2
    return 1
  fi
  echo "backed up ${SO_PATH} sha256=${SO_BAK_SHA}" | tee -a "${REPORT}"
}

start_san_stub() {
  cat >"${HOST_DIR}/san.cnf" <<'EOF'
[req]
distinguished_name = dn
x509_extensions = v3_req
prompt = no
[dn]
CN = 127.0.0.1
[v3_req]
subjectAltName = @alt
[alt]
IP.1 = 127.0.0.1
EOF
  openssl req -x509 -newkey rsa:2048 \
    -keyout "${HOST_DIR}/stub.key" -out "${HOST_DIR}/stub.crt" \
    -days 1 -nodes -config "${HOST_DIR}/san.cnf" -extensions v3_req \
    >/dev/null 2>&1
  docker cp "${HOST_DIR}/stub.crt" "${CONTAINER}:${TLS_CERT}"
  docker cp "${HOST_DIR}/stub.key" "${CONTAINER}:${TLS_KEY}"
  docker cp "${SCRIPT_DIR}/ezkey-pam-tls-stub.py" \
    "${CONTAINER}:/tmp/ezkey-pam-tls-stub.py"
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${TLS_PID_FILE}' ]; then kill \"\$(cat '${TLS_PID_FILE}')\" 2>/dev/null || true; fi
     fuser -k ${TLS_PORT}/tcp >/dev/null 2>&1 || true
     rm -f '${TLS_REQ_LOG}'; : >'${TLS_REQ_LOG}'
     nohup python3 /tmp/ezkey-pam-tls-stub.py '${TLS_PORT}' '${TLS_CERT}' '${TLS_KEY}' \
       '${TLS_REQ_LOG}' '${TLS_PID_FILE}' >/tmp/ezkey-pam-tls-mut.log 2>&1 &
     true"
  local i
  for i in $(seq 1 50); do
    docker exec "${CONTAINER}" sh -c "ss -ltn | grep -q ':${TLS_PORT}'" 2>/dev/null && return 0
    sleep 0.1
  done
  echo "error: TLS stub did not listen on :${TLS_PORT}" >&2
  return 1
}

apply_mutant_conf() {
  docker exec "${CONTAINER}" sh -c \
    "cp -a '${CONF_PATH}' '${CONF_BAK}'
     sed -i 's/^lab_http=.*/lab_http=false/' '${CONF_PATH}'
     sed -i 's|^integration_api_url=.*|integration_api_url=https://127.0.0.1:${TLS_PORT}|' \
       '${CONF_PATH}'
     sed -i 's/^challenge_requested=.*/challenge_requested=false/' '${CONF_PATH}'
     chown root:root '${CONF_PATH}'; chmod 600 '${CONF_PATH}'
     : >'${TLS_REQ_LOG}'"
  CONF_SWAPPED=1
}

run_runtime_mut() {
  local id="$1"
  local desc="$2"
  shift 2
  echo "" | tee -a "${REPORT}"
  echo "--- ${id}: ${desc} (runtime / SAN stub) ---" | tee -a "${REPORT}"
  if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
    echo "[SURVIVED] ${id} — ${CONTAINER} not running (runtime proof required)" \
      | tee -a "${REPORT}"
    SURVIVED=$((SURVIVED + 1))
    return 0
  fi
  require_cmd openssl
  require_cmd docker

  restore_http
  rm -rf "${SCRATCH}/build"
  local sed_args=()
  local expr
  for expr in "$@"; do
    sed_args+=(-e "${expr}")
  done
  sed -i "${sed_args[@]}" "${SCRATCH}/src/ezkey_http.c"
  # Build mutant .so only (check-source would reject these; runtime is the claim).
  (cd "${SCRATCH}" && make all) >"${SCRATCH}/mut-build.out" 2>&1

  backup_so_or_abort
  docker cp "${SCRATCH}/build/pam_ezkey.so" "${CONTAINER}:${SO_PATH}"
  SO_SWAPPED=1

  start_san_stub
  apply_mutant_conf

  # shellcheck disable=SC1091
  . "${SCRIPT_DIR}/common.sh"
  local log="${ROOT}/runtime/artifacts/pam-tls-mut-${id}.log"
  mkdir -p "$(dirname "${log}")"
  set +e
  ezkey_ssh_kbdint_autoenter "${log}" 'echo SHOULD_NOT_AUTH; exit 0' 12
  set -e
  local reqs
  reqs="$(docker exec "${CONTAINER}" sh -c \
    "wc -l <'${TLS_REQ_LOG}' 2>/dev/null || echo 0" | tr -d ' \r')"
  reqs="${reqs:-0}"

  echo "${id} stub requests=${reqs}" | tee -a "${REPORT}"
  if [ "${reqs}" -ge 1 ]; then
    echo "[KILLED] ${id} runtime: mutant reached stub (reqs=${reqs}) — suite 0-req assert would fail" \
      | tee -a "${REPORT}"
    case "${id}" in
      M_tls_peer) RUNTIME_PEER_KILLED=1 ;;
      M_tls_both) RUNTIME_BOTH_KILLED=1 ;;
    esac
  else
    echo "[SURVIVED] ${id} runtime: still 0 requests (inconclusive)" | tee -a "${REPORT}"
    SURVIVED=$((SURVIVED + 1))
  fi

  # Restore before next mutant (EXIT trap also restores).
  restore_container
  restore_http
}

run_runtime_mut M_tls_peer "VERIFYPEER 0 alone" \
  's/CURLOPT_SSL_VERIFYPEER, 1L/CURLOPT_SSL_VERIFYPEER, 0L/'

run_runtime_mut M_tls_both "VERIFYPEER 0 + VERIFYHOST 0" \
  's/CURLOPT_SSL_VERIFYPEER, 1L/CURLOPT_SSL_VERIFYPEER, 0L/' \
  's/CURLOPT_SSL_VERIFYHOST, 2L/CURLOPT_SSL_VERIFYHOST, 0L/'

echo "" | tee -a "${REPORT}"
echo "=== Summary (honest layers) ===" | tee -a "${REPORT}"
echo "check-source kills: HOST alone → killed=${CHECKSOURCE_HOST_KILLED}" \
  | tee -a "${REPORT}"
echo "runtime kills:      PEER alone → killed=${RUNTIME_PEER_KILLED}" \
  | tee -a "${REPORT}"
echo "runtime kills:      both       → killed=${RUNTIME_BOTH_KILLED}" \
  | tee -a "${REPORT}"
echo "survived=${SURVIVED}" | tee -a "${REPORT}"

[ "${CHECKSOURCE_HOST_KILLED}" -eq 1 ]
[ "${RUNTIME_PEER_KILLED}" -eq 1 ]
[ "${RUNTIME_BOTH_KILLED}" -eq 1 ]
[ "${SURVIVED}" -eq 0 ]
