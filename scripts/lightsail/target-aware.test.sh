#!/usr/bin/env bash
# Dry-run / unit checks for Lightsail SSH target resolution (#812).
# Run from repo root: ./scripts/lightsail/target-aware.test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
COMMON="${ROOT}/scripts/lightsail/common.sh"
EXPORT="${ROOT}/experimental-hybrid/scripts/export-backend-images-to-lightsail.sh"
UPGRADE="${ROOT}/experimental-hybrid/scripts/full-exp-environment-upgrade.sh"
BOOTSTRAP="${ROOT}/scripts/lightsail/bootstrap-host.sh"
CREATE="${ROOT}/scripts/lightsail/create-instance.sh"

pass=0
fail=0

assert_ok() {
  local name="$1"
  shift
  if "$@" >/tmp/lightsail-target-test.out 2>/tmp/lightsail-target-test.err; then
    echo "PASS: ${name}"
    pass=$((pass + 1))
  else
    echo "FAIL: ${name} (expected success)" >&2
    echo "--- stdout ---" >&2
    cat /tmp/lightsail-target-test.out >&2 || true
    echo "--- stderr ---" >&2
    cat /tmp/lightsail-target-test.err >&2 || true
    fail=$((fail + 1))
  fi
}

assert_fail() {
  local name="$1"
  shift
  if "$@" >/tmp/lightsail-target-test.out 2>/tmp/lightsail-target-test.err; then
    echo "FAIL: ${name} (expected failure)" >&2
    echo "--- stdout ---" >&2
    cat /tmp/lightsail-target-test.out >&2 || true
    fail=$((fail + 1))
  else
    echo "PASS: ${name}"
    pass=$((pass + 1))
  fi
}

assert_stdout_contains() {
  local name="$1"
  local needle="$2"
  if grep -qF -- "${needle}" /tmp/lightsail-target-test.out; then
    echo "PASS: ${name}"
    pass=$((pass + 1))
  else
    echo "FAIL: ${name} (stdout missing: ${needle})" >&2
    cat /tmp/lightsail-target-test.out >&2 || true
    fail=$((fail + 1))
  fi
}

assert_stderr_contains() {
  local name="$1"
  local needle="$2"
  if grep -qF -- "${needle}" /tmp/lightsail-target-test.err; then
    echo "PASS: ${name}"
    pass=$((pass + 1))
  else
    echo "FAIL: ${name} (stderr missing: ${needle})" >&2
    cat /tmp/lightsail-target-test.err >&2 || true
    fail=$((fail + 1))
  fi
}

# --- common.sh helpers ---
(
  # shellcheck source=common.sh
  source "${COMMON}"
  unset LIGHTSAIL_SSH_HOST || true
  LIGHTSAIL_TARGET=""
  # lightsail_die uses exit — nest so we can assert without killing this shell.
  if (lightsail_resolve_ssh_target "") 2>/tmp/lightsail-target-test.err; then
    echo "FAIL: resolve without target should die" >&2
    exit 1
  fi
  grep -q 'no Lightsail SSH target declared' /tmp/lightsail-target-test.err
  echo "PASS: common resolve fails without target"
) && pass=$((pass + 1)) || fail=$((fail + 1))

(
  # shellcheck source=common.sh
  source "${COMMON}"
  unset LIGHTSAIL_SSH_HOST || true
  LIGHTSAIL_TARGET=""
  lightsail_resolve_ssh_target "community"
  [[ "${LIGHTSAIL_SSH_HOST}" == "ezkey-online" ]]
  [[ "${LIGHTSAIL_TARGET}" == "community" ]]
  src="$(lightsail_caddyfile_source_for_sync "${ROOT}/experimental-hybrid/lightsail")"
  [[ "$(basename "${src}")" == "Caddyfile.ezkey-online" ]]
  echo "PASS: community maps host + Caddyfile.ezkey-online"
) && pass=$((pass + 1)) || fail=$((fail + 1))

(
  # shellcheck source=common.sh
  source "${COMMON}"
  unset LIGHTSAIL_SSH_HOST || true
  LIGHTSAIL_TARGET=""
  lightsail_resolve_ssh_target "exp1"
  [[ "${LIGHTSAIL_SSH_HOST}" == "ezkey" ]]
  [[ "${LIGHTSAIL_TARGET}" == "exp1" ]]
  src="$(lightsail_caddyfile_source_for_sync "${ROOT}/experimental-hybrid/lightsail")"
  [[ "$(basename "${src}")" == "Caddyfile" ]]
  echo "PASS: exp1 maps host + EXP1 Caddyfile"
) && pass=$((pass + 1)) || fail=$((fail + 1))

# --- export: no target fails loudly ---
unset LIGHTSAIL_SSH_HOST || true
assert_fail "export without target fails" bash "${EXPORT}" --dry-run --apis-only
assert_stderr_contains "export fail mentions #812 or target" "no Lightsail SSH target declared"

# --- export: community dry-run uses ezkey-online + community Caddyfile ---
assert_ok "export --target community --dry-run --sync" \
  bash "${EXPORT}" --target community --dry-run --apis-only --sync-operator-files
assert_stdout_contains "community dry-run SSH host" "SSH: ezkey-online"
assert_stdout_contains "community dry-run Caddyfile source" "Caddyfile source=Caddyfile.ezkey-online"
assert_stdout_contains "community dry-run scp Caddyfile.ezkey-online" "Caddyfile.ezkey-online"
# Must never scp the EXP1-named Caddyfile as the Caddyfile source for community
if grep -E 'scp .*lightsail/Caddyfile ' /tmp/lightsail-target-test.out | grep -vq 'Caddyfile.ezkey-online'; then
  echo "FAIL: community sync must not scp EXP1 Caddyfile" >&2
  fail=$((fail + 1))
else
  echo "PASS: community sync does not scp EXP1 Caddyfile"
  pass=$((pass + 1))
fi

# --- export: exp1 dry-run ---
assert_ok "export --target exp1 --dry-run --sync" \
  bash "${EXPORT}" --target exp1 --dry-run --apis-only --sync-operator-files
assert_stdout_contains "exp1 dry-run SSH host" "SSH: ezkey"
assert_stdout_contains "exp1 dry-run Caddyfile source" "Caddyfile source=Caddyfile"
assert_stdout_contains "exp1 dry-run scp EXP1 Caddyfile" "lightsail/Caddyfile"

# --- export: LIGHTSAIL_SSH_HOST alone ---
assert_ok "export via LIGHTSAIL_SSH_HOST=ezkey-online" \
  env LIGHTSAIL_SSH_HOST=ezkey-online bash "${EXPORT}" --dry-run --apis-only
assert_stdout_contains "env host community" "SSH: ezkey-online"

# --- full upgrade: no target fails ---
assert_fail "upgrade without target fails" bash "${UPGRADE}" rolling --dry-run
assert_stderr_contains "upgrade fail mentions target" "no Lightsail SSH target declared"

# --- full upgrade: both targets dry-run ---
assert_ok "upgrade --target community rolling --dry-run" \
  bash "${UPGRADE}" --target community rolling --dry-run
assert_stdout_contains "upgrade community host" "LIGHTSAIL_SSH_HOST: ezkey-online"

assert_ok "upgrade --target exp1 rolling --dry-run" \
  bash "${UPGRADE}" --target exp1 rolling --dry-run
assert_stdout_contains "upgrade exp1 host" "LIGHTSAIL_SSH_HOST: ezkey"

# --- bootstrap ---
assert_fail "bootstrap without target fails" bash "${BOOTSTRAP}" --dry-run
assert_ok "bootstrap --target community --dry-run" bash "${BOOTSTRAP}" --target community --dry-run
assert_stdout_contains "bootstrap community" "ezkey-online"

# --- create-instance ---
assert_fail "create without target fails" bash "${CREATE}"
assert_ok "create --target community dry-run" bash "${CREATE}" --target community
assert_fail "create --target exp1 refused" bash "${CREATE}" --target exp1

echo
echo "Results: ${pass} passed, ${fail} failed"
if [[ "${fail}" -ne 0 ]]; then
  exit 1
fi
