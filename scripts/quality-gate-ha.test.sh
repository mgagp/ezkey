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
SHEDLOCK_KILL_EXPECTED=0
SHEDLOCK_RESTORE_OK=0
SHEDLOCK_KILLED=""
RUN_DIR="$TMP/run"
mkdir -p "$RUN_DIR/phases"
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
  HA_INVALID=0; OVERALL_AMBER=0
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

echo "passes=$pass fails=$fail"
[[ "$fail" -eq 0 ]]
