# Sourced by host-grade-accept.sh. One scenario sequence, no second entry point.
# shellcheck shell=bash
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
  hg_authpriv_up
  PAM_SO="$(hg_pam_so)"
  hg_host_keys
  install -d -m 700 "${KEY_DIR}"
  hg_users
  hg_prepare_wrong_key

  # shellcheck disable=SC1091
  . "${PAM_ROOT}/scripts/sshd-warn-global-maxauthtries-1.sh"

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

  printf 'pass=%s\nfail=%s\nchecks=%s\n' "${HG_PASS}" "${HG_FAIL}" \
    "$((HG_PASS + HG_FAIL))" >>"${RESULT}"
  echo "host-grade ${EZKEY_PAM_IMAGE_LABEL}: pass=${HG_PASS} fail=${HG_FAIL} checks=$((HG_PASS + HG_FAIL))"
  if [ "${HG_FAIL}" -ne 0 ]; then
    exit 1
  fi
}

