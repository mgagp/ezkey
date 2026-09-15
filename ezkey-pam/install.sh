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
echo "  2. Add this line at the top of /etc/pam.d/sshd:"
echo "       auth  required  pam_ezkey.so debug conf=/etc/security/pam_ezkey.conf"
echo "  3. Restart sshd."
