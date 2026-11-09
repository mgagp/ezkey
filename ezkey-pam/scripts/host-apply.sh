#!/usr/bin/env bash
# Host-grade fixture tool (#1200). The acceptance runner calls these
# subcommands. An operator can call them too. This is not a systemd unit.
# The script ships in the source tree. make install does not copy it, and
# install.sh does not call it.
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
#
# Every environment value is rejected when it contains CR or LF. Group, user,
# and both ports must be simple tokens:
#   group, user  [A-Za-z_][A-Za-z0-9_-]{0,31}
#   ports        decimal 1-65535, no leading zero ([1-9][0-9]{0,4})
# The API URL must match ^https:// with no space or tab. Conf, sshd, pam.d,
# the admin config, and the state directory must be absolute paths.
# EZKEY_PAM_ROLLBACK_SECONDS, when set, must be a positive integer.
# Snapshot copies use || return, because apply calls ha_snapshot under ||
# and that disables set -e. A failed copy does not write READY.
# apply writes each file to a temp in that file's directory, then renames it
# over the destination. sshd_config, pam.d, and the admin config keep the
# existing mode and owner (644 root:root when the file is new) and are
# refused when the file or its directory is not root-owned or is group- or
# world-writable. The conf and its snapshot copy are always 600 root:root.
# Other snapshot copies are not made more permissive than the original.
# An existing state directory that is not root:root mode 700 is refused.
# A new parent is created root-only. EZKEY_PAM_SSHD must be a root-owned
# executable that is not a symlink and not writable by group or other.
# Symlinked config targets are refused. A leftover .hg temp is removed on
# exit and on INT, TERM, and HUP. restorecon runs when the command exists.
# If any step fails, the snapshot is restored.
# sshd -t must pass on the MFA config and the admin config before apply
# returns 0. remove, and a check that restores, run sshd -t on the restored
# MFA config. A restoring check then deletes the rollback record.
#
# EZKEY_PAM_APPLY_FAIL_AFTER=sshd is the suite hook. apply writes
# sshd_config, then fails and restores the snapshot. Any other non-empty
# value is rejected before a write.
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
SSHD_BIN="${EZKEY_PAM_SSHD:-/usr/sbin/sshd}"
# Path of the temp file currently being written. The trap removes it so a
# killed write cannot leave .hg.XXXXXX in /etc/pam.d, where PAM would treat
# it as a service.
HA_TMP=""
ha_cleanup_tmp() {
  if [ -n "${HA_TMP}" ]; then
    rm -f "${HA_TMP}"
    HA_TMP=""
  fi
}
trap ha_cleanup_tmp EXIT
trap 'ha_cleanup_tmp; exit 1' INT TERM HUP

ha_reject_crlf() {
  local name="$1"
  local value="$2"
  case "${value}" in
    *$'\n'*|*$'\r'*)
      echo "host-apply: ${name} contains a newline or CR" >&2
      return 1
      ;;
  esac
  return 0
}

# grep -E matches one line. A newline in the value would satisfy the pattern
# on the first line and inject another config line. ha_reject_crlf is what
# blocks that injection. A group or user name that contains a dot stays refused.
ha_simple_token() {
  local name="$1"
  local value="$2"
  if printf '%s' "${value}" | grep -Eq '^[A-Za-z_][A-Za-z0-9_-]{0,31}$'; then
    return 0
  fi
  echo "host-apply: ${name} must match [A-Za-z_][A-Za-z0-9_-]{0,31}" >&2
  return 1
}

ha_port_token() {
  local name="$1"
  local value="$2"
  if printf '%s' "${value}" | grep -Eq '^[1-9][0-9]{0,4}$' \
    && [ "${value}" -le 65535 ]; then
    return 0
  fi
  echo "host-apply: ${name} must be a decimal port 1-65535 with no leading zero" >&2
  return 1
}

# Reject CR/LF in every value this process read from the environment.
ha_reject_crlf_env() {
  ha_reject_crlf EZKEY_PAM_CONF "${CONF}" || return 1
  ha_reject_crlf EZKEY_PAM_SSHD_CONFIG "${SSHD_CFG}" || return 1
  ha_reject_crlf EZKEY_PAM_PAM_SSHD "${PAM_SSHD}" || return 1
  ha_reject_crlf EZKEY_PAM_ADMIN_SSHD "${ADMIN_CFG}" || return 1
  ha_reject_crlf EZKEY_PAM_STATE_DIR "${STATE_DIR}" || return 1
  ha_reject_crlf EZKEY_PAM_MFA_PORT "${MFA_PORT}" || return 1
  ha_reject_crlf EZKEY_PAM_ADMIN_PORT "${ADMIN_PORT}" || return 1
  ha_reject_crlf EZKEY_PAM_MFA_GROUP "${MFA_GROUP}" || return 1
  ha_reject_crlf EZKEY_PAM_ADMIN_USER "${ADMIN_USER}" || return 1
  ha_reject_crlf EZKEY_PAM_INTEGRATION_KEY "${IKEY}" || return 1
  ha_reject_crlf EZKEY_PAM_SECRET_KEY "${SKEY}" || return 1
  ha_reject_crlf EZKEY_PAM_SSHD "${SSHD_BIN}" || return 1
  ha_reject_crlf EZKEY_PAM_API_URL "${EZKEY_PAM_API_URL:-}" || return 1
  ha_reject_crlf EZKEY_PAM_ROLLBACK_SECONDS "${EZKEY_PAM_ROLLBACK_SECONDS:-}" || return 1
  ha_reject_crlf EZKEY_PAM_ROLLBACK_MINUTES "${EZKEY_PAM_ROLLBACK_MINUTES:-}" || return 1
  ha_reject_crlf EZKEY_PAM_APPLY_FAIL_AFTER "${EZKEY_PAM_APPLY_FAIL_AFTER:-}" || return 1
}

ha_validate_tokens() {
  ha_simple_token EZKEY_PAM_MFA_GROUP "${MFA_GROUP}" || return 1
  ha_simple_token EZKEY_PAM_ADMIN_USER "${ADMIN_USER}" || return 1
  ha_port_token EZKEY_PAM_MFA_PORT "${MFA_PORT}" || return 1
  ha_port_token EZKEY_PAM_ADMIN_PORT "${ADMIN_PORT}" || return 1
  if [ "${MFA_PORT}" = "${ADMIN_PORT}" ]; then
    echo "host-apply: MFA and admin ports must differ" >&2
    return 1
  fi
}

ha_validate_url() {
  local url="$1"
  if printf '%s' "${url}" | grep -Eq '^https://[^[:space:]]+$'; then
    return 0
  fi
  echo "host-apply: EZKEY_PAM_API_URL must match ^https:// with no space or tab" >&2
  return 1
}

ha_abs_path() {
  local name="$1"
  local value="$2"
  case "${value}" in
    /*) return 0 ;;
  esac
  echo "host-apply: ${name} must be an absolute path" >&2
  return 1
}

ha_validate_abs_paths() {
  ha_abs_path EZKEY_PAM_CONF "${CONF}" || return 1
  ha_abs_path EZKEY_PAM_SSHD_CONFIG "${SSHD_CFG}" || return 1
  ha_abs_path EZKEY_PAM_PAM_SSHD "${PAM_SSHD}" || return 1
  ha_abs_path EZKEY_PAM_ADMIN_SSHD "${ADMIN_CFG}" || return 1
  ha_abs_path EZKEY_PAM_STATE_DIR "${STATE_DIR}" || return 1
}

ha_not_symlink() {
  local name="$1"
  local path="$2"
  if [ -L "${path}" ]; then
    echo "host-apply: ${name} must not be a symlink" >&2
    return 1
  fi
}

ha_refuse_symlinks() {
  ha_not_symlink EZKEY_PAM_CONF "${CONF}" || return 1
  ha_not_symlink EZKEY_PAM_SSHD_CONFIG "${SSHD_CFG}" || return 1
  ha_not_symlink EZKEY_PAM_PAM_SSHD "${PAM_SSHD}" || return 1
  ha_not_symlink EZKEY_PAM_SSHD "${SSHD_BIN}" || return 1
  ha_not_symlink EZKEY_PAM_STATE_DIR "${STATE_DIR}" || return 1
  if [ -e "${ADMIN_CFG}" ] || [ -L "${ADMIN_CFG}" ]; then
    ha_not_symlink EZKEY_PAM_ADMIN_SSHD "${ADMIN_CFG}" || return 1
  fi
}

# uid 0, and not group- or world-writable. World-readable is allowed.
ha_require_root_not_open() {
  local name="$1"
  local path="$2"
  local mode uid extra
  mode="$(stat -c '%a' "${path}")" || return 1
  uid="$(stat -c '%u' "${path}")" || return 1
  if [ "${uid}" -ne 0 ]; then
    echo "host-apply: ${name} is not root-owned" >&2
    return 1
  fi
  extra=$(( (8#${mode}) & 022 ))
  if [ "${extra}" -ne 0 ]; then
    echo "host-apply: ${name} is group- or world-writable" >&2
    return 1
  fi
}

ha_validate_sshd_bin() {
  if [ ! -f "${SSHD_BIN}" ] || [ ! -x "${SSHD_BIN}" ]; then
    echo "host-apply: EZKEY_PAM_SSHD must be a root-owned executable" >&2
    return 1
  fi
  ha_require_root_not_open EZKEY_PAM_SSHD "${SSHD_BIN}" || return 1
}

ha_validate_write_targets() {
  ha_require_root_not_open "sshd config directory" "$(dirname "${SSHD_CFG}")" || return 1
  ha_require_root_not_open "sshd config" "${SSHD_CFG}" || return 1
  ha_require_root_not_open "pam.d directory" "$(dirname "${PAM_SSHD}")" || return 1
  ha_require_root_not_open "pam.d sshd" "${PAM_SSHD}" || return 1
  ha_require_root_not_open "admin config directory" "$(dirname "${ADMIN_CFG}")" || return 1
  if [ -e "${ADMIN_CFG}" ]; then
    ha_require_root_not_open "admin config" "${ADMIN_CFG}" || return 1
  fi
  ha_require_root_not_open "conf directory" "$(dirname "${CONF}")" || return 1
}

# An existing state directory must already be root:root mode 700. Do not
# chmod it into compliance. A missing parent is created root-only.
ha_prepare_state_dir() {
  local parent mode ug
  parent="$(dirname "${STATE_DIR}")"
  if [ -L "${parent}" ] || [ -L "${STATE_DIR}" ]; then
    echo "host-apply: state directory must not be a symlink" >&2
    return 1
  fi
  if [ ! -d "${parent}" ]; then
    mkdir -p "${parent}" || return 1
    chown root:root "${parent}" || return 1
    chmod 700 "${parent}" || return 1
  fi
  ha_require_root_not_open "state parent" "${parent}" || return 1
  if [ -e "${STATE_DIR}" ]; then
    if [ ! -d "${STATE_DIR}" ]; then
      echo "host-apply: STATE_DIR must be a directory" >&2
      return 1
    fi
    mode="$(stat -c '%a' "${STATE_DIR}")" || return 1
    ug="$(stat -c '%u:%g' "${STATE_DIR}")" || return 1
    if [ "${ug}" != "0:0" ] || [ "${mode}" != "700" ]; then
      echo "host-apply: STATE_DIR must be root:root mode 700" >&2
      return 1
    fi
  else
    mkdir "${STATE_DIR}" || return 1
    chown root:root "${STATE_DIR}" || return 1
    chmod 700 "${STATE_DIR}" || return 1
  fi
}

ha_snapshot_file() {
  local src="$1"
  local dst="$2"
  local kind="$3"
  local src_mode dst_mode extra
  cp -a "${src}" "${dst}" || return 1
  if [ -L "${dst}" ]; then
    rm -f "${dst}"
    echo "host-apply: snapshot of ${src} is a symlink" >&2
    return 1
  fi
  if [ "${kind}" = "conf" ]; then
    chmod 600 "${dst}" || return 1
    chown root:root "${dst}" || return 1
    return 0
  fi
  chmod --reference="${src}" "${dst}" || return 1
  chown --reference="${src}" "${dst}" || return 1
  src_mode="$(stat -c '%a' "${src}")" || return 1
  dst_mode="$(stat -c '%a' "${dst}")" || return 1
  extra=$(( (8#${dst_mode}) & ~(8#${src_mode}) & 0777 ))
  if [ "${extra}" -ne 0 ]; then
    echo "host-apply: snapshot of ${src} is more permissive than the original" >&2
    return 1
  fi
}

ha_snapshot() {
  if [ -f "${SNAP}/READY" ]; then
    return 0
  fi
  mkdir -p "${SNAP}" || return 1
  chown root:root "${SNAP}" || return 1
  chmod 700 "${SNAP}" || return 1
  # apply invokes this function with ||, which turns set -e off for the body.
  ha_snapshot_file "${SSHD_CFG}" "${SNAP}/sshd_config" other || return 1
  ha_snapshot_file "${PAM_SSHD}" "${SNAP}/pam.d-sshd" other || return 1
  ha_snapshot_file "${CONF}" "${SNAP}/pam_ezkey.conf" conf || return 1
  : >"${SNAP}/READY" || return 1
  chmod 600 "${SNAP}/READY" || return 1
}

ha_restore_snapshot() {
  cp -a "${SNAP}/sshd_config" "${SSHD_CFG}" || return 1
  cp -a "${SNAP}/pam.d-sshd" "${PAM_SSHD}" || return 1
  cp -a "${SNAP}/pam_ezkey.conf" "${CONF}" || return 1
  rm -f "${ADMIN_CFG}" || return 1
}

ha_sshd_ok() {
  local cfg="$1"
  "${SSHD_BIN}" -t -f "${cfg}"
}

# stdin is the file body. The temp file lives in the destination directory so
# mv is a rename on that filesystem. With no second argument, the existing
# mode and owner are copied onto the temp file first. A new path falls back
# to 644 root:root. A second argument forces that mode and root:root instead.
# pam_ezkey.conf passes 600.
ha_write_atomic() {
  local dest="$1"
  local force="${2:-}"
  local dir tmp
  dir="$(dirname "${dest}")"
  tmp="$(mktemp "${dir}/.hg.XXXXXX")" || return 1
  HA_TMP="${tmp}"
  if ! cat >"${tmp}"; then
    ha_cleanup_tmp
    return 1
  fi
  if [ -n "${force}" ]; then
    chmod "${force}" "${tmp}" || {
      ha_cleanup_tmp
      return 1
    }
    chown root:root "${tmp}" || {
      ha_cleanup_tmp
      return 1
    }
  elif [ -e "${dest}" ]; then
    chmod --reference="${dest}" "${tmp}" || {
      ha_cleanup_tmp
      return 1
    }
    chown --reference="${dest}" "${tmp}" || {
      ha_cleanup_tmp
      return 1
    }
  else
    chmod 644 "${tmp}" || {
      ha_cleanup_tmp
      return 1
    }
    chown root:root "${tmp}" || {
      ha_cleanup_tmp
      return 1
    }
  fi
  mv -f "${tmp}" "${dest}" || {
    ha_cleanup_tmp
    return 1
  }
  HA_TMP=""
  if command -v restorecon >/dev/null 2>&1; then
    restorecon "${dest}" || return 1
  fi
}

ha_write_conf() {
  local url="$1"
  # lab_http is set twice on purpose. The parser keeps the last key, trims
  # spaces, and accepts FALSE as false. The effective value is on the
  # attempt line, not on a literal lab_http=false row.
  ha_write_atomic "${CONF}" 600 <<EOF
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
}

ha_write_sshd() {
  ha_write_atomic "${SSHD_CFG}" <<EOF
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
}

ha_write_pam() {
  ha_write_atomic "${PAM_SSHD}" <<EOF
#%PAM-1.0
# Host-grade fixture (#1200). required keeps MFA fail-closed.
# Do not use sufficient or [default=ignore] on pam_ezkey.
auth       required     pam_ezkey.so conf=${CONF}
account    required     pam_permit.so
password   required     pam_deny.so
session    optional     pam_loginuid.so
session    required     pam_permit.so
EOF
}

ha_write_admin() {
  ha_write_atomic "${ADMIN_CFG}" <<EOF
# Break-glass daemon (#1200). UsePAM no. Keys only. Not the MFA daemon.
Port ${ADMIN_PORT}
Protocol 2
HostKey /etc/ssh/ssh_host_rsa_key
HostKey /etc/ssh/ssh_host_ecdsa_key
HostKey /etc/ssh/ssh_host_ed25519_key
PidFile /run/sshd-hg-admin.pid
UseDNS no
SyslogFacility AUTHPRIV
LogLevel VERBOSE
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
}

ha_apply_writes() {
  local url="$1"
  ha_write_conf "${url}" || return 1
  ha_write_sshd || return 1
  if [ "${EZKEY_PAM_APPLY_FAIL_AFTER:-}" = "sshd" ]; then
    echo "host-apply apply: forced failure after sshd_config" >&2
    return 1
  fi
  ha_write_pam || return 1
  ha_write_admin || return 1
  ha_sshd_ok "${SSHD_CFG}" || return 1
  ha_sshd_ok "${ADMIN_CFG}" || return 1
}

ha_apply() {
  local url="${EZKEY_PAM_API_URL:-}"
  local rc
  if [ -z "${url}" ]; then
    echo "host-apply apply: set EZKEY_PAM_API_URL" >&2
    return 1
  fi
  ha_reject_crlf_env || return 1
  ha_validate_tokens || return 1
  ha_validate_url "${url}" || return 1
  ha_validate_abs_paths || return 1
  ha_refuse_symlinks || return 1
  ha_validate_sshd_bin || return 1
  ha_validate_write_targets || return 1
  ha_prepare_state_dir || return 1
  case "${EZKEY_PAM_APPLY_FAIL_AFTER:-}" in
    "" | sshd) ;;
    *)
      echo "host-apply apply: EZKEY_PAM_APPLY_FAIL_AFTER must be empty or sshd" >&2
      return 1
      ;;
  esac
  ha_snapshot || return 1
  # set -e would exit before the snapshot is restored.
  set +e
  ha_apply_writes "${url}"
  rc=$?
  set -e
  if [ "${rc}" -ne 0 ]; then
    echo "host-apply apply: failed; restoring snapshot" >&2
    if ! ha_restore_snapshot; then
      echo "host-apply apply: snapshot restore failed" >&2
      return 1
    fi
    return 1
  fi
}

ha_remove() {
  if [ ! -f "${SNAP}/READY" ]; then
    echo "host-apply remove: no snapshot" >&2
    return 1
  fi
  ha_reject_crlf_env || return 1
  ha_validate_abs_paths || return 1
  ha_refuse_symlinks || return 1
  ha_prepare_state_dir || return 1
  ha_restore_snapshot || return 1
  ha_sshd_ok "${SSHD_CFG}" || return 1
}

ha_arm() {
  local seconds="${EZKEY_PAM_ROLLBACK_SECONDS:-}"
  local minutes="${EZKEY_PAM_ROLLBACK_MINUTES:-15}"
  local delay now
  ha_reject_crlf_env || return 1
  ha_validate_abs_paths || return 1
  ha_prepare_state_dir || return 1
  if [ ! -f "${SNAP}/READY" ]; then
    echo "host-apply arm: no snapshot" >&2
    return 1
  fi
  if [ -n "${seconds}" ]; then
    case "${seconds}" in
      *[!0-9]*)
        echo "host-apply arm: EZKEY_PAM_ROLLBACK_SECONDS must be a positive integer" >&2
        return 1
        ;;
    esac
    if [ "${seconds}" -lt 1 ]; then
      echo "host-apply arm: EZKEY_PAM_ROLLBACK_SECONDS must be a positive integer" >&2
      return 1
    fi
    delay="${seconds}"
  else
    case "${minutes}" in
      '' | *[!0-9]*)
        echo "host-apply arm: EZKEY_PAM_ROLLBACK_MINUTES must be decimal" >&2
        return 1
        ;;
    esac
    delay=$((minutes * 60))
  fi
  now="$(date +%s)"
  if [ -L "${ROLL}" ]; then
    echo "host-apply: rollback directory must not be a symlink" >&2
    return 1
  fi
  mkdir -p "${ROLL}" || return 1
  chown root:root "${ROLL}" || return 1
  chmod 700 "${ROLL}" || return 1
  printf 'deadline=%s\nconfirmed=0\n' "$((now + delay))" >"${ROLL}/state" || return 1
  chown root:root "${ROLL}/state" || return 1
  chmod 600 "${ROLL}/state" || return 1
}

ha_confirm() {
  if [ ! -f "${ROLL}/state" ]; then
    echo "host-apply confirm: not armed" >&2
    return 1
  fi
  sed -i 's/^confirmed=.*/confirmed=1/' "${ROLL}/state"
}

ha_close_rollback() {
  rm -f "${ROLL}/state"
}

ha_check() {
  local deadline confirmed now
  if [ -L "${ROLL}/state" ]; then
    echo "host-apply: rollback state must not be a symlink" >&2
    return 1
  fi
  if [ ! -f "${ROLL}/state" ]; then
    return 0
  fi
  ha_prepare_state_dir || return 1
  ha_reject_crlf_env || return 1
  deadline="$(awk -F= '/^deadline=/{print $2}' "${ROLL}/state")"
  confirmed="$(awk -F= '/^confirmed=/{print $2}' "${ROLL}/state")"
  now="$(date +%s)"
  if [ "${confirmed}" = "1" ]; then
    return 0
  fi
  case "${deadline}" in
    '' | *[!0-9]*)
      echo "host-apply check: rollback deadline is not decimal" >&2
      return 1
      ;;
  esac
  if [ "${now}" -ge "${deadline}" ]; then
    ha_remove || return 1
    ha_close_rollback
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
