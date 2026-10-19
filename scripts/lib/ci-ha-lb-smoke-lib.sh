# Shared HA LB smoke helpers (sourced by scripts/ci-ha-lb-smoke.sh and its unit test).
# shellcheck shell=bash

PROBE_ATTEMPTS="${PROBE_ATTEMPTS:-6}"
PROBE_SLEEP_S="${PROBE_SLEEP_S:-5}"

# curl wrapper — override in unit tests via PATH / CURL_BIN.
CURL_BIN="${CURL_BIN:-curl}"

probe_lb() {
  local name="$1"
  local url="$2"
  local expect="$3"
  local attempt=1
  local code=""
  local body_file
  body_file="$(mktemp)"

  while (( attempt <= PROBE_ATTEMPTS )); do
    code="$("${CURL_BIN}" -sS -o "${body_file}" -w '%{http_code}' --max-time 30 "$url" || true)"
    if [[ "$code" != "503" && "$code" == "$expect" ]]; then
      echo "PASS: ${name} HTTP ${code} (${url}) attempt=${attempt}/${PROBE_ATTEMPTS}"
      rm -f "${body_file}"
      return 0
    fi
    if (( attempt < PROBE_ATTEMPTS )); then
      echo "  … ${name} got HTTP ${code:-curl-fail}, retry ${attempt}/${PROBE_ATTEMPTS} in ${PROBE_SLEEP_S}s"
      sleep "${PROBE_SLEEP_S}"
    fi
    attempt=$((attempt + 1))
  done

  if [[ "$code" == "503" ]]; then
    echo "FAIL: ${name} returned 503 after ${PROBE_ATTEMPTS} attempts (HAProxy backends DOWN?) url=${url}" >&2
  else
    echo "FAIL: ${name} expected HTTP ${expect}, got ${code:-curl-fail} after ${PROBE_ATTEMPTS} attempts (budget $((PROBE_ATTEMPTS * PROBE_SLEEP_S))s) url=${url}" >&2
  fi
  head -c 500 "${body_file}" >&2 || true
  echo >&2
  rm -f "${body_file}"
  return 1
}
