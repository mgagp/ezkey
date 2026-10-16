#!/bin/bash
# Ezkey PAM SSH demo container startup.
# Writes /etc/security/pam_ezkey.conf from environment so sshd/PAM can
# load credentials at runtime (sshd does not pass Docker ENV into PAM).
# Credentials live only in that conf file (root:root 0600) — the module
# does not read ikey/skey/URL from the process environment (M1).
set -euo pipefail

CONF="${EZKEY_CONFIG_FILE:-/etc/security/pam_ezkey.conf}"
API_URL="${EZKEY_INTEGRATION_API_URL:-${EZKEY_M2M_API_URL:-http://integration-api:7080}}"
WAIT_TIMEOUT="${EZKEY_WAIT_TIMEOUT:-90}"
WAIT_POLLING="${EZKEY_WAIT_POLLING:-2}"
API_TIMEOUT="${EZKEY_API_TIMEOUT:-10}"
TITLE="${EZKEY_CONTEXT_TITLE:-SSH login}"
MESSAGE="${EZKEY_CONTEXT_MESSAGE:-}"
SSH_USER="${EZKEY_SSH_USER:-testuser}"
# Demo containers default debug on so /var/log/ezkey-pam transcripts help
# lab debugging. Host-oriented sample conf defaults to false (see config/).
# Debug files use O_NOFOLLOW under /var/log/ezkey-pam (#818), not /tmp.
DEBUG="${EZKEY_DEBUG:-true}"

if ! id "${SSH_USER}" >/dev/null 2>&1; then
    echo "[entrypoint] Creating Linux user ${SSH_USER}"
    useradd -m -s /bin/bash "${SSH_USER}"
fi

umask 077
cat > "${CONF}" <<EOF
# Generated at container start. Credentials are read only from this file
# (root:root 0600). Do not rely on ENV overrides for ikey/skey/URL inside PAM.
integration_api_url=${API_URL}
integration_key=${EZKEY_INTEGRATION_KEY:-}
secret_key=${EZKEY_SECRET_KEY:-}
wait_timeout=${WAIT_TIMEOUT}
wait_polling=${WAIT_POLLING}
api_timeout=${API_TIMEOUT}
challenge_requested=false
context_title=${TITLE}
context_message=${MESSAGE}
debug=${DEBUG}
EOF
chown root:root "${CONF}"
chmod 600 "${CONF}"

# Demo containers have no systemd journal. Stock Rocky/AL2023 rsyslog sets
# imuxsock SysSock.Use=off and relies on imjournal — so logger/syslog() never
# reach /var/log/secure. Enable the Unix socket and drop imjournal (M1).
if [ -f /etc/rsyslog.conf ]; then
    sed -i 's/SysSock\.Use="off"/SysSock.Use="on"/g' /etc/rsyslog.conf
    # Comment imjournal module block (multi-line) so local socket is the source.
    if grep -q 'module(load="imjournal"' /etc/rsyslog.conf; then
        sed -i '/module(load="imjournal"/,/StateFile=/ s/^/#EZKEY_DOCKER /' \
            /etc/rsyslog.conf || true
    fi
fi
mkdir -p /var/log /var/log/ezkey-pam
chmod 700 /var/log/ezkey-pam
touch /var/log/secure
chmod 640 /var/log/secure
# Ensure authpriv rule exists (stock images already have it).
if ! grep -qE 'authpriv\.\*' /etc/rsyslog.conf /etc/rsyslog.d/*.conf 2>/dev/null; then
    echo 'authpriv.*    /var/log/secure' >> /etc/rsyslog.conf
fi

# Restart cleanly if a previous entrypoint left rsyslogd running (compose restart).
pkill rsyslogd 2>/dev/null || true
sleep 0.2
rsyslogd
# Prove authpriv reaches /var/log/secure before sshd starts.
logger -p authpriv.info "ezkey-pam-entrypoint: rsyslog authpriv probe"
sleep 0.3
if ! grep -q 'ezkey-pam-entrypoint: rsyslog authpriv probe' /var/log/secure; then
    echo "[entrypoint] WARNING: authpriv probe did not reach /var/log/secure" >&2
fi

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
