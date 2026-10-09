#!/usr/bin/env bash
#
# quality-gate — on-demand full-suite quality gate for Ezkey.
# Thin orchestrator: clean-start HA+JavaMelody → unit → functional → elective →
# Playwright → operational churn → stack health → REPORT.md + summary.json.
#
# Usage (Git Bash on Windows, Linux, or macOS), from repo root:
#   ./scripts/quality-gate.sh
#   ./scripts/quality-gate.sh --churn-minutes 5
#   ./scripts/quality-gate.sh --skip unit-tests --no-clean-start
#
# Keyword: quality-gate
# Outputs: logs/quality-gate/<UTC>-<shortsha>/ (gitignored)
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
# Space-delimited skip list (bash 3.2 / Git Bash portable — no associative arrays).
SKIP_PHASES=""

PHASES="preflight clean-start unit-tests functional-tests elective-tests playwright churn-init churn health"

usage() {
  cat <<'EOF'
Usage: ./scripts/quality-gate.sh [options]

  --churn-minutes N   Minutes per concurrent churn shell (default: 5)
  --skip PHASE        Skip a phase (repeatable). Phases:
                      preflight, clean-start, unit-tests, functional-tests,
                      elective-tests, playwright, churn-init, churn, health
  --no-clean-start    Alias for --skip clean-start (reuse running stack)
  -h, --help          Show this help

Keyword: quality-gate
Docs: product-docs/global/hygiene/quality-gate/README.md
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --churn-minutes)
      CHURN_MINUTES="${2:-}"
      shift 2
      ;;
    --skip)
      SKIP_PHASES="${SKIP_PHASES} ${2:-}"
      shift 2
      ;;
    --no-clean-start)
      NO_CLEAN_START=1
      SKIP_PHASES="${SKIP_PHASES} clean-start"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ "$NO_CLEAN_START" -eq 1 ]]; then
  SKIP_PHASES="${SKIP_PHASES} clean-start"
fi

SHORT_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
FULL_SHA="$(git rev-parse HEAD 2>/dev/null || echo unknown)"
UTC_STAMP="$(date -u +"%Y%m%dT%H%M%SZ")"
RUN_DIR="$REPO_ROOT/logs/quality-gate/${UTC_STAMP}-${SHORT_SHA}"
mkdir -p "$RUN_DIR/phases" "$RUN_DIR/churn" "$RUN_DIR/health"
PHASE_TSV="$RUN_DIR/phases.tsv"
: >"$PHASE_TSV"

echo "[$KEYWORD] run dir: $RUN_DIR"
echo "[$KEYWORD] tip: $FULL_SHA"
echo "[$KEYWORD] churn minutes: $CHURN_MINUTES"

now_ms() {
  date +%s%3N 2>/dev/null || python3 -c 'import time; print(int(time.time()*1000))'
}

duration_ms() {
  local start="$1" end="$2"
  echo $((end - start))
}

ms_to_human() {
  local ms="$1"
  local sec=$((ms / 1000))
  local min=$((sec / 60))
  sec=$((sec % 60))
  if [[ $min -gt 0 ]]; then
    printf '%dm%02ds' "$min" "$sec"
  else
    printf '%ds' "$sec"
  fi
}

parse_surefire_counts() {
  # Prints: run failed errors skipped
  local log="$1"
  local run=0 failed=0 errors=0 skipped=0
  local line
  # Prefer final "Tests run:" totals (last match wins across modules).
  while IFS= read -r line; do
    if [[ "$line" =~ Tests\ run:\ *([0-9]+),\ *Failures:\ *([0-9]+),\ *Errors:\ *([0-9]+),\ *Skipped:\ *([0-9]+) ]]; then
      run="${BASH_REMATCH[1]}"
      failed="${BASH_REMATCH[2]}"
      errors="${BASH_REMATCH[3]}"
      skipped="${BASH_REMATCH[4]}"
    fi
  done < <(grep -E 'Tests run:' "$log" 2>/dev/null || true)
  # Sum Results: lines when present (multi-module).
  local sum_run=0 sum_f=0 sum_e=0 sum_s=0 found=0
  while IFS= read -r line; do
    if [[ "$line" =~ Tests\ run:\ *([0-9]+),\ *Failures:\ *([0-9]+),\ *Errors:\ *([0-9]+),\ *Skipped:\ *([0-9]+) ]]; then
      sum_run=$((sum_run + BASH_REMATCH[1]))
      sum_f=$((sum_f + BASH_REMATCH[2]))
      sum_e=$((sum_e + BASH_REMATCH[3]))
      sum_s=$((sum_s + BASH_REMATCH[4]))
      found=1
    fi
  done < <(grep -E 'Tests run:.*, Failures:.*, Errors:.*, Skipped:' "$log" 2>/dev/null || true)
  if [[ $found -eq 1 && $sum_run -ge $run ]]; then
    echo "$sum_run $sum_f $sum_e $sum_s"
  else
    echo "$run $failed $errors $skipped"
  fi
}

phase_verdict_from_exit() {
  local code="$1"
  if [[ "$code" -eq 0 ]]; then
    echo GREEN
  else
    echo RED
  fi
}

record_phase() {
  local name="$1" verdict="$2" exit_code="$3" duration="$4" log_path="$5" counts="$6" note="$7"
  printf '%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$name" "$verdict" "$exit_code" "$duration" "$log_path" "$counts" "$note" >>"$PHASE_TSV"
  echo "[$KEYWORD] phase=$name verdict=$verdict exit=$exit_code duration=$(ms_to_human "$duration") counts=$counts"
}

should_skip() {
  local name="$1" p
  for p in $SKIP_PHASES; do
    if [[ "$p" == "$name" ]]; then
      return 0
    fi
  done
  return 1
}

stack_up() {
  # Actuator is on management ports (HAProxy API ports return 404 for /actuator/*).
  # Use a public Admin API path + Demo Device health — works for HA and non-HA.
  curl -sf http://localhost:9080/api/v1/public/instance-info >/dev/null 2>&1 \
    && curl -sf http://localhost:8083/actuator/health >/dev/null 2>&1
}

run_phase_preflight() {
  local log="$RUN_DIR/phases/preflight.log"
  local start end code=0
  start="$(now_ms)"
  {
    echo "=== preflight ==="
    echo "date_utc: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo "date_toronto: $(TZ=America/Toronto date +"%Y-%m-%d %H:%M:%S %Z")"
    echo "git_sha: $FULL_SHA"
    echo "git_short: $SHORT_SHA"
    echo "git_describe: $(git describe --always --dirty 2>/dev/null || true)"
    echo "cpus: $(nproc 2>/dev/null || sysctl -n hw.ncpu 2>/dev/null || echo unknown)"
    echo "memory:"
    free -h 2>/dev/null || true
    echo "disk:"
    df -h / 2>/dev/null || df -h . 2>/dev/null || true
    echo "docker: $(docker --version 2>/dev/null || echo missing)"
    echo "compose: $(docker compose version 2>/dev/null || echo missing)"
    echo "java: $(java -version 2>&1 | head -1)"
    echo "maven: $(mvn -version 2>&1 | head -1)"
    echo "node: $(node -v 2>/dev/null || echo missing)"
    echo "JAVA_HOME=${JAVA_HOME:-}"
    if ! command -v docker >/dev/null 2>&1; then
      echo "FATAL: docker missing"
      code=1
    fi
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  # Snapshot tip metadata for the report header.
  cp "$log" "$RUN_DIR/preflight.txt"
  record_phase preflight "$(phase_verdict_from_exit "$code")" "$code" "$(duration_ms "$start" "$end")" "$log" "-" "resources + tip SHA"
  return "$code"
}

ha_compose_retry() {
  # Known HA race: concurrent admin-api replicas can fail once on encryption-key
  # PK sync, restart, then become healthy while `compose up` already aborted.
  # Smallest recovery: wait for both admin replicas, then `compose up -d` again.
  local compose="docker compose -f docker/docker-compose.ha.yml"
  if [[ -f docker/docker-compose.ha.docker-dev.yml ]]; then
    compose="$compose -f docker/docker-compose.ha.docker-dev.yml"
  fi
  if [[ "${EZKEY_ENABLE_JAVA_MELODY:-}" == "1" || "${EZKEY_ENABLE_JAVA_MELODY:-}" == "true" ]]; then
    compose="$compose -f docker/docker-compose.ha.javamelody.yml"
  fi
  local i h1 h2
  for i in $(seq 1 24); do
    h1="$(docker inspect -f '{{.State.Health.Status}}' ezkey-admin-api-1 2>/dev/null || echo missing)"
    h2="$(docker inspect -f '{{.State.Health.Status}}' ezkey-admin-api-2 2>/dev/null || echo missing)"
    echo "HA compose retry wait $i: admin-api-1=$h1 admin-api-2=$h2"
    if [[ "$h1" == "healthy" && "$h2" == "healthy" ]]; then
      echo "Both admin replicas healthy — resuming compose up -d"
      SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-docker,docker-dev,docker-test}" \
        EZKEY_ENABLE_JAVA_MELODY="${EZKEY_ENABLE_JAVA_MELODY:-true}" \
        $compose up -d
      return $?
    fi
    sleep 5
  done
  echo "HA compose retry: admin replicas did not become healthy in time"
  return 1
}

run_phase_clean_start() {
  local log="$RUN_DIR/phases/clean-start.log"
  local start end code=0
  start="$(now_ms)"
  {
    echo "=== clean-start HA + JavaMelody ==="
    echo "Building and starting from tip $FULL_SHA"
    export EZKEY_ENABLE_JAVA_MELODY=true
    if ! ./ezkey-tests/clean-start.sh --ha --with-java-melody; then
      echo "clean-start returned non-zero — attempting HA compose retry (admin keyset race)"
      ha_compose_retry || true
    fi
    # start-ha waits are skipped when compose aborted early; ensure LB ports respond.
    local elapsed=0
    while ! stack_up; do
      if [[ $elapsed -ge 180 ]]; then
        echo "Stack still not healthy after 180s"
        break
      fi
      sleep 5
      elapsed=$((elapsed + 5))
    done
    if curl -sf http://localhost:8088/ >/dev/null 2>&1; then
      echo "JavaMelody collector: OK"
    else
      echo "WARNING: JavaMelody collector not responding on :8088"
    fi
    echo "--- container list ---"
    docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}\t{{.ID}}'
    echo "--- image IDs (ezkey) ---"
    docker images --format '{{.Repository}}:{{.Tag}}\t{{.ID}}\t{{.CreatedSince}}' | grep -E 'ezkey|REPOSITORY' || docker images
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}\t{{.ID}}' >"$RUN_DIR/containers.txt" 2>/dev/null || true
  docker images --format '{{.Repository}}:{{.Tag}}\t{{.ID}}\t{{.CreatedSince}}' >"$RUN_DIR/images.txt" 2>/dev/null || true
  if ! stack_up; then
    code=1
    echo "Stack health check failed after clean-start" >>"$log"
  else
    code=0
  fi
  record_phase clean-start "$(phase_verdict_from_exit "$code")" "$code" "$(duration_ms "$start" "$end")" "$log" "-" "HA + JavaMelody"
  return "$code"
}

run_phase_unit_tests() {
  local log="$RUN_DIR/phases/unit-tests.log"
  local start end code=0 counts
  start="$(now_ms)"
  {
    echo "=== unit tests: ./scripts/build.sh ==="
    ./scripts/build.sh
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  counts="$(parse_surefire_counts "$log")"
  record_phase unit-tests "$(phase_verdict_from_exit "$code")" "$code" "$(duration_ms "$start" "$end")" "$log" "$counts" "build.sh baseline"
  return "$code"
}

run_phase_functional_tests() {
  local log="$RUN_DIR/phases/functional-tests.log"
  local start end code=0 counts
  start="$(now_ms)"
  {
    echo "=== functional: mvn test -pl ezkey-tests -P all-tests ==="
    mvn test -pl ezkey-tests -P all-tests
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  counts="$(parse_surefire_counts "$log")"
  record_phase functional-tests "$(phase_verdict_from_exit "$code")" "$code" "$(duration_ms "$start" "$end")" "$log" "$counts" "profile all-tests"
  return "$code"
}

run_phase_elective_tests() {
  local log="$RUN_DIR/phases/elective-tests.log"
  local start end code=0 counts
  start="$(now_ms)"
  {
    echo "=== elective: ezkey-tests/scripts/run-elective-tests.sh ==="
    ./ezkey-tests/scripts/run-elective-tests.sh
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  counts="$(parse_surefire_counts "$log")"
  record_phase elective-tests "$(phase_verdict_from_exit "$code")" "$code" "$(duration_ms "$start" "$end")" "$log" "$counts" "elective profile"
  return "$code"
}

run_phase_playwright() {
  local log="$RUN_DIR/phases/playwright.log"
  local start end code=0
  local results="$RUN_DIR/playwright-results"
  mkdir -p "$results"
  start="$(now_ms)"
  {
    echo "=== Playwright: ezkey-admin-ui/scripts/run-ui-tests.sh ==="
    echo "Against running HA stack Demo Device :8083; local Vite preview on :4173"
    # Prefer host runner: Playwright starts its own webServer on 4173.
    # Free a stale Vite on 4173 if present (do not kill unrelated stack).
    if command -v lsof >/dev/null 2>&1; then
      local pids
      pids="$(lsof -t -i:4173 2>/dev/null || true)"
      if [[ -n "$pids" ]]; then
        echo "Freeing port 4173: $pids"
        # shellcheck disable=SC2086
        kill $pids 2>/dev/null || true
        sleep 1
      fi
    fi
    EZKEY_BROWSER_TEST_RESULTS_DIR="$results" \
      ./ezkey-admin-ui/scripts/run-ui-tests.sh
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  local counts="-"
  if grep -Eq '[0-9]+ passed' "$log" 2>/dev/null; then
    counts="$(grep -E '[0-9]+ (passed|failed|skipped|flaky)' "$log" | tail -5 | tr '\n' ' ' | sed 's/[[:space:]]\+/ /g')"
  fi
  record_phase playwright "$(phase_verdict_from_exit "$code")" "$code" "$(duration_ms "$start" "$end")" "$log" "$counts" "Admin UI e2e"
  return "$code"
}

run_phase_churn_init() {
  local log="$RUN_DIR/phases/churn-init.log"
  local start end code=0
  start="$(now_ms)"
  {
    echo "=== churn init ==="
    ./ezkey-tests/scripts/run-operational-churn.sh --init
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  record_phase churn-init "$(phase_verdict_from_exit "$code")" "$code" "$(duration_ms "$start" "$end")" "$log" "-" "peer Global Admin"
  return "$code"
}

run_phase_churn() {
  local log="$RUN_DIR/phases/churn.log"
  local start end code=0
  local log1="$RUN_DIR/churn/churn-a.log"
  local log2="$RUN_DIR/churn/churn-b.log"
  start="$(now_ms)"
  {
    echo "=== operational churn: 2× ${CHURN_MINUTES} min (seeds 41 and 42) ==="
    ./ezkey-tests/scripts/run-operational-churn.sh \
      --minutes "$CHURN_MINUTES" --seed 41 --log-file "$log1" &
    local pid1=$!
    ./ezkey-tests/scripts/run-operational-churn.sh \
      --minutes "$CHURN_MINUTES" --seed 42 --log-file "$log2" &
    local pid2=$!
    local c1=0 c2=0
    wait "$pid1" || c1=$?
    wait "$pid2" || c2=$?
    echo "churn-a exit=$c1 log=$log1"
    echo "churn-b exit=$c2 log=$log2"
    if [[ "$c1" -ne 0 || "$c2" -ne 0 ]]; then
      code=1
    fi
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  record_phase churn "$(phase_verdict_from_exit "$code")" "$code" "$(duration_ms "$start" "$end")" "$log" "-" "2 concurrent shells ${CHURN_MINUTES}m"
  return "$code"
}

run_phase_health() {
  local log="$RUN_DIR/phases/health.log"
  local start end code=0
  start="$(now_ms)"
  {
    echo "=== stack-health-check ==="
    ./scripts/stack-health-check.sh --output-dir "$RUN_DIR/health"
  } >"$log" 2>&1 || code=$?
  end="$(now_ms)"
  local counts="-"
  if [[ -f "$RUN_DIR/health/health.json" ]]; then
    counts="$(python3 -c "import json;print(json.load(open('$RUN_DIR/health/health.json'))['verdict'])" 2>/dev/null || echo -)"
  fi
  # Health AMBER is not a RED phase for the orchestrator exit, but recorded.
  local verdict
  verdict="$(phase_verdict_from_exit "$code")"
  if [[ "$code" -eq 0 && "$counts" == "AMBER" ]]; then
    verdict=AMBER
  elif [[ "$counts" == "RED" ]]; then
    verdict=RED
    code=1
  fi
  record_phase health "$verdict" "$code" "$(duration_ms "$start" "$end")" "$log" "$counts" "JavaMelody + container logs"
  return "$code"
}

GATE_START="$(now_ms)"
STACK_DOWN=0
OVERALL_RED=0
OVERALL_AMBER=0

for phase in $PHASES; do
  if should_skip "$phase"; then
    record_phase "$phase" "SKIP" "0" "0" "-" "-" "skipped by flag"
    continue
  fi

  if [[ "$phase" != "preflight" && "$phase" != "clean-start" && "$phase" != "unit-tests" ]]; then
    if ! stack_up; then
      echo "[$KEYWORD] stack is down — stopping before phase=$phase" | tee -a "$RUN_DIR/phases/${phase}.log"
      record_phase "$phase" "RED" "1" "0" "$RUN_DIR/phases/${phase}.log" "-" "stack down; aborted"
      STACK_DOWN=1
      OVERALL_RED=1
      break
    fi
  fi

  case "$phase" in
    preflight) run_phase_preflight || true ;;
    clean-start) run_phase_clean_start || true ;;
    unit-tests) run_phase_unit_tests || true ;;
    functional-tests) run_phase_functional_tests || true ;;
    elective-tests) run_phase_elective_tests || true ;;
    playwright) run_phase_playwright || true ;;
    churn-init) run_phase_churn_init || true ;;
    churn) run_phase_churn || true ;;
    health) run_phase_health || true ;;
  esac

  # Inspect last recorded verdict
  last_verdict="$(tail -1 "$PHASE_TSV" | cut -f2)"
  if [[ "$last_verdict" == "RED" ]]; then
    OVERALL_RED=1
    if [[ "$phase" == "clean-start" ]] && ! stack_up; then
      STACK_DOWN=1
      echo "[$KEYWORD] clean-start failed and stack is down — stopping"
      break
    fi
  elif [[ "$last_verdict" == "AMBER" ]]; then
    OVERALL_AMBER=1
  fi
done

GATE_END="$(now_ms)"
TOTAL_MS="$(duration_ms "$GATE_START" "$GATE_END")"

# --- Write summary.json + REPORT.md ---
python3 - "$RUN_DIR" "$FULL_SHA" "$SHORT_SHA" "$TOTAL_MS" "$CHURN_MINUTES" "$OVERALL_RED" "$OVERALL_AMBER" "$STACK_DOWN" <<'PY'
import json, os, re, sys
from datetime import datetime, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

run_dir, full_sha, short_sha, total_ms, churn_min, overall_red, overall_amber, stack_down = sys.argv[1:9]
run = Path(run_dir)
tsv = (run / "phases.tsv").read_text(encoding="utf-8").splitlines()
phases = []
for line in tsv:
    parts = line.split("\t")
    while len(parts) < 7:
        parts.append("")
    name, verdict, exit_code, duration, log_path, counts, note = parts[:7]
    phases.append({
        "name": name,
        "verdict": verdict,
        "exitCode": int(exit_code) if str(exit_code).lstrip("-").isdigit() else exit_code,
        "durationMs": int(duration) if str(duration).isdigit() else 0,
        "logPath": log_path,
        "counts": counts,
        "note": note,
    })

def human(ms):
    try:
        ms = int(ms)
    except Exception:
        return str(ms)
    sec = ms // 1000
    m, s = divmod(sec, 60)
    return f"{m}m{s:02d}s" if m else f"{s}s"

# Extract failing tests from functional/elective/unit logs
failures = []
for phase in phases:
    if phase["verdict"] != "RED":
        continue
    log = Path(phase["logPath"]) if phase["logPath"] not in ("-", "") else None
    if not log or not log.exists():
        continue
    text = log.read_text(encoding="utf-8", errors="replace")
    for m in re.finditer(r"^\[ERROR\]\s+(\S+)(?:\s+--\s+Time).*", text, re.M):
        failures.append({"phase": phase["name"], "test": m.group(1), "error": "see log", "classification": "unclassified"})
    for m in re.finditer(r"<<< FAILURE! -- in (\S+)", text):
        failures.append({"phase": phase["name"], "test": m.group(1), "error": "FAILURE", "classification": "unclassified"})
    for m in re.finditer(r"<<< ERROR! -- in (\S+)", text):
        failures.append({"phase": phase["name"], "test": m.group(1), "error": "ERROR", "classification": "unclassified"})

# Dedup failures
seen = set()
uniq = []
for f in failures:
    key = (f["phase"], f["test"])
    if key in seen:
        continue
    seen.add(key)
    uniq.append(f)
failures = uniq

health = {}
hj = run / "health" / "health.json"
if hj.exists():
    health = json.loads(hj.read_text(encoding="utf-8"))

if int(overall_red):
    overall = "NO-GO"
elif int(overall_amber) or (health.get("verdict") == "AMBER"):
    overall = "GO with reservations"
else:
    overall = "GO"

toronto = datetime.now(ZoneInfo("America/Toronto")).strftime("%Y-%m-%d %H:%M %Z")
utc = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

preflight = ""
pf = run / "preflight.txt"
if pf.exists():
    preflight = pf.read_text(encoding="utf-8", errors="replace")

cpus = re.search(r"cpus:\s*(.+)", preflight)
mem = "see preflight"
if "Mem:" in preflight:
    for line in preflight.splitlines():
        if line.strip().startswith("Mem:"):
            mem = line.strip()
            break

summary = {
    "keyword": "quality-gate",
    "tipSha": full_sha,
    "tipShortSha": short_sha,
    "generatedAtUtc": utc,
    "generatedAtToronto": toronto,
    "stackMode": "HA + JavaMelody",
    "churnMinutes": int(churn_min),
    "totalDurationMs": int(total_ms),
    "totalDurationHuman": human(total_ms),
    "overallVerdict": overall,
    "stackDown": bool(int(stack_down)),
    "machine": {
        "cpus": cpus.group(1).strip() if cpus else "unknown",
        "memoryLine": mem,
    },
    "phases": phases,
    "failures": failures,
    "healthVerdict": health.get("verdict"),
    "runDir": str(run),
}

(run / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")

# REPORT.md
lines = []
lines.append("# Quality gate report")
lines.append("")
lines.append(f"- **Tip SHA:** `{full_sha}`")
lines.append(f"- **Date (America/Toronto):** {toronto}")
lines.append(f"- **Stack mode:** HA + JavaMelody (default runtime integrity, default proxy)")
lines.append(f"- **Total duration:** {human(total_ms)}")
lines.append(f"- **Machine:** cpus={summary['machine']['cpus']}; {mem}")
lines.append(f"- **Churn:** 2 concurrent shells × {churn_min} min")
lines.append(f"- **Overall verdict:** **{overall}**")
lines.append("")
lines.append("## Phase table")
lines.append("")
lines.append("| Phase | Verdict | Duration | Counts | Log |")
lines.append("| --- | --- | --- | --- | --- |")
for p in phases:
    rel = p["logPath"]
    if rel.startswith(str(run)):
        rel = os.path.relpath(rel, run)
    lines.append(
        f"| {p['name']} | {p['verdict']} | {human(p['durationMs'])} | {p['counts']} | `{rel}` |"
    )
lines.append("")
lines.append("## Failures")
lines.append("")
if not failures:
    lines.append("_No parsed test failures (phases may still be RED for infra reasons)._")
else:
    lines.append("| Phase | Test | Error | Classification |")
    lines.append("| --- | --- | --- | --- |")
    for f in failures:
        lines.append(f"| {f['phase']} | `{f['test']}` | {f['error']} | {f['classification']} |")
lines.append("")
lines.append("_Classification values: product bug / flaky / environment (cloud VM) / test bug — filled by the operator/agent after log review._")
lines.append("")
lines.append("## Health highlights")
lines.append("")
if health:
    lines.append(f"- Health verdict: **{health.get('verdict')}**")
    jm = health.get("javamelody", {}).get("perApp", {})
    for app, block in jm.items():
        lines.append(f"- **{app}** top HTTP:")
        for row in (block.get("topHttp") or [])[:5]:
            lines.append(
                f"  - {row.get('name')} mean={row.get('mean')}ms max={row.get('maximum')}ms err%={row.get('errorRatePct')}"
            )
        lines.append(f"  top SQL:")
        for row in (block.get("topSql") or [])[:5]:
            name = str(row.get("name") or "").replace("\n", " ")[:100]
            lines.append(f"  - {name} mean={row.get('mean')}ms max={row.get('maximum')}ms")
    cons = health.get("containers", {})
    lines.append(
        f"- Containers: maxRestarts={cons.get('maxRestarts')} oom={cons.get('oomKills')} unhealthy={cons.get('unhealthy')} errorLines={cons.get('totalErrorLines')}"
    )
    for sig in (cons.get("topErrorSignatures") or [])[:8]:
        lines.append(f"  - [{sig.get('service')}] ×{sig.get('count')}: {sig.get('signature')}")
    lines.append("")
    lines.append("See also `health/HEALTH.md` and `health/container-logs.md`.")
else:
    lines.append("_Health phase did not produce health.json._")
lines.append("")
lines.append("## Overall verdict rationale")
lines.append("")
if overall == "GO":
    lines.append("All phases GREEN; stack health within thresholds. Main is healthy to move to the next batch.")
elif overall == "GO with reservations":
    lines.append("No RED phases (or only AMBER health/threshold signals). Review reservations before the next batch.")
else:
    lines.append("One or more RED phases. Do not treat main as ready for the next batch until failures are classified and addressed (outside this gate PR for product bugs).")
lines.append("")
lines.append("## Thresholds")
lines.append("")
lines.append("Initial thresholds live in `config/quality-gate/thresholds.json`. This first run is the baseline; update thresholds from observed values plus margin after reviewing HEALTH.md.")
lines.append("")

(run / "REPORT.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"summary: {run / 'summary.json'}")
print(f"report: {run / 'REPORT.md'}")
print(f"overall: {overall}")
PY

echo "[$KEYWORD] artifacts under $RUN_DIR"
cat "$RUN_DIR/summary.json" | python3 -c 'import json,sys; print("OVERALL:", json.load(sys.stdin)["overallVerdict"])'

if [[ "$OVERALL_RED" -ne 0 ]]; then
  exit 1
fi
exit 0
