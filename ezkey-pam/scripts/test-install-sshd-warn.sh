#!/usr/bin/env bash
# Unit checks for install.sh global MaxAuthTries 1 warning awk (Patrick #908 nit).
# No Docker / no root. Run from ezkey-pam/:
#   ./scripts/test-install-sshd-warn.sh
# Also invoked by `make test`.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_SH="${SCRIPT_DIR}/../install.sh"

# Must stay in lockstep with install.sh (found=1; exit / END { exit !found }).
AWK_GLOBAL_MAXAUTHTRIES_1='
  /^[[:space:]]*#/ { next }
  /^[[:space:]]*Match[[:space:]]/ { exit }
  /^[[:space:]]*MaxAuthTries[[:space:]]+1([[:space:]]|$)/ { found=1; exit }
  END { exit !found }
'

PASS=0
FAIL=0
ok()   { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

run_awk() {
  awk "${AWK_GLOBAL_MAXAUTHTRIES_1}" "$1"
}

echo "== test-install-sshd-warn.sh (install.sh global MaxAuthTries 1 awk) =="

if [ ! -f "${INSTALL_SH}" ]; then
  fail "install.sh missing at ${INSTALL_SH}"
  echo "pass=${PASS} fail=${FAIL}"
  exit 1
fi

# Source contract: corrected awk must remain in install.sh (dead END-exit bug).
if grep -q '{ found=1; exit }' "${INSTALL_SH}" \
  && grep -q 'END { exit !found }' "${INSTALL_SH}"; then
  ok "install.sh keeps found=1; exit + END { exit !found }"
else
  fail "install.sh missing corrected global MaxAuthTries awk (Patrick #908)"
fi

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

# Global MaxAuthTries 1 before Match → warning should fire (awk exit 0).
cat >"${TMP}/global-one.conf" <<'EOF'
MaxAuthTries 1
Match Group ezkey-mfa
	MaxAuthTries 1
EOF
if run_awk "${TMP}/global-one.conf"; then
  ok "detects global MaxAuthTries 1 before Match"
else
  fail "expected detect global MaxAuthTries 1 before Match"
fi

# Comment-only MaxAuthTries 1 before Match → must NOT fire.
cat >"${TMP}/commented.conf" <<'EOF'
# MaxAuthTries 1
Match Group ezkey-mfa
	MaxAuthTries 1
EOF
if run_awk "${TMP}/commented.conf"; then
  fail "must ignore commented MaxAuthTries 1"
else
  ok "ignores commented MaxAuthTries 1 before Match"
fi

# Match-scoped only (global 6) → must NOT fire.
cat >"${TMP}/match-only.conf" <<'EOF'
MaxAuthTries 6
Match Group ezkey-mfa
	MaxAuthTries 1
EOF
if run_awk "${TMP}/match-only.conf"; then
  fail "must not warn when only Match has MaxAuthTries 1"
else
  ok "no warn when MaxAuthTries 1 is Match-scoped only"
fi

# Demo sshd_config fixture (repo) → must NOT fire.
DEMO_CFG="${SCRIPT_DIR}/../sshd_config"
if [ -f "${DEMO_CFG}" ]; then
  if run_awk "${DEMO_CFG}"; then
    fail "demo sshd_config must not trigger global MaxAuthTries 1 warn"
  else
    ok "demo sshd_config has no global MaxAuthTries 1"
  fi
else
  fail "demo sshd_config missing"
fi

echo "pass=${PASS} fail=${FAIL}"
if [ "${FAIL}" -ne 0 ]; then
  exit 1
fi
