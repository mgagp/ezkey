#!/usr/bin/env bash
# #1054 review — prove TLS verify mutants are killed by execution.
#
# Three mutants on src/ezkey_http.c:
#   PEER=0 alone   — check-source + (optional) behavioural stub suite
#   HOST=0 alone   — check-source (VERIFYHOST-only)
#   both=0         — check-source
#
# Behavioural PEER=0 kill (when EZKEY_PAM_CONTAINER is running): with a
# SAN-matching self-signed cert, VERIFYHOST alone no longer rejects; PEER=0
# lets the handshake complete and the TLS stub sees ≥1 request — so the
# stub-suite assertion (0 requests) would fail. We load the mutant .so and
# assert reqs≥1 to prove that kill path.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SCRATCH="$(mktemp -d /tmp/ezkey-pam-tls-mut-XXXXXX)"
REPORT="${EZKEY_PAM_ARTIFACT_DIR:-${ROOT}/runtime/artifacts}/pam-mutation-tls-verify-proof.txt"
mkdir -p "$(dirname "${REPORT}")"

cleanup() { rm -rf "${SCRATCH}"; }
trap cleanup EXIT

cp -a "${ROOT}/." "${SCRATCH}/"
rm -rf "${SCRATCH}/build"

killed=0
survived=0
: >"${REPORT}"
echo "=== #1054 TLS verify mutation proof ===" | tee -a "${REPORT}"

restore_http() {
  cp -a "${ROOT}/src/ezkey_http.c" "${SCRATCH}/src/ezkey_http.c"
}

run_checksource_mut() {
  local id="$1"
  local desc="$2"
  shift 2
  echo "" | tee -a "${REPORT}"
  echo "--- ${id}: ${desc} (make check-source) ---" | tee -a "${REPORT}"
  restore_http
  rm -rf "${SCRATCH}/build"
  local sed_args=()
  local expr
  for expr in "$@"; do
    sed_args+=(-e "${expr}")
  done
  sed -i "${sed_args[@]}" "${SCRATCH}/src/ezkey_http.c"
  set +e
  (cd "${SCRATCH}" && make check-source) >"${SCRATCH}/mut.out" 2>&1
  local rc=$?
  set -e
  if [ "${rc}" -eq 0 ]; then
    echo "[SURVIVED] ${id} (check-source exit 0)" | tee -a "${REPORT}"
    survived=$((survived + 1))
  else
    echo "[KILLED] ${id} by make check-source (rc=${rc})" | tee -a "${REPORT}"
    killed=$((killed + 1))
    grep -E 'VERIFYPEER|VERIFYHOST|error:' "${SCRATCH}/mut.out" | tail -n 6 \
      | tee -a "${REPORT}" || true
  fi
  restore_http
}

run_checksource_mut M_tls_peer "VERIFYPEER 0 alone" \
  's/CURLOPT_SSL_VERIFYPEER, 1L/CURLOPT_SSL_VERIFYPEER, 0L/'

run_checksource_mut M_tls_host "VERIFYHOST 0 alone" \
  's/CURLOPT_SSL_VERIFYHOST, 2L/CURLOPT_SSL_VERIFYHOST, 0L/'

run_checksource_mut M_tls_both "VERIFYPEER 0 + VERIFYHOST 0" \
  's/CURLOPT_SSL_VERIFYPEER, 1L/CURLOPT_SSL_VERIFYPEER, 0L/' \
  's/CURLOPT_SSL_VERIFYHOST, 2L/CURLOPT_SSL_VERIFYHOST, 0L/'

# --- Optional behavioural PEER=0 kill (needs live demo container) ---
CONTAINER="${EZKEY_PAM_CONTAINER:-ezkey-pam-ssh}"
TLS_PORT=18443
TLS_REQ_LOG="/tmp/ezkey-pam-tls-mut-reqs.log"
TLS_CERT="/tmp/ezkey-pam-tls-mut.crt"
TLS_KEY="/tmp/ezkey-pam-tls-mut.key"
TLS_PID_FILE="/tmp/ezkey-pam-tls-mut.pid"
SO_PATH="/lib64/security/pam_ezkey.so"
SO_BAK="/tmp/pam_ezkey.so.tls-mut.bak"
HOST_DIR="${SCRATCH}/tls-mut-certs"
mkdir -p "${HOST_DIR}"

behavioural_peer0() {
  echo "" | tee -a "${REPORT}"
  echo "--- M_tls_peer behavioural: PEER=0 alone vs SAN-matching stub ---" \
    | tee -a "${REPORT}"
  if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER}"; then
    echo "[SKIP] behavioural PEER=0 — ${CONTAINER} not running" | tee -a "${REPORT}"
    return 0
  fi
  require_cmd() { command -v "$1" >/dev/null 2>&1 || { echo "missing $1" >&2; return 1; }; }
  require_cmd openssl
  require_cmd docker

  restore_http
  rm -rf "${SCRATCH}/build"
  sed -i 's/CURLOPT_SSL_VERIFYPEER, 1L/CURLOPT_SSL_VERIFYPEER, 0L/' \
    "${SCRATCH}/src/ezkey_http.c"
  # Build .so only (do not run check-source — that already killed this mutant).
  (cd "${SCRATCH}" && make all) >"${SCRATCH}/mut-build.out" 2>&1

  # SAN must match the conf URL host so VERIFYHOST alone cannot reject.
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

  docker exec "${CONTAINER}" sh -c \
    "cp -a '${SO_PATH}' '${SO_BAK}'" || true
  docker cp "${SCRATCH}/build/pam_ezkey.so" "${CONTAINER}:${SO_PATH}"
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
    docker exec "${CONTAINER}" sh -c "ss -ltn | grep -q ':${TLS_PORT}'" 2>/dev/null && break
    sleep 0.1
  done

  docker exec "${CONTAINER}" sh -c \
    "cp -a /etc/security/pam_ezkey.conf /tmp/pam_ezkey.conf.tls-mut.bak
     sed -i 's/^lab_http=.*/lab_http=false/' /etc/security/pam_ezkey.conf
     sed -i 's|^integration_api_url=.*|integration_api_url=https://127.0.0.1:${TLS_PORT}|' \
       /etc/security/pam_ezkey.conf
     sed -i 's/^challenge_requested=.*/challenge_requested=false/' /etc/security/pam_ezkey.conf
     chown root:root /etc/security/pam_ezkey.conf; chmod 600 /etc/security/pam_ezkey.conf
     : >'${TLS_REQ_LOG}'"

  # One interactive attempt — PEER=0 + matching SAN should reach the stub.
  # shellcheck disable=SC1091
  . "${SCRIPT_DIR}/common.sh"
  local log="${ROOT}/runtime/artifacts/pam-tls-mut-peer0.log"
  mkdir -p "$(dirname "${log}")"
  set +e
  ezkey_ssh_kbdint_autoenter "${log}" 'echo SHOULD_NOT_AUTH; exit 0' 12
  set -e
  local reqs
  reqs="$(docker exec "${CONTAINER}" sh -c \
    "wc -l <'${TLS_REQ_LOG}' 2>/dev/null || echo 0" | tr -d ' \r')"
  reqs="${reqs:-0}"

  # Restore pristine .so + conf + stub.
  docker exec "${CONTAINER}" sh -c \
    "if [ -f '${SO_BAK}' ]; then cp -a '${SO_BAK}' '${SO_PATH}'; fi
     if [ -f /tmp/pam_ezkey.conf.tls-mut.bak ]; then
       cp -a /tmp/pam_ezkey.conf.tls-mut.bak /etc/security/pam_ezkey.conf
       chown root:root /etc/security/pam_ezkey.conf; chmod 600 /etc/security/pam_ezkey.conf
     fi
     if [ -f '${TLS_PID_FILE}' ]; then kill \"\$(cat '${TLS_PID_FILE}')\" 2>/dev/null || true; fi
     fuser -k ${TLS_PORT}/tcp >/dev/null 2>&1 || true" || true

  echo "behavioural PEER=0 stub requests=${reqs}" | tee -a "${REPORT}"
  if [ "${reqs}" -ge 1 ]; then
    echo "[KILLED] M_tls_peer behavioural: PEER=0 alone reached stub (reqs=${reqs}) — suite 0-req assert would fail" \
      | tee -a "${REPORT}"
    killed=$((killed + 1))
  else
    echo "[SURVIVED] M_tls_peer behavioural: PEER=0 alone still 0 requests (SAN/PEER inconclusive)" \
      | tee -a "${REPORT}"
    survived=$((survived + 1))
  fi
  restore_http
}

behavioural_peer0

echo "" | tee -a "${REPORT}"
echo "=== Summary: killed=${killed} survived=${survived} ===" | tee -a "${REPORT}"
[ "${survived}" -eq 0 ]
