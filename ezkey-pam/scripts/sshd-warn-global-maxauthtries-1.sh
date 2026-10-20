# Shared global-MaxAuthTries-1 warning helper (sourceable).
# Used by install.sh, docker-entrypoint.sh, and test-install-sshd-warn.sh.
# Detector program (sibling): sshd-warn-global-maxauthtries-1.awk
#
# Env:
#   EZKEY_SSHD_WARN_AWK     — override path to the awk detector (tests / mutation)
#   EZKEY_SSHD_WARN_PREFIX  — optional line prefix (e.g. "[entrypoint] ")
#
# ezkey_pam_warn_global_maxauthtries_1 <sshd_config>
#   Prints WARNING lines to stdout. Returns:
#     0 — global MaxAuthTries 1 found (warned)
#     1 — not found (no warning)
#     2 — detector awk file missing (warned; not a silent skip)

ezkey_pam_sshd_warn_awk_default() {
  local here
  here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  printf '%s\n' "${here}/sshd-warn-global-maxauthtries-1.awk"
}

ezkey_pam_warn_global_maxauthtries_1() {
  local sshd_cfg="${1:?sshd_config path required}"
  local prefix="${EZKEY_SSHD_WARN_PREFIX:-}"
  # Overridable for tests; default is the sibling .awk next to this helper.
  local SSHD_WARN_AWK="${EZKEY_SSHD_WARN_AWK:-$(ezkey_pam_sshd_warn_awk_default)}"

  if [ ! -f "${SSHD_WARN_AWK}" ]; then
    echo "${prefix}WARNING: shared MaxAuthTries detector missing: ${SSHD_WARN_AWK}"
    echo "${prefix}         Cannot check for global MaxAuthTries 1 before Match (see README)."
    echo "${prefix}         Expected scripts/sshd-warn-global-maxauthtries-1.awk next to this helper."
    return 2
  fi

  if [ ! -f "${sshd_cfg}" ]; then
    return 1
  fi

  # Exact invocation contract (test greps this line) — do not replace with a fake awk.
  if awk -f "${SSHD_WARN_AWK}" "${sshd_cfg}"; then
    echo "${prefix}WARNING: global MaxAuthTries 1 found (before Match) — refused public keys"
    echo "${prefix}         also consume tries and can lock out non-MFA users (e.g. ec2-user)."
    echo "${prefix}         Prefer Match Group ezkey-mfa / Match User … MaxAuthTries 1 (README)."
    return 0
  fi
  return 1
}
