#!/usr/bin/env bash
# #1016 cycle 2b — re-run craft-review mutations on a scratch tree; prove make test kills them.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRATCH="$(mktemp -d /tmp/ezkey-pam-mut-XXXXXX)"
REPORT="/opt/cursor/artifacts/pam-mutation-cycle2b.txt"

cleanup() { rm -rf "${SCRATCH}"; }
trap cleanup EXIT

cp -a "${ROOT}/." "${SCRATCH}/"
rm -rf "${SCRATCH}/build"

killed=0
survived=0
: >"${REPORT}"
echo "=== #1016 cycle 2b mutation re-run ===" | tee -a "${REPORT}"

restore() {
  local f="$1"
  cp -a "${ROOT}/${f}" "${SCRATCH}/${f}"
}

run_mut() {
  local id="$1"
  local desc="$2"
  local file="$3"
  local target="$4"
  shift 4
  # remaining args: sed expressions applied in order (-e ...)

  echo "" | tee -a "${REPORT}"
  echo "--- ${id}: ${desc} ---" | tee -a "${REPORT}"
  restore "${file}"
  rm -rf "${SCRATCH}/build"
  local sed_args=()
  local expr
  for expr in "$@"; do
    sed_args+=(-e "${expr}")
  done
  sed -i "${sed_args[@]}" "${SCRATCH}/${file}"

  set +e
  (cd "${SCRATCH}" && make "${target}") >"${SCRATCH}/mut.out" 2>&1
  local rc=$?
  set -e
  if [ "${rc}" -eq 0 ]; then
    echo "[SURVIVED] ${id} (make ${target} exit 0)" | tee -a "${REPORT}"
    survived=$((survived + 1))
    tail -n 8 "${SCRATCH}/mut.out" | tee -a "${REPORT}" || true
  else
    echo "[KILLED] ${id} by make ${target} (rc=${rc})" | tee -a "${REPORT}"
    killed=$((killed + 1))
    grep -E 'FAIL|error:|Results:|check-source|check-hardening' "${SCRATCH}/mut.out" \
      | tail -n 12 | tee -a "${REPORT}" || true
  fi
  restore "${file}"
}

run_mut M_url "api_url_allowed always 1" src/ezkey_decision.h test-decision \
  '/lab_http && strncasecmp/{n;n;n;s/return 0;/return 1;/}'

run_mut M_http "pam_rc_for_http always SUCCESS" src/ezkey_decision.h test-decision \
  's/return PAM_AUTHINFO_UNAVAIL;/return PAM_SUCCESS;/'

run_mut M20 "ACCEPTED -> AUTH_ERR" src/ezkey_decision.h test-decision \
  '/strcmp(status_str, "ACCEPTED")/{n;s/PAM_SUCCESS/PAM_AUTH_ERR/;n;s/"SUCCESS"/"DENIED"/}'

run_mut M_rej "REJECTED -> SUCCESS" src/ezkey_decision.h test-decision \
  '/strcmp(status_str, "REJECTED")/,/summary_out = "DENIED"/{s/PAM_AUTH_ERR/PAM_SUCCESS/;s/"DENIED"/"SUCCESS"/}'

run_mut M_unk "unknown wait status continues" src/ezkey_decision.h test-decision \
  '/strcmp(status_str, "PENDING") != 0/,/return 1;/{s/\*pam_rc_out = PAM_AUTH_ERR;/*pam_rc_out = PAM_AUTH_ERR;/;s/return 1;/return 0;/}'

run_mut M_xfer "xferinfo never aborts" src/ezkey_decision.h test-decision \
  '/ezkey_xferinfo_should_abort/,/^}/{s/return 1;/return 0;/}'

run_mut M_confirm "confirm always true" src/ezkey_challenge.c test-decision \
  '/^int ezkey_prewait_response_confirmed/,/^}/{s/return 0;/return 1;/g}'

run_mut M_lab "lab_http default 1" src/ezkey_conf.c test-conf \
  '0,/cfg->lab_http = 0;/s/cfg->lab_http = 0;/cfg->lab_http = 1;/'

run_mut M_wipe "wipe_secrets no-op" src/ezkey_conf.c test-conf \
  '/void ezkey_config_wipe_secrets/,/^}/{s/ezkey_conf_secure_wipe.*;/;/g}'

run_mut M_tls "VERIFYPEER 0" src/ezkey_http.c check-source \
  's/CURLOPT_SSL_VERIFYPEER, 1L/CURLOPT_SSL_VERIFYPEER, 0L/'

run_mut M_mode "remove conf mode check" src/pam_ezkey.c check-source \
  '/mode != 0600 && mode != 0400/,/return -2;/{s/.*/    \/* mutated *\//}'

run_mut M_timeout "CURLOPT_TIMEOUT 0" src/pam_ezkey.c check-source \
  's/CURLOPT_TIMEOUT, timeout_seconds/CURLOPT_TIMEOUT, 0L/'

run_mut M_cancel "do_cancel=0 after create" src/pam_ezkey.c check-source \
  '0,/do_cancel = 1;/s/do_cancel = 1;/do_cancel = 0;/'

run_mut M_enter "MAX_ENTER 26" src/ezkey_config.h test-decision \
  's/#define EZKEY_MAX_ENTER_SECS 25/#define EZKEY_MAX_ENTER_SECS 26/'

run_mut M_line "DIAG_LINE_MAX 767" src/ezkey_diag.h test-decision \
  's/#define EZKEY_DIAG_LINE_MAX 768/#define EZKEY_DIAG_LINE_MAX 767/'

echo "" | tee -a "${REPORT}"
echo "=== Summary: killed=${killed} survived=${survived} ===" | tee -a "${REPORT}"
[ "${survived}" -eq 0 ]
