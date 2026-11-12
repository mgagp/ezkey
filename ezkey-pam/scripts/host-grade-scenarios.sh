# Sourced by host-grade-accept.sh. One scenario sequence, no second entry point.
# shellcheck shell=bash

# After a snapshot exists, apply must leave the conf at 0600 root:root.
hg_expect_conf_forced() {
  local key="$1"
  local msg="$2"
  local rc=0 meta
  set +e
  hg_apply
  rc=$?
  set -e
  local snap_meta
  meta="$(stat -c '%a %u:%g' "${CONF}" 2>/dev/null || printf '%s' missing)"
  snap_meta="$(stat -c '%a %u:%g' "${SNAP}/pam_ezkey.conf" 2>/dev/null || printf '%s' missing)"
  if [ "${rc}" -eq 0 ] && [ "${meta}" = "600 0:0" ] && [ "${snap_meta}" = "600 0:0" ]; then
    hg_pass "${key}" "${msg}"
  else
    hg_fail "${key}" "conf=${meta} snap=${snap_meta} rc=${rc}"
  fi
}

# apply must fail and leave sshd, pam.d, and conf byte for byte.
hg_expect_apply_rejected() {
  local key="$1"
  local msg="$2"
  local hash_before hash_after rc
  hash_before="$(hg_base_hash)"
  set +e
  hg_apply
  rc=$?
  set -e
  hash_after="$(hg_base_hash)"
  if [ "${rc}" -ne 0 ] && [ "${hash_before}" = "${hash_after}" ] \
    && [ ! -f "${SNAP}/READY" ]; then
    hg_pass "${key}" "${msg}"
  else
    hg_fail "${key}" "${msg} (rc=${rc})"
  fi
}

hg_run_scenarios() {
  hg_refuse_unless_container
  hg_need_root
  hg_result_init
  # EXIT stops the stub and sshd and puts pam_ezkey.so back if break-glass
  # replaced it. It does not restore sshd_config, pam.d, or pam_ezkey.conf.
  # That is acceptable because --inside refuses to run outside a disposable
  # container.
  trap hg_cleanup_procs EXIT

  hg_packages
  local saved_group saved_skey saved_port saved_user
  saved_group="${MFA_GROUP}"
  MFA_GROUP=$'ezkey-mfa\nPermitRootLogin yes'
  hg_expect_apply_rejected line5_reject_group_newline \
    "a newline in the group is rejected and files stay unchanged"
  MFA_GROUP="${saved_group}"
  saved_skey="${HG_SKEY}"
  HG_SKEY=$'hgSkeyDoNotLog0001\nintegration_api_url=https://evil.example'
  hg_expect_apply_rejected line5_reject_secret_newline \
    "a newline in the secret is rejected and files stay unchanged"
  HG_SKEY="${saved_skey}"
  saved_port="${MFA_PORT}"
  MFA_PORT=$'22\r'
  hg_expect_apply_rejected line5_reject_port_cr \
    "a CR in the port is rejected and files stay unchanged"
  MFA_PORT="${saved_port}"
  saved_user="${ADMIN_USER}"
  ADMIN_USER='hgadmin extra'
  hg_expect_apply_rejected line5_reject_user_token \
    "a user that is not a simple token is rejected and files stay unchanged"
  ADMIN_USER="${saved_user}"
  local saved_conf saved_state hash_before hash_after rc
  hash_before="$(hg_base_hash)"
  set +e
  hg_apply_url "https://127.0.0.1/bad path"
  rc=$?
  set -e
  hash_after="$(hg_base_hash)"
  if [ "${rc}" -ne 0 ] && [ "${hash_before}" = "${hash_after}" ] \
    && [ ! -f "${SNAP}/READY" ]; then
    hg_pass line5_reject_url_space \
      "a URL with a space is rejected and files stay unchanged"
  else
    hg_fail line5_reject_url_space "URL with a space rc=${rc}"
  fi
  saved_state="${STATE_DIR}"
  STATE_DIR="relative-state"
  hash_before="$(hg_base_hash)"
  set +e
  hg_apply
  rc=$?
  set -e
  STATE_DIR="${saved_state}"
  hash_after="$(hg_base_hash)"
  if [ "${rc}" -ne 0 ] && [ "${hash_before}" = "${hash_after}" ] \
    && [ ! -f "${SNAP}/READY" ] && [ ! -d relative-state ]; then
    hg_pass line5_reject_relative_state \
      "a relative state directory is rejected and files stay unchanged"
  else
    hg_fail line5_reject_relative_state "relative state directory rc=${rc}"
  fi
  local state_parent
  state_parent="$(dirname "${STATE_DIR}")"
  if [ -e "${STATE_DIR}" ] || [ -e "${state_parent}" ]; then
    hg_fail line5_reject_fail_after_before_dirs \
      "state directory already exists before the FAIL_AFTER check"
  else
    hash_before="$(hg_base_hash)"
    export EZKEY_PAM_APPLY_FAIL_AFTER=bogus
    set +e
    hg_apply
    rc=$?
    set -e
    unset EZKEY_PAM_APPLY_FAIL_AFTER
    hash_after="$(hg_base_hash)"
    if [ "${rc}" -ne 0 ] && [ "${hash_before}" = "${hash_after}" ] \
      && [ ! -e "${STATE_DIR}" ] && [ ! -e "${state_parent}" ]; then
      hg_pass line5_reject_fail_after_before_dirs \
        "an invalid FAIL_AFTER is rejected and creates no state directory"
    else
      hg_fail line5_reject_fail_after_before_dirs \
        "invalid FAIL_AFTER rc=${rc}"
    fi
  fi
  saved_conf="${CONF}"
  hash_before="$(sha256sum "${SSHD_CFG}" "${PAM_SSHD}" "${saved_conf}" \
    | awk '{print $1}' | sha256sum | awk '{print $1}')"
  CONF="${saved_conf}.missing"
  rm -f "${CONF}"
  set +e
  hg_apply
  rc=$?
  set -e
  CONF="${saved_conf}"
  hash_after="$(sha256sum "${SSHD_CFG}" "${PAM_SSHD}" "${CONF}" \
    | awk '{print $1}' | sha256sum | awk '{print $1}')"
  if [ "${rc}" -ne 0 ] && [ ! -f "${SNAP}/READY" ] \
    && [ "${hash_before}" = "${hash_after}" ] && [ ! -f "${ADMIN_CFG}" ]; then
    hg_pass line5_snapshot_missing_conf \
      "a missing conf fails the snapshot and does not write READY"
  else
    hg_fail line5_snapshot_missing_conf \
      "missing conf rc=${rc} ready=$( [ -f "${SNAP}/READY" ] && echo yes || echo no )"
  fi
  local sshd_mode before_link after_link fake_sshd
  sshd_mode="$(stat -c '%a' "${SSHD_CFG}")"
  chmod g+w "${SSHD_CFG}"
  hg_expect_apply_rejected line5_reject_group_writable_target \
    "a group-writable sshd_config is refused and files stay unchanged"
  chmod "${sshd_mode}" "${SSHD_CFG}"
  mv "${SSHD_CFG}" "${SSHD_CFG}.hg-orig"
  ln -s "${SSHD_CFG}.hg-orig" "${SSHD_CFG}"
  before_link="$(sha256sum "${SSHD_CFG}.hg-orig" | awk '{print $1}')"
  set +e
  hg_apply
  rc=$?
  set -e
  after_link="$(sha256sum "${SSHD_CFG}.hg-orig" | awk '{print $1}')"
  if [ "${rc}" -ne 0 ] && [ "${before_link}" = "${after_link}" ] \
    && [ -L "${SSHD_CFG}" ] && [ ! -f "${SNAP}/READY" ]; then
    hg_pass line5_reject_symlink_target \
      "a symlinked sshd_config is refused and the target stays unchanged"
  else
    hg_fail line5_reject_symlink_target "symlink target rc=${rc}"
  fi
  rm -f "${SSHD_CFG}"
  mv "${SSHD_CFG}.hg-orig" "${SSHD_CFG}"
  chown nobody "${STATE_DIR}"
  hg_expect_apply_rejected line5_reject_state_dir_owner \
    "a state directory not owned by root is refused"
  chown root:root "${STATE_DIR}"
  chmod 700 "${STATE_DIR}"
  fake_sshd="$(mktemp /tmp/hg-sshd-open.XXXXXX)"
  printf '#!/bin/sh\nexit 0\n' >"${fake_sshd}"
  chown root:root "${fake_sshd}"
  chmod 777 "${fake_sshd}"
  export EZKEY_PAM_SSHD="${fake_sshd}"
  hg_expect_apply_rejected line5_reject_writable_sshd \
    "a world-writable sshd binary is refused"
  unset EZKEY_PAM_SSHD
  rm -f "${fake_sshd}"

  hg_authpriv_up
  PAM_SO="$(hg_pam_so)"
  hg_host_keys
  install -d -m 700 "${KEY_DIR}"
  hg_users
  hg_prepare_wrong_key

  # shellcheck disable=SC1091
  . "${PAM_ROOT}/scripts/sshd-warn-global-maxauthtries-1.sh"

  chmod 640 "${SSHD_CFG}"
  chown root:wheel "${SSHD_CFG}"
  chmod 644 "${CONF}"
  chown "${MFA_USER}:${MFA_USER}" "${CONF}"
  hg_expect_conf_forced line5_conf_forced_from_644 \
    "a 644 conf is rewritten 600 root:root, including the snapshot"
  local hash1 hash2 sshd_meta
  hash1="$(hg_content_hash)"
  hg_apply
  hash2="$(hg_content_hash)"
  if [ "${hash1}" = "${hash2}" ]; then
    hg_pass line5_apply_idempotent "second apply leaves fixture bytes unchanged"
  else
    hg_fail line5_apply_idempotent "second apply changed fixture bytes"
  fi
  sshd_meta="$(stat -c '%a %U:%G' "${SSHD_CFG}")"
  if [ "${sshd_meta}" = "640 root:wheel" ]; then
    hg_pass line5_preserve_mode_owner \
      "sshd_config stays 640 root:wheel after apply"
  else
    hg_fail line5_preserve_mode_owner "sshd_config is ${sshd_meta}"
  fi
  rm -f "${CONF}"
  hg_expect_conf_forced line5_conf_forced_when_absent \
    "a missing conf is created 600 root:root, including the snapshot"

  hg_control_flag_cases
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

  if grep -q '^integration_api_url=https://' "${CONF}" \
    && ! grep -q '^lab_http=false$' "${CONF}"; then
    hg_pass line9_https_url \
      "fixture URL is https; effective lab_http is not a literal lab_http=false row"
  else
    hg_fail line9_https_url "fixture URL is not https, or lab_http is only a literal row"
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
  if [ "${attempt_delta}" -eq 1 ] \
    && printf '%s\n' "$(hg_last_attempt)" | grep -q 'lab_http=false'; then
    hg_pass line9_effective_lab_http \
      "approve attempt line records lab_http=false"
  else
    hg_fail line9_effective_lab_http \
      "approve attempt line did not record lab_http=false"
  fi

  # --- key + deny: no session, one create, one start, one attempt ---
  hg_start_stub rejected
  hg_apply_url "https://127.0.0.1:${STUB_PORT}"
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
  attempt_before="$(hg_log_count 'pam_ezkey: attempt ')"
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  hg_wait_log 'pam_ezkey: attempt ' "${attempt_before}" 1 || true
  attempt_delta="$(($(hg_log_count 'pam_ezkey: attempt ') - attempt_before))"
  if ! hg_session_yes && [ "$(hg_creates)" -eq 1 ] && [ "${attempt_delta}" -eq 1 ]; then
    hg_pass line2_expire_no_session "expiration yields no session and one attempt"
  else
    hg_fail line2_expire_no_session \
      "expire rc=${HG_RC} creates=$(hg_creates) attempt=${attempt_delta}"
  fi

  # --- HTTP 404 ---
  hg_start_stub http_404
  hg_reset_stub_log
  attempt_before="$(hg_log_count 'pam_ezkey: attempt ')"
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  hg_wait_log 'pam_ezkey: attempt ' "${attempt_before}" 1 || true
  attempt_delta="$(($(hg_log_count 'pam_ezkey: attempt ') - attempt_before))"
  if ! hg_session_yes && [ "$(hg_creates)" -eq 1 ] && [ "${attempt_delta}" -eq 1 ] \
    && printf '%s\n' "$(hg_last_attempt)" | grep -q 'result=UNAVAIL'; then
    hg_pass line2_http_404_no_session "HTTP 404 yields no session after one UNAVAIL attempt"
  else
    hg_fail line2_http_404_no_session \
      "HTTP 404 rc=${HG_RC} creates=$(hg_creates) attempt=${attempt_delta}"
  fi

  # --- API down ---
  hg_stop_pidfile "${STUB_PID}"
  hg_apply_url "https://127.0.0.1:${DOWN_PORT}"
  attempt_before="$(hg_log_count 'pam_ezkey: attempt ')"
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  hg_wait_log 'pam_ezkey: attempt ' "${attempt_before}" 1 || true
  attempt_delta="$(($(hg_log_count 'pam_ezkey: attempt ') - attempt_before))"
  if ! hg_session_yes && [ "${attempt_delta}" -eq 1 ] \
    && printf '%s\n' "$(hg_last_attempt)" | grep -q 'result=UNAVAIL'; then
    hg_pass line2_api_down_no_session "API down yields no session after one UNAVAIL attempt"
  else
    hg_fail line2_api_down_no_session \
      "API down rc=${HG_RC} attempt=${attempt_delta}"
  fi

  # --- mode 0666 ---
  hg_start_stub ok
  hg_apply_url "https://127.0.0.1:${STUB_PORT}"
  chmod 666 "${CONF}"
  attempt_before="$(hg_log_count 'pam_ezkey: attempt ')"
  start_before="$(hg_log_count 'pam_ezkey: start ')"
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  hg_wait_log 'pam_ezkey: attempt ' "${attempt_before}" 1 || true
  attempt_delta="$(($(hg_log_count 'pam_ezkey: attempt ') - attempt_before))"
  start_delta="$(($(hg_log_count 'pam_ezkey: start ') - start_before))"
  last_attempt="$(hg_last_attempt)"
  if ! hg_session_yes && [ "${attempt_delta}" -eq 1 ] && [ "${start_delta}" -eq 0 ] \
    && printf '%s\n' "${last_attempt}" | grep -q 'result=UNAVAIL' \
    && printf '%s\n' "${last_attempt}" | grep -q 'user=-'; then
    hg_pass line7_mode_0666_unavail \
      "conf mode 0666 returns one UNAVAIL attempt with user=- and no session"
  else
    hg_fail line7_mode_0666_unavail \
      "conf mode 0666 attempt=${attempt_delta} start=${start_delta}"
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
  hg_apply_url "https://127.0.0.1:${TLS_STUB_PORT}"
  attempt_before="$(hg_log_count 'pam_ezkey: attempt ')"
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  hg_wait_log 'pam_ezkey: attempt ' "${attempt_before}" 1 || true
  attempt_delta="$(($(hg_log_count 'pam_ezkey: attempt ') - attempt_before))"
  local tls_hits
  tls_hits="$(wc -l <"${TLS_STUB_LOG}" | tr -d ' ')"
  if ! hg_session_yes && [ "${tls_hits}" -eq 0 ] && [ "${attempt_delta}" -eq 1 ] \
    && printf '%s\n' "$(hg_last_attempt)" | grep -q 'result=UNAVAIL'; then
    hg_pass line9_self_signed_unavail \
      "self-signed https yields one UNAVAIL attempt and zero requests"
  else
    hg_fail line9_self_signed_unavail \
      "self-signed rc=${HG_RC} requests=${tls_hits} attempt=${attempt_delta}"
  fi
  hg_stop_pidfile "${TLS_STUB_PID}"
  hg_start_stub ok
  hg_apply_url "https://127.0.0.1:${STUB_PORT}"

  if hg_authpriv_has_secret || hg_authpriv_has_challenge; then
    hg_fail line8_no_secrets "authpriv contains a key or the challenge digits"
  else
    hg_pass line8_no_secrets "authpriv has no integration key, secret, or challenge digits"
  fi

  local member_attempts
  member_attempts="$(grep -Ec 'pam_ezkey: attempt .*authAttemptId=[0-9]+ ' /var/log/secure \
    || true)"
  if [ "${member_attempts}" -ge 1 ]; then
    hg_pass line4_member_attempt \
      "member authpriv has an attempt line with an auth attempt id"
  else
    hg_fail line4_member_attempt "member authpriv has no attempt id"
  fi

  # --- break-glass: broken module, then removed module ---
  cp -a "${PAM_SO}" "${PAM_SO}.hg-real"
  printf 'not-an-elf\n' >"${PAM_SO}"
  chmod 755 "${PAM_SO}"
  chown root:root "${PAM_SO}"
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  if hg_session_yes; then
    hg_fail line3_mfa_port_dies_broken "broken module still opened an MFA session"
  else
    hg_pass line3_mfa_port_dies_broken \
      "broken module: ezkey-mfa member gets no session"
  fi
  hg_assert_plain_session line3_plain_on_mfa_when_module_broken
  hg_ssh_batch "${ADMIN_PORT}" "${ADMIN_USER}" "${KEY_DIR}/${ADMIN_USER}"
  if hg_session_yes; then
    hg_pass line3_admin_port_during_broken_module \
      "admin port accepts the admin key while the module is broken"
  else
    hg_fail line3_admin_port_during_broken_module \
      "admin port failed while the module was broken"
  fi
  # UsePAM no rejects a locked shadow before it reads AllowUsers. Unlock the
  # member so the remaining denial is the AllowUsers line. Password
  # authentication stays off on this daemon.
  usermod -p "$(openssl passwd -6 "$(openssl rand -hex 16)")" "${MFA_USER}"
  local allow_before allow_delta
  allow_before="$(hg_log_count 'not listed in AllowUsers')"
  hg_ssh_batch "${ADMIN_PORT}" "${MFA_USER}" "${KEY_DIR}/${MFA_USER}"
  hg_wait_log 'not listed in AllowUsers' "${allow_before}" 1 || true
  allow_delta="$(($(hg_log_count 'not listed in AllowUsers') - allow_before))"
  if ! hg_session_yes && [ "${allow_delta}" -ge 1 ]; then
    hg_pass line3_admin_port_refuses_mfa_member \
      "admin port AllowUsers refuses an ezkey-mfa member"
  else
    hg_fail line3_admin_port_refuses_mfa_member \
      "admin port refusal was not an AllowUsers log line (delta=${allow_delta})"
  fi

  rm -f "${PAM_SO}"
  hg_ssh_prompts "${MFA_USER}" "-i ${KEY_DIR}/${MFA_USER}"
  if hg_session_yes; then
    hg_fail line3_mfa_port_dies_removed "removed module still opened an MFA session"
  else
    hg_pass line3_mfa_port_dies_removed \
      "removed module: ezkey-mfa member gets no session"
  fi
  hg_assert_plain_session line3_plain_on_mfa_when_module_removed
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

  local now deadline delta hash_before hash_after rc
  set +e
  EZKEY_PAM_ROLLBACK_SECONDS=0 hg_rollback_arm
  rc=$?
  set -e
  unset EZKEY_PAM_ROLLBACK_SECONDS
  if [ "${rc}" -ne 0 ] && [ ! -f "${ROLL}/state" ]; then
    hg_pass line6_reject_zero_rollback_seconds \
      "rollback seconds of 0 is rejected and no record is armed"
  else
    hg_fail line6_reject_zero_rollback_seconds \
      "rollback seconds of 0 rc=${rc}"
  fi
  hg_rollback_arm
  now="$(date +%s)"
  deadline="$(awk -F= '/^deadline=/{print $2}' "${ROLL}/state")"
  delta="$((deadline - now))"
  if [ "${delta}" -ge 890 ] && [ "${delta}" -le 910 ]; then
    hg_pass line6_default_window "rollback default is about 15 minutes"
  else
    hg_fail line6_default_window "rollback default delta was ${delta}s"
  fi
  hash_before="$(hg_content_hash)"
  hg_rollback_check
  hash_after="$(hg_content_hash)"
  if [ "${hash_before}" = "${hash_after}" ] && [ -f "${ROLL}/state" ]; then
    hg_pass line6_check_before_deadline_noop \
      "check before the deadline leaves the files and the rollback record"
  else
    hg_fail line6_check_before_deadline_noop \
      "check before the deadline changed files or closed the record"
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
  if [ ! -f "${ROLL}/state" ]; then
    hg_pass line6_check_closes_record \
      "a restoring check closes the rollback record"
  else
    hg_fail line6_check_closes_record "rollback record still armed after restore"
  fi

  hg_apply
  hash_before="$(hg_content_hash)"
  hg_rollback_check
  hash_after="$(hg_content_hash)"
  if [ "${hash_before}" = "${hash_after}" ] && [ ! -f "${ROLL}/state" ]; then
    hg_pass line6_check_without_arm_keeps \
      "check without a new arm does not undo apply"
  else
    hg_fail line6_check_without_arm_keeps \
      "check without a new arm changed the fixture"
  fi
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

  local err rc fake marker
  export EZKEY_PAM_APPLY_FAIL_AFTER=sshd
  set +e
  err="$(hg_apply 2>&1 >/dev/null)"
  rc=$?
  set -e
  unset EZKEY_PAM_APPLY_FAIL_AFTER
  if [ "${rc}" -ne 0 ] && hg_bytes_match_snapshot \
    && printf '%s\n' "${err}" | grep -q 'forced failure after sshd_config'; then
    hg_pass line5_mid_apply_failure_restores \
      "a failure after sshd_config restores the snapshot byte for byte"
  else
    hg_fail line5_mid_apply_failure_restores \
      "mid-apply failure rc=${rc} did not restore the snapshot"
  fi

  fake="$(mktemp /tmp/hg-sshd-fail.XXXXXX)"
  marker="$(mktemp /tmp/hg-sshd-mark.XXXXXX)"
  rm -f "${marker}"
  cat >"${fake}" <<EOF
#!/bin/sh
echo invoked >"${marker}"
exit 1
EOF
  chmod 755 "${fake}"
  export EZKEY_PAM_SSHD="${fake}"
  set +e
  hg_apply >/dev/null 2>&1
  rc=$?
  set -e
  unset EZKEY_PAM_SSHD
  if [ "${rc}" -ne 0 ] && hg_bytes_match_snapshot && [ -f "${marker}" ]; then
    hg_pass line5_sshd_test_failure_restores \
      "sshd -t failure restores the snapshot byte for byte"
  else
    hg_fail line5_sshd_test_failure_restores \
      "sshd -t failure rc=${rc} did not restore the snapshot"
  fi
  rm -f "${fake}" "${marker}"

  printf 'pass=%s\nfail=%s\nchecks=%s\n' "${HG_PASS}" "${HG_FAIL}" \
    "$((HG_PASS + HG_FAIL))" >>"${RESULT}"
  echo "host-grade ${EZKEY_PAM_IMAGE_LABEL}: pass=${HG_PASS} fail=${HG_FAIL} checks=$((HG_PASS + HG_FAIL))"
  if [ "${HG_FAIL}" -ne 0 ]; then
    exit 1
  fi
}

