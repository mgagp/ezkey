#!/usr/bin/env bash
# Mechanical RCA helpers for real-device Maestro campaigns.
# Sourced by run-mobile-real-device.sh. No NLP — grep + fixed hypothesis map only.
# shellcheck shell=bash

json_get() {
  local file="$1"
  local key="$2"
  if [[ ! -f "$file" ]]; then
    echo ""
    return 0
  fi
  if command -v python >/dev/null 2>&1; then
    python -c "import json,sys; d=json.load(open(sys.argv[1],encoding='utf-8')); v=d.get(sys.argv[2]); print('' if v is None else v)" "$file" "$key" 2>/dev/null && return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 -c "import json,sys; d=json.load(open(sys.argv[1],encoding='utf-8')); v=d.get(sys.argv[2]); print('' if v is None else v)" "$file" "$key" 2>/dev/null && return 0
  fi
  sed -n "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p" "$file" | head -n1
}

json_get_number() {
  local file="$1"
  local key="$2"
  if [[ ! -f "$file" ]]; then
    echo ""
    return 0
  fi
  if command -v python >/dev/null 2>&1; then
    python -c "import json,sys; d=json.load(open(sys.argv[1],encoding='utf-8')); v=d.get(sys.argv[2]); print('' if v is None else v)" "$file" "$key" 2>/dev/null && return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 -c "import json,sys; d=json.load(open(sys.argv[1],encoding='utf-8')); v=d.get(sys.argv[2]); print('' if v is None else v)" "$file" "$key" 2>/dev/null && return 0
  fi
  sed -n "s/.*\"${key}\"[[:space:]]*:[[:space:]]*\\([0-9][0-9]*\\).*/\\1/p" "$file" | head -n1
}

filter_logcat() {
  local raw="$1"
  local out="$2"
  if [[ ! -f "$raw" ]]; then
    : >"$out"
    return 0
  fi
  grep -E '\[PendingAuthRespond\]|EzkeyCrypto|pendingAuth_|handleRespond_|ERROR|Error|Exception|Element not found' "$raw" >"$out" || true
}

last_pending_auth_trace_step() {
  local filtered="$1"
  if [[ ! -f "$filtered" ]]; then
    echo "none"
    return 0
  fi
  local step
  step="$(
    grep -oE 'pendingAuth_[A-Za-z0-9_]+|handleRespond_[A-Za-z0-9_]+' "$filtered" |
      grep -vE 'handleRespond_finally$' |
      tail -n1 || true
  )"
  if [[ -z "$step" ]]; then
    echo "none"
  else
    echo "$step"
  fi
}

maestro_last_error() {
  local log="$1"
  if [[ ! -f "$log" ]]; then
    echo ""
    return 0
  fi
  grep -E 'Element not found|No visible element|Assertion is false|Timeout|timed out|Flow failed' "$log" | tail -n1 | cut -c1-240 || true
}

hypothesis_hint() {
  local last_step="$1"
  local maestro_exit="$2"
  local scenario="$3"
  local outcome="${4:-}"
  local maestro_err="${5:-}"
  if [[ "$outcome" == "pass" ]]; then
    echo "ok"
    return 0
  fi
  if [[ "$scenario" == "home-visible" ]]; then
    echo "Home list/hydration: enrollment row not visible (still loading, accordion collapsed, or wrong id)"
    return 0
  fi
  if [[ "$scenario" == "f2a-enroll" ]]; then
    echo "F2a wizard: seed input, bypass tap, or verify/challenge did not complete"
    return 0
  fi
  if [[ "$scenario" == "skip-consume" ]]; then
    echo "skip-consume: API status did not remain PENDING (no Maestro UI ran)"
    return 0
  fi
  if [[ "$maestro_exit" == "0" || "$maestro_exit" == "not-run" ]]; then
    echo "ok"
    return 0
  fi
  if echo "${maestro_err}" | grep -qi 'checkPending'; then
    echo "stayed on Pending / hierarchy: Maestro timed out waiting for checkPending after respond"
    return 0
  fi
  if [[ -z "$last_step" || "$last_step" == "none" ]]; then
    echo "gesture/selector or hierarchy: no PendingAuthRespond JS step (tap missed React, or never reached pending)"
    return 0
  fi
  case "$last_step" in
    pendingAuth_ui_approve_press | pendingAuth_ui_deny_press)
      echo "UI press reached JS but handleRespond did not start — challenge length, already processing, or hook skip"
      ;;
    handleRespond_skipped_* | handleRespond_abort_*)
      echo "hook returned early (${last_step}) — challenge gate or abort path"
      ;;
    handleRespond_try_begin | handleRespond_after_prefs)
      echo "biometric/keystore: try_begin without after_device_sign"
      ;;
    handleRespond_after_device_sign)
      echo "Auth URL/network: signed but HTTP respond not verified"
      ;;
    handleRespond_http_verified)
      echo "HTTP verified but Maestro still failed — navigation/selector after respond"
      ;;
    handleRespond_catch)
      echo "JS catch in handleRespond — read logcat-filtered.txt for message"
      ;;
    *)
      echo "last trace step ${last_step}; see maestro README investigation ladder"
      ;;
  esac
}

write_rca() {
  local iteration_dir="$1"
  local outcome="$2"
  local maestro_exit="$3"
  local auth_attempt_id="$4"
  local enrollment_id="$5"
  local scenario="$6"
  local api_status="$7"
  local last_step="$8"
  local maestro_err="$9"

  local hint
  hint="$(hypothesis_hint "$last_step" "$maestro_exit" "$scenario" "$outcome" "$maestro_err")"

  cat >"${iteration_dir}/rca.md" <<EOF
# RCA digest (read this first)

- outcome: ${outcome}
- maestro_exit_code: ${maestro_exit}
- auth_attempt_id: ${auth_attempt_id}
- enrollment_id: ${enrollment_id}
- scenario: ${scenario}
- api_status_after: ${api_status}
- last_pending_auth_trace_step: ${last_step}
- maestro_last_error: ${maestro_err}
- hypothesis_hint: ${hint}
- logcat_filtered: logcat-filtered.txt
- maestro_log: maestro.log

Agent read-order: SESSION.md / SESSION-table.md → this file → logcat-filtered.txt → maestro.log / logcat.txt only if still inconclusive.
EOF
}

write_session_table() {
  local session_root="$1"
  local jsonl="${session_root}/summary.jsonl"
  local table="${session_root}/SESSION-table.md"
  {
    echo "# Session table"
    echo
    echo "Read failed rows' \`iterations/<n>/rca.md\` before opening full Maestro logs."
    echo
    echo "| iteration | scenario | outcome | auth_attempt_id | maestro_exit | api_status |"
    echo "|---|---|---|---|---|---|"
  } >"$table"
  if [[ ! -f "$jsonl" ]]; then
    return 0
  fi
  if command -v python >/dev/null 2>&1 || command -v python3 >/dev/null 2>&1; then
    local py=python
    command -v python >/dev/null 2>&1 || py=python3
    "$py" - "$jsonl" >>"$table" <<'PY'
import json, sys
path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    for line in f:
        line = line.strip()
        if not line:
            continue
        d = json.loads(line)
        print(
            "| {iteration} | {scenario} | {outcome} | {authAttemptId} | {maestroExitCode} | {apiStatus} |".format(
                iteration=d.get("iteration", ""),
                scenario=d.get("scenario", ""),
                outcome=d.get("outcome", ""),
                authAttemptId=d.get("authAttemptId", ""),
                maestroExitCode=d.get("maestroExitCode", ""),
                apiStatus=d.get("apiStatusAfter", ""),
            )
        )
PY
    return 0
  fi
  while IFS= read -r line || [[ -n "$line" ]]; do
    echo "| (install python to pretty-print) | | | | | |" >>"$table"
    break
  done <"$jsonl"
}
