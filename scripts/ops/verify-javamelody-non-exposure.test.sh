#!/usr/bin/env bash
# Unit checks for business-port acceptance in verify-javamelody-non-exposure.sh (#863 B2).
# No network: sources classification helpers only (via a tiny extracted harness).
# Run from repo root: ./scripts/ops/verify-javamelody-non-exposure.test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="${ROOT}/scripts/ops/verify-javamelody-non-exposure.sh"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

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

assert_eq() {
  local name="$1"
  local expected="$2"
  local actual="$3"
  if [[ "${actual}" == "${expected}" ]]; then
    echo "PASS: ${name}"
    pass=$((pass + 1))
  else
    echo "FAIL: ${name} (expected='${expected}' actual='${actual}')" >&2
    fail=$((fail + 1))
  fi
}

# Extract and source only the pure helpers (avoid running the main script).
# Order matters: looks_like → classify → is_ok.
# shellcheck disable=SC1090
source <(
  awk '
    /^looks_like_javamelody_report\(\)/ { keep=1 }
    /^business_port_probe_classify\(\)/ { keep=1 }
    /^business_port_probe_is_ok\(\)/ { keep=1 }
    keep { print }
    /^}$/ && keep { keep=0; print "" }
  ' "${SCRIPT}"
)

empty="${TMP}/empty.html"
: >"${empty}"
jm="${TMP}/jm.html"
printf '%s\n' '<html><body>Monitoring on JavaMelody</body></html>' >"${jm}"
plain200="${TMP}/ok.html"
printf '%s\n' '<html><body>hello</body></html>' >"${plain200}"

assert_ok "404 empty body is non-exposed" business_port_probe_is_ok 404 "${empty}"
assert_ok "401 empty body is non-exposed" business_port_probe_is_ok 401 "${empty}"
assert_ok "403 empty body is non-exposed" business_port_probe_is_ok 403 "${empty}"
assert_ok "404 plain HTML is non-exposed" business_port_probe_is_ok 404 "${plain200}"

assert_fail "200 empty body is exposed" business_port_probe_is_ok 200 "${empty}"
assert_fail "200 plain HTML is exposed" business_port_probe_is_ok 200 "${plain200}"
assert_fail "200 JM HTML is exposed" business_port_probe_is_ok 200 "${jm}"
assert_fail "404 with JM HTML is exposed" business_port_probe_is_ok 404 "${jm}"
assert_fail "401 with JM HTML is exposed" business_port_probe_is_ok 401 "${jm}"

# Inconclusive codes (#863 B2): must NOT count as OK.
assert_fail "000 is inconclusive (not OK)" business_port_probe_is_ok 000 "${empty}"
assert_fail "302 is inconclusive (not OK)" business_port_probe_is_ok 302 "${empty}"
assert_fail "500 is inconclusive (not OK)" business_port_probe_is_ok 500 "${empty}"
assert_fail "204 is inconclusive (not OK)" business_port_probe_is_ok 204 "${empty}"

assert_eq "classify 404 → ok" "ok" "$(business_port_probe_classify 404 "${empty}")"
assert_eq "classify 200 → exposed" "exposed" "$(business_port_probe_classify 200 "${empty}")"
assert_eq "classify 000 → inconclusive" "inconclusive" "$(business_port_probe_classify 000 "${empty}")"
assert_eq "classify 302 → inconclusive" "inconclusive" "$(business_port_probe_classify 302 "${empty}")"
assert_eq "classify 500 → inconclusive" "inconclusive" "$(business_port_probe_classify 500 "${empty}")"
assert_eq "classify 404+JM → exposed" "exposed" "$(business_port_probe_classify 404 "${jm}")"

# End-to-end: fake curl returns 404 for business ports (auth + acme) — must PASS.
# (Covers both /actuator/health and /monitoring probes.)
FAKE_BIN="${TMP}/fake-bin"
mkdir -p "${FAKE_BIN}"
cat >"${FAKE_BIN}/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -euo pipefail
cfg=""
out=""
i=1
while [[ $i -le $# ]]; do
  eval "arg=\${$i}"
  case "${arg}" in
    --config)
      i=$((i + 1))
      eval "cfg=\${$i}"
      ;;
  esac
  i=$((i + 1))
done
if [[ -n "${cfg}" && -f "${cfg}" ]]; then
  while IFS= read -r line || [[ -n "${line}" ]]; do
    case "${line}" in
      'output = '*)
        out="${line#output = }"
        out="${out%\"}"
        out="${out#\"}"
        ;;
    esac
  done <"${cfg}"
fi
[[ -n "${out}" ]] && : >"${out}"
printf '404'
FAKE_CURL
chmod +x "${FAKE_BIN}/curl"

cat >"${FAKE_BIN}/docker" <<'FAKE_DOCKER'
#!/usr/bin/env bash
echo "error: FAKE docker should not run in this test" >&2
exit 99
FAKE_DOCKER
chmod +x "${FAKE_BIN}/docker"

OUT="${TMP}/out"
ERR="${TMP}/err"
if env PATH="${FAKE_BIN}:${PATH}" \
  BUSINESS_PORT_URLS="http://auth-api:8080 http://demo-app-acme:8082" \
  bash "${SCRIPT}" --business-ports >"${OUT}" 2>"${ERR}"; then
  if grep -q 'OK  404' "${OUT}" && grep -q 'PASS' "${OUT}"; then
    echo "PASS: --business-ports with 404 on auth+acme exits 0"
    pass=$((pass + 1))
  else
    echo "FAIL: expected OK 404 + PASS in stdout" >&2
    cat "${OUT}" >&2
    cat "${ERR}" >&2
    fail=$((fail + 1))
  fi
else
  echo "FAIL: --business-ports with 404 should exit 0" >&2
  cat "${OUT}" >&2
  cat "${ERR}" >&2
  fail=$((fail + 1))
fi

# Path-aware fake: 404 for /actuator/health, 200+JM for /monitoring → must FAIL (exposure).
cat >"${FAKE_BIN}/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -euo pipefail
cfg=""
out=""
url=""
i=1
while [[ $i -le $# ]]; do
  eval "arg=\${$i}"
  case "${arg}" in
    --config) i=$((i + 1)); eval "cfg=\${$i}" ;;
  esac
  i=$((i + 1))
done
if [[ -n "${cfg}" && -f "${cfg}" ]]; then
  while IFS= read -r line || [[ -n "${line}" ]]; do
    case "${line}" in
      'output = '*)
        out="${line#output = }"
        out="${out%\"}"
        out="${out#\"}"
        ;;
      'url = '*)
        url="${line#url = }"
        url="${url%\"}"
        url="${url#\"}"
        ;;
    esac
  done <"${cfg}"
fi
if [[ "${url}" == *'/actuator/health'* ]]; then
  [[ -n "${out}" ]] && : >"${out}"
  printf '404'
  exit 0
fi
if [[ -n "${out}" ]]; then
  printf '%s\n' '<html>Monitoring on JavaMelody</html>' >"${out}"
fi
printf '200'
FAKE_CURL
chmod +x "${FAKE_BIN}/curl"

if env PATH="${FAKE_BIN}:${PATH}" \
  BUSINESS_PORT_URLS="http://auth-api:8080" \
  bash "${SCRIPT}" --business-ports >"${OUT}" 2>"${ERR}"; then
  echo "FAIL: 200 JM report should exit non-zero" >&2
  cat "${OUT}" >&2
  fail=$((fail + 1))
else
  if grep -q 'JavaMelody report on business port' "${ERR}"; then
    echo "PASS: --business-ports with 200 JM report exits non-zero"
    pass=$((pass + 1))
  else
    echo "FAIL: expected JM report failure message" >&2
    cat "${ERR}" >&2
    fail=$((fail + 1))
  fi
fi

# Path-aware: 404 health, connect failure on /monitoring → inconclusive.
cat >"${FAKE_BIN}/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -euo pipefail
cfg=""
url=""
i=1
while [[ $i -le $# ]]; do
  eval "arg=\${$i}"
  case "${arg}" in
    --config) i=$((i + 1)); eval "cfg=\${$i}" ;;
  esac
  i=$((i + 1))
done
if [[ -n "${cfg}" && -f "${cfg}" ]]; then
  while IFS= read -r line || [[ -n "${line}" ]]; do
    case "${line}" in
      'url = '*)
        url="${line#url = }"
        url="${url%\"}"
        url="${url#\"}"
        ;;
      'output = '*)
        out="${line#output = }"
        out="${out%\"}"
        out="${out#\"}"
        [[ -n "${out}" ]] && : >"${out}"
        ;;
    esac
  done <"${cfg}"
fi
if [[ "${url}" == *'/actuator/health'* ]]; then
  printf '404'
  exit 0
fi
exit 1
FAKE_CURL
chmod +x "${FAKE_BIN}/curl"

if env PATH="${FAKE_BIN}:${PATH}" \
  BUSINESS_PORT_URLS="http://auth-api:8080" \
  bash "${SCRIPT}" --business-ports >"${OUT}" 2>"${ERR}"; then
  echo "FAIL: 000 inconclusive should exit non-zero" >&2
  fail=$((fail + 1))
else
  if grep -qE 'FAIL 000 .*probe inconclusive' "${ERR}"; then
    echo "PASS: --business-ports with 000 reports inconclusive with real code"
    pass=$((pass + 1))
  else
    echo "FAIL: expected FAIL 000 … probe inconclusive" >&2
    cat "${ERR}" >&2
    fail=$((fail + 1))
  fi
fi

# Path-aware: 404 health, 302 monitoring → inconclusive with real code.
cat >"${FAKE_BIN}/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -euo pipefail
cfg=""
out=""
url=""
i=1
while [[ $i -le $# ]]; do
  eval "arg=\${$i}"
  case "${arg}" in
    --config) i=$((i + 1)); eval "cfg=\${$i}" ;;
  esac
  i=$((i + 1))
done
if [[ -n "${cfg}" && -f "${cfg}" ]]; then
  while IFS= read -r line || [[ -n "${line}" ]]; do
    case "${line}" in
      'output = '*)
        out="${line#output = }"
        out="${out%\"}"
        out="${out#\"}"
        ;;
      'url = '*)
        url="${line#url = }"
        url="${url%\"}"
        url="${url#\"}"
        ;;
    esac
  done <"${cfg}"
fi
[[ -n "${out}" ]] && : >"${out}"
if [[ "${url}" == *'/actuator/health'* ]]; then
  printf '404'
else
  printf '302'
fi
FAKE_CURL
chmod +x "${FAKE_BIN}/curl"

if env PATH="${FAKE_BIN}:${PATH}" \
  BUSINESS_PORT_URLS="http://auth-api:8080" \
  bash "${SCRIPT}" --business-ports >"${OUT}" 2>"${ERR}"; then
  echo "FAIL: 302 inconclusive should exit non-zero" >&2
  fail=$((fail + 1))
else
  if grep -qE 'FAIL 302 .*probe inconclusive' "${ERR}"; then
    echo "PASS: --business-ports with 302 reports inconclusive with real code"
    pass=$((pass + 1))
  else
    echo "FAIL: expected FAIL 302 … probe inconclusive" >&2
    cat "${ERR}" >&2
    fail=$((fail + 1))
  fi
fi

# Path-aware: 404 health, 500 monitoring → inconclusive.
cat >"${FAKE_BIN}/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -euo pipefail
cfg=""
out=""
url=""
i=1
while [[ $i -le $# ]]; do
  eval "arg=\${$i}"
  case "${arg}" in
    --config) i=$((i + 1)); eval "cfg=\${$i}" ;;
  esac
  i=$((i + 1))
done
if [[ -n "${cfg}" && -f "${cfg}" ]]; then
  while IFS= read -r line || [[ -n "${line}" ]]; do
    case "${line}" in
      'output = '*)
        out="${line#output = }"
        out="${out%\"}"
        out="${out#\"}"
        ;;
      'url = '*)
        url="${line#url = }"
        url="${url%\"}"
        url="${url#\"}"
        ;;
    esac
  done <"${cfg}"
fi
[[ -n "${out}" ]] && : >"${out}"
if [[ "${url}" == *'/actuator/health'* ]]; then
  printf '404'
else
  printf '500'
fi
FAKE_CURL
chmod +x "${FAKE_BIN}/curl"

if env PATH="${FAKE_BIN}:${PATH}" \
  BUSINESS_PORT_URLS="http://demo-app-acme:8082" \
  bash "${SCRIPT}" --business-ports >"${OUT}" 2>"${ERR}"; then
  echo "FAIL: 500 inconclusive should exit non-zero" >&2
  fail=$((fail + 1))
else
  if grep -qE 'FAIL 500 .*probe inconclusive' "${ERR}"; then
    echo "PASS: --business-ports with 500 reports inconclusive with real code"
    pass=$((pass + 1))
  else
    echo "FAIL: expected FAIL 500 … probe inconclusive" >&2
    cat "${ERR}" >&2
    fail=$((fail + 1))
  fi
fi

# --check-jm-overlay-bind against the real community overlay (must set 0.0.0.0).
if bash "${SCRIPT}" --check-jm-overlay-bind \
  "${ROOT}/experimental-hybrid/lightsail" >"${OUT}" 2>"${ERR}"; then
  if grep -q 'MANAGEMENT_SERVER_ADDRESS=0.0.0.0' "${OUT}"; then
    echo "PASS: --check-jm-overlay-bind accepts community overlay"
    pass=$((pass + 1))
  else
    echo "FAIL: expected OK overlay message" >&2
    cat "${OUT}" >&2
    fail=$((fail + 1))
  fi
else
  echo "FAIL: community JM overlay should pass --check-jm-overlay-bind" >&2
  cat "${OUT}" >&2
  cat "${ERR}" >&2
  fail=$((fail + 1))
fi

# Overlay missing 0.0.0.0 → FAIL (no real deploy / token).
BAD_OVERLAY_DIR="${TMP}/bad-overlay"
mkdir -p "${BAD_OVERLAY_DIR}"
printf '%s\n' 'services: {}' >"${BAD_OVERLAY_DIR}/docker-compose.javamelody.yml"
if bash "${SCRIPT}" --check-jm-overlay-bind "${BAD_OVERLAY_DIR}" >"${OUT}" 2>"${ERR}"; then
  echo "FAIL: overlay without 0.0.0.0 should exit non-zero" >&2
  fail=$((fail + 1))
else
  if grep -q 'MANAGEMENT_SERVER_ADDRESS is not 0.0.0.0' "${ERR}"; then
    echo "PASS: --check-jm-overlay-bind rejects overlay without 0.0.0.0"
    pass=$((pass + 1))
  else
    echo "FAIL: expected overlay address failure message" >&2
    cat "${ERR}" >&2
    fail=$((fail + 1))
  fi
fi

echo
echo "Results: ${pass} passed, ${fail} failed"
if [[ "${fail}" -ne 0 ]]; then
  exit 1
fi
