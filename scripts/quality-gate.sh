#!/usr/bin/env bash
#
# quality-gate — on-demand full-suite gate (cloud agent / Git Bash).
#
# Phase order (unit tests before stack — memory headroom on ~16Gi VMs):
#   preflight → unit-tests → clean-start HA+JM (+gate memory overlay)
#   → post-clean-health → functional → elective → Playwright
#   → churn-init → 2× churn → health → REPORT.md + summary.json
#
# Exit: 0 = GO, 3 = GO with reservations, other non-zero = NO-GO
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

export LC_ALL="${LC_ALL:-C.UTF-8}"
export LANG="${LANG:-C.UTF-8}"

KEYWORD="quality-gate"
CHURN_MINUTES=5
NO_CLEAN_START=0
SKIP_PHASES=""
COMPOSE_RETRY_FIRED=0
HA_INVALID=0
OVERALL_RED=0
OVERALL_AMBER=0
STACK_DOWN=0
CHURN_PIDS=""
MEM_SAMPLER_PID=""

GATE_OVERLAY="$REPO_ROOT/docker/docker-compose.ha.quality-gate.yml"
PHASES="preflight unit-tests clean-start post-clean-health functional-tests elective-tests playwright churn-init churn health"

HA_REPLICAS="ezkey-admin-api-1 ezkey-admin-api-2 ezkey-auth-api-1 ezkey-auth-api-2 ezkey-integration-api-1 ezkey-integration-api-2 ezkey-crypto-api-ha"
MEM_SAMPLE_CONTAINERS="$HA_REPLICAS ezkey-demo-device-ha ezkey-demo-app-acme-ha"

usage() {
  cat <<'EOF'
Usage: ./scripts/quality-gate.sh [options]

  --churn-minutes N   Minutes per concurrent churn shell (default: 5)
  --skip PHASE        Skip a phase (repeatable)
  --no-clean-start    Skip clean-start (reuse running stack)
  -h, --help          Show this help

Docs: product-docs/global/hygiene/quality-gate/README.md
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --churn-minutes) CHURN_MINUTES="${2:-}"; shift 2 ;;
    --skip) SKIP_PHASES="${SKIP_PHASES} ${2:-}"; shift 2 ;;
    --no-clean-start) NO_CLEAN_START=1; SKIP_PHASES="${SKIP_PHASES} clean-start post-clean-health"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

SHORT_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
FULL_SHA="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
UTC_STAMP="$(date -u +"%Y%m%dT%H%M%SZ")"
RUN_DIR="$REPO_ROOT/logs/quality-gate/${UTC_STAMP}-${SHORT_SHA}"
mkdir -p "$RUN_DIR/phases" "$RUN_DIR/churn" "$RUN_DIR/health" "$RUN_DIR/nmt"
PHASE_TSV="$RUN_DIR/phases.tsv"
: >"$PHASE_TSV"
REPLICAS_TSV="$RUN_DIR/replicas.tsv"
: >"$REPLICAS_TSV"
MEM_SAMPLES_TSV="$RUN_DIR/mem-samples.tsv"
printf 'utc\tphase\tcontainer\tmem_usage\tmem_limit\tmem_perc\n' >"$MEM_SAMPLES_TSV"

echo "[$KEYWORD] run dir: $RUN_DIR"
echo "[$KEYWORD] tip: $FULL_SHA"

stop_mem_sampler() {
  if [[ -n "$MEM_SAMPLER_PID" ]]; then
    kill "$MEM_SAMPLER_PID" 2>/dev/null || true
    wait "$MEM_SAMPLER_PID" 2>/dev/null || true
    MEM_SAMPLER_PID=""
  fi
}

# Sample docker stats every 10s into mem-samples.tsv while a stack phase runs.
start_mem_sampler() {
  local phase="$1"
  stop_mem_sampler
  (
    phase="$phase"
    samples="$MEM_SAMPLES_TSV"
    containers="$MEM_SAMPLE_CONTAINERS"
    while true; do
      utc="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
      # docker stats --no-stream: MemUsage is "used / limit"
      docker stats --no-stream --format '{{.Name}}\t{{.MemUsage}}\t{{.MemPerc}}' $containers 2>/dev/null \
        | while IFS=$'\t' read -r name usage perc; do
            [[ -z "$name" ]] && continue
            used="$(echo "$usage" | awk -F'/' '{gsub(/^ +| +$/,"",$1); print $1}')"
            limit="$(echo "$usage" | awk -F'/' '{gsub(/^ +| +$/,"",$2); print $2}')"
            printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$utc" "$phase" "$name" "$used" "$limit" "$perc" >>"$samples"
          done
      sleep 10
    done
  ) &
  MEM_SAMPLER_PID=$!
}

capture_nmt() {
  local phase="$1"
  local out="$RUN_DIR/nmt/admin-api-1-${phase}.txt"
  if ! docker inspect ezkey-admin-api-1 >/dev/null 2>&1; then
    echo "admin-api-1 not present — skip NMT ($phase)" >"$out"
    return
  fi
  if docker exec ezkey-admin-api-1 sh -c 'command -v jcmd >/dev/null 2>&1'; then
    docker exec ezkey-admin-api-1 jcmd 1 VM.native_memory summary >"$out" 2>&1 || \
      echo "jcmd failed (exit $?)" >"$out"
  else
    echo "jcmd unavailable in admin-api image (report and continue)" >"$out"
  fi
}

cleanup_churn() {
  stop_mem_sampler
  if [[ -n "$CHURN_PIDS" ]]; then
    for p in $CHURN_PIDS; do
      kill "$p" 2>/dev/null || true
    done
  fi
}
trap cleanup_churn INT TERM EXIT

now_ms() { date +%s%3N 2>/dev/null || node -e 'console.log(Date.now())'; }
duration_ms() { echo $(($2 - $1)); }
ms_to_human() {
  local ms="${1:-0}"
  local sec=$((ms / 1000))
  local min=$((sec / 60))
  sec=$((sec % 60))
  if [[ $min -gt 0 ]]; then printf '%dm%02ds' "$min" "$sec"; else printf '%ds' "$sec"; fi
}

should_skip() {
  local name="$1" p
  for p in $SKIP_PHASES; do [[ "$p" == "$name" ]] && return 0; done
  return 1
}

record_phase() {
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' "$1" "$2" "$3" "$4" "$5" "$6" "$7" >>"$PHASE_TSV"
  echo "[$KEYWORD] phase=$1 verdict=$2 exit=$3 duration=$(ms_to_human "$4") counts=$6"
}

parse_surefire_counts() {
  local log="$1" run=0 failed=0 errors=0 skipped=0 line sum_run=0 sum_f=0 sum_e=0 sum_s=0 found=0
  while IFS= read -r line; do
    if [[ "$line" =~ Tests\ run:\ *([0-9]+),\ *Failures:\ *([0-9]+),\ *Errors:\ *([0-9]+),\ *Skipped:\ *([0-9]+) ]]; then
      sum_run=$((sum_run + BASH_REMATCH[1])); sum_f=$((sum_f + BASH_REMATCH[2]))
      sum_e=$((sum_e + BASH_REMATCH[3])); sum_s=$((sum_s + BASH_REMATCH[4])); found=1
      run="${BASH_REMATCH[1]}"; failed="${BASH_REMATCH[2]}"; errors="${BASH_REMATCH[3]}"; skipped="${BASH_REMATCH[4]}"
    fi
  done < <(grep -E 'Tests run:' "$log" 2>/dev/null || true)
  if [[ $found -eq 1 && $sum_run -ge $run ]]; then echo "$sum_run $sum_f $sum_e $sum_s"; else echo "$run $failed $errors $skipped"; fi
}

stack_up() {
  curl -sf http://127.0.0.1:9080/api/v1/public/instance-info >/dev/null 2>&1 \
    && curl -sf http://127.0.0.1:8083/actuator/health >/dev/null 2>&1
}

ha_compose_cmd() {
  local compose="docker compose -f docker/docker-compose.ha.yml"
  [[ -f docker/docker-compose.ha.docker-dev.yml ]] && compose="$compose -f docker/docker-compose.ha.docker-dev.yml"
  [[ "${EZKEY_ENABLE_JAVA_MELODY:-}" == "1" || "${EZKEY_ENABLE_JAVA_MELODY:-}" == "true" ]] \
    && compose="$compose -f docker/docker-compose.ha.javamelody.yml"
  [[ -n "${EZKEY_COMPOSE_EXTRA_FILES:-}" ]] && for f in $EZKEY_COMPOSE_EXTRA_FILES; do compose="$compose -f $f"; done
  echo "$compose"
}

stop_stacks() {
  echo "Stopping Ezkey stacks for unit-test headroom…"
  local ha; ha="$(EZKEY_ENABLE_JAVA_MELODY=true EZKEY_COMPOSE_EXTRA_FILES="$GATE_OVERLAY" ha_compose_cmd)"
  $ha down -v --remove-orphans 2>/dev/null || true
  docker compose -f docker/docker-compose.yml down -v --remove-orphans 2>/dev/null || true
  docker rm -f ezkey-javamelody-collector ezkey-javamelody-collector-ha >/dev/null 2>&1 || true
}

# Inspect every API JVM replica. Dead/restarted → AMBER + HA_INVALID.
check_replicas() {
  local phase="$1" bad=0 name status health restarts
  for name in $HA_REPLICAS; do
    status="$(docker inspect -f '{{.State.Status}}' "$name" 2>/dev/null || echo missing)"
    health="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$name" 2>/dev/null || echo missing)"
    restarts="$(docker inspect -f '{{.RestartCount}}' "$name" 2>/dev/null || echo 0)"
    printf '%s\t%s\t%s\t%s\t%s\n' "$phase" "$name" "$status" "$health" "$restarts" >>"$REPLICAS_TSV"
    if [[ "$status" != "running" || ( "$health" != "healthy" && "$health" != "none" ) ]]; then
      bad=1
      echo "[$KEYWORD] replica issue phase=$phase $name status=$status health=$health restarts=$restarts"
    fi
  done
  if [[ "$bad" -eq 1 ]]; then
    HA_INVALID=1
    OVERALL_AMBER=1
    return 1
  fi
  return 0
}

docker_mem_total_mib() {
  node -e '
const {execFileSync}=require("child_process");
try {
  const j=JSON.parse(execFileSync("docker",["info","--format","{{json .MemTotal}}"],{encoding:"utf8"}).trim());
  const n=Number(j); console.log(Number.isFinite(n)?Math.floor(n/1048576):0);
} catch { console.log(0); }'
}

gate_memory_budget_mib() {
  node -e '
const fs=require("fs");
const t=JSON.parse(fs.readFileSync("config/quality-gate/thresholds.json","utf8"));
const g=t.gateMemory||{};
const lim=g.jvmMemLimitsMiB||{};
let sum=0; for (const v of Object.values(lim)) sum+=Number(v)||0;
sum += Number(g.postgresReserveMiB||1024);
sum += Number(g.hostMarginMiB||3072);
console.log(sum);'
}

run_phase_preflight() {
  local log="$RUN_DIR/phases/preflight.log" start end code=0 verdict=GREEN
  local note="tip + docker MemTotal vs gate memory budget; stack stopped"
  start="$(now_ms)"
  {
    echo "=== preflight ==="
    echo "git_sha: $FULL_SHA"
    echo "date_utc: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    stop_stacks
    local mem_total budget
    mem_total="$(docker_mem_total_mib)"
    budget="$(gate_memory_budget_mib)"
    echo "docker_mem_total_mib: $mem_total"
    echo "gate_memory_budget_mib: $budget (sum JVM limits + postgres + margin)"
    if [[ "$mem_total" -gt 0 && "$mem_total" -lt "$budget" ]]; then
      echo "AMBER: docker MemTotal ${mem_total} MiB < gate budget ${budget} MiB"
      verdict=AMBER
      note="AMBER: MemTotal ${mem_total}MiB < budget ${budget}MiB"
    else
      echo "RAM budget OK"
    fi
    command -v docker >/dev/null || { echo FATAL: docker missing; code=1; verdict=RED; }
    command -v node >/dev/null || { echo FATAL: node missing; code=1; verdict=RED; }
    echo "phase_order: unit-tests before stack (intentional RAM headroom)"
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  [[ "$code" -ne 0 ]] && verdict=RED
  cp "$log" "$RUN_DIR/preflight.txt"
  record_phase preflight "$verdict" "$code" "$(duration_ms "$start" "$end")" "$log" "-" "$note"
  return "$code"
}

run_phase_unit_tests() {
  local log="$RUN_DIR/phases/unit-tests.log" start end code=0
  start="$(now_ms)"
  {
    echo "=== unit tests (no stack) ==="
    export MAVEN_OPTS="${MAVEN_OPTS:--Xmx1024m}"
    ./scripts/build.sh
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  record_phase unit-tests "$([[ $code -eq 0 ]] && echo GREEN || echo RED)" "$code" \
    "$(duration_ms "$start" "$end")" "$log" "$(parse_surefire_counts "$log")" "build.sh before stack"
  return "$code"
}

ha_compose_retry() {
  # Only for #747 encryption_key_pkey race. Bound compose up with timeout.
  COMPOSE_RETRY_FIRED=1
  local compose i h1 h2
  compose="$(ha_compose_cmd)"
  for i in $(seq 1 24); do
    h1="$(docker inspect -f '{{.State.Health.Status}}' ezkey-admin-api-1 2>/dev/null || echo missing)"
    h2="$(docker inspect -f '{{.State.Health.Status}}' ezkey-admin-api-2 2>/dev/null || echo missing)"
    echo "HA compose retry $i (#747): admin-1=$h1 admin-2=$h2"
    if [[ "$h1" == "healthy" && "$h2" == "healthy" ]]; then
      echo "Resuming compose up -d (timeout 180s) — AMBER #747"
      timeout 180 env SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-docker,docker-dev,docker-test}" \
        EZKEY_ENABLE_JAVA_MELODY=true $compose up -d || return 1
      return 0
    fi
    sleep 5
  done
  return 1
}

run_phase_clean_start() {
  local log="$RUN_DIR/phases/clean-start.log" start end code=0 verdict=GREEN
  local note="HA + JavaMelody + gate memory overlay"
  COMPOSE_RETRY_FIRED=0
  start="$(now_ms)"
  {
    echo "=== clean-start HA + JavaMelody + quality-gate memory overlay ==="
    export EZKEY_ENABLE_JAVA_MELODY=true
    export EZKEY_COMPOSE_EXTRA_FILES="$GATE_OVERLAY"
    set +e
    ./ezkey-tests/clean-start.sh --ha --with-java-melody
    local cs=$?
    if [[ "$cs" -ne 0 ]]; then
      local keyset=0
      # Log file is mid-write under this redirection; also inspect admin replica logs.
      docker logs ezkey-admin-api-1 2>&1 | tail -400 | grep -q 'ezkey_encryption_key_pkey' && keyset=1
      docker logs ezkey-admin-api-2 2>&1 | tail -400 | grep -q 'ezkey_encryption_key_pkey' && keyset=1
      if [[ "$keyset" -eq 1 ]]; then
        echo "Detected #747 keyset race — temporary compose retry"
        ha_compose_retry || true
      else
        echo "clean-start failed without ezkey_encryption_key_pkey — RED (no retry)"
        code=1
      fi
    fi
    set +e
    local elapsed=0
    while ! stack_up; do
      [[ $elapsed -ge 180 ]] && break
      sleep 5; elapsed=$((elapsed + 5))
    done
    echo "compose_retry_fired=${COMPOSE_RETRY_FIRED}"
    docker ps --format 'table {{.Names}}\t{{.Status}}'
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"

  if ! stack_up || ! check_replicas clean-start; then
    code=1
    verdict=RED
    note="stack/replicas unhealthy after clean-start"
  elif [[ "$COMPOSE_RETRY_FIRED" -eq 1 ]]; then
    code=0
    verdict=AMBER
    note="AMBER: compose retry fired — product bug #747"
    check_replicas clean-start || true
  else
    code=0
    verdict=GREEN
    note="HA + JM + memory overlay (no #747 retry)"
  fi
  # Re-check keyset evidence into log for allowlist context
  export COMPOSE_RETRY_FIRED
  record_phase clean-start "$verdict" "$code" "$(duration_ms "$start" "$end")" "$log" "-" "$note"
  return "$code"
}

run_phase_post_clean_health() {
  local log="$RUN_DIR/phases/post-clean-health.log" start end code=0
  start="$(now_ms)"
  {
    echo "=== post-clean-start health snapshot ==="
    COMPOSE_RETRY_FIRED="$COMPOSE_RETRY_FIRED" \
      ./scripts/stack-health-check.sh --output-dir "$RUN_DIR/health-post-clean"
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  local hv="-"
  [[ -f "$RUN_DIR/health-post-clean/health.json" ]] \
    && hv="$(node -e "console.log(JSON.parse(require('fs').readFileSync('$RUN_DIR/health-post-clean/health.json','utf8')).verdict)")"
  local verdict=GREEN
  [[ "$hv" == "AMBER" ]] && verdict=AMBER
  [[ "$hv" == "RED" || "$code" -ne 0 ]] && { verdict=RED; code=1; }
  record_phase post-clean-health "$verdict" "$code" "$(duration_ms "$start" "$end")" "$log" "$hv" "snapshot after clean-start"
  return "$code"
}

run_maven_phase() {
  local name="$1" cmd="$2" note="$3"
  local log="$RUN_DIR/phases/${name}.log" start end code=0
  start="$(now_ms)"
  {
    echo "=== $name ==="
    export MAVEN_OPTS="${MAVEN_OPTS:--Xmx1024m}"
    eval "$cmd"
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  record_phase "$name" "$([[ $code -eq 0 ]] && echo GREEN || echo RED)" "$code" \
    "$(duration_ms "$start" "$end")" "$log" "$(parse_surefire_counts "$log")" "$note"
  return "$code"
}

run_phase_playwright() {
  local log="$RUN_DIR/phases/playwright.log" start end code=0 results="$RUN_DIR/playwright-results"
  mkdir -p "$results"
  start="$(now_ms)"
  {
    echo "=== Playwright ==="
    if command -v lsof >/dev/null 2>&1; then
      local pids; pids="$(lsof -t -i:4173 2>/dev/null || true)"
      [[ -n "$pids" ]] && kill $pids 2>/dev/null || true
    fi
    EZKEY_BROWSER_TEST_RESULTS_DIR="$results" ./ezkey-admin-ui/scripts/run-ui-tests.sh
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  local counts="-"
  grep -Eq '[0-9]+ passed' "$log" 2>/dev/null \
    && counts="$(grep -E '[0-9]+ (passed|failed|skipped|flaky)' "$log" | tail -3 | tr '\n' ' ')"
  record_phase playwright "$([[ $code -eq 0 ]] && echo GREEN || echo RED)" "$code" \
    "$(duration_ms "$start" "$end")" "$log" "$counts" "Admin UI e2e"
  return "$code"
}

run_phase_churn_init() {
  local log="$RUN_DIR/phases/churn-init.log" start end code=0
  start="$(now_ms)"
  { ./ezkey-tests/scripts/run-operational-churn.sh --init; } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  record_phase churn-init "$([[ $code -eq 0 ]] && echo GREEN || echo RED)" "$code" \
    "$(duration_ms "$start" "$end")" "$log" "-" "peer Global Admin"
  return "$code"
}

run_phase_churn() {
  local log="$RUN_DIR/phases/churn.log" start end code=0
  local log1="$RUN_DIR/churn/churn-a.log" log2="$RUN_DIR/churn/churn-b.log"
  start="$(now_ms)"
  {
    ./ezkey-tests/scripts/run-operational-churn.sh --minutes "$CHURN_MINUTES" --seed 41 --log-file "$log1" &
    local pid1=$!
    ./ezkey-tests/scripts/run-operational-churn.sh --minutes "$CHURN_MINUTES" --seed 42 --log-file "$log2" &
    local pid2=$!
    CHURN_PIDS="$pid1 $pid2"
    local c1=0 c2=0
    wait "$pid1" || c1=$?
    wait "$pid2" || c2=$?
    CHURN_PIDS=""
    echo "churn-a=$c1 churn-b=$c2"
    [[ "$c1" -ne 0 || "$c2" -ne 0 ]] && code=1
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  record_phase churn "$([[ $code -eq 0 ]] && echo GREEN || echo RED)" "$code" \
    "$(duration_ms "$start" "$end")" "$log" "-" "2 shells ${CHURN_MINUTES}m"
  return "$code"
}

run_phase_health() {
  local log="$RUN_DIR/phases/health.log" start end code=0
  start="$(now_ms)"
  {
    COMPOSE_RETRY_FIRED="$COMPOSE_RETRY_FIRED" \
      ./scripts/stack-health-check.sh --output-dir "$RUN_DIR/health"
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  local hv="-"
  [[ -f "$RUN_DIR/health/health.json" ]] \
    && hv="$(node -e "console.log(JSON.parse(require('fs').readFileSync('$RUN_DIR/health/health.json','utf8')).verdict)")"
  local verdict=GREEN
  [[ "$hv" == "AMBER" ]] && verdict=AMBER
  [[ "$hv" == "RED" || "$code" -ne 0 ]] && { verdict=RED; code=1; }
  record_phase health "$verdict" "$code" "$(duration_ms "$start" "$end")" "$log" "$hv" "JavaMelody + logs"
  return "$code"
}

write_report() {
  node "$SCRIPT_DIR/quality-gate-report.mjs" \
    --run-dir "$RUN_DIR" \
    --tip "$FULL_SHA" \
    --short "$SHORT_SHA" \
    --total-ms "$TOTAL_MS" \
    --churn-min "$CHURN_MINUTES" \
    --red "$OVERALL_RED" \
    --amber "$OVERALL_AMBER" \
    --ha-invalid "$HA_INVALID" \
    --compose-retry "$COMPOSE_RETRY_FIRED"
}

GATE_START="$(now_ms)"

for phase in $PHASES; do
  if should_skip "$phase"; then
    record_phase "$phase" "SKIP" "0" "0" "-" "-" "skipped by flag"
    continue
  fi

  if [[ "$phase" != "preflight" && "$phase" != "unit-tests" && "$phase" != "clean-start" ]]; then
    if ! stack_up; then
      echo "[$KEYWORD] stack down — abort before $phase" | tee -a "$RUN_DIR/phases/${phase}.log"
      record_phase "$phase" "RED" "1" "0" "$RUN_DIR/phases/${phase}.log" "-" "stack down"
      STACK_DOWN=1; OVERALL_RED=1; break
    fi
  fi

  # Memory sampler for stack-bearing phases (not preflight/unit-tests).
  if [[ "$phase" != "preflight" && "$phase" != "unit-tests" ]]; then
    start_mem_sampler "$phase"
  fi

  case "$phase" in
    preflight) run_phase_preflight || true ;;
    unit-tests) run_phase_unit_tests || true ;;
    clean-start) run_phase_clean_start || true ;;
    post-clean-health) run_phase_post_clean_health || true ;;
    functional-tests) run_maven_phase functional-tests "mvn test -pl ezkey-tests -P all-tests" "all-tests" || true ;;
    elective-tests) run_maven_phase elective-tests "./ezkey-tests/scripts/run-elective-tests.sh" "elective" || true ;;
    playwright) run_phase_playwright || true ;;
    churn-init) run_phase_churn_init || true ;;
    churn) run_phase_churn || true ;;
    health) run_phase_health || true ;;
  esac

  if [[ "$phase" != "preflight" && "$phase" != "unit-tests" ]]; then
    capture_nmt "$phase"
    stop_mem_sampler
  fi

  last_verdict="$(tail -1 "$PHASE_TSV" | cut -f2)"
  if [[ "$last_verdict" == "RED" ]]; then
    OVERALL_RED=1
    if [[ "$phase" == "preflight" || "$phase" == "unit-tests" || "$phase" == "clean-start" ]]; then
      echo "[$KEYWORD] fail-fast: $phase RED — stopping"
      break
    fi
  elif [[ "$last_verdict" == "AMBER" ]]; then
    OVERALL_AMBER=1
  fi

  # Replica liveness after stack-bearing phases
  if [[ "$phase" != "preflight" && "$phase" != "unit-tests" ]]; then
    if ! check_replicas "$phase"; then
      # Downgrade last phase to AMBER if it was GREEN (HA invalid)
      if [[ "$last_verdict" == "GREEN" ]]; then
        OVERALL_AMBER=1
        # rewrite last line note
        tmp="$(mktemp)"
        head -n -1 "$PHASE_TSV" >"$tmp"
        last="$(tail -1 "$PHASE_TSV")"
        name="$(echo "$last" | cut -f1)"
        printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
          "$name" "AMBER" "$(echo "$last" | cut -f3)" "$(echo "$last" | cut -f4)" \
          "$(echo "$last" | cut -f5)" "$(echo "$last" | cut -f6)" \
          "HA invalid: replica dead/unhealthy after phase" >>"$tmp"
        mv "$tmp" "$PHASE_TSV"
        echo "[$KEYWORD] phase=$name downgraded GREEN→AMBER (HA invalid)"
      fi
    fi
  fi
done

GATE_END="$(now_ms)"
TOTAL_MS="$(duration_ms "$GATE_START" "$GATE_END")"
trap - INT TERM EXIT
cleanup_churn

write_report
echo "[$KEYWORD] artifacts under $RUN_DIR"
OVERALL="$(node -e "console.log(JSON.parse(require('fs').readFileSync('$RUN_DIR/summary.json','utf8')).overallVerdict)")"
echo "OVERALL: $OVERALL"

if [[ "$OVERALL_RED" -ne 0 ]]; then exit 1; fi
if [[ "$OVERALL_AMBER" -ne 0 || "$HA_INVALID" -ne 0 ]]; then exit 3; fi
exit 0
