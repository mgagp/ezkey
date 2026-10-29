#!/usr/bin/env bash
# Fake-docker HA helper tests (~Patrick C1-bis / restore / C2).
# Requires Bash >= 4 (associative arrays / declare -A). On macOS: brew install bash,
# then launch via #!/usr/bin/env bash or `bash` from PATH — not /bin/bash (system 3.2).
# Run: ./scripts/quality-gate-ha.test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# Early Bash >= 4 guard (must run before declare -A; parses under Bash 3.2).
# Same check lives in scripts/lib/quality-gate-ha.sh (auto-runs when sourced alone).
# QUALITY_GATE_BASH_MAJOR overrides the detected major for tests only.
_qg_bash_major="${QUALITY_GATE_BASH_MAJOR:-${BASH_VERSINFO[0]}}"
if [ -z "${_qg_bash_major}" ] || [ "${_qg_bash_major}" -lt 4 ]; then
  echo "quality-gate: Bash 4 or newer is required (found ${_qg_bash_major:-unknown})." >&2
  echo "On macOS: brew install bash, then launch via #!/usr/bin/env bash or" >&2
  echo "\`bash\` from PATH — not /bin/bash (system Bash is 3.2)." >&2
  exit 2
fi
unset _qg_bash_major

# --- fake docker ---
mkdir -p "$TMP/bin" "$TMP/state"
cat >"$TMP/bin/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
STATE="${FAKE_DOCKER_STATE:?}"
cmd="${1:-}"; shift || true
case "$cmd" in
  inspect)
    # docker inspect -f '{{.State.Status}}' NAME
    fmt=""; name=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        -f) fmt="$2"; shift 2 ;;
        *) name="$1"; shift ;;
      esac
    done
    status="$(cat "$STATE/$name.status" 2>/dev/null || echo missing)"
    health="$(cat "$STATE/$name.health" 2>/dev/null || echo none)"
    restarts="$(cat "$STATE/$name.restarts" 2>/dev/null || echo 0)"
    case "$fmt" in
      *RestartCount*) echo "$restarts" ;;
      *Health*) echo "$health" ;;
      *State.Status*|*.Status*) echo "$status" ;;
      *) echo "?" ;;
    esac
    ;;
  start)
    name="$1"
    echo "${FAKE_DOCKER_START_STATUS:-running}" >"$STATE/$name.status"
    echo "${FAKE_DOCKER_START_HEALTH:-healthy}" >"$STATE/$name.health"
    r="$(cat "$STATE/$name.restarts" 2>/dev/null || echo 0)"
    echo $((r + 1)) >"$STATE/$name.restarts"
    ;;
  events)
    # M2: simulate docker events failure (never silent "no OOM")
    if [[ "${FAKE_DOCKER_EVENTS_FAIL:-0}" == "1" ]]; then
      echo "fake-docker: events failed" >&2
      exit 1
    fi
    since=""; until=""; format=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --since) since="$2"; shift 2 ;;
        --until) until="$2"; shift 2 ;;
        --format) format="$2"; shift 2 ;;
        --filter) shift 2 ;;
        *) shift ;;
      esac
    done
    if [[ -n "${FAKE_DOCKER_EVENTS_LINES:-}" ]]; then
      # Lines: "name", "name|exitCode", "ISOZ name", or "ISOZ name|exitCode".
      while IFS= read -r eline; do
        [[ -z "$eline" ]] && continue
        ets=""; rest="$eline"
        if [[ "$eline" =~ ^([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:]+Z)[[:space:]]+(.+)$ ]]; then
          ets="${BASH_REMATCH[1]}"
          rest="${BASH_REMATCH[2]}"
          if [[ -n "$since" && "$ets" < "$since" ]]; then continue; fi
          if [[ -n "$until" && "$ets" > "$until" ]]; then continue; fi
        fi
        if [[ "$format" == *exitCode* ]]; then
          # Ensure name|exitCode for prove_shedlock_kill parser.
          if [[ "$rest" == *'|'* ]]; then
            echo "$rest"
          else
            echo "${rest}|"
          fi
        else
          echo "${rest%%|*}"
        fi
      done <<< "$FAKE_DOCKER_EVENTS_LINES"
    fi
    exit 0
    ;;
  logs)
    # docker logs [--since ISO] NAME — dump STATE/NAME.logs when present.
    name=""
    while [[ $# -gt 0 ]]; do
      case "$1" in
        --since) shift 2 ;;
        *) name="$1"; shift ;;
      esac
    done
    if [[ -n "$name" && -f "$STATE/$name.logs" ]]; then
      cat "$STATE/$name.logs"
    fi
    exit 0
    ;;
  *) echo "fake-docker: unsupported $cmd" >&2; exit 1 ;;
esac
EOF
chmod +x "$TMP/bin/docker"
export PATH="$TMP/bin:$PATH"
export FAKE_DOCKER_STATE="$TMP/state"

# --- harness globals ---
KEYWORD="quality-gate-test"
HA_REPLICAS="ezkey-admin-api-1 ezkey-admin-api-2"
declare -A PREV_RESTARTS=()
HA_INVALID=0
OVERALL_AMBER=0
OVERALL_RED=0
SHEDLOCK_KILL_EXPECTED=0
SHEDLOCK_KILL_MISSING=0
SHEDLOCK_RESTORE_OK=0
SHEDLOCK_KILLED=""
SHEDLOCK_PROOF_PATH=""
SHEDLOCK_PROOF_AT=""
GATE_STARTED_AT="2026-10-11T00:00:00Z"
ELECTIVE_STARTED_AT="2026-10-11T01:00:00Z"
RUN_DIR="$TMP/run"
mkdir -p "$RUN_DIR/phases"
: >"$RUN_DIR/findings-extra.txt"
REPLICAS_TSV="$RUN_DIR/replicas.tsv"
RESTARTS_TSV="$RUN_DIR/restarts.tsv"
: >"$REPLICAS_TSV"
: >"$RESTARTS_TSV"
export QUALITY_GATE_RESTORE_TIMEOUT_S=2
export QUALITY_GATE_RESTORE_POLL_S=1

# shellcheck source=lib/quality-gate-ha.sh
source "$ROOT/scripts/lib/quality-gate-ha.sh"

set_replica() {
  local n="$1" st="$2" h="$3" r="${4:-0}"
  echo "$st" >"$FAKE_DOCKER_STATE/$n.status"
  echo "$h" >"$FAKE_DOCKER_STATE/$n.health"
  echo "$r" >"$FAKE_DOCKER_STATE/$n.restarts"
}
all_healthy() {
  set_replica ezkey-admin-api-1 running healthy 0
  set_replica ezkey-admin-api-2 running healthy 0
  PREV_RESTARTS=([ezkey-admin-api-1]=0 [ezkey-admin-api-2]=0)
  HA_INVALID=0; OVERALL_AMBER=0; OVERALL_RED=0
  SHEDLOCK_KILL_MISSING=0; SHEDLOCK_KILL_EXPECTED=0; SHEDLOCK_RESTORE_OK=0; SHEDLOCK_KILLED=""
  SHEDLOCK_PROOF_PATH=""; SHEDLOCK_PROOF_AT=""
}
pass=0; fail=0
assert_eq() {
  local got="$1" want="$2" msg="$3"
  if [[ "$got" == "$want" ]]; then echo "ok - $msg"; pass=$((pass+1))
  else echo "not ok - $msg (got=$got want=$want)"; fail=$((fail+1)); fi
}

# 0) Bash >= 4 guard — simulate old major via QUALITY_GATE_BASH_MAJOR (no bash 3.2 install)
set +e
QUALITY_GATE_BASH_MAJOR=3 "$ROOT/scripts/quality-gate.sh" --help \
  >"$TMP/bash-guard-qg.log" 2>&1
guard_qg_ec=$?
QUALITY_GATE_BASH_MAJOR=3 bash -c 'source "$1"' _ "$ROOT/scripts/lib/quality-gate-ha.sh" \
  >"$TMP/bash-guard-lib.log" 2>&1
guard_lib_ec=$?
QUALITY_GATE_BASH_MAJOR=3 bash "$ROOT/scripts/quality-gate-ha.test.sh" \
  >"$TMP/bash-guard-ha.log" 2>&1
guard_ha_ec=$?
QUALITY_GATE_BASH_MAJOR=4 "$ROOT/scripts/quality-gate.sh" --help \
  >"$TMP/bash-guard-ok.log" 2>&1
guard_ok_ec=$?
set -e
assert_eq "$guard_qg_ec" "2" "bash major 3 → quality-gate.sh exit 2"
assert_eq "$guard_lib_ec" "2" "bash major 3 → lib sourced alone exit 2"
assert_eq "$guard_ha_ec" "2" "bash major 3 → quality-gate-ha.test.sh exit 2"
assert_eq "$guard_ok_ec" "0" "bash major 4 override → quality-gate.sh --help ok"
grep -q 'brew install bash' "$TMP/bash-guard-qg.log" \
  && echo "ok - quality-gate.sh guard mentions brew install bash" && pass=$((pass+1)) \
  || { echo "not ok - quality-gate.sh guard mentions brew install bash"; fail=$((fail+1)); }
grep -q 'not /bin/bash' "$TMP/bash-guard-qg.log" \
  && echo "ok - quality-gate.sh guard warns against /bin/bash" && pass=$((pass+1)) \
  || { echo "not ok - quality-gate.sh guard warns against /bin/bash"; fail=$((fail+1)); }
grep -q 'brew install bash' "$TMP/bash-guard-lib.log" \
  && echo "ok - lib guard mentions brew install bash" && pass=$((pass+1)) \
  || { echo "not ok - lib guard mentions brew install bash"; fail=$((fail+1)); }
grep -q 'brew install bash' "$TMP/bash-guard-ha.log" \
  && echo "ok - HA test guard mentions brew install bash" && pass=$((pass+1)) \
  || { echo "not ok - HA test guard mentions brew install bash"; fail=$((fail+1)); }

# 1) restore OK
all_healthy
set_replica ezkey-admin-api-2 exited none 1
echo 'Crashing container: ezkey-admin-api-2' >"$RUN_DIR/phases/elective-tests.log"
restore_after_elective
assert_eq "$?" "0" "restore OK"
assert_eq "$SHEDLOCK_RESTORE_OK" "1" "restore OK flag"
assert_eq "$HA_INVALID" "0" "restore OK not HA invalid"
assert_eq "$SHEDLOCK_PROOF_PATH" "State.Status=exited" "restore OK → proof path Status"

# 2) timeout expired → HA invalid (+ elective AMBER)
all_healthy
set_replica ezkey-admin-api-2 exited none 1
echo 'Crashing container: ezkey-admin-api-2' >"$RUN_DIR/phases/elective-tests.log"
FAKE_DOCKER_START_HEALTH=starting QUALITY_GATE_RESTORE_TIMEOUT_S=1 \
  restore_after_elective || true
assert_eq "$HA_INVALID" "1" "timeout → HA invalid"
assert_eq "$OVERALL_AMBER" "1" "timeout → elective AMBER signal"
unset FAKE_DOCKER_START_HEALTH

# 3) second replica down (beyond tolerated kill)
all_healthy
set_replica ezkey-admin-api-1 exited none 0
set_replica ezkey-admin-api-2 exited none 1
echo 'Crashing container: ezkey-admin-api-2' >"$RUN_DIR/phases/elective-tests.log"
restore_after_elective || true
assert_eq "$HA_INVALID" "1" "second replica down → HA invalid"

# 4) C1-bis: restored replica restarts again in a later phase (not tolerated)
all_healthy
set_replica ezkey-admin-api-2 exited none 1
echo 'Crashing container: ezkey-admin-api-2' >"$RUN_DIR/phases/elective-tests.log"
restore_after_elective
# After restore, RestartCount was baselined; bump it → later phase must HA-invalid.
r="$(cat "$FAKE_DOCKER_STATE/ezkey-admin-api-2.restarts")"
echo $((r + 1)) >"$FAKE_DOCKER_STATE/ezkey-admin-api-2.restarts"
HA_INVALID=0
check_replicas "playwright" || true
assert_eq "$HA_INVALID" "1" "C1-bis: post-restore restart delta → HA invalid"

# 5) C2: clean-start failed without compose retry stays RED even if stack/replicas OK
v="$(classify_clean_start_verdict 1 0 1 1)"
assert_eq "$v" "RED" "C2: exit≠0 without #747 retry → RED"
v="$(classify_clean_start_verdict 1 1 1 1)"
assert_eq "$v" "AMBER" "C2 contrast: #747 retry → AMBER"
v="$(classify_clean_start_verdict 0 0 1 1)"
assert_eq "$v" "GREEN" "C2 contrast: clean exit → GREEN"

# 5b) #747 detect: signature buried past a 400-line window still matches (no tail cap)
all_healthy
: >"$FAKE_DOCKER_STATE/ezkey-admin-api-1.logs"
: >"$FAKE_DOCKER_STATE/ezkey-admin-api-2.logs"
i=0
while [[ $i -lt 500 ]]; do
  echo "filler line $i — Spring Boot noise" >>"$FAKE_DOCKER_STATE/ezkey-admin-api-1.logs"
  i=$((i + 1))
done
echo 'ERROR: duplicate key value violates unique constraint "ezkey_encryption_key_pkey"' \
  >>"$FAKE_DOCKER_STATE/ezkey-admin-api-1.logs"
detect_encryption_key_pkey_race "2026-10-19T00:00:00Z"
assert_eq "$?" "0" "5b: #747 signature past 400 lines → detected"

# 5c) #747 detect: no signature → not detected
all_healthy
echo "no keyset race here" >"$FAKE_DOCKER_STATE/ezkey-admin-api-1.logs"
echo "no keyset race here" >"$FAKE_DOCKER_STATE/ezkey-admin-api-2.logs"
set +e
detect_encryption_key_pkey_race "2026-10-19T00:00:00Z"
detect_rc=$?
set -e
assert_eq "$detect_rc" "1" "5c: no #747 signature → not detected"

# 5c-bis) #747 detect: no --since branch (full container logs)
all_healthy
echo 'ERROR: duplicate key value violates unique constraint "ezkey_encryption_key_pkey"' \
  >"$FAKE_DOCKER_STATE/ezkey-admin-api-2.logs"
: >"$FAKE_DOCKER_STATE/ezkey-admin-api-1.logs"
detect_encryption_key_pkey_race ""
assert_eq "$?" "0" "5c-bis: #747 detect without --since → detected"
set +e
detect_encryption_key_pkey_race
detect_rc=$?
set -e
assert_eq "$detect_rc" "0" "5c-bis: #747 detect with omitted since → detected"

# 5e) Patrick B1-bis: under pipefail, signature FIRST then a continuing writer.
# A single fat line (~70 KiB) is often one write() and does NOT SIGPIPE — so that
# proof passed on the old grep -Fq code too. Use many lines after the match
# (seq) so cat keeps writing after grep -q would have closed the pipe.
all_healthy
{
  echo 'ERROR: duplicate key value violates unique constraint "ezkey_encryption_key_pkey"'
  seq 1 2000000
} >"$FAKE_DOCKER_STATE/ezkey-admin-api-1.logs"
: >"$FAKE_DOCKER_STATE/ezkey-admin-api-2.logs"
set -o pipefail
set +e
detect_encryption_key_pkey_race "2026-10-19T00:00:00Z"
detect_rc=$?
set -e
assert_eq "$detect_rc" "0" "5e B1: early signature + seq noise under pipefail → detected"

# 5e-bis) since backoff moves timestamp earlier by QUALITY_GATE_SINCE_BACKOFF_S
backed="$(QUALITY_GATE_SINCE_BACKOFF_S=5 quality_gate_since_backoff "2026-10-19T12:00:10Z")"
assert_eq "$backed" "2026-10-19T12:00:05Z" "5e-bis: since backoff −5s"

# 5e-ter) N1: python3 failure → raw timestamp + stderr (never empty --since drop)
mkdir -p "$TMP/bin-bad-py"
printf '%s\n' '#!/bin/bash' 'echo boom >&2' 'exit 1' >"$TMP/bin-bad-py/python3"
chmod +x "$TMP/bin-bad-py/python3"
backoff_err="$TMP/backoff-pyfail.err"
backed_fail="$(
  PATH="$TMP/bin-bad-py:$PATH" QUALITY_GATE_SINCE_BACKOFF_S=5 \
    quality_gate_since_backoff "2026-10-19T12:00:10Z" 2>"$backoff_err"
)"
assert_eq "$backed_fail" "2026-10-19T12:00:10Z" "5e-ter N1: python3 fail → raw timestamp"
grep -Fq 'python3 failed' "$backoff_err" \
  && echo "ok - 5e-ter N1: python3 fail warns on stderr" && pass=$((pass+1)) \
  || { echo "not ok - 5e-ter N1: python3 fail warns on stderr"; fail=$((fail+1)); cat "$backoff_err" || true; }

# 5e-quater) N2: non-numeric backoff secs → raw timestamp (no bash -eq error)
backoff_err2="$TMP/backoff-badsecs.err"
backed_bad="$(
  QUALITY_GATE_SINCE_BACKOFF_S=abc quality_gate_since_backoff "2026-10-19T12:00:10Z" \
    2>"$backoff_err2"
)"
assert_eq "$backed_bad" "2026-10-19T12:00:10Z" "5e-quater N2: bad secs → raw timestamp"
grep -Fq 'not a non-negative integer' "$backoff_err2" \
  && echo "ok - 5e-quater N2: bad secs warns on stderr" && pass=$((pass+1)) \
  || { echo "not ok - 5e-quater N2: bad secs warns on stderr"; fail=$((fail+1)); cat "$backoff_err2" || true; }

# 5d) now_ms always returns digits (portable; BSD date must not poison arithmetic)
ms="$(now_ms)"
if [[ "$ms" =~ ^[0-9]+$ ]]; then
  echo "ok - now_ms returns digits only"; pass=$((pass+1))
else
  echo "not ok - now_ms returns digits only (got=$ms)"; fail=$((fail+1))
fi
# Simulate BSD date: %3N → literal "3N", exit 0 (macOS). Fallback must still be numeric.
mkdir -p "$TMP/bin-bsd-date"
# #!/bin/bash (not env): seconds-fallback test uses PATH=bin-bsd-date only.
cat >"$TMP/bin-bsd-date/date" <<'BSDDATE'
#!/bin/bash
for arg in "$@"; do
  # Match %N and %3N (BSD leaves the N-family conversion as a literal suffix).
  if [[ "$arg" == *%*N* ]]; then
    printf '%s3N\n' "$(/bin/date +%s 2>/dev/null || echo 1700000000)"
    exit 0
  fi
done
exec /bin/date "$@"
BSDDATE
chmod +x "$TMP/bin-bsd-date/date"
ms_bsd="$(PATH="$TMP/bin-bsd-date:$PATH" /bin/bash -c '
  source "'"$ROOT"'/scripts/lib/quality-gate-ha.sh"
  now_ms
')"
if [[ "$ms_bsd" =~ ^[0-9]+$ ]]; then
  echo "ok - now_ms BSD-date fallback returns digits"; pass=$((pass+1))
else
  echo "not ok - now_ms BSD-date fallback returns digits (got=$ms_bsd)"; fail=$((fail+1))
fi
# Arithmetic must succeed (the old %3N path produced "…3N" and broke duration_ms).
if d=$((ms_bsd + 1)) && [[ "$d" =~ ^[0-9]+$ ]]; then
  echo "ok - now_ms BSD fallback is arithmetic-safe"; pass=$((pass+1))
else
  echo "not ok - now_ms BSD fallback is arithmetic-safe"; fail=$((fail+1))
fi
# Seconds fallback must warn on stderr when python3/node are unavailable.
# PATH is only the BSD-date stub (it execs /bin/date for +%s). Do not append
# /bin — usr-merged distros expose /bin/python3 and would skip this path.
# Invoke /bin/bash by absolute path: PATH=… bash looks up bash on the new PATH.
ms_sec_err="$TMP/now_ms_seconds.err"
ms_sec="$(
  PATH="$TMP/bin-bsd-date" /bin/bash -c '
    source "'"$ROOT"'/scripts/lib/quality-gate-ha.sh"
    now_ms
  ' 2>"$ms_sec_err"
)"
if [[ "$ms_sec" =~ ^[0-9]+$ ]]; then
  echo "ok - now_ms seconds fallback returns digits"; pass=$((pass+1))
else
  echo "not ok - now_ms seconds fallback returns digits (got=$ms_sec)"; fail=$((fail+1))
fi
grep -Fq 'falling back to whole-second precision' "$ms_sec_err" \
  && echo "ok - now_ms seconds fallback warns on stderr" && pass=$((pass+1)) \
  || { echo "not ok - now_ms seconds fallback warns on stderr"; fail=$((fail+1)); cat "$ms_sec_err" || true; }

# 5f) rewrite_last_phase — portable sed '$d' (BSD head rejects head -n -1; #915 defect 2)
PHASE_TSV="$TMP/phases-rewrite.tsv"
printf '%s\n' \
  $'preflight\tGREEN\t0\t10\tpreflight.log\t-\tok' \
  $'unit-tests\tGREEN\t0\t20\tunit.log\t5 0 0 0\tok' \
  $'clean-start\tGREEN\t0\t30\tclean.log\t-\tclean' \
  >"$PHASE_TSV"
rewrite_last_phase "AMBER" "HA invalid: restore failed"
row1="$(sed -n '1p' "$PHASE_TSV")"
row2="$(sed -n '2p' "$PHASE_TSV")"
row3="$(sed -n '3p' "$PHASE_TSV")"
assert_eq "$row1" $'preflight\tGREEN\t0\t10\tpreflight.log\t-\tok' \
  "rewrite_last_phase leaves row 1 untouched"
assert_eq "$row2" $'unit-tests\tGREEN\t0\t20\tunit.log\t5 0 0 0\tok' \
  "rewrite_last_phase leaves row 2 untouched"
assert_eq "$row3" $'clean-start\tAMBER\t0\t30\tclean.log\t-\tHA invalid: restore failed' \
  "rewrite_last_phase AMBER-downgrades last row only"
# Stub head that rejects negative counts (BSD macOS). Must still succeed via sed.
mkdir -p "$TMP/bin-bsd-head"
cat >"$TMP/bin-bsd-head/head" <<'BSDHEAD'
#!/bin/bash
args=("$@")
for ((i = 0; i < ${#args[@]}; i++)); do
  if [[ "${args[$i]}" == "-n" && "${args[$((i + 1))]:-}" =~ ^- ]]; then
    echo "head: illegal line count -- ${args[$((i + 1))]}" >&2
    exit 1
  fi
  if [[ "${args[$i]}" =~ ^-n- ]]; then
    echo "head: illegal line count -- ${args[$i]#-n}" >&2
    exit 1
  fi
done
exec /usr/bin/head "$@"
BSDHEAD
chmod +x "$TMP/bin-bsd-head/head"
printf '%s\n' \
  $'a\tGREEN\t0\t1\ta.log\t-\tok' \
  $'b\tGREEN\t0\t2\tb.log\t-\tok' \
  $'c\tGREEN\t0\t3\tc.log\t-\tok' \
  >"$PHASE_TSV"
PATH="$TMP/bin-bsd-head:$PATH" /bin/bash -c '
  source "'"$ROOT"'/scripts/lib/quality-gate-ha.sh"
  PHASE_TSV="'"$PHASE_TSV"'"
  rewrite_last_phase "AMBER" "under-bsd-head-stub"
'
row_last="$(tail -1 "$PHASE_TSV")"
assert_eq "$(sed -n '1p' "$PHASE_TSV")" $'a\tGREEN\t0\t1\ta.log\t-\tok' \
  "rewrite under BSD-head stub leaves row 1"
assert_eq "$(sed -n '2p' "$PHASE_TSV")" $'b\tGREEN\t0\t2\tb.log\t-\tok' \
  "rewrite under BSD-head stub leaves row 2"
assert_eq "$row_last" $'c\tAMBER\t0\t3\tc.log\t-\tunder-bsd-head-stub' \
  "rewrite under BSD-head stub AMBER-downgrades last row"

# 5g) parse_surefire_counts — exclude Time elapsed class lines (#915 defect 5)
SF_LOG="$TMP/surefire-sample.log"
cat >"$SF_LOG" <<'SF'
[INFO] Running org.ezkey.FooTest
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.512 s -- in org.ezkey.FooTest
[INFO] Running org.ezkey.BarTest
Tests run: 2, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 0.301 s -- in org.ezkey.BarTest
[INFO] Results:
[INFO] Tests run: 5, Failures: 1, Errors: 0, Skipped: 0
[INFO] Running org.ezkey.OtherModuleTest
Tests run: 4, Failures: 0, Errors: 0, Skipped: 1, Time elapsed: 0.200 s -- in org.ezkey.OtherModuleTest
[INFO] Results:
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 1
SF
sf_counts="$(parse_surefire_counts "$SF_LOG")"
assert_eq "$sf_counts" "9 1 0 1" \
  "surefire multi-module: class lines excluded, module totals summed"
# Single-module: only module total counts (not class + total).
SF_ONE="$TMP/surefire-one.log"
cat >"$SF_ONE" <<'SF'
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.0 s -- in org.ezkey.OnlyTest
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
SF
assert_eq "$(parse_surefire_counts "$SF_ONE")" "7 0 0 0" \
  "surefire single-module: exact module total (no double-count)"

# 5h) timeout/gtimeout resolve (#915) — gtimeout-only PATH, and neither
mkdir -p "$TMP/bin-gtimeout-only" "$TMP/bin-empty"
cat >"$TMP/bin-gtimeout-only/gtimeout" <<'GTO'
#!/bin/bash
# Marker only — resolve uses command -v; run_bounded tests exercise the warn path.
exit 0
GTO
chmod +x "$TMP/bin-gtimeout-only/gtimeout"
gt_path="$(
  PATH="$TMP/bin-gtimeout-only" /bin/bash -c '
    source "'"$ROOT"'/scripts/lib/quality-gate-ha.sh"
    quality_gate_resolve_timeout_cmd
  '
)"
[[ "$gt_path" == *gtimeout ]] \
  && echo "ok - resolve prefers gtimeout when timeout absent" && pass=$((pass+1)) \
  || { echo "not ok - resolve prefers gtimeout when timeout absent (got=$gt_path)"; fail=$((fail+1)); }
set +e
neither_out="$(
  PATH="$TMP/bin-empty" /bin/bash -c '
    source "'"$ROOT"'/scripts/lib/quality-gate-ha.sh"
    quality_gate_resolve_timeout_cmd
  ' 2>/dev/null
)"
neither_ec=$?
set -e
assert_eq "$neither_ec" "1" "resolve returns 1 when neither timeout nor gtimeout"
assert_eq "$neither_out" "" "resolve prints nothing when neither exists"
# run_bounded with neither: warns on stderr and still runs the command.
bound_err="$TMP/run-bounded.err"
bound_out="$(
  PATH="$TMP/bin-empty" /bin/bash -c '
    source "'"$ROOT"'/scripts/lib/quality-gate-ha.sh"
    quality_gate_run_bounded 1 /bin/echo ran-unbound
  ' 2>"$bound_err"
)"
assert_eq "$bound_out" "ran-unbound" "run_bounded without timeout still runs command"
grep -Fq 'neither timeout nor gtimeout' "$bound_err" \
  && echo "ok - run_bounded warns on stderr when unbound" && pass=$((pass+1)) \
  || { echo "not ok - run_bounded warns on stderr when unbound"; fail=$((fail+1)); cat "$bound_err" || true; }
# gtimeout-only: run_bounded must invoke gtimeout (not hang / silent skip).
cat >"$TMP/bin-gtimeout-only/gtimeout" <<'GTO'
#!/bin/bash
# Consume secs then exec remaining argv (like real gtimeout without a clock).
shift
exec "$@"
GTO
chmod +x "$TMP/bin-gtimeout-only/gtimeout"
gt_run="$(
  PATH="$TMP/bin-gtimeout-only" /bin/bash -c '
    source "'"$ROOT"'/scripts/lib/quality-gate-ha.sh"
    quality_gate_run_bounded 180 /bin/echo via-gtimeout
  '
)"
assert_eq "$gt_run" "via-gtimeout" "run_bounded uses gtimeout when only gtimeout on PATH"

# 6) Missing ShedLock kill (failover skipped / unresolved / no kill in 30s) → INCONCLUSIVE
all_healthy
: >"$RUN_DIR/phases/elective-tests.log"
echo 'Mode: HA (High Availability)' >>"$RUN_DIR/phases/elective-tests.log"
echo "Cannot determine container to kill from instance ID '74d78fe655b3'. Skipping failover test." \
  >>"$RUN_DIR/phases/elective-tests.log"
restore_after_elective || true
assert_eq "$SHEDLOCK_KILL_MISSING" "1" "missing kill → SHEDLOCK_KILL_MISSING"
assert_eq "$OVERALL_AMBER" "1" "missing kill → OVERALL_AMBER"
assert_eq "$SHEDLOCK_RESTORE_OK" "0" "missing kill → no restore OK"
assert_eq "$HA_INVALID" "1" "missing kill → HA_INVALID (never GO)"
# Report must classify haInvalid as INCONCLUSIVE, not GO / GO-with-reservations
mkdir -p "$RUN_DIR/health"
printf '%s\n' '{"verdict":"GREEN"}' >"$RUN_DIR/health/health.json"
: >"$RUN_DIR/phases.tsv"
node "$ROOT/scripts/quality-gate-report.mjs" \
  --run-dir "$RUN_DIR" --tip deadbeef --short deadbeef --total-ms 1 \
  --churn-min 0 --red 0 --amber 1 --ha-invalid 1 --compose-retry 0 --shedlock-restored 0 \
  >/dev/null
ov="$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).overallVerdict)' \
  "$RUN_DIR/summary.json")"
assert_eq "$ov" "INCONCLUSIVE: HA invalid" "missing kill → report INCONCLUSIVE (not GO)"

# 7) Elective RED stays RED on kill-missing (never rewrite RED→AMBER)
v="$(elective_kill_missing_phase_verdict RED)"
assert_eq "$v" "RED" "elective RED stays RED on kill-missing"
v="$(elective_kill_missing_phase_verdict GREEN)"
assert_eq "$v" "AMBER" "elective GREEN→AMBER on kill-missing"
v="$(elective_kill_missing_phase_verdict AMBER)"
assert_eq "$v" "AMBER" "elective AMBER stays AMBER on kill-missing"

# 7b) Log grep alone is never kill proof (both replicas still running, no die)
all_healthy
echo 'Crashing container: ezkey-admin-api-2' >"$RUN_DIR/phases/elective-tests.log"
restore_after_elective || true
assert_eq "$SHEDLOCK_KILL_MISSING" "1" "log-only → SHEDLOCK_KILL_MISSING"
assert_eq "$HA_INVALID" "1" "log-only → HA_INVALID (Status/die required)"
assert_eq "$SHEDLOCK_RESTORE_OK" "0" "log-only → no restore OK"
grep -q 'Status/die proof missing' "$RUN_DIR/findings-extra.txt" \
  && echo "ok - log-only → findings mention Status/die" && pass=$((pass+1)) \
  || { echo "not ok - log-only → findings mention Status/die"; fail=$((fail+1)); }

# 7c) INVERTED (Patrick): elective-window die without matching Crashing container /
# exit 137 must NOT prove kill (preflight-style false proof).
all_healthy
: >"$RUN_DIR/phases/elective-tests.log"
: >"$RUN_DIR/findings-extra.txt"
ELECTIVE_STARTED_AT="2026-10-11T01:00:00Z"
export FAKE_DOCKER_EVENTS_LINES="2026-10-11T01:05:00Z ezkey-admin-api-2|137"
restore_after_elective || true
unset FAKE_DOCKER_EVENTS_LINES
assert_eq "$SHEDLOCK_KILL_MISSING" "1" "7c inverted: die without Crashing log → missing"
assert_eq "$SHEDLOCK_RESTORE_OK" "0" "7c inverted: not restored"
assert_eq "$HA_INVALID" "1" "7c inverted: HA_INVALID / INCONCLUSIVE"
assert_eq "$SHEDLOCK_PROOF_PATH" "" "7c inverted: no proof path"

# 7c-bis) pre-elective die (e.g. preflight stop_stacks) must NOT prove kill
all_healthy
echo 'Crashing container: ezkey-admin-api-2' >"$RUN_DIR/phases/elective-tests.log"
: >"$RUN_DIR/findings-extra.txt"
GATE_STARTED_AT="2026-10-11T00:00:00Z"
ELECTIVE_STARTED_AT="2026-10-11T01:00:00Z"
export FAKE_DOCKER_EVENTS_LINES="2026-10-11T00:00:30Z ezkey-admin-api-2|137"
restore_after_elective || true
unset FAKE_DOCKER_EVENTS_LINES
assert_eq "$SHEDLOCK_KILL_MISSING" "1" "pre-elective die → SHEDLOCK_KILL_MISSING"
assert_eq "$SHEDLOCK_RESTORE_OK" "0" "pre-elective die → not restored"
assert_eq "$HA_INVALID" "1" "pre-elective die → HA_INVALID / INCONCLUSIVE"
assert_eq "$SHEDLOCK_PROOF_PATH" "" "pre-elective die → no proof path"

# 7c-ter) die fallback OK: elective window + matching Crashing container + exit 137
all_healthy
echo 'Crashing container: ezkey-admin-api-2' >"$RUN_DIR/phases/elective-tests.log"
ELECTIVE_STARTED_AT="2026-10-11T01:00:00Z"
export FAKE_DOCKER_EVENTS_LINES="2026-10-11T01:05:00Z ezkey-admin-api-2|137"
restore_after_elective
rc=$?
unset FAKE_DOCKER_EVENTS_LINES
assert_eq "$rc" "0" "die+log+137 → restore OK"
assert_eq "$SHEDLOCK_KILLED" "ezkey-admin-api-2" "die+log+137 → killed name"
assert_eq "$SHEDLOCK_RESTORE_OK" "1" "die+log+137 → restore OK flag"
assert_eq "$HA_INVALID" "0" "die+log+137 → not HA invalid"
assert_eq "$SHEDLOCK_PROOF_PATH" "docker-die" "die+log+137 → proof path docker-die"

# 7c-quater) die with wrong exit code (not 137) must not prove
all_healthy
echo 'Crashing container: ezkey-admin-api-2' >"$RUN_DIR/phases/elective-tests.log"
: >"$RUN_DIR/findings-extra.txt"
ELECTIVE_STARTED_AT="2026-10-11T01:00:00Z"
export FAKE_DOCKER_EVENTS_LINES="2026-10-11T01:05:00Z ezkey-admin-api-2|0"
restore_after_elective || true
unset FAKE_DOCKER_EVENTS_LINES
assert_eq "$SHEDLOCK_KILL_MISSING" "1" "die exit≠137 → missing"
assert_eq "$HA_INVALID" "1" "die exit≠137 → HA_INVALID"

# 7d) skipped phase → report INCONCLUSIVE (never GO)
mkdir -p "$RUN_DIR/health"
printf '%s\n' '{"verdict":"GREEN"}' >"$RUN_DIR/health/health.json"
printf '%s\n' $'health\tSKIP\t0\t0\t-\t-\tskipped by flag' >"$RUN_DIR/phases.tsv"
node "$ROOT/scripts/quality-gate-report.mjs" \
  --run-dir "$RUN_DIR" --tip deadbeef --short deadbeef --total-ms 1 \
  --churn-min 0 --red 0 --amber 0 --ha-invalid 0 --skipped 1 --compose-retry 0 --shedlock-restored 0 \
  >/dev/null
ov="$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).overallVerdict)' \
  "$RUN_DIR/summary.json")"
assert_eq "$ov" "INCONCLUSIVE: skipped phase(s)" "skip → report INCONCLUSIVE (not GO)"

# 8) M2: docker events failure → UNKNOWN/AMBER, never silent "no OOM"
all_healthy
: >"$RUN_DIR/findings-extra.txt"
OVERALL_RED=0
OVERALL_AMBER=0
export FAKE_DOCKER_EVENTS_FAIL=1
check_oom_evidence || true
unset FAKE_DOCKER_EVENTS_FAIL
assert_eq "$OVERALL_AMBER" "1" "docker events fail → OVERALL_AMBER"
assert_eq "$OVERALL_RED" "0" "docker events fail → not RED (no silent empty success)"
grep -q 'OOM check UNKNOWN' "$RUN_DIR/findings-extra.txt" \
  && echo "ok - docker events fail → findings UNKNOWN" && pass=$((pass+1)) \
  || { echo "not ok - docker events fail → findings UNKNOWN"; fail=$((fail+1)); }

# 9) OOM burst: more than 20 events → RED (head -20 still non-empty → OVERALL_RED)
all_healthy
: >"$RUN_DIR/findings-extra.txt"
OVERALL_RED=0
OVERALL_AMBER=0
burst_lines=""
for i in $(seq 1 25); do
  burst_lines+="2026-10-11T00:00:00.000000000Z container oom (fake-burst-$i)"$'\n'
done
export FAKE_DOCKER_EVENTS_LINES="$burst_lines"
check_oom_evidence || true
unset FAKE_DOCKER_EVENTS_LINES
assert_eq "$OVERALL_RED" "1" "OOM burst (>20 events) → OVERALL_RED"
assert_eq "$OVERALL_AMBER" "0" "OOM burst → not AMBER-only"
grep -q 'docker events reported container OOM' "$RUN_DIR/findings-extra.txt" \
  && echo "ok - OOM burst → findings RED" && pass=$((pass+1)) \
  || { echo "not ok - OOM burst → findings RED"; fail=$((fail+1)); }

# 10) quality-gate.sh exit 4 when any phase skipped (fake: skip all phases)
SKIP_OUT="$TMP/skip-all-run"
# Run from repo root; all phases skipped → INCONCLUSIVE exit 4 (no stack needed).
set +e
(
  cd "$ROOT"
  # Isolate artifacts under TMP via QUALITY_GATE override is unavailable — use --skip all.
  ./scripts/quality-gate.sh \
    --skip preflight --skip unit-tests --skip clean-start --skip post-clean-health \
    --skip functional-tests --skip elective-tests --skip playwright \
    --skip churn-init --skip churn --skip health \
    >"$SKIP_OUT.log" 2>&1
)
skip_ec=$?
set -e
assert_eq "$skip_ec" "4" "all-phases --skip → exit 4"
grep -q 'INCONCLUSIVE: skipped phase(s)' "$SKIP_OUT.log" \
  && echo "ok - all-skip → OVERALL INCONCLUSIVE" && pass=$((pass+1)) \
  || { echo "not ok - all-skip → OVERALL INCONCLUSIVE"; fail=$((fail+1)); grep -E 'OVERALL|overall' "$SKIP_OUT.log" || true; }

# 11) --no-clean-start must not call `docker compose … down` (#915 defect 3).
# Recording fake-docker on PATH; run preflight only (other phases skipped).
REC_BIN="$TMP/bin-record"
REC_LOG="$TMP/docker-calls.log"
mkdir -p "$REC_BIN"
: >"$REC_LOG"
cat >"$REC_BIN/docker" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
# Record every invocation (space-joined) for down/rm assertions.
printf '%s\n' "$*" >>"${FAKE_DOCKER_CALL_LOG:?}"
cmd="${1:-}"
case "$cmd" in
  compose|rm) exit 0 ;;
  info) echo 17179869184; exit 0 ;;
  events|logs|inspect) exit 0 ;;
  *) echo "record-docker: unsupported $cmd" >&2; exit 1 ;;
esac
EOF
chmod +x "$REC_BIN/docker"

NCS_OUT="$TMP/no-clean-start-run"
set +e
(
  cd "$ROOT"
  FAKE_DOCKER_CALL_LOG="$REC_LOG" PATH="$REC_BIN:$PATH" \
    ./scripts/quality-gate.sh --no-clean-start \
      --skip functional-tests --skip elective-tests --skip playwright \
      --skip churn-init --skip churn --skip health \
      >"$NCS_OUT.log" 2>&1
)
ncs_ec=$?
set -e
assert_eq "$ncs_ec" "4" "--no-clean-start → exit 4 (INCONCLUSIVE)"
NCS_RUN_DIR="$(sed -n 's/.*run dir: //p' "$NCS_OUT.log" | head -1)"
[[ -n "$NCS_RUN_DIR" && -f "$NCS_RUN_DIR/preflight.txt" ]] \
  && grep -q 'reusing running stack' "$NCS_RUN_DIR/preflight.txt" \
  && echo "ok - --no-clean-start logs reusing running stack" && pass=$((pass+1)) \
  || { echo "not ok - --no-clean-start logs reusing running stack"; fail=$((fail+1)); \
       echo "run_dir=${NCS_RUN_DIR:-missing}"; cat "$NCS_OUT.log" || true; }
# unit-tests / clean-start / post-clean-health are skipped by the flag.
grep -E $'^unit-tests\tSKIP\t' "$NCS_RUN_DIR/phases.tsv" >/dev/null 2>&1 \
  && echo "ok - --no-clean-start skips unit-tests" && pass=$((pass+1)) \
  || { echo "not ok - --no-clean-start skips unit-tests"; fail=$((fail+1)); }
grep -E '(^|[[:space:]])down([[:space:]]|$)' "$REC_LOG" >/dev/null \
  && { echo "not ok - --no-clean-start must not invoke compose down"; fail=$((fail+1)); cat "$REC_LOG"; } \
  || { echo "ok - --no-clean-start → no docker down"; pass=$((pass+1)); }
grep -q 'rm -f' "$REC_LOG" >/dev/null 2>&1 \
  && { echo "not ok - --no-clean-start must not docker rm collectors"; fail=$((fail+1)); } \
  || { echo "ok - --no-clean-start → no docker rm"; pass=$((pass+1)); }

# Control: without the flag, preflight still runs stop_stacks (down present).
: >"$REC_LOG"
STOP_OUT="$TMP/with-stop-stacks-run"
set +e
(
  cd "$ROOT"
  FAKE_DOCKER_CALL_LOG="$REC_LOG" PATH="$REC_BIN:$PATH" \
    ./scripts/quality-gate.sh \
      --skip unit-tests --skip clean-start --skip post-clean-health \
      --skip functional-tests --skip elective-tests --skip playwright \
      --skip churn-init --skip churn --skip health \
      >"$STOP_OUT.log" 2>&1
)
stop_ec=$?
set -e
assert_eq "$stop_ec" "4" "preflight-only (no --no-clean-start) → exit 4"
grep -E '(^|[[:space:]])down([[:space:]]|$)' "$REC_LOG" >/dev/null \
  && echo "ok - default preflight still invokes compose down" && pass=$((pass+1)) \
  || { echo "not ok - default preflight still invokes compose down"; fail=$((fail+1)); cat "$REC_LOG"; }

# 12) Playwright regenerates Orval before UI tests (#915 defect 6).
# Fake npm records script order; fake curl satisfies stack_up (Admin+Crypto probes);
# all_healthy keeps check_replicas from rewriting GREEN→AMBER. Failing generate:api
# must RED and must not invoke UI test npm scripts. Missing Admin UI deps → npm ci
# first (fresh worktree); npm ci failure → RED, no UI tests.
NPM_REC_BIN="$TMP/bin-npm-record"
NPM_REC_LOG="$TMP/npm-calls.log"
mkdir -p "$NPM_REC_BIN"
: >"$NPM_REC_LOG"
cat >"$NPM_REC_BIN/npm" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
# Record npm argv (ci / run generate:api / run test:browser*).
printf '%s\n' "$*" >>"${FAKE_NPM_CALL_LOG:?}"
if [[ "${1:-}" == "ci" && "${FAKE_NPM_CI_FAIL:-0}" == "1" ]]; then
  echo "fake-npm: npm ci failed (test stub)" >&2
  exit 1
fi
if [[ "${1:-}" == "run" && "${2:-}" == "generate:api" && "${FAKE_NPM_GENERATE_FAIL:-0}" == "1" ]]; then
  echo "fake-npm: generate:api failed (test stub)" >&2
  exit 1
fi
exit 0
EOF
chmod +x "$NPM_REC_BIN/npm"
# stack_up: curl -sf http://127.0.0.1:9080/... && curl -sf http://127.0.0.1:8084/...
cat >"$NPM_REC_BIN/curl" <<'EOF'
#!/usr/bin/env bash
exit 0
EOF
chmod +x "$NPM_REC_BIN/curl"

PW_SKIP=(
  --skip preflight --skip unit-tests --skip clean-start --skip post-clean-health
  --skip functional-tests --skip elective-tests
  --skip churn-init --skip churn --skip health
)

# Seed the full gate HA_REPLICAS set (harness all_healthy only covers admin-api-1/2).
seed_gate_replicas_healthy() {
  local n
  for n in ezkey-admin-api-1 ezkey-admin-api-2 \
           ezkey-auth-api-1 ezkey-auth-api-2 \
           ezkey-integration-api-1 ezkey-integration-api-2 \
           ezkey-crypto-api-ha; do
    set_replica "$n" running healthy 0
  done
}

# Hide/restore Orval so the gate sees missing Admin UI deps without moving
# the whole node_modules tree. Keep the backup beside .bin (not under $TMP)
# and restore on EXIT so a failed assertion cannot leave Orval missing.
# CI checkouts often have no ezkey-admin-ui/node_modules — seed a stub Orval
# for the "deps present" cases, then remove only that stub on EXIT.
ORVAL_BIN="$ROOT/ezkey-admin-ui/node_modules/.bin/orval"
ORVAL_HIDE=""
STUB_ORVAL=0
ensure_admin_ui_deps_present_for_test() {
  mkdir -p "$(dirname "$ORVAL_BIN")"
  if [[ ! -e "$ORVAL_BIN" && ! -L "$ORVAL_BIN" ]]; then
    printf '#!/bin/sh\nexit 0\n' >"$ORVAL_BIN"
    chmod +x "$ORVAL_BIN"
    STUB_ORVAL=1
  fi
}
cleanup_stub_admin_ui_orval() {
  if [[ "${STUB_ORVAL:-0}" -eq 1 ]]; then
    rm -f "$ORVAL_BIN"
    rmdir "$(dirname "$ORVAL_BIN")" 2>/dev/null || true
    rmdir "$ROOT/ezkey-admin-ui/node_modules" 2>/dev/null || true
    STUB_ORVAL=0
  fi
}
hide_admin_ui_orval() {
  ORVAL_HIDE=""
  if [[ -e "$ORVAL_BIN" || -L "$ORVAL_BIN" ]]; then
    ORVAL_HIDE="${ORVAL_BIN}.__qg_ha_bak"
    mv "$ORVAL_BIN" "$ORVAL_HIDE"
  fi
}
restore_admin_ui_orval() {
  if [[ -n "${ORVAL_HIDE:-}" && -e "$ORVAL_HIDE" ]]; then
    mkdir -p "$(dirname "$ORVAL_BIN")"
    mv "$ORVAL_HIDE" "$ORVAL_BIN"
    ORVAL_HIDE=""
  fi
  # Stale backup from a killed prior run.
  if [[ -e "${ORVAL_BIN}.__qg_ha_bak" && ! -e "$ORVAL_BIN" ]]; then
    mv "${ORVAL_BIN}.__qg_ha_bak" "$ORVAL_BIN"
  fi
}
trap 'restore_admin_ui_orval; cleanup_stub_admin_ui_orval; rm -rf "$TMP"' EXIT

# 12a) deps present → no npm ci; generate:api runs before UI test npm scripts.
ensure_admin_ui_deps_present_for_test
seed_gate_replicas_healthy
: >"$NPM_REC_LOG"
PW_OK_OUT="$TMP/playwright-orval-ok"
set +e
(
  cd "$ROOT"
  FAKE_NPM_CALL_LOG="$NPM_REC_LOG" PATH="$NPM_REC_BIN:$PATH" \
    ./scripts/quality-gate.sh "${PW_SKIP[@]}" \
    >"$PW_OK_OUT.log" 2>&1
)
pw_ok_ec=$?
set -e
# Skipped phases → exit 4 when playwright GREEN; RED would be 1.
[[ "$pw_ok_ec" -eq 4 || "$pw_ok_ec" -eq 0 ]] \
  && echo "ok - playwright+Orval regen → exit $pw_ok_ec (not NO-GO)" && pass=$((pass+1)) \
  || { echo "not ok - playwright+Orval regen unexpected exit $pw_ok_ec"; fail=$((fail+1)); \
       cat "$PW_OK_OUT.log" || true; cat "$NPM_REC_LOG" || true; }
PW_OK_RUN="$(sed -n 's/.*run dir: //p' "$PW_OK_OUT.log" | head -1)"
[[ -n "$PW_OK_RUN" && -f "$PW_OK_RUN/phases.tsv" ]] \
  && grep -E $'^playwright\tGREEN\t' "$PW_OK_RUN/phases.tsv" >/dev/null \
  && echo "ok - playwright phase GREEN after generate:api" && pass=$((pass+1)) \
  || { echo "not ok - playwright phase GREEN after generate:api"; fail=$((fail+1)); \
       echo "run_dir=${PW_OK_RUN:-missing}"; cat "$PW_OK_OUT.log" || true; \
       [[ -n "${PW_OK_RUN:-}" ]] && cat "$PW_OK_RUN/phases/playwright.log" 2>/dev/null || true; \
       [[ -n "${PW_OK_RUN:-}" ]] && cat "$PW_OK_RUN/phases.tsv" 2>/dev/null || true; }
# First npm run must be generate:api; a UI-test script must appear later.
gen_line="$(grep -nE '^run generate:api([[:space:]]|$)' "$NPM_REC_LOG" | head -1 | cut -d: -f1)"
ui_line="$(grep -nE '^run test:browser' "$NPM_REC_LOG" | head -1 | cut -d: -f1)"
[[ -n "$gen_line" && -n "$ui_line" && "$gen_line" -lt "$ui_line" ]] \
  && echo "ok - generate:api before test:browser* (lines $gen_line < $ui_line)" && pass=$((pass+1)) \
  || { echo "not ok - generate:api before test:browser* (gen=$gen_line ui=$ui_line)"; fail=$((fail+1)); \
       cat "$NPM_REC_LOG" || true; }
grep -E '^ci([[:space:]]|$)' "$NPM_REC_LOG" >/dev/null \
  && { echo "not ok - deps present must not run npm ci"; fail=$((fail+1)); cat "$NPM_REC_LOG"; } \
  || { echo "ok - deps present → no npm ci"; pass=$((pass+1)); }

# 12b) failing generate:api → RED; UI tests never invoked.
seed_gate_replicas_healthy
: >"$NPM_REC_LOG"
PW_FAIL_OUT="$TMP/playwright-orval-fail"
set +e
(
  cd "$ROOT"
  FAKE_NPM_CALL_LOG="$NPM_REC_LOG" FAKE_NPM_GENERATE_FAIL=1 PATH="$NPM_REC_BIN:$PATH" \
    ./scripts/quality-gate.sh "${PW_SKIP[@]}" \
    >"$PW_FAIL_OUT.log" 2>&1
)
pw_fail_ec=$?
set -e
assert_eq "$pw_fail_ec" "1" "generate:api fail → exit 1 (NO-GO)"
PW_FAIL_RUN="$(sed -n 's/.*run dir: //p' "$PW_FAIL_OUT.log" | head -1)"
[[ -n "$PW_FAIL_RUN" && -f "$PW_FAIL_RUN/phases.tsv" ]] \
  && grep -E $'^playwright\tRED\t' "$PW_FAIL_RUN/phases.tsv" >/dev/null \
  && echo "ok - generate:api fail → playwright RED" && pass=$((pass+1)) \
  || { echo "not ok - generate:api fail → playwright RED"; fail=$((fail+1)); \
       echo "run_dir=${PW_FAIL_RUN:-missing}"; cat "$PW_FAIL_OUT.log" || true; }
[[ -n "$PW_FAIL_RUN" && -f "$PW_FAIL_RUN/phases/playwright.log" ]] \
  && grep -q 'refusing Playwright with a stale Orval client' "$PW_FAIL_RUN/phases/playwright.log" \
  && echo "ok - generate:api fail → clear stale-client note in log" && pass=$((pass+1)) \
  || { echo "not ok - generate:api fail → clear stale-client note in log"; fail=$((fail+1)); \
       [[ -n "${PW_FAIL_RUN:-}" ]] && cat "$PW_FAIL_RUN/phases/playwright.log" 2>/dev/null || true; }
grep -E '^run test:browser' "$NPM_REC_LOG" >/dev/null \
  && { echo "not ok - generate:api fail must not run test:browser*"; fail=$((fail+1)); cat "$NPM_REC_LOG"; } \
  || { echo "ok - generate:api fail → no test:browser*"; pass=$((pass+1)); }
grep -E '^run generate:api([[:space:]]|$)' "$NPM_REC_LOG" >/dev/null \
  && echo "ok - generate:api was attempted before abort" && pass=$((pass+1)) \
  || { echo "not ok - generate:api was attempted before abort"; fail=$((fail+1)); cat "$NPM_REC_LOG"; }

# 12c) deps missing → npm ci ok → generate:api (and UI tests) run.
seed_gate_replicas_healthy
: >"$NPM_REC_LOG"
hide_admin_ui_orval
PW_CI_OK_OUT="$TMP/playwright-npm-ci-ok"
set +e
(
  cd "$ROOT"
  FAKE_NPM_CALL_LOG="$NPM_REC_LOG" PATH="$NPM_REC_BIN:$PATH" \
    ./scripts/quality-gate.sh "${PW_SKIP[@]}" \
    >"$PW_CI_OK_OUT.log" 2>&1
)
pw_ci_ok_ec=$?
set -e
restore_admin_ui_orval
[[ "$pw_ci_ok_ec" -eq 4 || "$pw_ci_ok_ec" -eq 0 ]] \
  && echo "ok - deps missing + npm ci ok → exit $pw_ci_ok_ec (not NO-GO)" && pass=$((pass+1)) \
  || { echo "not ok - deps missing + npm ci ok unexpected exit $pw_ci_ok_ec"; fail=$((fail+1)); \
       cat "$PW_CI_OK_OUT.log" || true; cat "$NPM_REC_LOG" || true; }
PW_CI_OK_RUN="$(sed -n 's/.*run dir: //p' "$PW_CI_OK_OUT.log" | head -1)"
[[ -n "$PW_CI_OK_RUN" && -f "$PW_CI_OK_RUN/phases.tsv" ]] \
  && grep -E $'^playwright\tGREEN\t' "$PW_CI_OK_RUN/phases.tsv" >/dev/null \
  && echo "ok - deps missing + npm ci ok → playwright GREEN" && pass=$((pass+1)) \
  || { echo "not ok - deps missing + npm ci ok → playwright GREEN"; fail=$((fail+1)); \
       echo "run_dir=${PW_CI_OK_RUN:-missing}"; cat "$PW_CI_OK_OUT.log" || true; \
       [[ -n "${PW_CI_OK_RUN:-}" ]] && cat "$PW_CI_OK_RUN/phases/playwright.log" 2>/dev/null || true; }
[[ -n "$PW_CI_OK_RUN" && -f "$PW_CI_OK_RUN/phases/playwright.log" ]] \
  && grep -q 'npm ci (Admin UI deps missing)' "$PW_CI_OK_RUN/phases/playwright.log" \
  && echo "ok - deps missing → npm ci logged to phase log" && pass=$((pass+1)) \
  || { echo "not ok - deps missing → npm ci logged to phase log"; fail=$((fail+1)); \
       [[ -n "${PW_CI_OK_RUN:-}" ]] && cat "$PW_CI_OK_RUN/phases/playwright.log" 2>/dev/null || true; }
ci_line="$(grep -nE '^ci([[:space:]]|$)' "$NPM_REC_LOG" | head -1 | cut -d: -f1)"
gen_line="$(grep -nE '^run generate:api([[:space:]]|$)' "$NPM_REC_LOG" | head -1 | cut -d: -f1)"
ui_line="$(grep -nE '^run test:browser' "$NPM_REC_LOG" | head -1 | cut -d: -f1)"
[[ -n "$ci_line" && -n "$gen_line" && "$ci_line" -lt "$gen_line" ]] \
  && echo "ok - npm ci before generate:api (lines $ci_line < $gen_line)" && pass=$((pass+1)) \
  || { echo "not ok - npm ci before generate:api (ci=$ci_line gen=$gen_line)"; fail=$((fail+1)); \
       cat "$NPM_REC_LOG" || true; }
[[ -n "$gen_line" && -n "$ui_line" && "$gen_line" -lt "$ui_line" ]] \
  && echo "ok - deps missing path: generate:api before test:browser*" && pass=$((pass+1)) \
  || { echo "not ok - deps missing path: generate:api before test:browser*"; fail=$((fail+1)); \
       cat "$NPM_REC_LOG" || true; }

# 12d) npm ci fails → RED; UI tests never invoked (nor generate:api).
seed_gate_replicas_healthy
: >"$NPM_REC_LOG"
hide_admin_ui_orval
PW_CI_FAIL_OUT="$TMP/playwright-npm-ci-fail"
set +e
(
  cd "$ROOT"
  FAKE_NPM_CALL_LOG="$NPM_REC_LOG" FAKE_NPM_CI_FAIL=1 PATH="$NPM_REC_BIN:$PATH" \
    ./scripts/quality-gate.sh "${PW_SKIP[@]}" \
    >"$PW_CI_FAIL_OUT.log" 2>&1
)
pw_ci_fail_ec=$?
set -e
restore_admin_ui_orval
assert_eq "$pw_ci_fail_ec" "1" "npm ci fail → exit 1 (NO-GO)"
PW_CI_FAIL_RUN="$(sed -n 's/.*run dir: //p' "$PW_CI_FAIL_OUT.log" | head -1)"
[[ -n "$PW_CI_FAIL_RUN" && -f "$PW_CI_FAIL_RUN/phases.tsv" ]] \
  && grep -E $'^playwright\tRED\t' "$PW_CI_FAIL_RUN/phases.tsv" >/dev/null \
  && echo "ok - npm ci fail → playwright RED" && pass=$((pass+1)) \
  || { echo "not ok - npm ci fail → playwright RED"; fail=$((fail+1)); \
       echo "run_dir=${PW_CI_FAIL_RUN:-missing}"; cat "$PW_CI_FAIL_OUT.log" || true; }
[[ -n "$PW_CI_FAIL_RUN" && -f "$PW_CI_FAIL_RUN/phases/playwright.log" ]] \
  && grep -q 'FATAL: npm ci failed in ezkey-admin-ui' "$PW_CI_FAIL_RUN/phases/playwright.log" \
  && echo "ok - npm ci fail → clear FATAL in phase log" && pass=$((pass+1)) \
  || { echo "not ok - npm ci fail → clear FATAL in phase log"; fail=$((fail+1)); \
       [[ -n "${PW_CI_FAIL_RUN:-}" ]] && cat "$PW_CI_FAIL_RUN/phases/playwright.log" 2>/dev/null || true; }
grep -E '^ci([[:space:]]|$)' "$NPM_REC_LOG" >/dev/null \
  && echo "ok - npm ci was attempted before abort" && pass=$((pass+1)) \
  || { echo "not ok - npm ci was attempted before abort"; fail=$((fail+1)); cat "$NPM_REC_LOG"; }
grep -E '^run generate:api([[:space:]]|$)' "$NPM_REC_LOG" >/dev/null \
  && { echo "not ok - npm ci fail must not run generate:api"; fail=$((fail+1)); cat "$NPM_REC_LOG"; } \
  || { echo "ok - npm ci fail → no generate:api"; pass=$((pass+1)); }
grep -E '^run test:browser' "$NPM_REC_LOG" >/dev/null \
  && { echo "not ok - npm ci fail must not run test:browser*"; fail=$((fail+1)); cat "$NPM_REC_LOG"; } \
  || { echo "ok - npm ci fail → no test:browser*"; pass=$((pass+1)); }

echo "passes=$pass fails=$fail"
[[ "$fail" -eq 0 ]]
