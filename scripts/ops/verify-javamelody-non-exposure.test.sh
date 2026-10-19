#!/usr/bin/env bash
# Unit checks for business-port acceptance in verify-javamelody-non-exposure.sh (#863).
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

# Extract and source only the pure helpers (avoid running the main script).
# shellcheck disable=SC1090
source <(
  awk '
    /^looks_like_javamelody_report\(\)/ { keep=1 }
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

# End-to-end dry path: fake curl returns 404 for business ports (auth + acme) — must PASS.
FAKE_BIN="${TMP}/fake-bin"
mkdir -p "${FAKE_BIN}"
cat >"${FAKE_BIN}/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -euo pipefail
# Honor curl --config write-out / output when present; otherwise simple -w mode.
cfg=""
out=""
write_out=""
url=""
i=1
while [[ $i -le $# ]]; do
  eval "arg=\${$i}"
  case "${arg}" in
    --config)
      i=$((i + 1))
      eval "cfg=\${$i}"
      ;;
    -o|--output)
      i=$((i + 1))
      eval "out=\${$i}"
      ;;
    -w|--write-out)
      i=$((i + 1))
      eval "write_out=\${$i}"
      ;;
    http://*|https://*)
      url="${arg}"
      ;;
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
        ;;
      'write-out = '*)
        write_out='%{http_code}'
        ;;
    esac
  done <"${cfg}"
fi
[[ -n "${out}" ]] && : >"${out}"
printf '404'
FAKE_CURL
chmod +x "${FAKE_BIN}/curl"

# docker must not be required for direct (non --via-caddy) business-port probes.
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

# Fake curl returns 200 + JM body → must FAIL.
cat >"${FAKE_BIN}/curl" <<'FAKE_CURL'
#!/usr/bin/env bash
set -euo pipefail
cfg=""
out=""
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
    esac
  done <"${cfg}"
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

echo
echo "Results: ${pass} passed, ${fail} failed"
if [[ "${fail}" -ne 0 ]]; then
  exit 1
fi
