#!/usr/bin/env bash
# Unit checks for check-ezkey-online-hosts.sh.
# No network. Run from repo root:
#   ./scripts/ops/check-ezkey-online-hosts.test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CHECK="${ROOT}/scripts/ops/check-ezkey-online-hosts.sh"

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

assert_ok "repo tree passes check-ezkey-online-hosts.sh" bash "${CHECK}"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

mkdir -p "${TMP}/docs/lightsail" "${TMP}/other"

# Minimal SSOT with not-served inventory (must be allowed).
cat >"${TMP}/docs/lightsail/community-host.md" <<'EOF'
## Product hostnames (ezkey.online)
| Host | Role |
| `auth-api.ezkey.online` | Auth API |
### Not served
| `n.ezkey.online` | does not exist |
| `auth.ezkey.online` | does not exist |
| `www.ezkey.online` | 530 pending Marc |
EOF

# Allowlisted host elsewhere — OK.
echo 'url=https://admin-api.ezkey.online' >"${TMP}/other/ok.md"
# Bare zone (not URL) — OK.
echo 'Community instance branding: ezkey.online' >>"${TMP}/other/ok.md"
# Wildcard prose — OK.
echo 'behind *.ezkey.online' >>"${TMP}/other/ok.md"

export CHECK_EZKEY_ONLINE_HOSTS_ROOT="${TMP}"
export CHECK_EZKEY_ONLINE_HOSTS_GIT=0

assert_ok "fixture with allowlist + SSOT not-served passes" bash "${CHECK}"

echo 'bad=https://n.ezkey.online' >"${TMP}/other/bad.md"
assert_fail "not-served host outside SSOT fails" bash "${CHECK}"
rm -f "${TMP}/other/bad.md"

echo 'bad=https://auth.ezkey.online' >"${TMP}/other/bad.md"
assert_fail "auth.ezkey.online outside SSOT fails" bash "${CHECK}"
rm -f "${TMP}/other/bad.md"

echo 'bad=https://ezkey.online/path' >"${TMP}/other/bad.md"
assert_fail "https://ezkey.online URL fails" bash "${CHECK}"
rm -f "${TMP}/other/bad.md"

echo 'bad=https://mystery.ezkey.online' >"${TMP}/other/bad.md"
assert_fail "unknown subdomain fails" bash "${CHECK}"
rm -f "${TMP}/other/bad.md"

if [[ "${fail}" -gt 0 ]]; then
  echo "check-ezkey-online-hosts.test: ${fail} failed, ${pass} passed" >&2
  exit 1
fi

echo "OK: check-ezkey-online-hosts.test (${pass} passed)"
exit 0
