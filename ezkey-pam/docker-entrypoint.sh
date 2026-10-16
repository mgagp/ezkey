#!/bin/bash
# Ezkey PAM SSH demo container startup.
# Writes /etc/security/pam_ezkey.conf from environment so sshd/PAM can
# load credentials at runtime (sshd does not pass Docker ENV into PAM).
# Credentials live only in that conf file (root:root 0600) — the module
# does not read ikey/skey/URL/timeouts/debug/conf path from the process
# environment (M1 review).
set -euo pipefail

CONF="${EZKEY_CONFIG_FILE:-/etc/security/pam_ezkey.conf}"
API_URL="${EZKEY_INTEGRATION_API_URL:-${EZKEY_M2M_API_URL:-http://integration-api:7080}}"
WAIT_TIMEOUT="${EZKEY_WAIT_TIMEOUT:-75}"
WAIT_POLLING="${EZKEY_WAIT_POLLING:-2}"
API_TIMEOUT="${EZKEY_API_TIMEOUT:-10}"
TITLE="${EZKEY_CONTEXT_TITLE:-SSH login}"
MESSAGE="${EZKEY_CONTEXT_MESSAGE:-}"
SSH_USER="${EZKEY_SSH_USER:-testuser}"
# Demo containers default debug on so /var/log/ezkey-pam transcripts help
# lab debugging. Host-oriented sample conf defaults to false (see config/).
DEBUG="${EZKEY_DEBUG:-true}"
IKEY="${EZKEY_INTEGRATION_KEY:-}"
SKEY="${EZKEY_SECRET_KEY:-}"

# Reject newline / CR injection into the conf heredoc (conf key=value lines).
reject_bad_value() {
    local name="$1"
    local value="$2"
    case "${value}" in
        *$'\n'*|*$'\r'*)
            echo "[entrypoint] ERROR: ${name} contains a newline — refusing to write conf" >&2
            exit 1
            ;;
    esac
}

reject_bad_value API_URL "${API_URL}"
reject_bad_value WAIT_TIMEOUT "${WAIT_TIMEOUT}"
reject_bad_value WAIT_POLLING "${WAIT_POLLING}"
reject_bad_value API_TIMEOUT "${API_TIMEOUT}"
reject_bad_value TITLE "${TITLE}"
reject_bad_value MESSAGE "${MESSAGE}"
reject_bad_value DEBUG "${DEBUG}"
reject_bad_value EZKEY_INTEGRATION_KEY "${IKEY}"
reject_bad_value EZKEY_SECRET_KEY "${SKEY}"
reject_bad_value SSH_USER "${SSH_USER}"

if ! id "${SSH_USER}" >/dev/null 2>&1; then
    echo "[entrypoint] Creating Linux user ${SSH_USER}"
    useradd -m -s /bin/bash "${SSH_USER}"
fi

umask 077
# Write secrets under /etc/security (not /tmp) — Christophe N1.
CONF_DIR="$(dirname "${CONF}")"
mkdir -p "${CONF_DIR}"
CONF_TMP="$(mktemp "${CONF_DIR}/pam_ezkey.conf.XXXXXX")"
# shellcheck disable=SC2064
trap 'rm -f "${CONF_TMP}"' EXIT
cat > "${CONF_TMP}" <<EOF
# Generated at container start. Credentials are read only from this file
# (root:root 0600). The pam_ezkey .so does not read process ENV for secrets,
# URL, timeouts, debug, or conf path — only this file and pam.d module args.
integration_api_url=${API_URL}
integration_key=${IKEY}
secret_key=${SKEY}
wait_timeout=${WAIT_TIMEOUT}
wait_polling=${WAIT_POLLING}
api_timeout=${API_TIMEOUT}
challenge_requested=false
lab_http=true
context_title=${TITLE}
context_message=${MESSAGE}
debug=${DEBUG}
EOF
# Atomic install keeps root:root 0600 semantics (temp already in CONF_DIR).
install -m 600 -o root -g root "${CONF_TMP}" "${CONF}"
rm -f "${CONF_TMP}"
trap - EXIT

# Demo containers have no systemd journal. Stock Rocky/AL2023 rsyslog sets
# imuxsock SysSock.Use=off and relies on imjournal — so logger/syslog() never
# reach /var/log/secure. Enable the Unix socket and drop imjournal (M1).
if [ -f /etc/rsyslog.conf ]; then
    sed -i 's/SysSock\.Use="off"/SysSock.Use="on"/g' /etc/rsyslog.conf
    if grep -q 'module(load="imjournal"' /etc/rsyslog.conf; then
        sed -i '/module(load="imjournal"/,/StateFile=/ s/^/#EZKEY_DOCKER /' \
            /etc/rsyslog.conf || true
    fi
fi
mkdir -p /var/log /var/log/ezkey-pam
chmod 700 /var/log/ezkey-pam
touch /var/log/secure
chmod 640 /var/log/secure
if ! grep -qE 'authpriv\.\*' /etc/rsyslog.conf /etc/rsyslog.d/*.conf 2>/dev/null; then
    echo 'authpriv.*    /var/log/secure' >> /etc/rsyslog.conf
fi

pkill rsyslogd 2>/dev/null || true
# Wait briefly for the old daemon to exit (deterministic bound).
for _ in 1 2 3 4 5; do
    pgrep -x rsyslogd >/dev/null 2>&1 || break
    sleep 0.1
done
rsyslogd
PROBE="ezkey-pam-entrypoint: rsyslog authpriv probe $$"
logger -p authpriv.info "${PROBE}"
# Poll until the probe appears (no bare sleep-only proof).
PROBE_OK=0
for _ in $(seq 1 50); do
    if grep -qF "${PROBE}" /var/log/secure 2>/dev/null; then
        PROBE_OK=1
        break
    fi
    sleep 0.1
done
if [ "${PROBE_OK}" -ne 1 ]; then
    echo "[entrypoint] WARNING: authpriv probe did not reach /var/log/secure" >&2
fi

for key_type in rsa ecdsa ed25519; do
    key_file="/etc/ssh/ssh_host_${key_type}_key"
    if [ ! -f "${key_file}" ]; then
        echo "[entrypoint] Generating SSH host key: ${key_type}"
        ssh-keygen -t "${key_type}" -f "${key_file}" -N ""
    fi
done

if [ -z "${IKEY}" ] || [ -z "${SKEY}" ]; then
    echo "[entrypoint] WARNING: EZKEY_INTEGRATION_KEY or EZKEY_SECRET_KEY is not set."
    echo "[entrypoint] SSH PAM authentication will fail until credentials are provided."
fi
if [ -z "${API_URL}" ]; then
    echo "[entrypoint] WARNING: integration_api_url is empty — PAM will return UNAVAIL."
fi

echo "[entrypoint] Ezkey PAM SSH demo ready."
echo "[entrypoint] Integration API URL: ${API_URL}"
echo "[entrypoint] SSH user (must match enrollment userIdentifier): ${SSH_USER}"
echo "[entrypoint] Wait timeout: ${WAIT_TIMEOUT}s"

exec "$@"
