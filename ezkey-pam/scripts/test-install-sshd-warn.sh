#!/usr/bin/env bash
# Unit checks for the shared global MaxAuthTries 1 awk (Patrick #908 / #934 B1).
# Consumes scripts/sshd-warn-global-maxauthtries-1.awk — the same file install.sh
# and docker-entrypoint.sh run. No duplicated awk body. No Docker / no root.
#   ./scripts/test-install-sshd-warn.sh
# Also invoked by `make test`.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAM_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
AWK_FILE="${SCRIPT_DIR}/sshd-warn-global-maxauthtries-1.awk"
INSTALL_SH="${PAM_ROOT}/install.sh"
ENTRYPOINT="${PAM_ROOT}/docker-entrypoint.sh"
AWK_BASENAME="sshd-warn-global-maxauthtries-1.awk"

PASS=0
FAIL=0
ok()   { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

run_shared() {
  awk -f "${AWK_FILE}" "$1"
}

echo "== test-install-sshd-warn.sh (shared ${AWK_BASENAME}) =="

if [ ! -f "${AWK_FILE}" ]; then
  fail "shared awk missing at ${AWK_FILE}"
  echo "pass=${PASS} fail=${FAIL}"
  exit 1
fi
ok "shared awk present"

# Callers must invoke the shared file (no inline duplicate of the detector).
if grep -qE "awk -f .*${AWK_BASENAME}" "${INSTALL_SH}" 2>/dev/null \
  || grep -qF "${AWK_BASENAME}" "${INSTALL_SH}" 2>/dev/null; then
  ok "install.sh references shared ${AWK_BASENAME}"
else
  fail "install.sh must awk -f ${AWK_BASENAME} (B1: no duplicated awk)"
fi

if grep -qF "${AWK_BASENAME}" "${ENTRYPOINT}" 2>/dev/null; then
  ok "docker-entrypoint.sh references shared ${AWK_BASENAME}"
else
  fail "docker-entrypoint.sh must awk -f ${AWK_BASENAME} (B1)"
fi

# install.sh must not keep an inline MaxAuthTries detector body.
if grep -nE 'maxauthtries\[\[:space:\]\]\+1|MaxAuthTries\[\[:space:\]\]\+1' \
  "${INSTALL_SH}" 2>/dev/null | grep -vqE 'echo |#|AWK|awk -f'; then
  fail "install.sh still embeds MaxAuthTries awk regex (must use shared file)"
else
  ok "install.sh has no inline MaxAuthTries detector regex"
fi

# Contract line in the shared file — if removed/broken, MaxAuthTries 10 / empty
# behavioral cases above fail when the detector is wrong; this pins the pattern.
if grep -qF 'maxauthtries[[:space:]]+1([[:space:]]|$)' "${AWK_FILE}"; then
  ok "shared awk keeps maxauthtries…+1(space|EOL) contract line"
else
  fail "shared awk missing maxauthtries[[:space:]]+1([[:space:]]|$) contract"
fi

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

# Global MaxAuthTries 1 before Match → warning should fire (awk exit 0).
cat >"${TMP}/global-one.conf" <<'EOF'
MaxAuthTries 1
Match Group ezkey-mfa
	MaxAuthTries 1
EOF
if run_shared "${TMP}/global-one.conf"; then
  ok "detects global MaxAuthTries 1 before Match"
else
  fail "expected detect global MaxAuthTries 1 before Match"
fi

# Case-insensitive (sshd keywords) — maxauthtries / MAXAUTHTRIES.
cat >"${TMP}/lower.conf" <<'EOF'
maxauthtries 1
Match Group ezkey-mfa
EOF
if run_shared "${TMP}/lower.conf"; then
  ok "case-insensitive: maxauthtries 1"
else
  fail "expected detect lowercase maxauthtries 1"
fi

cat >"${TMP}/upper.conf" <<'EOF'
MAXAUTHTRIES 1
Match User testuser
EOF
if run_shared "${TMP}/upper.conf"; then
  ok "case-insensitive: MAXAUTHTRIES 1"
else
  fail "expected detect uppercase MAXAUTHTRIES 1"
fi

# Tab separator between keyword and 1.
printf 'MaxAuthTries\t1\nMatch Group ezkey-mfa\n' >"${TMP}/tab.conf"
if run_shared "${TMP}/tab.conf"; then
  ok "detects MaxAuthTries<TAB>1"
else
  fail "expected detect MaxAuthTries with tab separator"
fi

# MaxAuthTries 10 must NOT fire (prefix of 1 would be a false positive).
cat >"${TMP}/ten.conf" <<'EOF'
MaxAuthTries 10
Match Group ezkey-mfa
	MaxAuthTries 1
EOF
if run_shared "${TMP}/ten.conf"; then
  fail "must not warn on MaxAuthTries 10 (only exact 1)"
else
  ok "no warn on MaxAuthTries 10"
fi

# Empty file → must NOT fire.
: >"${TMP}/empty.conf"
if run_shared "${TMP}/empty.conf"; then
  fail "must not warn on empty sshd_config"
else
  ok "no warn on empty file"
fi

# Comment-only MaxAuthTries 1 before Match → must NOT fire.
cat >"${TMP}/commented.conf" <<'EOF'
# MaxAuthTries 1
Match Group ezkey-mfa
	MaxAuthTries 1
EOF
if run_shared "${TMP}/commented.conf"; then
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
if run_shared "${TMP}/match-only.conf"; then
  fail "must not warn when only Match has MaxAuthTries 1"
else
  ok "no warn when MaxAuthTries 1 is Match-scoped only"
fi

# Demo sshd_config fixture (repo) → must NOT fire.
DEMO_CFG="${PAM_ROOT}/sshd_config"
if [ -f "${DEMO_CFG}" ]; then
  if run_shared "${DEMO_CFG}"; then
    fail "demo sshd_config must not trigger global MaxAuthTries 1 warn"
  else
    ok "demo sshd_config has no global MaxAuthTries 1"
  fi
else
  fail "demo sshd_config missing"
fi

# Prove shared-file breakage fails the suite: a mutated detector that matches
# MaxAuthTries 10 must make the "no warn on 10" expectation fail when run.
MUTATED="${TMP}/broken.awk"
cat >"${MUTATED}" <<'EOF'
{
  if ($0 ~ /^[[:space:]]*#/) { next }
  low = tolower($0)
  if (low ~ /^[[:space:]]*match[[:space:]]/) { exit }
  # Broken: any MaxAuthTries value (including 10) — must NOT ship.
  if (low ~ /^[[:space:]]*maxauthtries[[:space:]]+[0-9]+([[:space:]]|$)/) {
    found = 1
    exit
  }
}
END { exit !found }
EOF
if awk -f "${MUTATED}" "${TMP}/ten.conf"; then
  # Mutated detector fires on 10 — confirms the ten.conf fixture is valid, and
  # that the real shared file's stricter regex is what keeps the PASS above.
  ok "mutation probe: loose regex would false-positive on MaxAuthTries 10"
else
  fail "mutation probe setup broken (ten.conf should match loose regex)"
fi

echo "pass=${PASS} fail=${FAIL}"
if [ "${FAIL}" -ne 0 ]; then
  exit 1
fi
