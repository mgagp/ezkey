#!/bin/bash
# ==================================================
# docker-entrypoint.sh - Ezkey PAM container startup
# ==================================================
set -e

# Start rsyslog for PAM/auth logging
rsyslogd 2>/dev/null || true

# Generate SSH host keys if not present (should already be done at build time,
# but this ensures the container still works if /etc/ssh was mounted as a volume)
for key_type in rsa ecdsa ed25519; do
    key_file="/etc/ssh/ssh_host_${key_type}_key"
    if [ ! -f "$key_file" ]; then
        echo "[entrypoint] Generating SSH host key: $key_type"
        ssh-keygen -t "$key_type" -f "$key_file" -N ""
    fi
done

# Validate that M2M credentials are provided
if [ -z "$EZKEY_INTEGRATION_KEY" ] || [ -z "$EZKEY_SECRET_KEY" ]; then
    echo "[entrypoint] WARNING: EZKEY_INTEGRATION_KEY or EZKEY_SECRET_KEY is not set."
    echo "[entrypoint] SSH PAM authentication will fail until credentials are provided."
fi

echo "[entrypoint] Ezkey PAM container ready."
echo "[entrypoint] M2M API URL: ${EZKEY_M2M_API_URL:-http://localhost:7080}"

# Execute the main command (typically sshd -D)
exec "$@"
