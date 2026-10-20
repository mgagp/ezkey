#!/usr/bin/env bash
# Unit checks for the shared global MaxAuthTries 1 detector (#908 / #934).
# Runs the same helper install.sh and docker-entrypoint.sh source — not a
# duplicated awk body and not a name-only grep. No Docker / no root.
#   ./scripts/test-install-sshd-warn.sh
# Also invoked by `make test`.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAM_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
AWK_FILE="${SCRIPT_DIR}/sshd-warn-global-maxauthtries-1.awk"
HELPER_SH="${SCRIPT_DIR}/sshd-warn-global-maxauthtries-1.sh"
INSTALL_SH="${PAM_ROOT}/install.sh"
ENTRYPOINT="${PAM_ROOT}/docker-entrypoint.sh"
AWK_BASENAME="sshd-warn-global-maxauthtries-1.awk"
HELPER_BASENAME="sshd-warn-global-maxauthtries-1.sh"
FUNC_NAME="ezkey_pam_warn_global_maxauthtries_1"

PASS=0
FAIL=0
ok()   { echo "[PASS] $1"; PASS=$((PASS + 1)); }
fail() { echo "[FAIL] $1"; FAIL=$((FAIL + 1)); }

echo "== test-install-sshd-warn.sh (shared ${HELPER_BASENAME} + ${AWK_BASENAME}) =="

if [ ! -f "${AWK_FILE}" ] || [ ! -f "${HELPER_SH}" ]; then
  fail "shared detector missing (awk and/or helper .sh)"
  echo "pass=${PASS} fail=${FAIL}"
  exit 1
fi
ok "shared awk + helper present"

# Exact invocation inside the helper — replacing awk -f "${SSHD_WARN_AWK}" with a
# fake must fail this assert (and break fixture runs that source the helper).
if grep -qF 'awk -f "${SSHD_WARN_AWK}"' "${HELPER_SH}"; then
  ok "helper has exact awk -f \"\${SSHD_WARN_AWK}\" invocation"
else
  fail "helper must invoke: awk -f \"\${SSHD_WARN_AWK}\" (no fake/substitute)"
fi

# install.sh / entrypoint must source the helper and call the function (not a
# name-only mention of the .awk). Replacing those calls with a fake awk fails.
if grep -qF "${HELPER_BASENAME}" "${INSTALL_SH}" \
  && grep -qF "${FUNC_NAME}" "${INSTALL_SH}"; then
  ok "install.sh sources helper and calls ${FUNC_NAME}"
else
  fail "install.sh must source ${HELPER_BASENAME} and call ${FUNC_NAME}"
fi

if grep -qF "${HELPER_BASENAME}" "${ENTRYPOINT}" \
  && grep -qF "${FUNC_NAME}" "${ENTRYPOINT}"; then
  ok "docker-entrypoint.sh sources helper and calls ${FUNC_NAME}"
else
  fail "entrypoint must source ${HELPER_BASENAME} and call ${FUNC_NAME}"
fi

# Neither caller may keep an inline MaxAuthTries detector body.
for f in "${INSTALL_SH}" "${ENTRYPOINT}"; do
  if grep -nE 'maxauthtries\[\[:space:\]\]\+1|MaxAuthTries\[\[:space:\]\]\+1' "${f}" \
    2>/dev/null | grep -vqE 'echo |#|AWK|WARN|sshd-warn|Prefer Match'; then
    fail "$(basename "${f}") still embeds MaxAuthTries awk regex"
  else
    ok "$(basename "${f}") has no inline MaxAuthTries detector regex"
  fi
done

if grep -qF 'maxauthtries[[:space:]]+1([[:space:]]|$)' "${AWK_FILE}"; then
  ok "shared awk keeps maxauthtries…+1(space|EOL) contract line"
else
  fail "shared awk missing maxauthtries[[:space:]]+1([[:space:]]|$) contract"
fi

# shellcheck disable=SC1090
. "${HELPER_SH}"

TMP="$(mktemp -d)"
trap 'rm -rf "${TMP}"' EXIT

# Run the REAL helper (same code path as install.sh / entrypoint).
run_helper() {
  # Discard warning text; callers care about exit status.
  "${FUNC_NAME}" "$1" >/dev/null
}

# Global MaxAuthTries 1 before Match → warning (exit 0).
cat >"${TMP}/global-one.conf" <<'EOF'
MaxAuthTries 1
Match Group ezkey-mfa
	MaxAuthTries 1
EOF
if run_helper "${TMP}/global-one.conf"; then
  ok "helper detects global MaxAuthTries 1 before Match"
else
  fail "expected helper detect global MaxAuthTries 1 before Match"
fi

cat >"${TMP}/lower.conf" <<'EOF'
maxauthtries 1
Match Group ezkey-mfa
EOF
if run_helper "${TMP}/lower.conf"; then
  ok "helper case-insensitive: maxauthtries 1"
else
  fail "expected helper detect lowercase maxauthtries 1"
fi

cat >"${TMP}/upper.conf" <<'EOF'
MAXAUTHTRIES 1
Match User testuser
EOF
if run_helper "${TMP}/upper.conf"; then
  ok "helper case-insensitive: MAXAUTHTRIES 1"
else
  fail "expected helper detect uppercase MAXAUTHTRIES 1"
fi

printf 'MaxAuthTries\t1\nMatch Group ezkey-mfa\n' >"${TMP}/tab.conf"
if run_helper "${TMP}/tab.conf"; then
  ok "helper detects MaxAuthTries<TAB>1"
else
  fail "expected helper detect MaxAuthTries with tab separator"
fi

cat >"${TMP}/ten.conf" <<'EOF'
MaxAuthTries 10
Match Group ezkey-mfa
	MaxAuthTries 1
EOF
if run_helper "${TMP}/ten.conf"; then
  fail "helper must not warn on MaxAuthTries 10 (only exact 1)"
else
  ok "helper no warn on MaxAuthTries 10"
fi

: >"${TMP}/empty.conf"
if run_helper "${TMP}/empty.conf"; then
  fail "helper must not warn on empty sshd_config"
else
  ok "helper no warn on empty file"
fi

cat >"${TMP}/commented.conf" <<'EOF'
# MaxAuthTries 1
Match Group ezkey-mfa
	MaxAuthTries 1
EOF
if run_helper "${TMP}/commented.conf"; then
  fail "helper must ignore commented MaxAuthTries 1"
else
  ok "helper ignores commented MaxAuthTries 1 before Match"
fi

cat >"${TMP}/match-only.conf" <<'EOF'
MaxAuthTries 6
Match Group ezkey-mfa
	MaxAuthTries 1
EOF
if run_helper "${TMP}/match-only.conf"; then
  fail "helper must not warn when only Match has MaxAuthTries 1"
else
  ok "helper no warn when MaxAuthTries 1 is Match-scoped only"
fi

DEMO_CFG="${PAM_ROOT}/sshd_config"
if [ -f "${DEMO_CFG}" ]; then
  if run_helper "${DEMO_CFG}"; then
    fail "helper: demo sshd_config must not trigger global MaxAuthTries 1 warn"
  else
    ok "helper: demo sshd_config has no global MaxAuthTries 1"
  fi
else
  fail "demo sshd_config missing"
fi

# Missing detector must WARN (return 2) — not silent skip.
MISSING_OUT="${TMP}/missing-out.txt"
set +e
EZKEY_SSHD_WARN_AWK="${TMP}/does-not-exist.awk" \
  "${FUNC_NAME}" "${TMP}/global-one.conf" >"${MISSING_OUT}"
MISSING_RC=$?
set -e
if [ "${MISSING_RC}" -eq 2 ] \
  && grep -q 'detector missing' "${MISSING_OUT}"; then
  ok "helper warns (rc=2) when awk file missing — no silent skip"
else
  fail "helper must warn+rc=2 when awk missing (got rc=${MISSING_RC})"
fi

# Mutation via overridable path: real helper loads a temp mutated awk.
MUTATED="${TMP}/broken.awk"
cat >"${MUTATED}" <<'EOF'
{
  if ($0 ~ /^[[:space:]]*#/) { next }
  low = tolower($0)
  if (low ~ /^[[:space:]]*match[[:space:]]/) { exit }
  if (low ~ /^[[:space:]]*maxauthtries[[:space:]]+[0-9]+([[:space:]]|$)/) {
    found = 1
    exit
  }
}
END { exit !found }
EOF
set +e
EZKEY_SSHD_WARN_AWK="${MUTATED}" "${FUNC_NAME}" "${TMP}/ten.conf" >/dev/null
MUT_RC=$?
set -e
if [ "${MUT_RC}" -eq 0 ]; then
  ok "mutation via EZKEY_SSHD_WARN_AWK: real helper loads mutated awk (warns on 10)"
else
  fail "mutation probe: helper with EZKEY_SSHD_WARN_AWK=mutated should warn on 10"
fi

# Control: default path still rejects MaxAuthTries 10.
unset EZKEY_SSHD_WARN_AWK
if run_helper "${TMP}/ten.conf"; then
  fail "after unset override, helper must again ignore MaxAuthTries 10"
else
  ok "after unset EZKEY_SSHD_WARN_AWK, real awk ignores MaxAuthTries 10"
fi

# Prove replacing install.sh's real calls with a fake fails the suite: a patched
# copy that invokes awk -f /dev/null instead of ${FUNC_NAME} must not satisfy
# the source/call contract (re-check on a temp install.sh).
FAKE_INSTALL="${TMP}/install.fake.sh"
cp "${INSTALL_SH}" "${FAKE_INSTALL}"
# Neutralize real function calls (leave a fake awk that never uses the shared file).
sed -i 's/ezkey_pam_warn_global_maxauthtries_1[^;]*/awk -f \/dev\/null "${SSHD_CFG}" || true/g' \
  "${FAKE_INSTALL}"
if grep -qF "${FUNC_NAME}" "${FAKE_INSTALL}"; then
  fail "fixture: sed should have removed ${FUNC_NAME} calls from fake install"
elif ! grep -qF "${FUNC_NAME}" "${INSTALL_SH}"; then
  fail "install.sh lost ${FUNC_NAME} unexpectedly"
else
  ok "replacing install.sh ${FUNC_NAME} calls with fake awk fails call-contract (real still has it)"
fi

echo "pass=${PASS} fail=${FAIL}"
if [ "${FAIL}" -ne 0 ]; then
  exit 1
fi
