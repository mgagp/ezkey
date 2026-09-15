#!/bin/bash
# Ezkey PAM SSH demo container startup.
# Writes /etc/security/pam_ezkey.conf from environment so sshd/PAM can
# load credentials at runtime (sshd does not pass Docker ENV into PAM).
set -euo pipefail

CONF="${EZKEY_CONFIG_FILE:-/etc/security/pam_ezkey.conf}"
API_URL="${EZKEY_INTEGRATION_API_URL:-${EZKEY_M2M_API_URL:-http://integration-api:7080}}"
WAIT_TIMEOUT="${EZKEY_WAIT_TIMEOUT:-90}"
WAIT_POLLING="${EZKEY_WAIT_POLLING:-2}"
API_TIMEOUT="${EZKEY_API_TIMEOUT:-10}"
TITLE="${EZKEY_CONTEXT_TITLE:-SSH login}"
MESSAGE="${EZKEY_CONTEXT_MESSAGE:-}"
SSH_USER="${EZKEY_SSH_USER:-testuser}"

if ! id "${SSH_USER}" >/dev/null 2>&1; then
    echo "[entrypoint] Creating Linux user ${SSH_USER}"
    useradd -m -s /bin/bash "${SSH_USER}"
fi

umask 077
cat > "${CONF}" <<EOF
# Generated at container start. Do not edit inside the running container
# if you expect environment variables to win on the next restart.
integration_api_url=${API_URL}
integration_key=${EZKEY_INTEGRATION_KEY:-}
secret_key=${EZKEY_SECRET_KEY:-}
wait_timeout=${WAIT_TIMEOUT}
wait_polling=${WAIT_POLLING}
api_timeout=${API_TIMEOUT}
challenge_requested=false
context_title=${TITLE}
context_message=${MESSAGE}
debug=true
EOF
chmod 600 "${CONF}"

rsyslogd 2>/dev/null || true

for key_type in rsa ecdsa ed25519; do
    key_file="/etc/ssh/ssh_host_${key_type}_key"
    if [ ! -f "${key_file}" ]; then
        echo "[entrypoint] Generating SSH host key: ${key_type}"
        ssh-keygen -t "${key_type}" -f "${key_file}" -N ""
    fi
done

if [ -z "${EZKEY_INTEGRATION_KEY:-}" ] || [ -z "${EZKEY_SECRET_KEY:-}" ]; then
    echo "[entrypoint] WARNING: EZKEY_INTEGRATION_KEY or EZKEY_SECRET_KEY is not set."
    echo "[entrypoint] SSH PAM authentication will fail until credentials are provided."
fi

echo "[entrypoint] Ezkey PAM SSH demo ready."
echo "[entrypoint] Integration API URL: ${API_URL}"
echo "[entrypoint] SSH user (must match enrollment userIdentifier): ${SSH_USER}"
echo "[entrypoint] Wait timeout: ${WAIT_TIMEOUT}s"

exec "$@"
