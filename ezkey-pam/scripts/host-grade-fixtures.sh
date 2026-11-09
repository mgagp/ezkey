# Sourced by host-grade-accept.sh. Fixture helpers for the host-grade runner.
# shellcheck shell=bash
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
  local _
  for _ in $(seq 1 50); do
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
  # UsePAM no treats a locked shadow ("!" or "!!") as an invalid user before
  # the key is read. The hash is a valid random password so the key is
  # considered. PasswordAuthentication no is what blocks password login.
  usermod -p "$(openssl passwd -6 "$(openssl rand -hex 16)")" "${ADMIN_USER}"
  usermod -aG "${MFA_GROUP}" "${MFA_USER}"
  id -nG "${MFA_USER}" | grep -qw "${MFA_GROUP}"
  if id -nG "${PLAIN_USER}" | grep -qw "${MFA_GROUP}"; then
    echo "plain user must not be in ${MFA_GROUP}" >&2
    return 1
  fi
}

# Delivered operator tool. The runner does not keep a second copy of apply,
# remove, or rollback.
hg_tool() {
  EZKEY_PAM_CONF="${CONF}" \
    EZKEY_PAM_SSHD_CONFIG="${SSHD_CFG}" \
    EZKEY_PAM_PAM_SSHD="${PAM_SSHD}" \
    EZKEY_PAM_ADMIN_SSHD="${ADMIN_CFG}" \
    EZKEY_PAM_STATE_DIR="${STATE_DIR}" \
    EZKEY_PAM_MFA_PORT="${MFA_PORT}" \
    EZKEY_PAM_ADMIN_PORT="${ADMIN_PORT}" \
    EZKEY_PAM_MFA_GROUP="${MFA_GROUP}" \
    EZKEY_PAM_ADMIN_USER="${ADMIN_USER}" \
    EZKEY_PAM_INTEGRATION_KEY="${HG_IKEY}" \
    EZKEY_PAM_SECRET_KEY="${HG_SKEY}" \
    EZKEY_PAM_SSHD="${EZKEY_PAM_SSHD:-/usr/sbin/sshd}" \
    EZKEY_PAM_APPLY_FAIL_AFTER="${EZKEY_PAM_APPLY_FAIL_AFTER:-}" \
    "${PAM_ROOT}/scripts/host-apply.sh" "$@"
}

hg_apply_url() {
  EZKEY_PAM_API_URL="$1" hg_tool apply
}

hg_apply() {
  hg_apply_url "https://127.0.0.1:${STUB_PORT}"
}

hg_remove() {
  hg_tool remove
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

# sshd, pam.d, and conf only. Used before the admin config exists.
hg_base_hash() {
  sha256sum "${SSHD_CFG}" "${PAM_SSHD}" "${CONF}" \
    | awk '{print $1}' | sha256sum | awk '{print $1}'
}

hg_rollback_arm() {
  if [ -n "${EZKEY_PAM_ROLLBACK_SECONDS:-}" ]; then
    EZKEY_PAM_ROLLBACK_SECONDS="${EZKEY_PAM_ROLLBACK_SECONDS}" hg_tool arm
  else
    hg_tool arm
  fi
}

hg_rollback_confirm() {
  hg_tool confirm
}

hg_rollback_check() {
  hg_tool check
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
  for _ in $(seq 1 50); do
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
  # $1 and $2 belong to the child shell.
  # shellcheck disable=SC2016
  timeout 1s sh -c 'printf "%s\n" "$1" >"$2"' _ "${msg}" "${fifo}" \
    >/dev/null 2>&1 || true
}

# One pre-create ok and one challenge Enter. Does not loop into a second try.
hg_ssh_prompts() {
  local user="$1"
  local keys="$2"
  HG_OUT="$(mktemp /tmp/hg-ssh-pty.XXXXXX)"
  local ctrl_dir ctrl bg flat sent_ok sent_enter
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
  for _ in $(seq 1 300); do
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
  local now
  for _ in $(seq 1 50); do
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

# Auth stack only. Comments are dropped. Comparison is case-insensitive
# (PAM is). A leading "-" on some other module is still that type. A leading
# "-" on the pam_ezkey line is rejected: PAM skips an unloadable module and
# the stack continues. The control field of pam_ezkey must be required or
# requisite. "required" buried in module arguments does not count. Before
# that line, sufficient, include, substack, @include, [default=ignore], any
# done action, and any jump =N (N>=1) for any key fail the check.
hg_auth_stack_ok() {
  local file="$1"
  local raw line low token body type control module saw
  saw=0
  while IFS= read -r raw || [ -n "${raw}" ]; do
    line="$(printf '%s' "${raw}" | sed -E 's/(^|[[:space:]])#.*$//')"
    line="$(printf '%s' "${line}" | sed -E 's/^[[:space:]]+//; s/[[:space:]]+$//')"
    [ -z "${line}" ] && continue
    low="$(printf '%s' "${line}" | tr '[:upper:]' '[:lower:]')"
    if printf '%s' "${low}" | grep -Eq '^@include([[:space:]]|$)'; then
      [ "${saw}" -eq 0 ] && return 1
      continue
    fi
    token="${low%%[[:space:]]*}"
    body="${low#"${token}"}"
    body="$(printf '%s' "${body}" | sed -E 's/^[[:space:]]+//')"
    type="${token#-}"
    [ "${type}" != "auth" ] && continue
    if [ "${body#\[}" != "${body}" ]; then
      control="$(printf '%s' "${body}" | sed -E 's/^(\[[^]]+\]).*/\1/')"
      module="${body#"${control}"}"
    else
      control="${body%%[[:space:]]*}"
      module="${body#"${control}"}"
    fi
    module="$(printf '%s' "${module}" | sed -E 's/^[[:space:]]+//')"
    if printf '%s' "${module}" | grep -q 'pam_ezkey\.so'; then
      # -auth required pam_ezkey.so is fail-open if the module cannot load.
      if [ "${token#-}" != "${token}" ]; then
        return 1
      fi
      case "${control}" in
        required|requisite) saw=1 ;;
        *) return 1 ;;
      esac
    elif [ "${saw}" -eq 0 ]; then
      case "${control}" in
        sufficient|include|substack) return 1 ;;
      esac
      if printf '%s' "${control}" | grep -Eq 'default=ignore'; then
        return 1
      fi
      # Any key=done action. done continues past this line on that result.
      if printf '%s' "${control}" | grep -Eq '=done([^[:alnum:]_]|$)'; then
        return 1
      fi
      # A jump of N>=1 on any key (success=1, success=2, default=1, =10).
      if printf '%s' "${control}" | grep -Eq '=[1-9][0-9]*([[:space:]]|]|$)'; then
        return 1
      fi
    fi
  done <"${file}"
  [ "${saw}" -eq 1 ]
}

hg_expect_stack() {
  local key="$1"
  local want="$2"
  local file="$3"
  local msg="$4"
  local rc=0
  hg_auth_stack_ok "${file}" || rc=1
  if [ "${want}" = "ok" ] && [ "${rc}" -eq 0 ]; then
    hg_pass "${key}" "${msg}"
  elif [ "${want}" = "bad" ] && [ "${rc}" -ne 0 ]; then
    hg_pass "${key}" "${msg}"
  else
    hg_fail "${key}" "${msg} (parser disagreed)"
  fi
}

hg_control_flag_cases() {
  local dir
  dir="$(mktemp -d /tmp/hg-pam-stack.XXXXXX)"
  if hg_auth_stack_ok "${PAM_SSHD}"; then
    hg_pass line2_control_flag "live pam_ezkey control is required or requisite"
  else
    hg_fail line2_control_flag "live pam_ezkey control is not required or requisite"
  fi
  printf '%s\n' '# auth required pam_ezkey.so' 'auth sufficient pam_ezkey.so' \
    >"${dir}/comment-then-sufficient"
  hg_expect_stack line2_reject_comment_then_sufficient bad \
    "${dir}/comment-then-sufficient" \
    "commented required line does not hide a sufficient control"
  printf '%s\n' 'auth optional pam_ezkey.so' >"${dir}/optional"
  hg_expect_stack line2_reject_optional bad "${dir}/optional" \
    "optional is not a fail-closed control"
  printf '%s\n' 'auth sufficient pam_ezkey.so conf=/tmp required' >"${dir}/args"
  hg_expect_stack line2_reject_required_in_args bad "${dir}/args" \
    "required in the module arguments is not the control field"
  printf '%s\n' 'auth sufficient pam_permit.so' 'auth required pam_ezkey.so' \
    >"${dir}/before-sufficient"
  hg_expect_stack line2_reject_sufficient_before bad "${dir}/before-sufficient" \
    "sufficient before pam_ezkey can short-circuit"
  printf '%s\n' 'auth [default=ignore] pam_permit.so' 'auth requisite pam_ezkey.so' \
    >"${dir}/before-ignore"
  hg_expect_stack line2_reject_default_ignore_before bad "${dir}/before-ignore" \
    "[default=ignore] before pam_ezkey can short-circuit"
  printf '%s\n' '# auth sufficient pam_ezkey.so' 'auth requisite pam_ezkey.so' \
    >"${dir}/comment-ok"
  hg_expect_stack line2_accept_requisite_after_comment ok "${dir}/comment-ok" \
    "a comment does not hide a requisite pam_ezkey line"
  printf '%s\n' '-auth sufficient pam_permit.so' 'auth required pam_ezkey.so' \
    >"${dir}/dash-sufficient"
  hg_expect_stack line2_reject_dash_sufficient_before bad "${dir}/dash-sufficient" \
    "-auth sufficient before pam_ezkey can short-circuit"
  printf '%s\n' 'auth [success=done default=die] pam_permit.so' \
    'auth required pam_ezkey.so' >"${dir}/success-done"
  hg_expect_stack line2_reject_success_done_before bad "${dir}/success-done" \
    "[success=done] before pam_ezkey can short-circuit"
  printf '%s\n' 'auth [success=1 default=die] pam_permit.so' \
    'auth required pam_ezkey.so' >"${dir}/success-1"
  hg_expect_stack line2_reject_success_1_before bad "${dir}/success-1" \
    "[success=1] before pam_ezkey can skip it"
  printf '%s\n' 'auth include common-auth' 'auth required pam_ezkey.so' \
    >"${dir}/include"
  hg_expect_stack line2_reject_include_before bad "${dir}/include" \
    "include before pam_ezkey can pull in another stack"
  printf '%s\n' 'auth substack common-auth' 'auth required pam_ezkey.so' \
    >"${dir}/substack"
  hg_expect_stack line2_reject_substack_before bad "${dir}/substack" \
    "substack before pam_ezkey can pull in another stack"
  printf '%s\n' '@include common-auth' 'auth required pam_ezkey.so' \
    >"${dir}/at-include"
  hg_expect_stack line2_reject_at_include_before bad "${dir}/at-include" \
    "@include before pam_ezkey can pull in another stack"
  printf '%s\n' 'AUTH SUFFICIENT PAM_PERMIT.SO' 'AUTH REQUIRED PAM_EZKEY.SO' \
    >"${dir}/upper"
  hg_expect_stack line2_reject_uppercase_sufficient_before bad "${dir}/upper" \
    "an uppercase sufficient line before pam_ezkey can short-circuit"
  printf '%s\n' 'AUTH REQUISITE PAM_EZKEY.SO' >"${dir}/upper-ok"
  hg_expect_stack line2_accept_uppercase_requisite ok "${dir}/upper-ok" \
    "uppercase requisite is a fail-closed control"
  printf '%s\n' '-auth required pam_ezkey.so' >"${dir}/dash-ok"
  hg_expect_stack line2_accept_dash_required bad "${dir}/dash-ok" \
    "a leading dash on the pam_ezkey line is fail-open"
  printf '%s\n' '-auth required pam_permit.so' 'auth required pam_ezkey.so' \
    >"${dir}/dash-other"
  hg_expect_stack line2_accept_dash_on_other_module ok "${dir}/dash-other" \
    "a leading dash on a different module does not hide pam_ezkey"
  printf '%s\n' 'auth [default=done] pam_permit.so' 'auth required pam_ezkey.so' \
    >"${dir}/default-done"
  hg_expect_stack line2_reject_default_done_before bad "${dir}/default-done" \
    "[default=done] before pam_ezkey can short-circuit"
  printf '%s\n' 'auth [success=2 default=die] pam_permit.so' \
    'auth required pam_ezkey.so' >"${dir}/success-2"
  hg_expect_stack line2_reject_success_2_before bad "${dir}/success-2" \
    "[success=2] before pam_ezkey can skip it"
  printf '%s\n' 'auth [success=ok default=1] pam_permit.so' \
    'auth required pam_ezkey.so' >"${dir}/default-jump"
  hg_expect_stack line2_reject_default_jump_before bad "${dir}/default-jump" \
    "[success=ok default=1] before pam_ezkey can skip it"
  rm -rf "${dir}"
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

# Non-members stay on publickey. A dead module must not lock them out.
# The key fails the suite unless the session opens.
hg_assert_plain_session() {
  local key="$1"
  hg_reset_stub_log
  hg_ssh_batch "${MFA_PORT}" "${PLAIN_USER}" "${KEY_DIR}/${PLAIN_USER}"
  if hg_session_yes && [ "$(hg_creates)" -eq 0 ]; then
    hg_note "${key}" "session"
    hg_pass "${key}_session" "non-member key still opens a session on the MFA port"
  else
    hg_note "${key}" "denied"
    hg_fail "${key}_session" "non-member key did not open a session on the MFA port"
  fi
}

hg_last_attempt() {
  grep -F 'pam_ezkey: attempt ' /var/log/secure 2>/dev/null | tail -n 1 || true
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

