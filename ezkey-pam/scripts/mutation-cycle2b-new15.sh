#!/usr/bin/env bash
# #1016 cycle 2b — 15 NEW decision-path mutations (not the craft set).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRATCH="$(mktemp -d /tmp/ezkey-pam-mut15-XXXXXX)"
REPORT="/opt/cursor/artifacts/pam-mutation-cycle2b-new15.txt"

cleanup() { rm -rf "${SCRATCH}"; }
trap cleanup EXIT

cp -a "${ROOT}/." "${SCRATCH}/"
rm -rf "${SCRATCH}/build"

killed=0
survived=0
: >"${REPORT}"
echo "=== #1016 cycle 2b NEW-15 mutation sweep ===" | tee -a "${REPORT}"

restore() { cp -a "${ROOT}/$1" "${SCRATCH}/$1"; }

run_mut() {
  local id="$1" desc="$2" file="$3" target="$4" sed_expr="$5"
  echo "" | tee -a "${REPORT}"
  echo "--- ${id}: ${desc} ---" | tee -a "${REPORT}"
  restore "${file}"
  rm -rf "${SCRATCH}/build"
  sed -i -e "${sed_expr}" "${SCRATCH}/${file}"
  set +e
  (cd "${SCRATCH}" && make "${target}") >"${SCRATCH}/mut.out" 2>&1
  local rc=$?
  set -e
  if [ "${rc}" -eq 0 ]; then
    echo "[SURVIVED] ${id}" | tee -a "${REPORT}"
    survived=$((survived + 1))
    tail -n 6 "${SCRATCH}/mut.out" | tee -a "${REPORT}" || true
  else
    echo "[KILLED] ${id} by make ${target} (rc=${rc})" | tee -a "${REPORT}"
    killed=$((killed + 1))
    grep -E 'FAIL|error:' "${SCRATCH}/mut.out" | tail -n 4 | tee -a "${REPORT}" || true
  fi
  restore "${file}"
}

run_mut N1 "EXPIRED -> SUCCESS" src/ezkey_decision.h test-decision \
  '/strcmp(status_str, "EXPIRED")/,/summary_out = "EXPIRED"/{s/PAM_AUTH_ERR/PAM_SUCCESS/;s/"EXPIRED"/"SUCCESS"/}'

run_mut N2 "PENDING continue flipped" src/ezkey_decision.h test-decision \
  's/strcmp(status_str, "PENDING") != 0/strcmp(status_str, "PENDING") == 0/'

run_mut N3 "https return 0" src/ezkey_decision.h test-decision \
  '/strncasecmp(url, "https:\/\//,/return 1;/s/return 1;/return 0;/'

run_mut N4 "NULL URL allowed" src/ezkey_decision.h test-decision \
  's/if (url == NULL || url\[0\] == '\''\\0'\'')/if (0)/'

run_mut N5 "lab_http gate removed" src/ezkey_decision.h test-decision \
  's/lab_http && strncasecmp/1 \&\& strncasecmp/'

run_mut N6 "HTTP always SUCCESS" src/ezkey_decision.h test-decision \
  's/return PAM_AUTHINFO_UNAVAIL;/return PAM_SUCCESS;/'

run_mut N7 "VERIFYHOST 0" src/ezkey_http.c check-source \
  's/CURLOPT_SSL_VERIFYHOST, 2L/CURLOPT_SSL_VERIFYHOST, 0L/'

run_mut N8 "NETRC optional" src/ezkey_http.c check-source \
  's/CURL_NETRC_IGNORED/CURL_NETRC_OPTIONAL/'

run_mut N9 "challenge default 0" src/ezkey_config.h test-conf \
  's/EZKEY_DEFAULT_CHALLENGE_REQUESTED 1/EZKEY_DEFAULT_CHALLENGE_REQUESTED 0/'

run_mut N10 "BODY_MAX 1MiB" src/ezkey_http.h test-http \
  's/(64 \* 1024)/(1024 * 1024)/'

run_mut N11 "PROXY NULL" src/ezkey_http.c check-source \
  's/CURLOPT_PROXY, ""/CURLOPT_PROXY, NULL/'

run_mut N12 "S_ISREG always false-negated" src/pam_ezkey.c check-source \
  's/!S_ISREG(st.st_mode)/0/'

run_mut N13 "uid check removed" src/pam_ezkey.c check-source \
  's/st.st_uid != 0 || st.st_gid != 0/0/'

run_mut N14 "confirm returns 1 on reject path" src/ezkey_challenge.c test-decision \
  '/^int ezkey_prewait_response_confirmed/,/^}/{s/return 0;/return 1;/}'

run_mut N15 "SSLKEYLOGFILE never detected" src/ezkey_http.c test-http \
  's/return (v != NULL) ? 1 : 0;/return 0;/'

echo "" | tee -a "${REPORT}"
echo "=== Summary: killed=${killed} survived=${survived} ===" | tee -a "${REPORT}"
# Copy counts for PR; survivors fixed below if cheap.
if [ "${survived}" -gt 0 ]; then
  echo "Survivors present — inspect ${REPORT}" | tee -a "${REPORT}"
fi
exit 0
