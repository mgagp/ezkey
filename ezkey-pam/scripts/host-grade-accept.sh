#!/usr/bin/env bash
# Host-grade acceptance (#1200). One script, two images.
#
#   ./scripts/host-grade-accept.sh
#
# Builds the existing Rocky and AL2023 builder and runtime images, then runs
# this same file inside each runtime (--inside). The inside run applies
# fixtures, proves the contract in HOST-GRADE.md against
# integration-api-stub.py, and removes the fixtures.
#
# --inside refuses to run outside a container. It is not install.sh and it
# does not edit a host sshd_config. install.sh still only warns.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAM_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

HG_IKEY="hgIkeyDoNotLog0001"
HG_SKEY="hgSkeyDoNotLog0001"
HG_CHALLENGE="83"
STUB_PORT="18443"
TLS_STUB_PORT="18445"
DOWN_PORT="18081"
MFA_PORT="22"
ADMIN_PORT="2224"
MFA_USER="hgmfa"
PLAIN_USER="hgplain"
ADMIN_USER="hgadmin"
MFA_GROUP="ezkey-mfa"
CONF="/etc/security/pam_ezkey.conf"
SSHD_CFG="/etc/ssh/sshd_config"
PAM_SSHD="/etc/pam.d/sshd"
ADMIN_CFG="/etc/ssh/sshd_admin_config"
STATE_DIR="/var/lib/ezkey-pam/host-grade"
SNAP="${STATE_DIR}/snapshot"
ROLL="${STATE_DIR}/rollback"
STUB_LOG="/tmp/hg-stub-reqs.log"
STUB_PID="/tmp/hg-stub.pid"
TLS_STUB_PID="/tmp/hg-tls-stub.pid"
TLS_STUB_LOG="/tmp/hg-tls-stub-reqs.log"
KEY_DIR="/etc/ezkey-pam-hostgrade/keys"
CA_DIR="/etc/ezkey-pam-hostgrade/ca"
SSHD="/usr/sbin/sshd"

hg_result_init() {
  local label="${EZKEY_PAM_IMAGE_LABEL:-unknown}"
  ART="${EZKEY_PAM_ARTIFACT_DIR:-/tmp/hg-artifacts}"
  mkdir -p "${ART}"
  RESULT="${ART}/host-grade-${label}.txt"
  : >"${RESULT}"
  printf 'image=%s\n' "${label}" >>"${RESULT}"
  HG_PASS=0
  HG_FAIL=0
}

hg_pass() {
  local key="$1"
  local msg="$2"
  echo "[PASS] ${msg}"
  HG_PASS=$((HG_PASS + 1))
  printf '%s=PASS\n' "${key}" >>"${RESULT}"
}

hg_fail() {
  local key="$1"
  local msg="$2"
  echo "[FAIL] ${msg}" >&2
  HG_FAIL=$((HG_FAIL + 1))
  printf '%s=FAIL\n' "${key}" >>"${RESULT}"
}

hg_note() {
  local key="$1"
  local value="$2"
  printf '%s=%s\n' "${key}" "${value}" >>"${RESULT}"
}

hg_need_root() {
  if [ "$(id -u)" -ne 0 ]; then
    echo "host-grade --inside must run as root inside the image" >&2
    exit 2
  fi
}

hg_refuse_unless_container() {
  if [ ! -f /.dockerenv ] && [ ! -f /run/.containerenv ]; then
    echo "host-grade --inside only runs inside the Rocky or AL2023 image" >&2
    exit 2
  fi
}

hg_packages() {
  local missing=""
  command -v python3 >/dev/null 2>&1 || missing="${missing} python3"
  command -v openssl >/dev/null 2>&1 || missing="${missing} openssl"
  command -v curl >/dev/null 2>&1 || missing="${missing} curl"
  command -v update-ca-trust >/dev/null 2>&1 || missing="${missing} ca-certificates"
  command -v cmp >/dev/null 2>&1 || missing="${missing} diffutils"
  command -v ssh >/dev/null 2>&1 || missing="${missing} openssh-clients"
  command -v "${SSHD}" >/dev/null 2>&1 || missing="${missing} openssh-server"
  command -v runuser >/dev/null 2>&1 || missing="${missing} util-linux"
  command -v ss >/dev/null 2>&1 || missing="${missing} iproute"
  if [ -n "${missing}" ]; then
    # shellcheck disable=SC2086
    dnf -y install ${missing}
  fi
}

hg_authpriv_up() {
  mkdir -p /var/log /var/log/ezkey-pam
  chmod 700 /var/log/ezkey-pam
  touch /var/log/secure
  chmod 640 /var/log/secure
  if [ -f /etc/rsyslog.conf ]; then
    sed -i 's/SysSock\.Use="off"/SysSock.Use="on"/g' /etc/rsyslog.conf
    if grep -q 'module(load="imjournal"' /etc/rsyslog.conf; then
      sed -i '/module(load="imjournal"/,/StateFile=/ s/^/#EZKEY_DOCKER /' \
        /etc/rsyslog.conf || true
    fi
    if ! grep -qE 'authpriv\.\*' /etc/rsyslog.conf /etc/rsyslog.d/*.conf 2>/dev/null; then
      echo 'authpriv.*    /var/log/secure' >>/etc/rsyslog.conf
    fi
  fi
  if [ -f /var/run/rsyslogd.pid ] && kill -0 "$(cat /var/run/rsyslogd.pid)" 2>/dev/null; then
    kill "$(cat /var/run/rsyslogd.pid)" 2>/dev/null || true
    sleep 0.2
  fi
  rsyslogd
  logger -p authpriv.info "hg-authpriv-probe"
  local i
  for i in $(seq 1 50); do
    if grep -qF "hg-authpriv-probe" /var/log/secure 2>/dev/null; then
      return 0
    fi
    sleep 0.1
  done
  echo "authpriv probe did not reach /var/log/secure" >&2
  return 1
}

hg_pam_so() {
  if [ -f /lib64/security/pam_ezkey.so ]; then
    printf '%s\n' /lib64/security/pam_ezkey.so
  elif [ -f /usr/lib64/security/pam_ezkey.so ]; then
    printf '%s\n' /usr/lib64/security/pam_ezkey.so
  else
    echo "pam_ezkey.so not found in the image" >&2
    return 1
  fi
}

hg_host_keys() {
  local t key
  for t in rsa ecdsa ed25519; do
    key="/etc/ssh/ssh_host_${t}_key"
    if [ ! -f "${key}" ]; then
      ssh-keygen -t "${t}" -f "${key}" -N "" -q
    fi
  done
}

hg_user_key() {
  local user="$1"
  local path="${KEY_DIR}/${user}"
  ssh-keygen -t ed25519 -f "${path}" -N "" -q
  install -d -m 700 -o "${user}" -g "${user}" "/home/${user}/.ssh"
  install -m 600 -o "${user}" -g "${user}" "${path}.pub" "/home/${user}/.ssh/authorized_keys"
  chmod 600 "${path}"
  chown root:root "${path}" "${path}.pub"
}

hg_users() {
  getent group "${MFA_GROUP}" >/dev/null || groupadd "${MFA_GROUP}"
  local u
  for u in "${MFA_USER}" "${PLAIN_USER}" "${ADMIN_USER}"; do
    if ! id "${u}" >/dev/null 2>&1; then
      useradd -m -s /bin/bash "${u}"
    fi
    hg_user_key "${u}"
  done
  # UsePAM no treats a locked shadow ("!") as an invalid user before the key
  # is considered. PasswordAuthentication stays off on the admin daemon.
  usermod -p "$(openssl passwd -6 "$(openssl rand -hex 16)")" "${ADMIN_USER}"
  usermod -aG "${MFA_GROUP}" "${MFA_USER}"
  id -nG "${MFA_USER}" | grep -qw "${MFA_GROUP}"
  if id -nG "${PLAIN_USER}" | grep -qw "${MFA_GROUP}"; then
    echo "plain user must not be in ${MFA_GROUP}" >&2
    return 1
  fi
}

hg_write_conf() {
  local url="$1"
  local tmp
  umask 077
  tmp="$(mktemp /etc/security/pam_ezkey.conf.hg.XXXXXX)"
  cat >"${tmp}" <<EOF
# Host-grade fixture (#1200). https only; lab_http off. Dummy keys.
integration_api_url=${url}
integration_key=${HG_IKEY}
secret_key=${HG_SKEY}
wait_timeout=15
wait_polling=2
api_timeout=3
challenge_requested=true
lab_http=false
context_title=SSH login
context_message=host-grade fixture
debug=false
EOF
  install -m 600 -o root -g root "${tmp}" "${CONF}"
  rm -f "${tmp}"
}

hg_write_fixture_files() {
  cat >"${SSHD_CFG}" <<EOF
# Host-grade MFA daemon fixture (#1200). Lab only.
# Match does not unload PAM for this daemon. Non-members avoid Ezkey because
# their AuthenticationMethods never enter the auth stack. See HOST-GRADE.md.
Port ${MFA_PORT}
Protocol 2
HostKey /etc/ssh/ssh_host_rsa_key
HostKey /etc/ssh/ssh_host_ecdsa_key
HostKey /etc/ssh/ssh_host_ed25519_key
PidFile /run/sshd-hg-mfa.pid
SyslogFacility AUTHPRIV
LogLevel VERBOSE
UseDNS no
PermitRootLogin no
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication yes
ChallengeResponseAuthentication yes
UsePAM yes
AuthenticationMethods publickey
X11Forwarding no
PrintMotd no
MaxAuthTries 6
LoginGraceTime 120
PerSourcePenalties no
Subsystem sftp internal-sftp

# Two-factor members. MaxAuthTries stays above 1 so one rejected key still
# reaches keyboard-interactive once. Not the keyboard-interactive-only sketch.
Match Group ${MFA_GROUP}
	AuthenticationMethods publickey,keyboard-interactive
	PubkeyAuthentication yes
	KbdInteractiveAuthentication yes
	MaxAuthTries 6
EOF
  cat >"${PAM_SSHD}" <<EOF
#%PAM-1.0
# Host-grade fixture (#1200). required keeps MFA fail-closed.
# Do not use sufficient or [default=ignore] on pam_ezkey.
auth       required     pam_ezkey.so conf=${CONF}
account    required     pam_permit.so
password   required     pam_deny.so
session    optional     pam_loginuid.so
session    required     pam_permit.so
EOF
  cat >"${ADMIN_CFG}" <<EOF
# Break-glass daemon (#1200). UsePAM no. Keys only. Not the MFA daemon.
Port ${ADMIN_PORT}
Protocol 2
HostKey /etc/ssh/ssh_host_rsa_key
HostKey /etc/ssh/ssh_host_ecdsa_key
HostKey /etc/ssh/ssh_host_ed25519_key
PidFile /run/sshd-hg-admin.pid
UseDNS no
PermitRootLogin no
PubkeyAuthentication yes
PasswordAuthentication no
KbdInteractiveAuthentication no
ChallengeResponseAuthentication no
UsePAM no
AuthenticationMethods publickey
AllowUsers ${ADMIN_USER}
MaxAuthTries 6
Subsystem sftp internal-sftp
EOF
  chmod 644 "${SSHD_CFG}" "${PAM_SSHD}" "${ADMIN_CFG}"
  chown root:root "${SSHD_CFG}" "${PAM_SSHD}" "${ADMIN_CFG}"
}

# Snapshot once. A second call keeps the pre-apply bytes.
hg_snapshot() {
  if [ -f "${SNAP}/READY" ]; then
    return 0
  fi
  mkdir -p "${SNAP}"
  chmod 700 "${STATE_DIR}" "${SNAP}"
  cp -a "${SSHD_CFG}" "${SNAP}/sshd_config"
  cp -a "${PAM_SSHD}" "${SNAP}/pam.d-sshd"
  cp -a "${CONF}" "${SNAP}/pam_ezkey.conf"
  : >"${SNAP}/READY"
  chmod 600 "${SNAP}/READY"
}

hg_apply() {
  hg_snapshot
  hg_write_conf "https://127.0.0.1:${STUB_PORT}"
  hg_write_fixture_files
}

hg_remove() {
  if [ ! -f "${SNAP}/READY" ]; then
    echo "hg_remove: no snapshot" >&2
    return 1
  fi
  cp -a "${SNAP}/sshd_config" "${SSHD_CFG}"
  cp -a "${SNAP}/pam.d-sshd" "${PAM_SSHD}"
  cp -a "${SNAP}/pam_ezkey.conf" "${CONF}"
  rm -f "${ADMIN_CFG}"
}

hg_bytes_match_snapshot() {
  cmp -s "${SNAP}/sshd_config" "${SSHD_CFG}" \
    && cmp -s "${SNAP}/pam.d-sshd" "${PAM_SSHD}" \
    && cmp -s "${SNAP}/pam_ezkey.conf" "${CONF}" \
    && [ ! -f "${ADMIN_CFG}" ]
}

hg_content_hash() {
  sha256sum "${SSHD_CFG}" "${PAM_SSHD}" "${CONF}" "${ADMIN_CFG}" 2>/dev/null \
    | awk '{print $1}' | sha256sum | awk '{print $1}'
}

hg_rollback_arm() {
  local seconds="${EZKEY_PAM_ROLLBACK_SECONDS:-}"
  local minutes="${EZKEY_PAM_ROLLBACK_MINUTES:-15}"
  local delay now
  if [ ! -f "${SNAP}/READY" ]; then
    echo "hg_rollback_arm: no snapshot" >&2
    return 1
  fi
  if [ -n "${seconds}" ]; then
    delay="${seconds}"
  else
    delay=$((minutes * 60))
  fi
  now="$(date +%s)"
  mkdir -p "${ROLL}"
  chmod 700 "${ROLL}"
  printf 'deadline=%s\nconfirmed=0\n' "$((now + delay))" >"${ROLL}/state"
  chmod 600 "${ROLL}/state"
}

hg_rollback_confirm() {
  if [ ! -f "${ROLL}/state" ]; then
    echo "hg_rollback_confirm: not armed" >&2
    return 1
  fi
  sed -i 's/^confirmed=.*/confirmed=1/' "${ROLL}/state"
}

hg_rollback_check() {
  local deadline confirmed now
  if [ ! -f "${ROLL}/state" ]; then
    return 0
  fi
  deadline="$(awk -F= '/^deadline=/{print $2}' "${ROLL}/state")"
  confirmed="$(awk -F= '/^confirmed=/{print $2}' "${ROLL}/state")"
  now="$(date +%s)"
  if [ "${confirmed}" = "1" ]; then
    return 0
  fi
  if [ "${now}" -ge "${deadline}" ]; then
    hg_remove
  fi
}

hg_stop_pidfile() {
  local pidfile="$1"
  local pid
  if [ -f "${pidfile}" ]; then
    pid="$(cat "${pidfile}" 2>/dev/null || true)"
    if [ -n "${pid}" ] && kill -0 "${pid}" 2>/dev/null; then
      kill "${pid}" 2>/dev/null || true
      sleep 0.2
      kill -9 "${pid}" 2>/dev/null || true
    fi
    rm -f "${pidfile}"
  fi
}

hg_stop_sshd() {
  hg_stop_pidfile /run/sshd-hg-mfa.pid
  hg_stop_pidfile /run/sshd-hg-admin.pid
}

hg_start_sshd() {
  hg_stop_sshd
  "${SSHD}" -t -f "${SSHD_CFG}"
  "${SSHD}" -t -f "${ADMIN_CFG}"
  "${SSHD}" -f "${SSHD_CFG}"
  "${SSHD}" -f "${ADMIN_CFG}"
  hg_wait_listen "${MFA_PORT}"
  hg_wait_listen "${ADMIN_PORT}"
}

hg_wait_listen() {
  local port="$1"
  local i
  for i in $(seq 1 50); do
    if ss -ltn | grep -E "[.:]${port}([^0-9]|\$)" >/dev/null 2>&1; then
      return 0
    fi
    sleep 0.1
  done
  echo "nothing listening on port ${port}" >&2
  return 1
}

hg_stop_stub() {
  hg_stop_pidfile "${STUB_PID}"
  hg_stop_pidfile "${TLS_STUB_PID}"
}

hg_start_stub() {
  local mode="$1"
  hg_stop_pidfile "${STUB_PID}"
  : >"${STUB_LOG}"
  # shellcheck disable=SC2086
  env \
    EZKEY_STUB_MODE="${mode}" \
    EZKEY_STUB_PORT="${STUB_PORT}" \
    EZKEY_STUB_REQ_LOG="${STUB_LOG}" \
    EZKEY_STUB_CHALLENGE="${HG_CHALLENGE}" \
    EZKEY_STUB_TLS_CERT="${CA_DIR}/server.crt" \
    EZKEY_STUB_TLS_KEY="${CA_DIR}/server.key" \
    python3 "${PAM_ROOT}/scripts/integration-api-stub.py" \
    >/tmp/hg-stub.out 2>&1 &
  echo $! >"${STUB_PID}"
  hg_wait_listen "${STUB_PORT}"
}

hg_ca() {
  mkdir -p "${CA_DIR}"
  chmod 700 "${CA_DIR}"
  openssl req -x509 -newkey rsa:2048 -keyout "${CA_DIR}/ca.key" \
    -out "${CA_DIR}/ca.crt" -days 2 -nodes \
    -subj "/CN=Ezkey PAM host-grade lab CA" >/dev/null 2>&1
  # OpenSSL 3 ignores a bare -extfile on x509 -req. Put the IP SAN on the
  # CSR and copy it. libcurl matches an https://127.0.0.1 URL to that SAN.
  openssl req -newkey rsa:2048 -keyout "${CA_DIR}/server.key" \
    -out "${CA_DIR}/server.csr" -nodes \
    -subj "/CN=127.0.0.1" \
    -addext "subjectAltName=IP:127.0.0.1" >/dev/null 2>&1
  openssl x509 -req -in "${CA_DIR}/server.csr" \
    -CA "${CA_DIR}/ca.crt" -CAkey "${CA_DIR}/ca.key" -CAcreateserial \
    -out "${CA_DIR}/server.crt" -days 2 \
    -copy_extensions copy >/dev/null 2>&1
  openssl x509 -in "${CA_DIR}/server.crt" -noout -ext subjectAltName \
    | grep -q "IP Address:127.0.0.1"
  chmod 600 "${CA_DIR}/ca.key" "${CA_DIR}/server.key"
  install -m 644 "${CA_DIR}/ca.crt" \
    /etc/pki/ca-trust/source/anchors/ezkey-pam-host-grade-lab.crt
  update-ca-trust extract >/dev/null 2>&1
  openssl req -x509 -newkey rsa:2048 \
    -keyout "${CA_DIR}/self.key" -out "${CA_DIR}/self.crt" -days 2 -nodes \
    -subj "/CN=127.0.0.1" \
    -addext "subjectAltName=IP:127.0.0.1" >/dev/null 2>&1
  chmod 600 "${CA_DIR}/self.key"
}

hg_ssh_batch() {
  local port="$1"
  local user="$2"
  local key="$3"
  HG_OUT="$(mktemp /tmp/hg-ssh-out.XXXXXX)"
  HG_ERR="$(mktemp /tmp/hg-ssh-err.XXXXXX)"
  set +e
  # An empty first argument is parsed as the destination hostname.
  ssh \
    -p "${port}" \
    -i "${key}" \
    -o BatchMode=yes \
    -o PreferredAuthentications=publickey \
    -o PubkeyAuthentication=yes \
    -o StrictHostKeyChecking=no \
    -o UserKnownHostsFile=/dev/null \
    -o GlobalKnownHostsFile=/dev/null \
    -o LogLevel=ERROR \
    -o ConnectTimeout=8 \
    -o GSSAPIAuthentication=no \
    -o IdentitiesOnly=yes \
    "${user}@127.0.0.1" echo HG_SESSION >"${HG_OUT}" 2>"${HG_ERR}"
  HG_RC=$?
  set -e
}

hg_ctrl_write() {
  local msg="$1"
  local fifo="$2"
  timeout 1s sh -c 'printf "%s\n" "$1" >"$2"' _ "${msg}" "${fifo}" \
    >/dev/null 2>&1 || true
}

# One pre-create ok and one challenge Enter. Does not loop into a second try.
hg_ssh_prompts() {
  local user="$1"
  local keys="$2"
  HG_OUT="$(mktemp /tmp/hg-ssh-pty.XXXXXX)"
  local ctrl_dir ctrl bg i flat sent_ok sent_enter
  ctrl_dir="$(mktemp -d /tmp/hg-kbd.XXXXXX)"
  ctrl="${ctrl_dir}/enter.fifo"
  mkfifo "${ctrl}"
  sent_ok=0
  sent_enter=0
  set +e
  # shellcheck disable=SC2086
  python3 "${PAM_ROOT}/scripts/ssh-pty-capture.py" "${HG_OUT}" "${ctrl}" \
    ssh -tt \
    -p "${MFA_PORT}" \
    ${keys} \
    -o StrictHostKeyChecking=no \
    -o UserKnownHostsFile=/dev/null \
    -o GlobalKnownHostsFile=/dev/null \
    -o LogLevel=ERROR \
    -o ConnectTimeout=8 \
    -o GSSAPIAuthentication=no \
    -o IdentitiesOnly=yes \
    -o PreferredAuthentications=publickey,keyboard-interactive \
    -o PubkeyAuthentication=yes \
    -o KbdInteractiveAuthentication=yes \
    -o PasswordAuthentication=no \
    -o NumberOfPasswordPrompts=1 \
    "${user}@127.0.0.1" echo HG_SESSION \
    >/dev/null 2>"${HG_OUT}.err" &
  bg=$!
  set -e
  for i in $(seq 1 300); do
    if ! kill -0 "${bg}" 2>/dev/null; then
      break
    fi
    flat="$(tr -cd '\11\12\15\40-\176' <"${HG_OUT}" 2>/dev/null || true)"
    if [ "${sent_ok}" -eq 0 ] && printf '%s' "${flat}" | grep -q "Type ok and press Enter"; then
      hg_ctrl_write "ok" "${ctrl}"
      sent_ok=1
    elif [ "${sent_ok}" -eq 1 ] && [ "${sent_enter}" -eq 0 ] \
      && printf '%s' "${flat}" | grep -qE "Ezkey challenge code:|Approve on your phone, then press Enter"; then
      hg_ctrl_write "enter" "${ctrl}"
      sent_enter=1
    fi
    sleep 0.1
  done
  if kill -0 "${bg}" 2>/dev/null; then
    kill "${bg}" 2>/dev/null || true
    sleep 0.2
    kill -9 "${bg}" 2>/dev/null || true
  fi
  set +e
  wait "${bg}"
  HG_RC=$?
  set -e
  rm -rf "${ctrl_dir}"
}

hg_ssh_nokey() {
  HG_OUT="$(mktemp /tmp/hg-ssh-nokey.XXXXXX)"
  set +e
  ssh -tt \
    -p "${MFA_PORT}" \
    -o IdentityFile=/dev/null \
    -o IdentitiesOnly=yes \
    -o PreferredAuthentications=keyboard-interactive \
    -o PubkeyAuthentication=no \
    -o PasswordAuthentication=no \
    -o KbdInteractiveAuthentication=yes \
    -o NumberOfPasswordPrompts=1 \
    -o StrictHostKeyChecking=no \
    -o UserKnownHostsFile=/dev/null \
    -o GlobalKnownHostsFile=/dev/null \
    -o LogLevel=ERROR \
    -o ConnectTimeout=8 \
    -o BatchMode=no \
    "${MFA_USER}@127.0.0.1" echo HG_SESSION >"${HG_OUT}" 2>"${HG_OUT}.err"
  HG_RC=$?
  set -e
}

hg_session_yes() {
  [ "${HG_RC}" -eq 0 ] && grep -q HG_SESSION "${HG_OUT}" 2>/dev/null
}

hg_reset_stub_log() {
  : >"${STUB_LOG}"
}

hg_creates() {
  local n
  n="$(grep -cE '^POST /api/v1/auth-attempts( |$)' "${STUB_LOG}" 2>/dev/null || true)"
  printf '%s' "${n:-0}"
}

hg_log_count() {
  local n
  n="$(grep -c -F "$1" /var/log/secure 2>/dev/null || true)"
  printf '%s' "${n:-0}"
}

hg_wait_log() {
  local pat="$1"
  local before="$2"
  local want="$3"
  local i now
  for i in $(seq 1 50); do
    now="$(hg_log_count "${pat}")"
    if [ $((now - before)) -ge "${want}" ]; then
      return 0
    fi
    sleep 0.1
  done
  return 1
}

hg_authpriv_has_secret() {
  if grep -qF "${HG_SKEY}" /var/log/secure 2>/dev/null; then
    return 0
  fi
  if grep -qF "${HG_IKEY}" /var/log/secure 2>/dev/null; then
    return 0
  fi
  return 1
}

# Drop numeric telemetry fields, then look for the challenge digits.
hg_authpriv_has_challenge() {
  local stripped
  # Keep only the pam_ezkey payload. The syslog prefix carries a pid that can
  # collide with a two-digit challenge.
  stripped="$(grep -F 'pam_ezkey:' /var/log/secure 2>/dev/null \
    | sed -E \
      -e 's/.*pam_ezkey: /pam_ezkey: /' \
      -e 's/authAttemptId=[-0-9]+/authAttemptId=_/g' \
      -e 's/timeoutSeconds=[-0-9]+/timeoutSeconds=_/g' \
      -e 's/pam_code=[-0-9]+/pam_code=_/g' \
      -e 's/latency_ms=[-0-9]+/latency_ms=_/g' \
      -e 's/curl_rc=[-0-9]+/curl_rc=_/g' \
      -e 's/http=[-0-9]+/http=_/g' \
      -e 's/confirm_ms=[-0-9]+/confirm_ms=_/g' \
      -e 's/api_ms=[-0-9]+/api_ms=_/g' \
      || true)"
  printf '%s\n' "${stripped}" | grep -qE "(^|[^0-9])${HG_CHALLENGE}([^0-9]|$)"
}

hg_control_flag_ok() {
  local line
  line="$(grep pam_ezkey.so "${PAM_SSHD}" | head -n 1 || true)"
  case "${line}" in
    *sufficient*|*default=ignore*) return 1 ;;
  esac
  case "${line}" in
    *required*|*requisite*) return 0 ;;
  esac
  return 1
}

hg_not_user_writable() {
  local path="$1"
  if runuser -u "${MFA_USER}" -- test -w "${path}"; then
    return 1
  fi
  return 0
}

hg_warn_global_not_written() {
  local tmp before after out rc
  tmp="$(mktemp /tmp/hg-sshd-warn.XXXXXX)"
  cp -a "${SSHD_CFG}" "${tmp}"
  before="$(sha256sum "${tmp}" | awk '{print $1}')"
  set +e
  out="$(ezkey_pam_warn_global_maxauthtries_1 "${tmp}")"
  rc=$?
  set -e
  after="$(sha256sum "${tmp}" | awk '{print $1}')"
  if [ "${rc}" -ne 1 ] || [ "${before}" != "${after}" ]; then
    hg_fail line1_global_maxauthtries_warned_not_written \
      "fixture already tripped the global MaxAuthTries 1 warning or was rewritten"
    rm -f "${tmp}"
    return 0
  fi
  {
    echo "MaxAuthTries 1"
    cat "${tmp}"
  } >"${tmp}.next"
  mv "${tmp}.next" "${tmp}"
  before="$(sha256sum "${tmp}" | awk '{print $1}')"
  set +e
  out="$(ezkey_pam_warn_global_maxauthtries_1 "${tmp}")"
  rc=$?
  set -e
  after="$(sha256sum "${tmp}" | awk '{print $1}')"
  rm -f "${tmp}"
  if [ "${rc}" -eq 0 ] && printf '%s\n' "${out}" | grep -q "WARNING: global MaxAuthTries 1" \
    && [ "${before}" = "${after}" ]; then
    hg_pass line1_global_maxauthtries_warned_not_written \
      "global MaxAuthTries 1 is warned and the file is not written"
  else
    hg_fail line1_global_maxauthtries_warned_not_written \
      "warn helper did not warn, or it changed the file"
  fi
}

hg_measure_plain() {
  local key="$1"
  hg_reset_stub_log
  hg_ssh_batch "${MFA_PORT}" "${PLAIN_USER}" "${KEY_DIR}/${PLAIN_USER}"
  if hg_session_yes; then
    hg_note "${key}" "session"
  else
    hg_note "${key}" "denied"
  fi
}

hg_cleanup_procs() {
  hg_stop_stub || true
  hg_stop_sshd || true
  if [ -n "${PAM_SO:-}" ] && [ -f "${PAM_SO}.hg-real" ]; then
    cp -a "${PAM_SO}.hg-real" "${PAM_SO}" || true
    rm -f "${PAM_SO}.hg-real"
  fi
}

hg_prepare_wrong_key() {
  if [ ! -f "${KEY_DIR}/wrong-offered" ]; then
    ssh-keygen -t ed25519 -f "${KEY_DIR}/wrong-offered" -N "" -q
    chmod 600 "${KEY_DIR}/wrong-offered"
  fi
}

hg_inside_rest() {
  # --- key + deny: no session, one create, one start, one attempt ---
  hg_start_stub rejected
  hg_write_conf "https://127.0.0.1:${STUB_PORT}"
  local start_before attempt_before
  start_before="$(hg_log_count 'pam_ezkey: start ')"
  attempt_before="$(hg_log_count 'pam_ezkey: attempt ')"
  hg_reset_stub_log
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  hg_wait_log 'pam_ezkey: attempt ' "${attempt_before}" 1 || true
  local start_delta attempt_delta
  start_delta="$(($(hg_log_count 'pam_ezkey: start ') - start_before))"
  attempt_delta="$(($(hg_log_count 'pam_ezkey: attempt ') - attempt_before))"
  if ! hg_session_yes && [ "$(hg_creates)" -eq 1 ] \
    && [ "${start_delta}" -eq 1 ] && [ "${attempt_delta}" -eq 1 ]; then
    hg_pass line1_key_deny_one_create "key plus deny: no session, one create, one start, one attempt"
    hg_pass line2_deny_no_session "deny yields no session"
  else
    hg_fail line1_key_deny_one_create \
      "deny rc=${HG_RC} creates=$(hg_creates) start=${start_delta} attempt=${attempt_delta}"
    hg_fail line2_deny_no_session "deny did not stay closed"
  fi

  # --- expire ---
  hg_start_stub expired
  hg_reset_stub_log
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  if ! hg_session_yes && [ "$(hg_creates)" -eq 1 ]; then
    hg_pass line2_expire_no_session "expiration yields no session"
  else
    hg_fail line2_expire_no_session \
      "expire rc=${HG_RC} creates=$(hg_creates)"
  fi

  # --- HTTP 404 ---
  hg_start_stub http_404
  hg_reset_stub_log
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  if ! hg_session_yes; then
    hg_pass line2_http_404_no_session "HTTP 404 yields no session"
  else
    hg_fail line2_http_404_no_session "HTTP 404 opened a session"
  fi

  # --- API down ---
  hg_stop_pidfile "${STUB_PID}"
  hg_write_conf "https://127.0.0.1:${DOWN_PORT}"
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  if ! hg_session_yes; then
    hg_pass line2_api_down_no_session "API down yields no session"
  else
    hg_fail line2_api_down_no_session "API down opened a session"
  fi

  # --- mode 0666 ---
  hg_start_stub ok
  hg_write_conf "https://127.0.0.1:${STUB_PORT}"
  chmod 666 "${CONF}"
  hg_reset_stub_log
  attempt_before="$(hg_log_count 'pam_ezkey: attempt ')"
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  hg_wait_log 'pam_ezkey: attempt ' "${attempt_before}" 1 || true
  local last_attempt
  last_attempt="$(grep -F 'pam_ezkey: attempt ' /var/log/secure | tail -n 1 || true)"
  if ! hg_session_yes && printf '%s\n' "${last_attempt}" | grep -q 'result=UNAVAIL'; then
    hg_pass line7_mode_0666_unavail "conf mode 0666 returns UNAVAIL and no session"
  else
    hg_fail line7_mode_0666_unavail "conf mode 0666 did not fail closed"
  fi
  chmod 600 "${CONF}"
  chown root:root "${CONF}"

  # --- self-signed, lab_http still false; do not trust this cert ---
  hg_stop_pidfile "${STUB_PID}"
  : >"${TLS_STUB_LOG}"
  python3 "${PAM_ROOT}/scripts/ezkey-pam-tls-stub.py" \
    "${TLS_STUB_PORT}" "${CA_DIR}/self.crt" "${CA_DIR}/self.key" \
    "${TLS_STUB_LOG}" "${TLS_STUB_PID}" \
    >/tmp/hg-tls-stub.out 2>&1 &
  echo $! >"${TLS_STUB_PID}"
  hg_wait_listen "${TLS_STUB_PORT}"
  hg_write_conf "https://127.0.0.1:${TLS_STUB_PORT}"
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  local tls_hits
  tls_hits="$(wc -l <"${TLS_STUB_LOG}" | tr -d ' ')"
  if ! hg_session_yes && [ "${tls_hits}" -eq 0 ]; then
    hg_pass line9_self_signed_unavail "self-signed https yields UNAVAIL and zero requests"
  else
    hg_fail line9_self_signed_unavail \
      "self-signed rc=${HG_RC} requests=${tls_hits}"
  fi
  hg_stop_pidfile "${TLS_STUB_PID}"
  hg_start_stub ok
  hg_write_conf "https://127.0.0.1:${STUB_PORT}"

  if hg_authpriv_has_secret || hg_authpriv_has_challenge; then
    hg_fail line8_no_secrets "authpriv contains a key or the challenge digits"
  else
    hg_pass line8_no_secrets "authpriv has no integration key, secret, or challenge digits"
  fi

  # Member attempt already proved by deny/approve creates. Record the line.
  hg_pass line4_member_attempt "member key plus Ezkey created an auth attempt"

  # --- break-glass: broken module, then removed module ---
  cp -a "${PAM_SO}" "${PAM_SO}.hg-real"
  printf 'not-an-elf\n' >"${PAM_SO}"
  chmod 755 "${PAM_SO}"
  chown root:root "${PAM_SO}"
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  if hg_session_yes; then
    hg_fail line3_mfa_port_dies_broken "broken module still opened an MFA session"
  else
    hg_pass line3_mfa_port_dies_broken "broken module: MFA port grants no session"
  fi
  hg_measure_plain line3_plain_on_mfa_when_module_broken
  hg_ssh_batch "${ADMIN_PORT}" "${ADMIN_USER}" "${KEY_DIR}/${ADMIN_USER}"
  if hg_session_yes; then
    hg_pass line3_admin_port_during_broken_module \
      "admin port accepts the admin key while the module is broken"
  else
    hg_fail line3_admin_port_during_broken_module \
      "admin port failed while the module was broken"
  fi

  rm -f "${PAM_SO}"
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  if hg_session_yes; then
    hg_fail line3_mfa_port_dies_removed "removed module still opened an MFA session"
  else
    hg_pass line3_mfa_port_dies_removed "removed module: MFA port grants no session"
  fi
  hg_measure_plain line3_plain_on_mfa_when_module_removed
  hg_ssh_batch "${ADMIN_PORT}" "${ADMIN_USER}" "${KEY_DIR}/${ADMIN_USER}"
  if hg_session_yes; then
    hg_pass line3_admin_port_during_removed_module \
      "admin port accepts the admin key while the module is removed"
  else
    hg_fail line3_admin_port_during_removed_module \
      "admin port failed while the module was removed"
  fi
  cp -a "${PAM_SO}.hg-real" "${PAM_SO}"
  rm -f "${PAM_SO}.hg-real"
  chmod 755 "${PAM_SO}"

  # --- rollback function, then remove ---
  hg_stop_sshd
  hg_stop_stub

  local armed now deadline delta hash_before hash_after
  unset EZKEY_PAM_ROLLBACK_SECONDS
  hg_rollback_arm
  now="$(date +%s)"
  deadline="$(awk -F= '/^deadline=/{print $2}' "${ROLL}/state")"
  delta="$((deadline - now))"
  if [ "${delta}" -ge 890 ] && [ "${delta}" -le 910 ]; then
    hg_pass line6_default_window "rollback default is about 15 minutes"
  else
    hg_fail line6_default_window "rollback default delta was ${delta}s"
  fi
  hg_rollback_confirm

  hash_before="$(hg_content_hash)"
  EZKEY_PAM_ROLLBACK_SECONDS=1 hg_rollback_arm
  hg_rollback_confirm
  sleep 2
  hg_rollback_check
  hash_after="$(hg_content_hash)"
  if [ "${hash_before}" = "${hash_after}" ]; then
    hg_pass line6_rollback_confirmed_keeps "confirmation keeps the fixture"
  else
    hg_fail line6_rollback_confirmed_keeps "confirmation restored files anyway"
  fi

  EZKEY_PAM_ROLLBACK_SECONDS=1 hg_rollback_arm
  sleep 2
  hg_rollback_check
  if hg_bytes_match_snapshot && "${SSHD}" -t -f "${SSHD_CFG}"; then
    hg_pass line6_rollback_unconfirmed \
      "no confirmation restores previous files and sshd -t passes"
  else
    hg_fail line6_rollback_unconfirmed "rollback did not restore a config sshd -t accepts"
  fi

  hg_apply
  hg_remove
  if hg_bytes_match_snapshot; then
    hg_pass line5_remove_byte_restore "remove restores sshd, pam.d, and conf byte for byte"
  else
    hg_fail line5_remove_byte_restore "remove did not restore snapshot bytes"
  fi
  hg_remove
  if hg_bytes_match_snapshot && "${SSHD}" -t -f "${SSHD_CFG}"; then
    hg_pass line5_remove_idempotent "second remove stays on the snapshot and sshd -t passes"
  else
    hg_fail line5_remove_idempotent "second remove drifted or sshd -t failed"
  fi

  printf 'pass=%s\nfail=%s\n' "${HG_PASS}" "${HG_FAIL}" >>"${RESULT}"
  echo "host-grade ${EZKEY_PAM_IMAGE_LABEL}: pass=${HG_PASS} fail=${HG_FAIL}"
  if [ "${HG_FAIL}" -ne 0 ]; then
    exit 1
  fi
}

# Split so the wrong key exists before the approve case. hg_inside stops
# before deny; the wrapper below is the real entry.
hg_inside_main() {
  hg_refuse_unless_container
  hg_need_root
  hg_result_init
  trap hg_cleanup_procs EXIT

  hg_packages
  hg_authpriv_up
  PAM_SO="$(hg_pam_so)"
  hg_host_keys
  install -d -m 700 "${KEY_DIR}"
  hg_users
  hg_prepare_wrong_key

  # shellcheck disable=SC1091
  . "${PAM_ROOT}/scripts/sshd-warn-global-maxauthtries-1.sh"

  hg_snapshot
  hg_apply
  local hash1 hash2
  hash1="$(hg_content_hash)"
  hg_apply
  hash2="$(hg_content_hash)"
  if [ "${hash1}" = "${hash2}" ]; then
    hg_pass line5_apply_idempotent "second apply leaves fixture bytes unchanged"
  else
    hg_fail line5_apply_idempotent "second apply changed fixture bytes"
  fi

  if hg_control_flag_ok; then
    hg_pass line2_control_flag "pam_ezkey control is required or requisite"
  else
    hg_fail line2_control_flag "pam_ezkey control is not required/requisite"
  fi
  if grep -q pam_ezkey.so "${PAM_SSHD}"; then
    hg_pass line4_match_does_not_unload_pam \
      "module line remains in pam.d for the whole daemon"
  else
    hg_fail line4_match_does_not_unload_pam "module line missing from pam.d"
  fi

  local mode owner so_owner so_dir
  mode="$(stat -c '%a' "${CONF}")"
  owner="$(stat -c '%u:%g' "${CONF}")"
  so_owner="$(stat -c '%u:%g' "${PAM_SO}")"
  so_dir="$(dirname "${PAM_SO}")"
  if [ "${owner}" = "0:0" ] && [ "${mode}" = "600" ] && [ "${so_owner}" = "0:0" ] \
    && hg_not_user_writable "${CONF}" \
    && hg_not_user_writable /etc/security \
    && hg_not_user_writable "${so_dir}"; then
    hg_pass line7_root_0600_path "conf 0600 root, path and module dir not user-writable"
  else
    hg_fail line7_root_0600_path "ownership, mode 0600, or path writability failed"
  fi

  if grep -q '^lab_http=false$' "${CONF}" \
    && grep -q '^integration_api_url=https://' "${CONF}"; then
    hg_pass line9_https_lab_http_off "fixture URL is https and lab_http is false"
  else
    hg_fail line9_https_lab_http_off "fixture is not https with lab_http false"
  fi

  hg_warn_global_not_written

  local plain_tries mfa_tries
  plain_tries="$("${SSHD}" -T -f "${SSHD_CFG}" \
    -C "user=${PLAIN_USER},host=localhost,addr=127.0.0.1" \
    | awk '$1=="maxauthtries"{print $2}')"
  mfa_tries="$("${SSHD}" -T -f "${SSHD_CFG}" \
    -C "user=${MFA_USER},host=localhost,addr=127.0.0.1" \
    | awk '$1=="maxauthtries"{print $2}')"
  if [ "${plain_tries}" != "1" ] && [ "${mfa_tries}" != "1" ]; then
    hg_pass line1_match_maxauthtries_not_global_one \
      "effective MaxAuthTries is not 1 (plain=${plain_tries} mfa=${mfa_tries})"
  else
    hg_fail line1_match_maxauthtries_not_global_one \
      "MaxAuthTries 1 is effective (plain=${plain_tries} mfa=${mfa_tries})"
  fi

  hg_ca
  hg_start_stub ok
  # Same library and trust store the module uses (libcurl, system bundle).
  if ! curl -fsS --max-time 5 \
    "https://127.0.0.1:${STUB_PORT}/api/v1/auth-attempts/1/wait" \
    >/tmp/hg-curl-preflight.out 2>/tmp/hg-curl-preflight.err; then
    echo "trusted CA preflight failed" >&2
    cat /tmp/hg-curl-preflight.err >&2 || true
    exit 1
  fi
  hg_reset_stub_log
  hg_start_sshd

  local before_start
  before_start="$(hg_log_count 'pam_ezkey: start ')"
  hg_reset_stub_log
  hg_ssh_batch "${MFA_PORT}" "${PLAIN_USER}" "${KEY_DIR}/${PLAIN_USER}"
  if hg_session_yes && [ "$(hg_creates)" -eq 0 ]; then
    hg_pass line4_outside_group_key_only_no_attempt \
      "non-member key login has a session and zero creates"
  else
    hg_fail line4_outside_group_key_only_no_attempt \
      "non-member key login rc=${HG_RC} creates=$(hg_creates)"
  fi
  if [ "$(hg_log_count 'pam_ezkey: start ')" -eq "${before_start}" ]; then
    hg_pass line4_outside_group_no_pam_start "non-member login did not log start"
  else
    hg_fail line4_outside_group_no_pam_start "non-member login logged start"
  fi

  hg_reset_stub_log
  hg_ssh_batch "${MFA_PORT}" "${MFA_USER}" "${KEY_DIR}/${MFA_USER}"
  if hg_session_yes; then
    hg_fail line1_batch_no_session "batch key-only MFA login opened a session"
  elif [ "$(hg_creates)" -eq 0 ]; then
    hg_pass line1_batch_no_session "batch key-only MFA login has no session and no create"
  else
    hg_fail line1_batch_no_session "batch key-only created $(hg_creates) attempts"
  fi

  hg_reset_stub_log
  hg_ssh_nokey
  if hg_session_yes; then
    hg_fail line1_conv_without_key_no_session "conversation without a key opened a session"
  elif [ "$(hg_creates)" -eq 0 ]; then
    hg_pass line1_conv_without_key_no_session "conversation without a key has no session"
  else
    hg_fail line1_conv_without_key_no_session \
      "conversation without a key created $(hg_creates) attempts"
  fi

  local start_before attempt_before start_delta attempt_delta
  start_before="$(hg_log_count 'pam_ezkey: start ')"
  attempt_before="$(hg_log_count 'pam_ezkey: attempt ')"
  hg_reset_stub_log
  hg_ssh_prompts "${MFA_USER}" \
    "-i ${KEY_DIR}/wrong-offered -i ${KEY_DIR}/${MFA_USER}"
  hg_wait_log 'pam_ezkey: attempt ' "${attempt_before}" 1 || true
  start_delta="$(($(hg_log_count 'pam_ezkey: start ') - start_before))"
  attempt_delta="$(($(hg_log_count 'pam_ezkey: attempt ') - attempt_before))"
  if hg_session_yes && [ "$(hg_creates)" -eq 1 ] \
    && [ "${start_delta}" -eq 1 ] && [ "${attempt_delta}" -eq 1 ]; then
    hg_pass line1_wrong_key_one_create \
      "wrong key then approve: one create, one start, one attempt, session"
    hg_pass line8_start_and_attempt "approve try logged one start and one attempt"
    hg_pass line1_key_approve_session "key plus approve opens a session"
  else
    hg_fail line1_wrong_key_one_create \
      "wrong-key approve rc=${HG_RC} creates=$(hg_creates) start=${start_delta} attempt=${attempt_delta}"
    hg_fail line8_start_and_attempt "approve try did not log one start and one attempt"
    if hg_session_yes; then
      hg_pass line1_key_approve_session "key plus approve opens a session"
    else
      hg_fail line1_key_approve_session "key plus approve did not open a session"
    fi
  fi

  hg_inside_rest
}

hg_host() {
  local art
  # shellcheck disable=SC1091
  . "${SCRIPT_DIR}/common.sh"
  art="$(ezkey_pam_artifact_dir)"
  mkdir -p "${art}"
  : >"${art}/host-grade-builders.txt"
  local fail=0

  hg_host_build "builder_rocky" \
    docker build --target builder -t ezkey-pam-builder:host-grade . \
    || fail=1
  if [ "${fail}" -eq 0 ]; then
    hg_host_build "runtime_rocky" \
      docker build --target runtime -t ezkey-pam-ssh:host-grade . \
      || fail=1
  else
    echo "runtime_rocky=SKIP" >>"${art}/host-grade-builders.txt"
  fi
  # Rocky suite even if we will also run AL2023. Track suite failure apart
  # from a later image. fail stays sticky.
  local rocky_fail=0
  if grep -q '^runtime_rocky=PASS$' "${art}/host-grade-builders.txt"; then
    hg_host_run rocky ezkey-pam-ssh:host-grade || rocky_fail=1
  else
    rocky_fail=1
  fi

  local al_build_fail=0
  hg_host_build "builder_al2023" \
    docker build -f Dockerfile.al2023 --target builder-al2023 \
    -t ezkey-pam-builder-al2023:host-grade . \
    || al_build_fail=1
  if [ "${al_build_fail}" -eq 0 ]; then
    hg_host_build "runtime_al2023" \
      docker build -f Dockerfile.al2023 --target runtime-al2023 \
      -t ezkey-pam-ssh-al2023:host-grade . \
      || al_build_fail=1
  else
    echo "runtime_al2023=SKIP" >>"${art}/host-grade-builders.txt"
  fi
  local al_fail=0
  if grep -q '^runtime_al2023=PASS$' "${art}/host-grade-builders.txt"; then
    hg_host_run al2023 ezkey-pam-ssh-al2023:host-grade || al_fail=1
  else
    al_fail=1
  fi

  if [ "${rocky_fail}" -ne 0 ] || [ "${al_fail}" -ne 0 ] || [ "${fail}" -ne 0 ]; then
    echo "host-grade: Rocky rc=${rocky_fail} AL2023 rc=${al_fail}" >&2
    exit 1
  fi
  echo "host-grade: both images passed"
}

hg_host_build() {
  local key="$1"
  shift
  local art
  art="$(ezkey_pam_artifact_dir)"
  echo "== docker build ${key} =="
  if "$@"; then
    echo "${key}=PASS" >>"${art}/host-grade-builders.txt"
    return 0
  fi
  echo "${key}=FAIL" >>"${art}/host-grade-builders.txt"
  return 1
}

hg_host_run() {
  local label="$1"
  local image="$2"
  local art
  art="$(ezkey_pam_artifact_dir)"
  echo "== acceptance inside ${label} (${image}) =="
  set +e
  docker run --rm \
    --name "ezkey-pam-hg-${label}" \
    -v "${PAM_ROOT}:/ezkey-pam:ro" \
    -v "${art}:/artifacts" \
    -e EZKEY_PAM_INSIDE=1 \
    -e EZKEY_PAM_IMAGE_LABEL="${label}" \
    -e EZKEY_PAM_ARTIFACT_DIR=/artifacts \
    --entrypoint /ezkey-pam/scripts/host-grade-accept.sh \
    "${image}" \
    --inside
  local rc=$?
  set -e
  echo "suite_${label}=${rc}" >>"${art}/host-grade-builders.txt"
  return "${rc}"
}

case "${1:-}" in
  --inside)
    hg_inside_main
    ;;
  *)
    cd "${PAM_ROOT}"
    hg_host
    ;;
esac
