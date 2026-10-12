#!/usr/bin/env bash
# Fake-docker HA helper tests (~Patrick C1-bis / restore / C2).
# Run: ./scripts/quality-gate-ha.test.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

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
      # Lines are either "name" (always emit) or "ISOZ name" (filter by --since/--until).
      while IFS= read -r eline; do
        [[ -z "$eline" ]] && continue
        if [[ "$eline" =~ ^([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:]+Z)[[:space:]]+(.+)$ ]]; then
          ets="${BASH_REMATCH[1]}"
          ename="${BASH_REMATCH[2]}"
          if [[ -n "$since" && "$ets" < "$since" ]]; then continue; fi
          if [[ -n "$until" && "$ets" > "$until" ]]; then continue; fi
          if [[ "$format" == *Attributes.name* || -z "$format" ]]; then
            echo "$ename"
          else
            echo "$eline"
          fi
        else
          echo "$eline"
        fi
      done <<< "$FAKE_DOCKER_EVENTS_LINES"
    fi
    exit 0
    ;;
  logs)
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

# 7c) docker die event proves kill when Status is already running again
all_healthy
: >"$RUN_DIR/phases/elective-tests.log"
ELECTIVE_STARTED_AT="2026-10-11T01:00:00Z"
export FAKE_DOCKER_EVENTS_LINES="2026-10-11T01:05:00Z ezkey-admin-api-2"
restore_after_elective
rc=$?
unset FAKE_DOCKER_EVENTS_LINES
assert_eq "$rc" "0" "die-event proof → restore OK"
assert_eq "$SHEDLOCK_KILLED" "ezkey-admin-api-2" "die-event → killed name"
assert_eq "$SHEDLOCK_RESTORE_OK" "1" "die-event → restore OK flag"
assert_eq "$HA_INVALID" "0" "die-event → not HA invalid"
assert_eq "$SHEDLOCK_PROOF_PATH" "docker-die" "die-event → proof path docker-die"

# 7c-bis) pre-elective die (e.g. preflight stop_stacks) must NOT prove kill
all_healthy
: >"$RUN_DIR/phases/elective-tests.log"
: >"$RUN_DIR/findings-extra.txt"
GATE_STARTED_AT="2026-10-11T00:00:00Z"
ELECTIVE_STARTED_AT="2026-10-11T01:00:00Z"
# die during preflight window only — nothing killed in elective
export FAKE_DOCKER_EVENTS_LINES="2026-10-11T00:00:30Z ezkey-admin-api-2"
restore_after_elective || true
unset FAKE_DOCKER_EVENTS_LINES
assert_eq "$SHEDLOCK_KILL_MISSING" "1" "pre-elective die → SHEDLOCK_KILL_MISSING"
assert_eq "$SHEDLOCK_RESTORE_OK" "0" "pre-elective die → not restored"
assert_eq "$HA_INVALID" "1" "pre-elective die → HA_INVALID / INCONCLUSIVE"
assert_eq "$SHEDLOCK_PROOF_PATH" "" "pre-elective die → no proof path"

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

echo "passes=$pass fails=$fail"
[[ "$fail" -eq 0 ]]
