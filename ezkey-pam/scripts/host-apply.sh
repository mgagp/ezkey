#!/usr/bin/env bash
# Host-grade fixture tool (#1200). The acceptance runner calls these
# subcommands. An operator can call them too. This is not a systemd unit
# and install.sh does not call it.
#
#   host-apply.sh apply     snapshot once, then write the fixture
#   host-apply.sh remove    restore the snapshot byte for byte
#   host-apply.sh arm       start the rollback window (no timer is installed)
#   host-apply.sh confirm   keep the fixture when check runs
#   host-apply.sh check     if the window elapsed and nobody confirmed, remove
#
# apply reads EZKEY_PAM_API_URL (required). arm reads EZKEY_PAM_ROLLBACK_SECONDS
# or EZKEY_PAM_ROLLBACK_MINUTES (default 15). A system timer that would call
# check is not part of this script; that trigger is phase 2 (#1201).
set -euo pipefail

CONF="${EZKEY_PAM_CONF:-/etc/security/pam_ezkey.conf}"
SSHD_CFG="${EZKEY_PAM_SSHD_CONFIG:-/etc/ssh/sshd_config}"
PAM_SSHD="${EZKEY_PAM_PAM_SSHD:-/etc/pam.d/sshd}"
ADMIN_CFG="${EZKEY_PAM_ADMIN_SSHD:-/etc/ssh/sshd_admin_config}"
STATE_DIR="${EZKEY_PAM_STATE_DIR:-/var/lib/ezkey-pam/host-grade}"
SNAP="${STATE_DIR}/snapshot"
ROLL="${STATE_DIR}/rollback"
MFA_PORT="${EZKEY_PAM_MFA_PORT:-22}"
ADMIN_PORT="${EZKEY_PAM_ADMIN_PORT:-2224}"
MFA_GROUP="${EZKEY_PAM_MFA_GROUP:-ezkey-mfa}"
ADMIN_USER="${EZKEY_PAM_ADMIN_USER:-hgadmin}"
IKEY="${EZKEY_PAM_INTEGRATION_KEY:-hgIkeyDoNotLog0001}"
SKEY="${EZKEY_PAM_SECRET_KEY:-hgSkeyDoNotLog0001}"

ha_snapshot() {
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

ha_write_conf() {
  local url="$1"
  local tmp
  umask 077
  tmp="$(mktemp /etc/security/pam_ezkey.conf.hg.XXXXXX)"
  # lab_http is set twice on purpose. The parser keeps the last key, trims
  # spaces, and accepts FALSE as false. The effective value is on the
  # attempt line, not on a literal lab_http=false row.
  cat >"${tmp}" <<EOF
# Host-grade fixture (#1200). https only. Dummy keys.
integration_api_url=${url}
integration_key=${IKEY}
secret_key=${SKEY}
wait_timeout=15
wait_polling=2
api_timeout=3
challenge_requested=true
lab_http=true
lab_http = FALSE
context_title=SSH login
context_message=host-grade fixture
debug=false
EOF
  install -m 600 -o root -g root "${tmp}" "${CONF}"
  rm -f "${tmp}"
}

ha_write_fixture_files() {
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

ha_apply() {
  local url="${EZKEY_PAM_API_URL:-}"
  if [ -z "${url}" ]; then
    echo "host-apply apply: set EZKEY_PAM_API_URL" >&2
    return 1
  fi
  ha_snapshot
  ha_write_conf "${url}"
  ha_write_fixture_files
}

ha_remove() {
  if [ ! -f "${SNAP}/READY" ]; then
    echo "host-apply remove: no snapshot" >&2
    return 1
  fi
  cp -a "${SNAP}/sshd_config" "${SSHD_CFG}"
  cp -a "${SNAP}/pam.d-sshd" "${PAM_SSHD}"
  cp -a "${SNAP}/pam_ezkey.conf" "${CONF}"
  rm -f "${ADMIN_CFG}"
}

ha_arm() {
  local seconds="${EZKEY_PAM_ROLLBACK_SECONDS:-}"
  local minutes="${EZKEY_PAM_ROLLBACK_MINUTES:-15}"
  local delay now
  if [ ! -f "${SNAP}/READY" ]; then
    echo "host-apply arm: no snapshot" >&2
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

ha_confirm() {
  if [ ! -f "${ROLL}/state" ]; then
    echo "host-apply confirm: not armed" >&2
    return 1
  fi
  sed -i 's/^confirmed=.*/confirmed=1/' "${ROLL}/state"
}

ha_check() {
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
    ha_remove
  fi
}

case "${1:-}" in
  apply) ha_apply ;;
  remove) ha_remove ;;
  arm) ha_arm ;;
  confirm) ha_confirm ;;
  check) ha_check ;;
  *)
    echo "usage: host-apply.sh apply|remove|arm|confirm|check" >&2
    exit 2
    ;;
esac
