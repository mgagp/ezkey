# quality-gate HA helpers — sourced by quality-gate.sh and quality-gate-ha.test.sh.
# Requires Bash >= 4 (associative arrays / declare -A). On macOS: brew install bash,
# then launch via #!/usr/bin/env bash or `bash` from PATH — not /bin/bash (system 3.2).
# Cloud agent tooling (Linux, Git Bash, Homebrew bash on macOS).
#
# Expected globals: KEYWORD HA_REPLICAS PREV_RESTARTS HA_INVALID OVERALL_AMBER
#   OVERALL_RED RUN_DIR REPLICAS_TSV RESTARTS_TSV SHEDLOCK_* GATE_STARTED_AT
#   QUALITY_GATE_RESTORE_TIMEOUT_S

# Bash >= 4 guard — must parse and run under Bash 3.2 (macOS /bin/bash).
# Override QUALITY_GATE_BASH_MAJOR for tests only (simulate an old major).
# Exit 2 matches quality-gate usage / bad-args convention.
quality_gate_require_bash4() {
  # No declare -A, no [[ ]], no other Bash 4+ syntax in this function.
  local maj
  maj="${QUALITY_GATE_BASH_MAJOR:-${BASH_VERSINFO[0]}}"
  if [ -z "${maj}" ] || [ "${maj}" -lt 4 ]; then
    echo "quality-gate: Bash 4 or newer is required (found ${maj:-unknown})." >&2
    echo "On macOS: brew install bash, then launch via #!/usr/bin/env bash or" >&2
    echo "\`bash\` from PATH — not /bin/bash (system Bash is 3.2)." >&2
    return 2
  fi
  return 0
}

quality_gate_require_bash4 || exit $?

QUALITY_GATE_RESTORE_TIMEOUT_S="${QUALITY_GATE_RESTORE_TIMEOUT_S:-180}"
QUALITY_GATE_RESTORE_POLL_S="${QUALITY_GATE_RESTORE_POLL_S:-5}"
# Docker Desktop macOS: Linux VM clock can lag the host by a few seconds.
# Back off docker logs --since so early container lines are not excluded.
QUALITY_GATE_SINCE_BACKOFF_S="${QUALITY_GATE_SINCE_BACKOFF_S:-5}"

# Epoch milliseconds for phase timing.
# GNU date supports %3N; BSD date on macOS does not — it often prints a literal "N"
# (or "3N") and still exits 0, so a bare `date +%s%3N || fallback` never triggers.
# Validate digits before trusting date; then python3 / node / whole seconds.
now_ms() {
  local t
  t="$(date +%s%3N 2>/dev/null || true)"
  if [[ "$t" =~ ^[0-9]+$ ]]; then
    printf '%s\n' "$t"
    return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    python3 -c 'import time; print(int(time.time() * 1000))'
    return 0
  fi
  if command -v node >/dev/null 2>&1; then
    node -e 'console.log(Date.now())'
    return 0
  fi
  # Last resort: whole seconds as ms (duration math still works; lower precision).
  echo "[quality-gate] now_ms: falling back to whole-second precision (no GNU %3N / python3 / node)" >&2
  printf '%s\n' "$(($(date +%s) * 1000))"
}

# Back off an ISO-8601 UTC timestamp by QUALITY_GATE_SINCE_BACKOFF_S (default 5).
# Prints adjusted ISO Zulu. On failure, warns and echoes the input unchanged
# (never empty — empty would make detect_encryption_key_pkey_race drop --since
# and silently scan all logs; Patrick N1).
quality_gate_since_backoff() {
  local iso="$1"
  local secs="${QUALITY_GATE_SINCE_BACKOFF_S:-5}"
  local out=""
  if [[ -z "$iso" ]]; then
    printf '%s\n' "$iso"
    return 0
  fi
  if [[ ! "$secs" =~ ^[0-9]+$ ]]; then
    echo "[quality-gate] warning: QUALITY_GATE_SINCE_BACKOFF_S not a non-negative integer (${secs}); using raw timestamp" >&2
    printf '%s\n' "$iso"
    return 0
  fi
  if [[ "$secs" -eq 0 ]]; then
    printf '%s\n' "$iso"
    return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    if out="$(python3 -c '
import sys
from datetime import datetime, timedelta, timezone
raw = sys.argv[1].rstrip("Z")
secs = int(sys.argv[2])
dt = datetime.fromisoformat(raw)
if dt.tzinfo is None:
    dt = dt.replace(tzinfo=timezone.utc)
print((dt - timedelta(seconds=secs)).strftime("%Y-%m-%dT%H:%M:%SZ"))
' "$iso" "$secs" 2>/dev/null)" && [[ -n "$out" ]]; then
      printf '%s\n' "$out"
      return 0
    fi
    echo "[quality-gate] warning: cannot back off --since by ${secs}s (python3 failed); using raw timestamp" >&2
    printf '%s\n' "$iso"
    return 0
  fi
  echo "[quality-gate] warning: cannot back off --since by ${secs}s (no python3); using raw timestamp" >&2
  printf '%s\n' "$iso"
}

# Match needle in docker logs without grep -q.
# Under set -o pipefail, grep -q exits at first match → writer gets SIGPIPE (141) →
# the pipeline is false even when the signature was found (Patrick B1 / #747).
# grep -F without -q drains the pipe; >/dev/null keeps logs quiet.
_qg_docker_logs_match() {
  local needle="$1"
  shift
  docker logs "$@" 2>&1 | grep -F "$needle" >/dev/null
}

# Detect #747 encryption keyset PK race in admin-api replica logs.
# Scans each replica with docker logs --since (no tail cap — verbose Spring Boot
# startup can push the signature past a short window; Carl 2026-10-19 macOS).
# $1 = ISO-8601 since timestamp (GATE_STARTED_AT or clean-start phase start);
#     empty/omit = full container logs (no --since).
# Returns 0 if ezkey_encryption_key_pkey appears, 1 otherwise.
detect_encryption_key_pkey_race() {
  local since="${1:-}"
  local since_adj name
  if [[ -n "$since" ]]; then
    since_adj="$(quality_gate_since_backoff "$since")"
  else
    since_adj=""
  fi
  for name in ezkey-admin-api-1 ezkey-admin-api-2; do
    if [[ -n "$since_adj" ]]; then
      if _qg_docker_logs_match 'ezkey_encryption_key_pkey' --since "$since_adj" "$name"; then
        return 0
      fi
    else
      if _qg_docker_logs_match 'ezkey_encryption_key_pkey' "$name"; then
        return 0
      fi
    fi
  done
  return 1
}

# Patrick: kill-missing may downgrade GREEN→AMBER only — never rewrite elective RED.
# Prints the phase verdict to apply (AMBER or the unchanged last verdict).
elective_kill_missing_phase_verdict() {
  local last_verdict="$1"
  if [[ "$last_verdict" == "GREEN" ]]; then
    echo "AMBER"
  else
    echo "$last_verdict"
  fi
}

# OOM evidence across the whole gate window (M1).
# Captured events → RED even when docker events exits 141 (SIGPIPE from head).
# Failing docker events with no output → UNKNOWN/AMBER, never silent "no OOM" (M2).
# PIPESTATUS must be read inside the subshell: after ev="$(…)", PIPESTATUS[0] is head.
check_oom_evidence() {
  local blob ev ev_ec=0 name
  set +o pipefail
  blob="$(
    docker events --since "${GATE_STARTED_AT:-}" --until "$(date -u +"%Y-%m-%dT%H:%M:%SZ")" \
      --filter event=oom --filter type=container 2>/dev/null | head -20
    printf 'rc=%s\n' "${PIPESTATUS[0]}"
  )"
  set -o pipefail
  ev_ec="$(printf '%s\n' "$blob" | sed -n 's/^rc=//p' | tail -1)"
  ev_ec="${ev_ec:-0}"
  ev="$(printf '%s\n' "$blob" | sed '/^rc=/d')"
  if [[ -n "$ev" ]]; then
    echo "[$KEYWORD] docker OOM events detected:"
    echo "$ev"
    OVERALL_RED=1
    printf '%s\n' "OOM: docker events reported container OOM since gate start" >>"$RUN_DIR/findings-extra.txt"
    return 0
  fi
  if [[ "$ev_ec" -ne 0 ]]; then
    echo "[$KEYWORD] docker events failed (ec=$ev_ec) — OOM check UNKNOWN/AMBER"
    OVERALL_AMBER=1
    printf '%s\n' "OOM check UNKNOWN: docker events failed (ec=$ev_ec)" >>"$RUN_DIR/findings-extra.txt"
    return 0
  fi
  for name in $HA_REPLICAS; do
    # Same pipefail/SIGPIPE rule as #747 detect: no grep -q (Patrick B1).
    if _qg_docker_logs_match 'Terminating due to java.lang.OutOfMemoryError' \
      --since "${GATE_STARTED_AT:-}" "$name"; then
      echo "[$KEYWORD] OOM log line in $name"
      OVERALL_RED=1
      printf '%s\n' "OOM: Terminating due to java.lang.OutOfMemoryError in $name" >>"$RUN_DIR/findings-extra.txt"
    fi
  done
  return 0
}

# Inspect API JVM replicas. tolerating="$name" may be down (ShedLock kill window only).
# RestartCount delta outside that window → HA_INVALID (C1-bis).
check_replicas() {
  local phase="$1"
  local tolerating="${2:-}"
  local bad=0 name status health restarts prev delta
  for name in $HA_REPLICAS; do
    status="$(docker inspect -f '{{.State.Status}}' "$name" 2>/dev/null || echo missing)"
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$name" 2>/dev/null || echo missing)"
    restarts="$(docker inspect -f '{{.RestartCount}}' "$name" 2>/dev/null || echo 0)"
    if [[ -z "${PREV_RESTARTS[$name]+x}" ]]; then
      prev="$restarts"
      delta=0
    else
      prev="${PREV_RESTARTS[$name]}"
      delta=$((restarts - prev))
      [[ "$delta" -lt 0 ]] && delta=0
    fi
    printf '%s\t%s\t%s\t%s\t%s\n' "$phase" "$name" "$status" "$health" "$restarts" >>"$REPLICAS_TSV"
    printf '%s\t%s\t%s\t%s\n' "$phase" "$name" "$restarts" "$delta" >>"$RESTARTS_TSV"
    PREV_RESTARTS[$name]="$restarts"
    if [[ -n "$tolerating" && "$name" == "$tolerating" ]]; then
      continue
    fi
    if [[ "$status" != "running" || ( "$health" != "healthy" && "$health" != "none" ) ]]; then
      bad=1
      echo "[$KEYWORD] replica issue phase=$phase $name status=$status health=$health restarts=$restarts"
    fi
    # C1-bis: only the kill/restore window passes tolerating=; later phases must see restarts.
    if [[ "$delta" -gt 0 && "$name" != "${tolerating:-}" ]]; then
      bad=1
      echo "[$KEYWORD] restart delta phase=$phase $name delta=$delta → HA invalid"
    fi
  done
  if [[ "$bad" -eq 1 ]]; then
    HA_INVALID=1
    OVERALL_AMBER=1
    return 1
  fi
  return 0
}

wait_replicas_healthy() {
  local timeout_s="${1:-$QUALITY_GATE_RESTORE_TIMEOUT_S}"
  local poll_s="${QUALITY_GATE_RESTORE_POLL_S:-5}"
  local elapsed=0 name status health
  while [[ "$elapsed" -lt "$timeout_s" ]]; do
    local bad=0
    for name in $HA_REPLICAS; do
      status="$(docker inspect -f '{{.State.Status}}' "$name" 2>/dev/null || echo missing)"
      health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$name" 2>/dev/null || echo missing)"
      if [[ "$status" != "running" || ( "$health" != "healthy" && "$health" != "none" ) ]]; then
        bad=1
        break
      fi
    done
    [[ "$bad" -eq 0 ]] && return 0
    sleep "$poll_s"
    elapsed=$((elapsed + poll_s))
  done
  return 1
}

# Patrick C1: do NOT compose up -d (loses docker-test profile / logs). docker start only.
# Quality-gate HA elective always expects ShedLockDistributedTest to kill one admin-api replica.
# Kill proof is State.Status=exited or a docker die event — log grep alone is never enough.
# Missing kill → HA_INVALID + AMBER → overall INCONCLUSIVE (exit 4), never GO.
#
# Prints proven kill as name|proofPath|iso on stdout when found; empty otherwise.
# Prefers inspect Status=exited; falls back to docker events die since ELECTIVE_STARTED_AT
# (not GATE_STARTED_AT — preflight stop_stacks emits die for admin-api replicas).
# Die fallback requires: elective window + name matching Crashing container log + exit 137.
# proofPath is State.Status=exited or docker-die. Caller parses (command substitution
# would drop globals).
#
# $1 = expected replica from elective "Crashing container:" log (may be empty).
prove_shedlock_kill() {
  local expected="${1:-}"
  local name status events_out line since until_iso ename ecode
  for name in ezkey-admin-api-1 ezkey-admin-api-2; do
    status="$(docker inspect -f '{{.State.Status}}' "$name" 2>/dev/null || echo missing)"
    if [[ "$status" == "exited" ]]; then
      if [[ -n "$expected" && "$name" != "$expected" ]]; then
        continue
      fi
      echo "${name}|State.Status=exited|$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
      return 0
    fi
  done
  # die only inside the elective window; must match Crashing container + SIGKILL (137).
  since="${ELECTIVE_STARTED_AT:-}"
  if [[ -z "$since" || -z "$expected" ]]; then
    return 1
  fi
  until_iso="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  events_out="$(
    docker events --since "$since" --until "$until_iso" \
      --filter 'type=container' --filter 'event=die' \
      --format '{{.Actor.Attributes.name}}|{{.Actor.Attributes.exitCode}}' 2>/dev/null || true
  )"
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    ename="${line%%|*}"
    ecode="${line#*|}"
    [[ "$ecode" == "$line" ]] && ecode=""
    if [[ "$ename" == "$expected" && "$ecode" == "137" ]]; then
      echo "${ename}|docker-die|${until_iso}"
      return 0
    fi
  done <<<"$events_out"
  return 1
}

restore_after_elective() {
  local elective_log="$RUN_DIR/phases/elective-tests.log"
  local killed="" log_hint="" proof_line=""
  SHEDLOCK_KILL_EXPECTED=0
  SHEDLOCK_KILL_MISSING=0
  SHEDLOCK_RESTORE_OK=0
  SHEDLOCK_KILLED=""
  SHEDLOCK_PROOF_PATH=""
  SHEDLOCK_PROOF_AT=""
  if [[ -f "$elective_log" ]]; then
    log_hint="$(grep -E 'Crashing container: ezkey-admin-api-[12]' "$elective_log" 2>/dev/null \
      | tail -1 | sed -E 's/.*Crashing container: (ezkey-admin-api-[12]).*/\1/' || true)"
  fi
  proof_line="$(prove_shedlock_kill "$log_hint" || true)"
  if [[ -n "$proof_line" ]]; then
    IFS='|' read -r killed SHEDLOCK_PROOF_PATH SHEDLOCK_PROOF_AT <<<"$proof_line"
  fi
  if [[ -n "$killed" ]]; then
    SHEDLOCK_KILL_EXPECTED=1
    SHEDLOCK_KILLED="$killed"
    echo "[$KEYWORD] expected: ShedLockDistributedTest killed $killed (${SHEDLOCK_PROOF_PATH}) — docker start restore"
    if [[ -n "$log_hint" && "$log_hint" != "$killed" ]]; then
      echo "[$KEYWORD] note: elective log hinted $log_hint; proof ${SHEDLOCK_PROOF_PATH} → $killed"
    fi
    printf '%s\n' \
      "test-hygiene: ShedLockDistributedTest docker-kills ${killed} and does not restore it; gate docker-starts after elective (proof=${SHEDLOCK_PROOF_PATH} at ${SHEDLOCK_PROOF_AT}; electiveSince=${ELECTIVE_STARTED_AT:-}; logHint=${log_hint:-})" \
      >>"$RUN_DIR/findings-extra.txt"
  fi

  if ! check_replicas "elective-pre-restore" "$killed"; then
    echo "[$KEYWORD] unexpected replica down (beyond tolerated ShedLock kill) → HA invalid"
    return 1
  fi

  if [[ -z "$killed" ]]; then
    SHEDLOCK_KILL_MISSING=1
    HA_INVALID=1
    OVERALL_AMBER=1
    echo "[$KEYWORD] expected ShedLock kill missing — no Status=exited / die event → HA invalid / INCONCLUSIVE"
    if [[ -n "$log_hint" ]]; then
      printf '%s\n' \
        "ShedLock: elective log mentioned ${log_hint} but Status/die proof missing — kill/restore path not proven" \
        >>"$RUN_DIR/findings-extra.txt"
    else
      printf '%s\n' \
        "ShedLock: expected kill missing (failover skipped or unresolved container id) — kill/restore path not proven" \
        >>"$RUN_DIR/findings-extra.txt"
    fi
    return 1
  fi

  if ! docker start "$killed" >/dev/null 2>&1; then
    echo "[$KEYWORD] docker start $killed failed"
    HA_INVALID=1
    OVERALL_AMBER=1
    return 1
  fi
  if ! wait_replicas_healthy "$QUALITY_GATE_RESTORE_TIMEOUT_S"; then
    echo "[$KEYWORD] restore: replicas not healthy within ${QUALITY_GATE_RESTORE_TIMEOUT_S}s → HA invalid"
    HA_INVALID=1
    OVERALL_AMBER=1
    check_replicas "elective-restore" || true
    return 1
  fi
  # Baseline restarts after intentional kill/start so delta does not trip later phases.
  PREV_RESTARTS[$killed]="$(docker inspect -f '{{.RestartCount}}' "$killed" 2>/dev/null || echo 0)"
  SHEDLOCK_RESTORE_OK=1
  check_replicas "elective-restore" || true
  echo "[$KEYWORD] restore OK after expected ShedLock kill ($killed)"
  return 0
}

# Patrick C2: clean-start exit without #747 retry must stay RED even if LB is up.
# Prints GREEN|AMBER|RED.
classify_clean_start_verdict() {
  local code="$1" compose_retry="$2" stack_ok="$3" replicas_ok="$4"
  if [[ "$stack_ok" != "1" || "$replicas_ok" != "1" ]]; then
    echo RED
  elif [[ "$compose_retry" -eq 1 ]]; then
    echo AMBER
  elif [[ "$code" -ne 0 ]]; then
    echo RED
  else
    echo GREEN
  fi
}

# Rewrite last phases.tsv row: optional new verdict + note.
# Uses POSIX `sed '$d'` — BSD head on macOS rejects `head -n -1` (#915 defect 2).
# Expected global: PHASE_TSV.
rewrite_last_phase() {
  local new_verdict="${1:-}" note="${2:-}"
  local tmp last name ver code dur log counts
  tmp="$(mktemp)"
  sed '$d' "$PHASE_TSV" >"$tmp"
  last="$(tail -1 "$PHASE_TSV")"
  name="$(echo "$last" | cut -f1)"
  ver="$(echo "$last" | cut -f2)"
  code="$(echo "$last" | cut -f3)"
  dur="$(echo "$last" | cut -f4)"
  log="$(echo "$last" | cut -f5)"
  counts="$(echo "$last" | cut -f6)"
  [[ -n "$new_verdict" ]] && ver="$new_verdict"
  [[ -z "$note" ]] && note="$(echo "$last" | cut -f7)"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$name" "$ver" "$code" "$dur" "$log" "$counts" "$note" >>"$tmp"
  mv "$tmp" "$PHASE_TSV"
}

# Sum Surefire module totals from a build log (display-only; verdict uses exit code).
# Exclude per-class lines (`… Time elapsed … in <Class>`) so class + module lines
# are not double-counted (#915 defect 5).
parse_surefire_counts() {
  local log="$1" run=0 failed=0 errors=0 skipped=0 line sum_run=0 sum_f=0 sum_e=0 sum_s=0 found=0
  while IFS= read -r line; do
    [[ "$line" == *"Time elapsed"* ]] && continue
    if [[ "$line" =~ Tests\ run:\ *([0-9]+),\ *Failures:\ *([0-9]+),\ *Errors:\ *([0-9]+),\ *Skipped:\ *([0-9]+) ]]; then
      sum_run=$((sum_run + BASH_REMATCH[1])); sum_f=$((sum_f + BASH_REMATCH[2]))
      sum_e=$((sum_e + BASH_REMATCH[3])); sum_s=$((sum_s + BASH_REMATCH[4])); found=1
      run="${BASH_REMATCH[1]}"; failed="${BASH_REMATCH[2]}"; errors="${BASH_REMATCH[3]}"; skipped="${BASH_REMATCH[4]}"
    fi
  done < <(grep -E 'Tests run:' "$log" 2>/dev/null || true)
  if [[ $found -eq 1 && $sum_run -ge $run ]]; then echo "$sum_run $sum_f $sum_e $sum_s"; else echo "$run $failed $errors $skipped"; fi
}

# Resolve GNU `timeout` or Homebrew `gtimeout` (stock macOS has neither).
# Prints absolute path on success; returns 1 when neither is on PATH (#915).
quality_gate_resolve_timeout_cmd() {
  local cmd
  if cmd="$(command -v timeout 2>/dev/null)" && [[ -n "$cmd" ]]; then
    printf '%s\n' "$cmd"
    return 0
  fi
  if cmd="$(command -v gtimeout 2>/dev/null)" && [[ -n "$cmd" ]]; then
    printf '%s\n' "$cmd"
    return 0
  fi
  return 1
}

# Run argv under a wall-clock bound when timeout/gtimeout exists; else warn on
# stderr and run unbound (never fail silently; never hang forever on purpose).
quality_gate_run_bounded() {
  local secs="$1"
  shift
  local cmd
  if cmd="$(quality_gate_resolve_timeout_cmd)"; then
    "$cmd" "$secs" "$@"
  else
    echo "[quality-gate] warning: neither timeout nor gtimeout on PATH; running unbound" >&2
    "$@"
  fi
}
