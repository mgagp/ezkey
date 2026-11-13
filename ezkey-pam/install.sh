#!/bin/bash
# Install pam_ezkey.so on a Rocky/RHEL host. Prefer the Docker demo for
# day-to-day use; this script is for a bare-metal lab install.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAM_LIB_DIR="${PAM_LIB_DIR:-/lib64/security}"
PAM_CONF_DIR="/etc/security"

echo "=== Ezkey PAM Module Installer ==="
cd "${SCRIPT_DIR}"
make clean
make all

echo "[install] Installing pam_ezkey.so -> ${PAM_LIB_DIR}/pam_ezkey.so"
install -d "${PAM_LIB_DIR}"
install -m 755 "${SCRIPT_DIR}/build/pam_ezkey.so" "${PAM_LIB_DIR}/pam_ezkey.so"

echo "[install] Installing config -> ${PAM_CONF_DIR}/pam_ezkey.conf"
install -d "${PAM_CONF_DIR}"
if [ ! -f "${PAM_CONF_DIR}/pam_ezkey.conf" ]; then
    install -m 600 "${SCRIPT_DIR}/config/pam_ezkey.conf" "${PAM_CONF_DIR}/pam_ezkey.conf"
else
    echo "[install] Keeping existing ${PAM_CONF_DIR}/pam_ezkey.conf"
fi

echo ""
echo "Installation complete."
echo "  1. Edit ${PAM_CONF_DIR}/pam_ezkey.conf (API URL + keys)."
echo "     Use https:// only; never commit origin IPs — placeholder"
echo "     <static-ip ezkey-online-community> for community docs."
echo "  2. Add this line at the top of /etc/pam.d/sshd:"
echo "       auth  required  pam_ezkey.so conf=/etc/security/pam_ezkey.conf"
echo "  3. MANDATORY sshd_config (operator must apply — install.sh only WARNs):"
echo "     LoginGraceTime >= 120, and MaxAuthTries 1 inside a dedicated Match"
echo "     (Group/User) for MFA accounts only — NOT global."
echo "     Why: each keyboard-interactive prompt round is one forked PAM child and"
echo "     one auth request; without Match MaxAuthTries 1, one SSH login can create"
echo "     multiple orphan pending attempts (#907 F9 / Edgar retest #908)."
echo "     Create the group and add MFA users so Match Group applies:"
echo "       groupadd ezkey-mfa"
echo "       usermod -aG ezkey-mfa <mfa-user>"
echo "     Host dogfood (other users like ec2-user stay on the global try budget):"
echo "       Match Group ezkey-mfa"
echo "           MaxAuthTries 1"
echo "           AuthenticationMethods keyboard-interactive"
echo "           PubkeyAuthentication no"
echo "     PubkeyAuthentication no: with MaxAuthTries 1, a refused client-offered"
echo "     pubkey consumes the single try before keyboard-interactive and locks out"
echo "     the MFA account; AuthenticationMethods keyboard-interactive makes pubkey"
echo "     unused anyway. Combining publickey,keyboard-interactive needs a higher"
echo "     MaxAuthTries (out of scope — document separately if funded)."
echo "     Single-user lab alternative: Match User <mfa-user> with the same three"
echo "     directives. See README Runbook / sshd.host-sketch."
echo "  4. Clients: ssh -o NumberOfPasswordPrompts=1 …"
echo "  5. Restart sshd (sshd -t first)."
echo "  6. Interaction model (confirm-before-create, challenge prompts, batch"
echo "     clients create nothing): see docs/INTERACTION-MODEL.md next to this"
echo "     installer in the ezkey-pam tree (also README Runbook)."
# Effective MaxAuthTries for the MFA user (sshd -T; avoids regex matching 10/12).
# Global MaxAuthTries 1 check: shared helper + awk (also entrypoint + unit tests).
# shellcheck source=scripts/sshd-warn-global-maxauthtries-1.sh
. "${SCRIPT_DIR}/scripts/sshd-warn-global-maxauthtries-1.sh"
CHECK_USER="${EZKEY_SSH_USER:-testuser}"
SSHD_CFG="/etc/ssh/sshd_config"
if [ -f "${SSHD_CFG}" ] && command -v sshd >/dev/null 2>&1; then
  if ! sshd -T -f "${SSHD_CFG}" \
      -C "user=${CHECK_USER},host=localhost,addr=127.0.0.1" 2>/dev/null \
    | grep -qx 'maxauthtries 1'; then
    echo ""
    echo "WARNING: sshd -T for user=${CHECK_USER} does not show maxauthtries 1."
    echo "         MANDATORY (not auto-enforced): add Match Group ezkey-mfa (or"
    echo "         Match User …) with MaxAuthTries 1 — effective value must be 1."
    echo "         Without it, each prompt round recreates an auth attempt (#907 F9)."
  fi
  # Global MaxAuthTries 1 (before any Match) still discouraged — pubkey rejects count.
  # Missing detector → helper WARNs (rc=2); never silent-skip.
  set +e
  _ezkey_sshd_warn_out="$(ezkey_pam_warn_global_maxauthtries_1 "${SSHD_CFG}")"
  _ezkey_sshd_warn_rc=$?
  set -e
  if [ "${_ezkey_sshd_warn_rc}" -eq 0 ] || [ "${_ezkey_sshd_warn_rc}" -eq 2 ]; then
    echo ""
    printf '%s\n' "${_ezkey_sshd_warn_out}"
  fi
elif [ -f "${SSHD_CFG}" ]; then
  echo ""
  echo "WARNING: sshd not on PATH — could not verify maxauthtries 1 via sshd -T."
  echo "         MANDATORY (not auto-enforced): Match Group ezkey-mfa (or Match User …)"
  echo "         MaxAuthTries 1 for MFA account ${CHECK_USER}."
  set +e
  _ezkey_sshd_warn_out="$(ezkey_pam_warn_global_maxauthtries_1 "${SSHD_CFG}")"
  _ezkey_sshd_warn_rc=$?
  set -e
  if [ "${_ezkey_sshd_warn_rc}" -eq 0 ] || [ "${_ezkey_sshd_warn_rc}" -eq 2 ]; then
    printf '%s\n' "${_ezkey_sshd_warn_out}"
  fi
fi
