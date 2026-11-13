#!/usr/bin/env bash
# Unit tests for probe_lb retry budget (#905 Patrick M2). No Docker / network.
# Run from repo root: ./scripts/ci-ha-lb-smoke.test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=lib/ci-ha-lb-smoke-lib.sh
source "${ROOT}/scripts/lib/ci-ha-lb-smoke-lib.sh"

pass=0
fail=0

assert_ok() {
  local name="$1"
  shift
  if "$@"; then
    echo "PASS: ${name}"
    pass=$((pass + 1))
  else
    echo "FAIL: ${name}" >&2
    fail=$((fail + 1))
  fi
}

assert_fail() {
  local name="$1"
  shift
  if "$@"; then
    echo "FAIL: ${name} (expected failure)" >&2
    fail=$((fail + 1))
  else
    echo "PASS: ${name}"
    pass=$((pass + 1))
  fi
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Fake curl: returns HTTP codes from SEQUENCE_FILE (one per invocation), writes body to -o.
install_fake_curl() {
  local sequence_csv="$1"
  printf '%s\n' "${sequence_csv}" | tr ',' '\n' >"${TMP}/sequence"
  echo 0 >"${TMP}/idx"
  cat >"${TMP}/fake-curl" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
out="/dev/null"
while [[ $# -gt 0 ]]; do
  case "$1" in
    -o)
      out="$2"
      shift 2
      ;;
    *)
      shift
      ;;
  esac
done
idx="$(cat "${DIR}/idx")"
mapfile -t codes <"${DIR}/sequence"
if (( idx >= ${#codes[@]} )); then
  echo "fake-curl: sequence exhausted (idx=${idx})" >&2
  printf '000'
  echo exhausted >"${out}"
  exit 0
fi
code="${codes[$idx]}"
echo $((idx + 1)) >"${DIR}/idx"
printf 'fake-body-%s' "${code}" >"${out}"
printf '%s' "${code}"
EOF
  chmod +x "${TMP}/fake-curl"
  export CURL_BIN="${TMP}/fake-curl"
}

export PROBE_ATTEMPTS=6
export PROBE_SLEEP_S=0

install_fake_curl "503,503,503,503,503,503"
assert_fail "probe_lb fails after 6×503" \
  probe_lb "fake-always-503" "http://127.0.0.1/fake" "200"

install_fake_curl "503,503,503,503,503,200"
assert_ok "probe_lb passes on 5×503 then 200" \
  probe_lb "fake-recover-200" "http://127.0.0.1/fake" "200"

filter_input=$'/ezkey-admin-api-1 1 running 0 starting
/ezkey-admin-api-2 0 running 0 healthy
/ezkey-auth-api-1 0 running 0 unhealthy
/ezkey-migration-ha 0 exited 0 none
/ezkey-db-grants-ha 0 exited 1 none
/ezkey-demo-device-ha 0 running 0 starting
/ezkey-haproxy-auth 0 running 0 none'
expected_names=$'ezkey-admin-api-1
ezkey-auth-api-1
ezkey-db-grants-ha
ezkey-demo-device-ha'
if [[ "$(ha_filter_containers_to_diagnose <<<"${filter_input}")" == "${expected_names}" ]]; then
  echo "PASS: diagnostics select restarted, unhealthy and failed containers (#1259)"
  pass=$((pass + 1))
else
  echo "FAIL: diagnostics container selection (#1259)" >&2
  fail=$((fail + 1))
fi

echo
echo "Test results: ${pass} passed, ${fail} failed"
[[ "${fail}" -eq 0 ]]
