#!/bin/bash
# ==================================================
# install.sh - Ezkey PAM module installer
# ==================================================
# Run as root to install the PAM module system-wide.
# Usage: sudo ./install.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAM_LIB_DIR="/lib64/security"
PAM_CONF_DIR="/etc/security"
PAM_SO="$SCRIPT_DIR/build/pam_ezkey.so"
PAM_CONF="$SCRIPT_DIR/config/pam_ezkey.conf"

echo "=== Ezkey PAM Module Installer ==="

# Build
echo "[install] Building pam_ezkey.so..."
cd "$SCRIPT_DIR"
make clean && make all

# Install shared library
echo "[install] Installing $PAM_SO -> $PAM_LIB_DIR/pam_ezkey.so"
cp "$PAM_SO" "$PAM_LIB_DIR/pam_ezkey.so"
chmod 755 "$PAM_LIB_DIR/pam_ezkey.so"

# Install config file
echo "[install] Installing $PAM_CONF -> $PAM_CONF_DIR/pam_ezkey.conf"
cp "$PAM_CONF" "$PAM_CONF_DIR/pam_ezkey.conf"
chmod 644 "$PAM_CONF_DIR/pam_ezkey.conf"

echo ""
echo "=== Installation complete ==="
echo ""
echo "Next steps:"
echo "  1. Add the following line to /etc/pam.d/sshd (before other auth lines):"
echo "       auth  required  pam_ezkey.so debug"
echo "  2. Set environment variables before starting sshd:"
echo "       export EZKEY_M2M_API_URL=http://integration-api:7080"
echo "       export EZKEY_INTEGRATION_KEY=<your-integration-key>"
echo "       export EZKEY_SECRET_KEY=<your-secret-key>"
echo "  3. Restart sshd:"
echo "       systemctl restart sshd"
