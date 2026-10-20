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
echo "  3. In sshd_config: LoginGraceTime >= 120. Enforce a single Ezkey try with a"
echo "     dedicated Match (User/Group), NOT a global MaxAuthTries 1:"
echo "       Match User <mfa-user>"
echo "           MaxAuthTries 1"
echo "           AuthenticationMethods keyboard-interactive"
echo "           PubkeyAuthentication no"
echo "     WARNING: without that Match, OpenSSH recreates auth attempts after"
echo "     Deny/Expire in the same TCP connection (#907 F9)."
echo "  4. Clients: ssh -o NumberOfPasswordPrompts=1 …"
echo "  5. Restart sshd (sshd -t first)."
# Effective MaxAuthTries for the MFA user (sshd -T; avoids regex matching 10/12).
CHECK_USER="${EZKEY_SSH_USER:-testuser}"
SSHD_CFG="/etc/ssh/sshd_config"
if [ -f "${SSHD_CFG}" ] && command -v sshd >/dev/null 2>&1; then
  if ! sshd -T -f "${SSHD_CFG}" \
      -C "user=${CHECK_USER},host=localhost,addr=127.0.0.1" 2>/dev/null \
    | grep -qx 'maxauthtries 1'; then
    echo ""
    echo "WARNING: sshd -T for user=${CHECK_USER} does not show maxauthtries 1."
    echo "         Add Match User|Group … MaxAuthTries 1 (effective value must be 1)."
    echo "         Without it, OpenSSH may recreate auth attempts after Deny/Expire (#907 F9)."
  fi
  # Global MaxAuthTries 1 (before any Match) still discouraged — pubkey rejects count.
  if awk '
    /^[[:space:]]*#/ { next }
    /^[[:space:]]*Match[[:space:]]/ { exit }
    /^[[:space:]]*MaxAuthTries[[:space:]]+1([[:space:]]|$)/ { found=1; exit }
    END { exit !found }
  ' "${SSHD_CFG}"; then
    echo ""
    echo "WARNING: global MaxAuthTries 1 found (before Match) — refused public keys"
    echo "         also consume tries. Prefer Match-scoped MaxAuthTries 1 (see README)."
  fi
elif [ -f "${SSHD_CFG}" ]; then
  echo ""
  echo "WARNING: sshd not on PATH — could not verify maxauthtries 1 via sshd -T."
  echo "         Ensure Match User|Group … MaxAuthTries 1 for ${CHECK_USER}."
fi
