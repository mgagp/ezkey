#!/usr/bin/env bash
#
# quality-gate — on-demand full-suite gate.
# Requires Bash >= 4 (associative arrays / declare -A). On macOS: brew install bash,
# then launch via #!/usr/bin/env bash or `bash` from PATH — not /bin/bash (system 3.2).
# Cloud agent tooling (Linux, Git Bash, Homebrew bash on macOS).
#
# Phase order (unit tests before stack — memory headroom on ~16Gi VMs):
#   preflight → unit-tests → clean-start HA+JM+prod-safe (+gate memory overlay)
#   → post-clean-health → functional → elective → docker-start restore
#   → Playwright → churn-init → 2× churn → health → REPORT.md + summary.json
#
# Exit codes:
#   0 = GO
#   1 = NO-GO (any RED)
#   3 = GO with reservations (AMBER only; HA valid; no skipped phases)
#   4 = INCONCLUSIVE (HA invalid and/or any skipped phase)
#   2 = usage / bad args (also Bash < 4)
# API replica check covers the 7 HA API JVMs (admin/auth/integration ×2 + crypto).
# demo-device / demo-app-acme are excluded: not LB replicas; mem-sampled only.
#
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

# Bash >= 4 guard (must parse under Bash 3.2). Same check auto-runs when the HA
# lib is sourced alone; QUALITY_GATE_BASH_MAJOR overrides the detected major for tests.
# shellcheck source=lib/quality-gate-ha.sh
source "$SCRIPT_DIR/lib/quality-gate-ha.sh"

export LC_ALL="${LC_ALL:-C.UTF-8}"
export LANG="${LANG:-C.UTF-8}"

# Inherited docker-log windows must not silently shrink a full-gate scan.
# Prefer unset over recording — operators who need a window pass --since/--until
# on stack-health-check directly.
unset QUALITY_GATE_LOG_SINCE QUALITY_GATE_LOG_UNTIL DOCKER_LOGS_SINCE DOCKER_LOGS_UNTIL

KEYWORD="quality-gate"
CHURN_MINUTES=5
NO_CLEAN_START=0
SKIP_PHASES=""
ANY_SKIPPED=0
COMPOSE_RETRY_FIRED=0
HA_INVALID=0
OVERALL_RED=0
OVERALL_AMBER=0
STACK_DOWN=0
CHURN_PIDS=""
MEM_SAMPLER_PID=""
SHEDLOCK_KILL_EXPECTED=0
SHEDLOCK_KILL_MISSING=0
SHEDLOCK_RESTORE_OK=0

GATE_OVERLAY="$REPO_ROOT/docker/docker-compose.ha.quality-gate.yml"
PHASES="preflight unit-tests clean-start post-clean-health functional-tests elective-tests playwright churn-init churn health"

HA_REPLICAS="ezkey-admin-api-1 ezkey-admin-api-2 ezkey-auth-api-1 ezkey-auth-api-2 ezkey-integration-api-1 ezkey-integration-api-2 ezkey-crypto-api-ha"
MEM_SAMPLE_CONTAINERS="$HA_REPLICAS ezkey-demo-device-ha ezkey-demo-app-acme-ha"

usage() {
  cat <<'EOF'
Usage: ./scripts/quality-gate.sh [options]

  --churn-minutes N   Minutes per concurrent churn shell (default: 5)
  --skip PHASE        Skip a phase (repeatable)
  --no-clean-start    Reuse running stack (no compose down -v); skip
                      unit-tests, clean-start, post-clean-health (exit 4)
  -h, --help          Show this help

Docs: product-docs/global/hygiene/quality-gate/README.md
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --churn-minutes) CHURN_MINUTES="${2:-}"; shift 2 ;;
    --skip) SKIP_PHASES="${SKIP_PHASES} ${2:-}"; shift 2 ;;
    # Skip unit-tests too: stack stays up and gate RAM headroom is tight (#915).
    --no-clean-start) NO_CLEAN_START=1; SKIP_PHASES="${SKIP_PHASES} unit-tests clean-start post-clean-health"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

SHORT_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
FULL_SHA="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
UTC_STAMP="$(date -u +"%Y%m%dT%H%M%SZ")"
RUN_DIR="$REPO_ROOT/logs/quality-gate/${UTC_STAMP}-${SHORT_SHA}"
mkdir -p "$RUN_DIR/phases" "$RUN_DIR/churn" "$RUN_DIR/health"
PHASE_TSV="$RUN_DIR/phases.tsv"
: >"$PHASE_TSV"
PHASE_WINDOWS_TSV="$RUN_DIR/phase-windows.tsv"
printf 'phase\tstart_iso\tend_iso\n' >"$PHASE_WINDOWS_TSV"
REPLICAS_TSV="$RUN_DIR/replicas.tsv"
: >"$REPLICAS_TSV"
RESTARTS_TSV="$RUN_DIR/restarts.tsv"
printf 'phase\tcontainer\trestarts\tdelta\n' >"$RESTARTS_TSV"
MEM_SAMPLES_TSV="$RUN_DIR/mem-samples.tsv"
printf 'utc\tphase\tcontainer\tmem_usage\tmem_limit\tmem_perc\n' >"$MEM_SAMPLES_TSV"
GATE_STARTED_AT="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
ELECTIVE_STARTED_AT=""
declare -A PREV_RESTARTS=()
SHEDLOCK_KILLED=""
SHEDLOCK_KILL_MISSING=0
SHEDLOCK_PROOF_PATH=""
SHEDLOCK_PROOF_AT=""

echo "[$KEYWORD] run dir: $RUN_DIR"
echo "[$KEYWORD] tip: $FULL_SHA"

iso_now() { date -u +"%Y-%m-%dT%H:%M:%SZ"; }

# Emission-time allowlist windows (phase name → [start,end] ISO).
record_phase_window() {
  printf '%s\t%s\t%s\n' "$1" "$2" "$3" >>"$PHASE_WINDOWS_TSV"
}

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

cleanup_churn() {
  stop_mem_sampler
  if [[ -n "$CHURN_PIDS" ]]; then
    for p in $CHURN_PIDS; do
      kill "$p" 2>/dev/null || true
    done
  fi
}
trap cleanup_churn INT TERM EXIT

# now_ms — defined in lib/quality-gate-ha.sh (portable; BSD date safe).
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

# Health scans: pass phase windows so #750 matches emission time, not scan phase.
health_env() {
  export QUALITY_GATE_PHASE_WINDOWS="$PHASE_WINDOWS_TSV"
  export COMPOSE_RETRY_FIRED
}

stack_up() {
  curl -sf http://127.0.0.1:9080/api/v1/public/instance-info >/dev/null 2>&1 \
    && curl -sf http://127.0.0.1:8084/actuator/health >/dev/null 2>&1
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
    # --no-clean-start must not destroy volumes (#915 defect 3).
    if [[ "$NO_CLEAN_START" -eq 1 ]]; then
      echo "reusing running stack"
      note="tip + docker MemTotal vs gate memory budget; reusing running stack"
    else
      stop_stacks
    fi
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
    if [[ "$NO_CLEAN_START" -eq 1 ]]; then
      echo "phase_order: unit-tests skipped (--no-clean-start; stack-up RAM)"
    else
      echo "phase_order: unit-tests before stack (intentional RAM headroom)"
    fi
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
  # Only for #747 encryption_key_pkey race. Bound compose up when timeout/gtimeout
  # exists (stock macOS has neither — #915); else warn and run unbound.
  COMPOSE_RETRY_FIRED=1
  local compose i h1 h2
  compose="$(ha_compose_cmd)"
  for i in $(seq 1 24); do
    h1="$(docker inspect -f '{{.State.Health.Status}}' ezkey-admin-api-1 2>/dev/null || echo missing)"
    h2="$(docker inspect -f '{{.State.Health.Status}}' ezkey-admin-api-2 2>/dev/null || echo missing)"
    echo "HA compose retry $i (#747): admin-1=$h1 admin-2=$h2"
    if [[ "$h1" == "healthy" && "$h2" == "healthy" ]]; then
      echo "Resuming compose up -d (timeout 180s) — AMBER #747"
      # Match --prod-safe clean-start (docker only; rate limits on). Do not
      # re-apply docker-test or the #912 C1 proof regresses after a #747 retry.
      # shellcheck disable=SC2086
      quality_gate_run_bounded 180 env SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-docker}" \
        EZKEY_ENABLE_JAVA_MELODY=true $compose up -d || return 1
      return 0
    fi
    sleep 5
  done
  return 1
}

run_phase_clean_start() {
  local log="$RUN_DIR/phases/clean-start.log" start end code=0 verdict=GREEN
  local note="HA + JavaMelody + prod-safe + gate memory overlay"
  local phase_since clean_start_bin
  COMPOSE_RETRY_FIRED=0
  phase_since="$(iso_now)"
  start="$(now_ms)"
  {
    echo "=== clean-start HA + JavaMelody + prod-safe + quality-gate memory overlay ==="
    export EZKEY_ENABLE_JAVA_MELODY=true
    export EZKEY_COMPOSE_EXTRA_FILES="$GATE_OVERLAY"
    # --prod-safe → Spring profile docker only (rate limits on; #912 C1).
    # Export so #747 ha_compose_retry inherits the same profiles.
    export SPRING_PROFILES_ACTIVE=docker
    # PATH-first so quality-gate-ha.test.sh can prepend a recorder; else repo script.
    if command -v clean-start.sh >/dev/null 2>&1; then
      clean_start_bin="$(command -v clean-start.sh)"
    else
      clean_start_bin="./ezkey-tests/clean-start.sh"
    fi
    set +e
    "$clean_start_bin" --ha --with-java-melody --prod-safe
    local cs=$?
    if [[ "$cs" -ne 0 ]]; then
      # Phase log is mid-write under this redirection; inspect admin replica logs
      # since phase start (full --since window, no tail cap — #747 / macOS).
      if detect_encryption_key_pkey_race "$phase_since"; then
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

  local stack_ok=0 replicas_ok=0
  stack_up && stack_ok=1
  check_replicas clean-start && replicas_ok=1 || true
  verdict="$(classify_clean_start_verdict "$code" "$COMPOSE_RETRY_FIRED" "$stack_ok" "$replicas_ok")"
  case "$verdict" in
    AMBER)
      code=0
      note="AMBER: compose retry fired — product bug #747"
      ;;
    RED)
      code=1
      if [[ "$stack_ok" != "1" || "$replicas_ok" != "1" ]]; then
        note="stack/replicas unhealthy after clean-start"
      else
        note="clean-start failed without ezkey_encryption_key_pkey"
      fi
      ;;
    *)
      code=0
      note="HA + JM + prod-safe + memory overlay (no #747 retry)"
      ;;
  esac
  export COMPOSE_RETRY_FIRED
  record_phase clean-start "$verdict" "$code" "$(duration_ms "$start" "$end")" "$log" "-" "$note"
  return "$code"
}

run_phase_post_clean_health() {
  local log="$RUN_DIR/phases/post-clean-health.log" start end code=0
  start="$(now_ms)"
  {
    echo "=== post-clean-start health snapshot ==="
    health_env
    QUALITY_GATE_PHASE=post-clean-health \
      ./scripts/stack-health-check.sh --output-dir "$RUN_DIR/health-post-clean"
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  local hv="-"
  [[ -f "$RUN_DIR/health-post-clean/health.json" ]] \
    && hv="$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).verdict)' \
      "$RUN_DIR/health-post-clean/health.json")"
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
  local note="Admin UI e2e (Orval generate:api then Playwright)"
  local admin_ui_ready=1
  mkdir -p "$results"
  start="$(now_ms)"
  {
    echo "=== Playwright ==="
    if command -v lsof >/dev/null 2>&1; then
      local pids; pids="$(lsof -t -i:4173 2>/dev/null || true)"
      [[ -n "$pids" ]] && kill $pids 2>/dev/null || true
    fi
    # Orval client under src/generated/ is gitignored; only build:cloudflare
    # regenerates it. A stale local client caused false RED Playwright results
    # (#915 defect 6). Fail closed: never run UI tests after a failed generate.
    # Fresh worktree often has no ezkey-admin-ui/node_modules, so generate:api
    # fails with "orval: command not found" and Playwright goes RED in 0s (#915).
    admin_ui_ready=1
    if [[ ! -d ezkey-admin-ui/node_modules \
       || ! -e ezkey-admin-ui/node_modules/.bin/orval ]]; then
      echo "=== npm ci (Admin UI deps missing) ==="
      if ! (cd ezkey-admin-ui && npm ci); then
        echo "FATAL: npm ci failed in ezkey-admin-ui — refusing Playwright (deps unavailable) (#915)"
        note="npm ci failed; Playwright not run"
        admin_ui_ready=0
      fi
    fi
    if [[ "$admin_ui_ready" -eq 1 ]]; then
      echo "=== Orval generate:api ==="
      if (cd ezkey-admin-ui && npm run generate:api); then
        EZKEY_BROWSER_TEST_RESULTS_DIR="$results" ./ezkey-admin-ui/scripts/run-ui-tests.sh
      else
        echo "FATAL: npm run generate:api failed — refusing Playwright with a stale Orval client (#915)"
        note="Orval generate:api failed; Playwright not run"
        false
      fi
    else
      false
    fi
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  local counts="-"
  grep -Eq '[0-9]+ passed' "$log" 2>/dev/null \
    && counts="$(grep -E '[0-9]+ (passed|failed|skipped|flaky)' "$log" | tail -3 | tr '\n' ' ')"
  record_phase playwright "$([[ $code -eq 0 ]] && echo GREEN || echo RED)" "$code" \
    "$(duration_ms "$start" "$end")" "$log" "$counts" "$note"
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
    # Use if/fi — a failing [[ ]] as the last command would make the group exit 1
    # and the trailing `|| code=$?` would false-RED a successful churn.
    if [[ "$c1" -ne 0 || "$c2" -ne 0 ]]; then
      code=1
    fi
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
    health_env
    QUALITY_GATE_PHASE=health \
      ./scripts/stack-health-check.sh --output-dir "$RUN_DIR/health"
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  local hv="-"
  [[ -f "$RUN_DIR/health/health.json" ]] \
    && hv="$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).verdict)' \
      "$RUN_DIR/health/health.json")"
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
    --skipped "$ANY_SKIPPED" \
    --compose-retry "$COMPOSE_RETRY_FIRED" \
    --shedlock-restored "$SHEDLOCK_RESTORE_OK"
}

GATE_START="$(now_ms)"

for phase in $PHASES; do
  if should_skip "$phase"; then
    ANY_SKIPPED=1
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

  phase_start_iso="$(iso_now)"
  case "$phase" in
    preflight) run_phase_preflight || true ;;
    unit-tests) run_phase_unit_tests || true ;;
    clean-start) run_phase_clean_start || true ;;
    post-clean-health) run_phase_post_clean_health || true ;;
    functional-tests) run_maven_phase functional-tests "mvn test -pl ezkey-tests -P all-tests" "all-tests" || true ;;
    elective-tests)
      # Bound ShedLock die-event proof to the elective window (not GATE_STARTED_AT /
      # preflight stop_stacks dies for admin-api-1/2).
      ELECTIVE_STARTED_AT="$phase_start_iso"
      export ELECTIVE_STARTED_AT
      run_maven_phase elective-tests "./ezkey-tests/scripts/run-elective-tests.sh" "elective" || true
      ;;
    playwright) run_phase_playwright || true ;;
    churn-init) run_phase_churn_init || true ;;
    churn) run_phase_churn || true ;;
    health) run_phase_health || true ;;
  esac
  record_phase_window "$phase" "$phase_start_iso" "$(iso_now)"

  if [[ "$phase" != "preflight" && "$phase" != "unit-tests" ]]; then
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

  if [[ "$phase" == "elective-tests" ]]; then
    if restore_after_elective; then
      if [[ "$SHEDLOCK_KILL_EXPECTED" -eq 1 ]]; then
        rewrite_last_phase "" \
          "expected: ShedLockDistributedTest kill; docker-start restored; all replicas healthy"
      fi
    else
      if [[ "${SHEDLOCK_KILL_MISSING:-0}" -eq 1 ]]; then
        # Never rewrite an elective RED down to AMBER — only GREEN → AMBER.
        new_v="$(elective_kill_missing_phase_verdict "$last_verdict")"
        if [[ "$new_v" == "AMBER" && "$last_verdict" == "GREEN" ]]; then
          rewrite_last_phase "AMBER" \
            "expected ShedLock kill missing (failover skipped / unresolved container)"
          echo "[$KEYWORD] phase=elective-tests downgraded GREEN→AMBER (ShedLock kill missing)"
        else
          echo "[$KEYWORD] ShedLock kill missing; phase stays $last_verdict (no rewrite)"
        fi
      elif [[ "$last_verdict" == "GREEN" ]]; then
        rewrite_last_phase "AMBER" "HA invalid: restore after ShedLock/elective failed"
        echo "[$KEYWORD] phase=elective-tests downgraded GREEN→AMBER (restore failed)"
      fi
    fi
  elif [[ "$phase" != "preflight" && "$phase" != "unit-tests" ]]; then
    if ! check_replicas "$phase"; then
      if [[ "$last_verdict" == "GREEN" ]]; then
        OVERALL_AMBER=1
        rewrite_last_phase "AMBER" "HA invalid: replica dead/unhealthy or restart delta"
        echo "[$KEYWORD] phase=$phase downgraded GREEN→AMBER (HA invalid)"
      fi
    fi
  fi
done

# M1: OOM evidence covers every phase, not only health.
check_oom_evidence || true

GATE_END="$(now_ms)"
TOTAL_MS="$(duration_ms "$GATE_START" "$GATE_END")"
trap - INT TERM EXIT
cleanup_churn

write_report
echo "[$KEYWORD] artifacts under $RUN_DIR"
OVERALL="$(node -e 'console.log(JSON.parse(require("fs").readFileSync(process.argv[1],"utf8")).overallVerdict)' \
  "$RUN_DIR/summary.json")"
echo "OVERALL: $OVERALL"

# Exit-code table (see header + hygiene README):
#   0 GO · 1 NO-GO · 3 GO with reservations · 4 INCONCLUSIVE
if [[ "$OVERALL_RED" -ne 0 ]]; then exit 1; fi
if [[ "$HA_INVALID" -ne 0 || "$ANY_SKIPPED" -ne 0 ]]; then exit 4; fi
if [[ "$OVERALL_AMBER" -ne 0 ]]; then exit 3; fi
exit 0
